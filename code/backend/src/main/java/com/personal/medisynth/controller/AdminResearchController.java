package com.personal.medisynth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.personal.medisynth.DTO.*;
import com.personal.medisynth.model.*;
import com.personal.medisynth.service.*;

import jakarta.validation.Valid;

/** Admin side of researcher requests, plus the audit trail. Whole /api/admin/** is ADMIN-only in SecurityConfig. */
@RestController
@RequestMapping("/api/admin")
public class AdminResearchController {

    private final ResearchRequestService researchService;
    private final AuditQueryService auditQueryService;

    public AdminResearchController(ResearchRequestService researchService, AuditQueryService auditQueryService) {
        this.researchService = researchService;
        this.auditQueryService = auditQueryService;
    }

    @GetMapping("/research-requests")
    public PageResponse<ResearchRequestResponse> list(@RequestParam(required = false) RequestStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return researchService.listAll(status, page, size);
    }

    @PatchMapping("/research-requests/{id}/approve")
    public ResearchRequestResponse approve(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal admin,
            @RequestBody(required = false) @Valid RegistrationDecisionRequest body) {
        return researchService.decide(id, admin.getId(), true, body == null ? null : body.reason());
    }

    @PatchMapping("/research-requests/{id}/reject")
    public ResearchRequestResponse reject(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal admin,
            @RequestBody(required = false) @Valid RegistrationDecisionRequest body) {
        return researchService.decide(id, admin.getId(), false, body == null ? null : body.reason());
    }

    @GetMapping("/audit-logs")
    public PageResponse<AuditLogResponse> auditLogs(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return auditQueryService.list(page, size);
    }
}
