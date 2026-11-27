package com.personal.medisynth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.personal.medisynth.DTO.*;
import com.personal.medisynth.model.*;
import com.personal.medisynth.service.*;

import jakarta.validation.Valid;

/** Researcher: ask for access to synthetic data and track the answers. */
@RestController
@RequestMapping("/api/research")
public class ResearchController {

    private final ResearchRequestService researchService;

    public ResearchController(ResearchRequestService researchService) {
        this.researchService = researchService;
    }

    @PostMapping("/requests")
    public ResponseEntity<ResearchRequestResponse> submit(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateResearchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(researchService.submit(principal.getId(), request));
    }

    @GetMapping("/requests/my")
    public PageResponse<ResearchRequestResponse> mine(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return researchService.listMine(principal.getId(), page, size);
    }
}
