package com.personal.medisynth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.personal.medisynth.DTO.AdminProvisionRequest;
import com.personal.medisynth.DTO.UserSummary;
import com.personal.medisynth.exception.ApiException;
import com.personal.medisynth.model.AuditAction;

import jakarta.annotation.PostConstruct;

/**
 * Developer-only first-admin provisioning. Authorization is the server-side bootstrap secret
 * (environment variable ADMIN_BOOTSTRAP_SECRET). Selecting an option in the UI proves nothing.
 *
 * Deliberately NOT transactional: failed attempts are audited in their own transaction (REQUIRES_NEW)
 * so the audit row is kept even though the request is rejected.
 */
@Service
public class AdminProvisioningService {

    private static final int MIN_SECRET_LENGTH = 32;

    @Value("${app.admin-bootstrap.secret:}")
    private String bootstrapSecret;

    private final AdminAccountService adminAccountService;
    private final AuditService auditService;

    public AdminProvisioningService(AdminAccountService adminAccountService, AuditService auditService) {
        this.adminAccountService = adminAccountService;
        this.auditService = auditService;
    }

    @PostConstruct
    void validateConfiguration() {
        if (bootstrapSecret != null && !bootstrapSecret.isBlank() && bootstrapSecret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "ADMIN_BOOTSTRAP_SECRET must be at least 32 characters (or empty to disable bootstrap)");
        }
    }

    public UserSummary bootstrapFirstAdmin(String providedSecret, AdminProvisionRequest request) {
        // Same response whether bootstrap is disabled or the secret is wrong: nothing is revealed to a guesser.
        if (bootstrapSecret == null || bootstrapSecret.isBlank()) {
            fail("reason=BOOTSTRAP_DISABLED");
            throw forbidden();
        }
        if (!secretMatches(providedSecret)) {
            fail("reason=INVALID_SECRET");
            throw forbidden();
        }
        try {
            return adminAccountService.createFirstAdmin(request);
        } catch (ApiException e) {
            fail("reason=" + e.getCode());
            throw e;
        }
    }

    public UserSummary createAdditionalAdmin(Long actorId, AdminProvisionRequest request) {
        return adminAccountService.createAdditionalAdmin(actorId, request);
    }

    private void fail(String metadata) {
        auditService.recordIndependent(null, AuditAction.ADMIN_BOOTSTRAP_FAILED, "ADMIN_BOOTSTRAP", null, metadata);
    }

    private static ApiException forbidden() {
        return ApiException.forbidden("ADMIN_PROVISIONING_FORBIDDEN", "Admin provisioning is not authorized");
    }

    /** Constant-time comparison of SHA-256 digests (no length or prefix leakage). */
    private boolean secretMatches(String provided) {
        if (provided == null || provided.isEmpty()) {
            return false;
        }
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] a = sha.digest(bootstrapSecret.getBytes(StandardCharsets.UTF_8));
            byte[] b = sha.digest(provided.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(a, b);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
