package com.personal.medisynth.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.personal.medisynth.DTO.AppointmentResponse;
import com.personal.medisynth.DTO.DoctorProfileResponse;
import com.personal.medisynth.DTO.DoctorPublicResponse;
import com.personal.medisynth.DTO.PageResponse;
import com.personal.medisynth.DTO.UpdateDoctorProfileRequest;
import com.personal.medisynth.model.AppointmentStatus;
import com.personal.medisynth.model.UserPrincipal;
import com.personal.medisynth.service.AppointmentService;
import com.personal.medisynth.service.DoctorService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    private final DoctorService doctorService;
    private final AppointmentService appointmentService;

    public DoctorController(DoctorService doctorService, AppointmentService appointmentService) {
        this.doctorService = doctorService;
        this.appointmentService = appointmentService;
    }

    /** Any signed-in user can browse verified, active doctors. */
    @GetMapping
    public PageResponse<DoctorPublicResponse> list(@RequestParam(required = false) String specialization,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return doctorService.listBookable(specialization, page, size);
    }

    @GetMapping("/{id}")
    public DoctorPublicResponse get(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal caller) {
        return doctorService.getPublic(id, caller);
    }

    @GetMapping("/me")
    public DoctorProfileResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return doctorService.getMe(principal.getId());
    }

    @PutMapping("/me")
    public DoctorProfileResponse updateMe(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateDoctorProfileRequest request) {
        return doctorService.updateMe(principal.getId(), request);
    }

    @GetMapping("/me/appointments")
    public PageResponse<AppointmentResponse> myAppointments(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return appointmentService.listMine(principal, status, page, size);
    }
}
