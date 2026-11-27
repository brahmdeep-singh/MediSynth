package com.personal.medisynth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.personal.medisynth.DTO.*;
import com.personal.medisynth.model.*;
import com.personal.medisynth.service.*;
import com.personal.medisynth.synthetix.SyntheticRecord;

/**
 * Researcher: browse READY datasets, and preview or download the ones an admin has approved.
 * Whole /api/research/** is RESEARCHER-only in SecurityConfig; the service re-checks the approval for every call.
 */
@RestController
@RequestMapping("/api/research/datasets")
public class ResearchDatasetController {

    private final SyntheticDatasetService datasetService;

    public ResearchDatasetController(SyntheticDatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @GetMapping
    public PageResponse<CatalogueItemResponse> catalogue(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return datasetService.catalogue(principal.getId(), page, size);
    }

    @GetMapping("/columns")
    public java.util.List<DatasetColumnResponse> columns() {
        return datasetService.columns();
    }

    @GetMapping("/{id}")
    public CatalogueItemResponse one(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return datasetService.catalogueItem(id, principal.getId());
    }

    @GetMapping("/{id}/preview")
    public java.util.List<SyntheticRecord> preview(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "20") int size) {
        return datasetService.researcherPreview(id, principal.getId(), size);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return AdminDatasetController.csv(datasetService.researcherDownload(id, principal.getId()));
    }
}
