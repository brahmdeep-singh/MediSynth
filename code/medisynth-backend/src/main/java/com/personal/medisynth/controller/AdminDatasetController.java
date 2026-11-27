package com.personal.medisynth.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.personal.medisynth.DTO.*;
import com.personal.medisynth.model.*;
import com.personal.medisynth.service.*;
import com.personal.medisynth.synthetix.SyntheticRecord;

import jakarta.validation.Valid;

/** Admin: run the Synthetix pipeline. Whole /api/admin/** is ADMIN-only in SecurityConfig. */
@RestController
@RequestMapping("/api/admin/datasets")
public class AdminDatasetController {

    private final SyntheticDatasetService datasetService;

    public AdminDatasetController(SyntheticDatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @PostMapping
    public ResponseEntity<DatasetResponse> create(@AuthenticationPrincipal UserPrincipal admin,
            @Valid @RequestBody CreateDatasetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(datasetService.create(admin.getId(), request));
    }

    @GetMapping
    public PageResponse<DatasetResponse> list(@RequestParam(required = false) DatasetStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return datasetService.list(status, page, size);
    }

    @GetMapping("/summary")
    public DatasetSummaryResponse summary() {
        return datasetService.summary();
    }

    @GetMapping("/columns")
    public java.util.List<DatasetColumnResponse> columns() {
        return datasetService.columns();
    }

    @GetMapping("/{id}")
    public DatasetResponse get(@PathVariable Long id) {
        return datasetService.get(id);
    }

    @PostMapping("/{id}/evaluate")
    public DatasetResponse evaluate(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal admin) {
        return datasetService.evaluate(id, admin.getId());
    }

    @PostMapping("/{id}/regenerate")
    public DatasetResponse regenerate(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal admin,
            @RequestBody(required = false) @Valid RegenerateDatasetRequest body) {
        return datasetService.regenerate(id, admin.getId(), body == null ? null : body.recordCount());
    }

    @PostMapping("/{id}/retire")
    public DatasetResponse retire(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal admin) {
        return datasetService.retire(id, admin.getId());
    }

    @GetMapping("/{id}/preview")
    public java.util.List<SyntheticRecord> preview(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal admin,
            @RequestParam(defaultValue = "20") int size) {
        return datasetService.adminPreview(id, admin.getId(), size);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal admin) {
        return csv(datasetService.adminDownload(id, admin.getId()));
    }

    static ResponseEntity<byte[]> csv(SyntheticDatasetService.CsvFile file) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(file.bytes());
    }
}
