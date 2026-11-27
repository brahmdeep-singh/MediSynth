package com.personal.medisynth.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.personal.medisynth.DTO.AppointmentResponse;
import com.personal.medisynth.DTO.PageResponse;
import com.personal.medisynth.DTO.PatientProfileResponse;
import com.personal.medisynth.DTO.UpdatePatientProfileRequest;
import com.personal.medisynth.model.AppointmentStatus;
import com.personal.medisynth.model.UserPrincipal;
import com.personal.medisynth.service.AppointmentService;
import com.personal.medisynth.service.PatientService;

import jakarta.validation.Valid;

/** Always acts on the authenticated caller ("me"): there is no patient id in any URL, so no IDOR surface. */
@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService patientService;
    private final AppointmentService appointmentService;

    public PatientController(PatientService patientService, AppointmentService appointmentService) {
        this.patientService = patientService;
        this.appointmentService = appointmentService;
    }

    @GetMapping("/me")
    public PatientProfileResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return patientService.getMe(principal.getId());
    }

    @PutMapping("/me")
    public PatientProfileResponse updateMe(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdatePatientProfileRequest request) {
        return patientService.updateMe(principal.getId(), request);
    }

    @GetMapping("/me/appointments")
    public PageResponse<AppointmentResponse> myAppointments(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return appointmentService.listMine(principal, status, page, size);
    }
}
