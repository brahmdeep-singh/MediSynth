package com.personal.medisynth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.personal.medisynth.DTO.AdminProvisionRequest;
import com.personal.medisynth.DTO.AuthResponse;
import com.personal.medisynth.DTO.LoginRequest;
import com.personal.medisynth.DTO.RegisterRequest;
import com.personal.medisynth.DTO.RegisterResponse;
import com.personal.medisynth.DTO.RegistrationStatusResponse;
import com.personal.medisynth.DTO.UserSummary;
import com.personal.medisynth.model.UserPrincipal;
import com.personal.medisynth.service.AdminProvisioningService;
import com.personal.medisynth.service.RegistrationService;
import com.personal.medisynth.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthorizationController {

    private final UserService userService;
    private final RegistrationService registrationService;
    private final AdminProvisioningService adminProvisioningService;

    public AuthorizationController(UserService userService, RegistrationService registrationService,
            AdminProvisioningService adminProvisioningService) {
        this.userService = userService;
        this.registrationService = registrationService;
        this.adminProvisioningService = adminProvisioningService;
    }

    /** 201 = patient created and active (token included). 202 = doctor request saved, awaiting approval (no token). */
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = userService.register(request);
        HttpStatus status = "ACTIVE".equals(response.outcome()) ? HttpStatus.CREATED : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserSummary> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userService.me(principal.getId()));
    }

    /** Public, rate-limited, and scoped to ONE request identified by its secret reference. */
    @GetMapping("/registration-status")
    public ResponseEntity<RegistrationStatusResponse> registrationStatus(@RequestParam("reference") String reference) {
        return ResponseEntity.ok(registrationService.getStatus(reference));
    }

    /**
     * "I am a developer and want to create an admin account."
     * Authorized ONLY by the server-side secret sent in the X-Bootstrap-Secret header (never in the URL or body).
     * One-time: after the first success the endpoint stays closed for good.
     */
    @PostMapping("/admin/bootstrap")
    public ResponseEntity<UserSummary> bootstrapAdmin(
            @RequestHeader(value = "X-Bootstrap-Secret", required = false) String bootstrapSecret,
            @Valid @RequestBody AdminProvisionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminProvisioningService.bootstrapFirstAdmin(bootstrapSecret, request));
    }
}
