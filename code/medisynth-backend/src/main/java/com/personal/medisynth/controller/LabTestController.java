package com.personal.medisynth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.personal.medisynth.DTO.*;
import com.personal.medisynth.model.*;
import com.personal.medisynth.service.*;

import jakarta.validation.Valid;

/** Lab test workflow. Role rules are in SecurityConfig; ownership is re-checked in LabTestService. */
@RestController
@RequestMapping("/api")
public class LabTestController {

    private final LabTestService labTestService;

    public LabTestController(LabTestService labTestService) {
        this.labTestService = labTestService;
    }

    /** DOCTOR: order a test for a patient of one of their own appointments. */
    @PostMapping("/lab-tests")
    public ResponseEntity<LabTestResponse> order(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody OrderLabTestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(labTestService.order(principal.getId(), request));
    }

    /** PATIENT: tests ordered for me. DOCTOR: tests I ordered. */
    @GetMapping("/lab-tests/my")
    public PageResponse<LabTestResponse> mine(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) LabTestStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return labTestService.listMine(principal, status, page, size);
    }

    /** DOCTOR: cancel a test that has not been started. */
    @PostMapping("/lab-tests/{id}/cancel")
    public LabTestResponse cancel(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return labTestService.cancel(id, principal.getId());
    }

    /** LAB_TECHNICIAN: the work queue (oldest first). */
    @GetMapping("/lab/tests")
    public PageResponse<LabTestResponse> queue(@RequestParam(required = false) LabTestStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return labTestService.queue(status, page, size);
    }

    @PatchMapping("/lab/tests/{id}/start")
    public LabTestResponse start(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return labTestService.start(id, principal.getId());
    }

    @PatchMapping("/lab/tests/{id}/complete")
    public LabTestResponse complete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CompleteLabTestRequest request) {
        return labTestService.complete(id, principal.getId(), request.resultSummary());
    }
}
