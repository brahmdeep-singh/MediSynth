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

import com.personal.medisynth.DTO.AppointmentResponse;
import com.personal.medisynth.DTO.CancelAppointmentRequest;
import com.personal.medisynth.DTO.CreateAppointmentRequest;
import com.personal.medisynth.DTO.PageResponse;
import com.personal.medisynth.DTO.UpdateAppointmentStatusRequest;
import com.personal.medisynth.model.AppointmentStatus;
import com.personal.medisynth.model.UserPrincipal;
import com.personal.medisynth.service.AppointmentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    /** Patients only. The patient is the caller; any patient id in the body is ignored (not even bound). */
    @PostMapping
    public ResponseEntity<AppointmentResponse> create(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateAppointmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(appointmentService.create(principal.getId(), request));
    }

    /** Patient: their appointments. Doctor: appointments assigned to them. */
    @GetMapping("/my")
    public PageResponse<AppointmentResponse> my(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return appointmentService.listMine(principal, status, page, size);
    }

    @GetMapping("/{id}")
    public AppointmentResponse get(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return appointmentService.getById(id, principal);
    }

    /**
     * Patient: may only set CANCELLED. Doctor: CONFIRMED / COMPLETED / CANCELLED on their own appointments.
     * Admin: any valid transition (audited).
     */
    @PatchMapping("/{id}/status")
    public AppointmentResponse updateStatus(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateAppointmentStatusRequest request) {
        return appointmentService.changeStatus(id, principal, request.status(), request.reason());
    }

    /** Documented cancellation endpoint: POST /api/appointments/{id}/cancel with an optional {"reason": "..."}. */
    @PostMapping("/{id}/cancel")
    public AppointmentResponse cancel(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) @Valid CancelAppointmentRequest request) {
        return appointmentService.cancel(id, principal, request == null ? null : request.reason());
    }
}
