package com.personal.medisynth.service;

import java.nio.charset.StandardCharsets;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personal.medisynth.DTO.AuthResponse;
import com.personal.medisynth.DTO.LoginRequest;
import com.personal.medisynth.DTO.RegisterRequest;
import com.personal.medisynth.DTO.RegisterResponse;
import com.personal.medisynth.DTO.UserSummary;
import com.personal.medisynth.exception.ApiException;
import com.personal.medisynth.model.AccountStatus;
import com.personal.medisynth.model.AuditAction;
import com.personal.medisynth.model.PatientProfile;
import com.personal.medisynth.model.Role;
import com.personal.medisynth.model.Users;
import com.personal.medisynth.repo.PatientProfileRepo;
import com.personal.medisynth.repo.UserRepo;
import com.personal.medisynth.validation.StrongPasswordValidator;

@Service
public class UserService {

    private final UserRepo userRepo;
    private final PatientProfileRepo patientProfileRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RegistrationService registrationService;
    private final AuditService auditService;
    /** Hash checked when the email is unknown so response time does not reveal which emails exist. */
    private final String dummyHash;

    public UserService(UserRepo userRepo, PatientProfileRepo patientProfileRepo, PasswordEncoder passwordEncoder,
            JwtService jwtService, RegistrationService registrationService, AuditService auditService) {
        this.userRepo = userRepo;
        this.patientProfileRepo = patientProfileRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.registrationService = registrationService;
        this.auditService = auditService;
        this.dummyHash = passwordEncoder.encode("not-a-real-password-for-timing-only-1");
    }

    /**
     * Public registration. The role in the payload is only a request:
     * PATIENT -> active immediately; DOCTOR / LAB_TECHNICIAN / RESEARCHER -> pending approval; ADMIN (or anything else) -> rejected.
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String requested = request.role() == null ? "" : request.role().trim().toUpperCase();
        return switch (requested) {
            case "PATIENT" -> registerPatient(request);
            case "DOCTOR" -> registrationService.submitDoctorApplication(request);
            case "LAB_TECHNICIAN" -> registrationService.submitStaffApplication(request, Role.LAB_TECHNICIAN);
            case "RESEARCHER" -> registrationService.submitStaffApplication(request, Role.RESEARCHER);
            case "ADMIN" -> throw ApiException.forbidden("ADMIN_REGISTRATION_FORBIDDEN",
                    "Administrator accounts cannot be created through public registration");
            default -> throw ApiException.badRequest("INVALID_ROLE", "Role must be PATIENT, DOCTOR, LAB_TECHNICIAN or RESEARCHER");
        };
    }

    private RegisterResponse registerPatient(RegisterRequest request) {
        String email = InputNormalizer.email(request.email());
        if (userRepo.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "This email is already registered");
        }
        Users user = new Users();
        user.setName(InputNormalizer.text(request.name()));
        user.setEmail(email);
        user.setPhone(InputNormalizer.text(request.phone()));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setGrantedRole(Role.PATIENT);           // decided here, never by the client
        user.setAccountStatus(AccountStatus.ACTIVE);  // patients need no approval
        try {
            userRepo.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "This email is already registered");
        }
        PatientProfile profile = new PatientProfile();
        profile.setUser(user);
        patientProfileRepo.save(profile);
        auditService.record(user.getId(), AuditAction.PATIENT_REGISTERED, "USER", user.getId(), null);
        return RegisterResponse.active(UserSummary.from(user), jwtService.generateToken(user));
    }

    /** Token is issued only after identity (password) AND account status are verified. */
    public AuthResponse login(LoginRequest request) {
        String email = InputNormalizer.email(request.email());
        String password = request.password();
        Users user = userRepo.findByEmail(email).orElse(null);

        boolean tooLongForBcrypt = password.getBytes(StandardCharsets.UTF_8).length > StrongPasswordValidator.MAX_BCRYPT_BYTES;
        if (user == null || tooLongForBcrypt) {
            if (!tooLongForBcrypt) {
                passwordEncoder.matches(password, dummyHash);
            }
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid email or password");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid email or password");
        }
        switch (user.getAccountStatus()) {
            case ACTIVE -> {
                // allowed
            }
            case PENDING_APPROVAL -> throw ApiException.forbidden("ACCOUNT_PENDING_APPROVAL",
                    "Your registration is awaiting admin approval");
            case REJECTED -> throw ApiException.forbidden("ACCOUNT_REJECTED",
                    "Your registration request was not approved");
            case SUSPENDED -> throw ApiException.forbidden("ACCOUNT_SUSPENDED", "This account is suspended");
        }
        return AuthResponse.bearer(jwtService.generateToken(user), jwtService.getExpirationMs(),
                UserSummary.from(user));
    }

    public UserSummary me(Long userId) {
        return userRepo.findById(userId).map(UserSummary::from)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "Authentication is required"));
    }
}
