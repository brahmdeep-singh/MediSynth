package com.personal.medisynth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.personal.medisynth.DTO.AdminProvisionRequest;
import com.personal.medisynth.DTO.AppointmentResponse;
import com.personal.medisynth.DTO.DoctorProfileResponse;
import com.personal.medisynth.DTO.PageResponse;
import com.personal.medisynth.DTO.RegistrationDecisionRequest;
import com.personal.medisynth.DTO.RegistrationRequestResponse;
import com.personal.medisynth.DTO.UserResponse;
import com.personal.medisynth.DTO.UserStatusUpdateRequest;
import com.personal.medisynth.DTO.UserSummary;
import com.personal.medisynth.model.AccountStatus;
import com.personal.medisynth.model.AppointmentStatus;
import com.personal.medisynth.model.RequestStatus;
import com.personal.medisynth.model.Role;
import com.personal.medisynth.model.UserPrincipal;
import com.personal.medisynth.model.VerificationStatus;
import com.personal.medisynth.service.AdminProvisioningService;
import com.personal.medisynth.service.AdminService;
import com.personal.medisynth.service.RegistrationService;

import jakarta.validation.Valid;

/** Everything under /api/admin/** requires an ACTIVE ADMIN (enforced in SecurityConfig and re-checked in services). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final RegistrationService registrationService;
    private final AdminProvisioningService provisioningService;

    public AdminController(AdminService adminService, RegistrationService registrationService,
            AdminProvisioningService provisioningService) {
        this.adminService = adminService;
        this.registrationService = registrationService;
        this.provisioningService = provisioningService;
    }

    @GetMapping("/users")
    public PageResponse<UserResponse> users(@RequestParam(required = false) Role role,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return adminService.listUsers(role, status, page, size);
    }

    @GetMapping("/patients")
    public PageResponse<UserResponse> patients(@RequestParam(required = false) AccountStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return adminService.listPatients(status, page, size);
    }

    @GetMapping("/doctors")
    public PageResponse<DoctorProfileResponse> doctors(
            @RequestParam(required = false) VerificationStatus verificationStatus,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return adminService.listDoctors(verificationStatus, page, size);
    }

    @GetMapping("/appointments")
    public PageResponse<AppointmentResponse> appointments(@RequestParam(required = false) AppointmentStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return adminService.listAppointments(status, page, size);
    }

    @GetMapping("/registration-requests")
    public PageResponse<RegistrationRequestResponse> registrationRequests(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) Role requestedRole,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return adminService.listRequests(status, requestedRole, page, size);
    }

    @GetMapping("/registration-requests/{id}")
    public RegistrationRequestResponse registrationRequest(@PathVariable Long id) {
        return adminService.getRequest(id);
    }

    @PatchMapping("/registration-requests/{id}/approve")
    public RegistrationRequestResponse approve(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal admin) {
        return registrationService.approve(id, admin.getId());
    }

    @PatchMapping("/registration-requests/{id}/reject")
    public RegistrationRequestResponse reject(@PathVariable Long id,
            @RequestBody(required = false) @Valid RegistrationDecisionRequest body,
            @AuthenticationPrincipal UserPrincipal admin) {
        return registrationService.reject(id, admin.getId(), body == null ? null : body.reason());
    }

    @PatchMapping("/users/{id}/status")
    public UserResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UserStatusUpdateRequest body,
            @AuthenticationPrincipal UserPrincipal admin) {
        return adminService.updateUserStatus(admin.getId(), id, body);
    }

    /** Subsequent admins: created by an existing admin (the first one comes from the bootstrap flow). */
    @PostMapping("/users/admins")
    public ResponseEntity<UserSummary> createAdmin(@Valid @RequestBody AdminProvisionRequest body,
            @AuthenticationPrincipal UserPrincipal admin) {
        return ResponseEntity.status(HttpStatus.CREATED).body(provisioningService.createAdditionalAdmin(admin.getId(), body));
    }
}
