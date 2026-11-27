package com.personal.medisynth.service;

import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personal.medisynth.DTO.AdminProvisionRequest;
import com.personal.medisynth.DTO.UserSummary;
import com.personal.medisynth.exception.ApiException;
import com.personal.medisynth.model.AccountStatus;
import com.personal.medisynth.model.AuditAction;
import com.personal.medisynth.model.BootstrapState;
import com.personal.medisynth.model.Role;
import com.personal.medisynth.model.Users;
import com.personal.medisynth.repo.BootstrapStateRepo;
import com.personal.medisynth.repo.UserRepo;

/**
 * Transactional account creation for ADMIN users. Authorization (bootstrap secret / existing admin) is
 * checked by the callers before these methods run. Kept as a separate bean so the transaction proxy applies.
 */
@Service
public class AdminAccountService {

    private final UserRepo userRepo;
    private final BootstrapStateRepo bootstrapStateRepo;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AdminAccountService(UserRepo userRepo, BootstrapStateRepo bootstrapStateRepo,
            PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepo = userRepo;
        this.bootstrapStateRepo = bootstrapStateRepo;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    /**
     * One-time first admin. The bootstrap_state row is locked (SELECT ... FOR UPDATE), so N concurrent
     * requests are serialised and only the first can succeed; the rest see consumed = true.
     */
    @Transactional
    public UserSummary createFirstAdmin(AdminProvisionRequest req) {
        BootstrapState state = bootstrapStateRepo.lockState()
                .orElseThrow(() -> new IllegalStateException("bootstrap_state row is missing"));
        if (state.isConsumed() || userRepo.existsByGrantedRole(Role.ADMIN)) {
            throw ApiException.conflict("BOOTSTRAP_ALREADY_USED",
                    "The one-time admin bootstrap has already been used");
        }
        Users admin = buildAdmin(req);
        state.setConsumed(true);
        state.setConsumedAt(Instant.now());
        state.setConsumedByUserId(admin.getId());
        auditService.record(admin.getId(), AuditAction.ADMIN_BOOTSTRAP_SUCCESS, "USER", admin.getId(), "firstAdmin=true");
        return UserSummary.from(admin);
    }

    /** Subsequent admins: only an existing, active ADMIN may create one. */
    @Transactional
    public UserSummary createAdditionalAdmin(Long actorId, AdminProvisionRequest req) {
        Users actor = userRepo.findById(actorId)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "Authentication is required"));
        if (actor.getGrantedRole() != Role.ADMIN || actor.getAccountStatus() != AccountStatus.ACTIVE) {
            throw ApiException.forbidden("FORBIDDEN", "Only active administrators can create administrators");
        }
        Users admin = buildAdmin(req);
        auditService.record(actorId, AuditAction.ADMIN_CREATED_BY_ADMIN, "USER", admin.getId(), null);
        return UserSummary.from(admin);
    }

    private Users buildAdmin(AdminProvisionRequest req) {
        String email = InputNormalizer.email(req.email());
        if (userRepo.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "This email is already registered");
        }
        Users admin = new Users();
        admin.setName(InputNormalizer.text(req.name()));
        admin.setEmail(email);
        admin.setPhone(InputNormalizer.text(req.phone()));
        admin.setPasswordHash(passwordEncoder.encode(req.password()));
        admin.setGrantedRole(Role.ADMIN);
        admin.setAccountStatus(AccountStatus.ACTIVE);
        try {
            return userRepo.saveAndFlush(admin);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "This email is already registered");
        }
    }
}
