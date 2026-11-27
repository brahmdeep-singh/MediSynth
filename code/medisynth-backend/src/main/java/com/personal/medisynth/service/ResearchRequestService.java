package com.personal.medisynth.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personal.medisynth.DTO.CreateResearchRequest;
import com.personal.medisynth.DTO.PageResponse;
import com.personal.medisynth.DTO.ResearchRequestResponse;
import com.personal.medisynth.exception.ApiException;
import com.personal.medisynth.model.AuditAction;
import com.personal.medisynth.model.DatasetStatus;
import com.personal.medisynth.model.SyntheticDataset;
import com.personal.medisynth.repo.SyntheticDatasetRepo;
import com.personal.medisynth.model.RequestStatus;
import com.personal.medisynth.model.ResearchAccessRequest;
import com.personal.medisynth.model.ResearcherProfile;
import com.personal.medisynth.model.Users;
import com.personal.medisynth.repo.ResearchAccessRequestRepo;
import com.personal.medisynth.repo.ResearcherProfileRepo;
import com.personal.medisynth.repo.UserRepo;

/**
 * Researchers ask for access to a catalogue dataset (or describe other data they want); admins approve or reject.
 * Approving a catalogue request is what lets the researcher preview and download that dataset
 * (see SyntheticDatasetService); a free-text request only records the decision.
 */
@Service
public class ResearchRequestService {

    static final int MAX_PENDING_PER_RESEARCHER = 10;

    private final ResearchAccessRequestRepo requestRepo;
    private final ResearcherProfileRepo profileRepo;
    private final UserRepo userRepo;
    private final AuditService auditService;
    private final SyntheticDatasetRepo datasetRepo;

    public ResearchRequestService(ResearchAccessRequestRepo requestRepo, ResearcherProfileRepo profileRepo,
            UserRepo userRepo, AuditService auditService, SyntheticDatasetRepo datasetRepo) {
        this.requestRepo = requestRepo;
        this.profileRepo = profileRepo;
        this.userRepo = userRepo;
        this.auditService = auditService;
        this.datasetRepo = datasetRepo;
    }

    // ------------------------------------------------------------------ researcher

    @Transactional
    public ResearchRequestResponse submit(Long researcherId, CreateResearchRequest body) {
        Users researcher = userRepo.findById(researcherId)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "Authentication is required"));
        if (requestRepo.countByResearcherIdAndStatus(researcherId, RequestStatus.PENDING_APPROVAL)
                >= MAX_PENDING_PER_RESEARCHER) {
            throw ApiException.conflict("TOO_MANY_PENDING_REQUESTS",
                    "You already have " + MAX_PENDING_PER_RESEARCHER + " requests awaiting review");
        }
        SyntheticDataset dataset = null;
        String title = InputNormalizer.blankToNull(body.datasetTitle());
        if (body.datasetId() != null) {
            dataset = datasetRepo.findById(body.datasetId())
                    .filter(d -> d.getStatus() == DatasetStatus.READY)
                    .orElseThrow(() -> ApiException.notFound("DATASET_NOT_FOUND", "Dataset not found"));
            if (requestRepo.existsByResearcherIdAndDatasetIdAndStatusIn(researcherId, dataset.getId(),
                    List.of(RequestStatus.PENDING_APPROVAL, RequestStatus.APPROVED))) {
                throw ApiException.conflict("DUPLICATE_DATASET_REQUEST",
                        "You already have a pending or approved request for this dataset");
            }
            title = dataset.getName();
        } else if (title == null) {
            throw ApiException.badRequest("DATASET_REQUIRED", "Choose a dataset or describe the data you need");
        }
        ResearchAccessRequest r = new ResearchAccessRequest();
        r.setResearcher(researcher);
        r.setDataset(dataset);
        r.setDatasetTitle(title);
        r.setPurpose(InputNormalizer.text(body.purpose()));
        r.setDataScope(InputNormalizer.blankToNull(body.dataScope()));
        r.setStatus(RequestStatus.PENDING_APPROVAL);
        requestRepo.saveAndFlush(r);
        auditService.record(researcherId, AuditAction.RESEARCH_REQUEST_SUBMITTED, "RESEARCH_REQUEST", r.getId(), null);
        return ResearchRequestResponse.from(r, institutionOf(researcherId));
    }

    @Transactional(readOnly = true)
    public PageResponse<ResearchRequestResponse> listMine(Long researcherId, int page, int size) {
        Pageable p = AdminService.pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ResearchAccessRequest> result = requestRepo.findByResearcherId(researcherId, p);
        String institution = institutionOf(researcherId);
        return PageResponse.of(result.map(r -> ResearchRequestResponse.from(r, institution)));
    }

    // ------------------------------------------------------------------ admin

    @Transactional(readOnly = true)
    public PageResponse<ResearchRequestResponse> listAll(RequestStatus status, int page, int size) {
        Pageable p = AdminService.pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ResearchAccessRequest> result = status == null ? requestRepo.findAllBy(p) : requestRepo.findByStatus(status, p);
        Set<Long> ids = result.getContent().stream().map(r -> r.getResearcher().getId()).collect(Collectors.toSet());
        Map<Long, String> institutions = new HashMap<>();
        if (!ids.isEmpty()) {
            for (ResearcherProfile rp : profileRepo.findByUserIdIn(ids)) {
                institutions.put(rp.getUser().getId(), rp.getInstitution());
            }
        }
        return PageResponse.of(result.map(r -> ResearchRequestResponse.from(r, institutions.get(r.getResearcher().getId()))));
    }

    @Transactional
    public ResearchRequestResponse decide(Long requestId, Long adminId, boolean approve, String note) {
        Users admin = userRepo.findById(adminId)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "Authentication is required"));
        ResearchAccessRequest r = requestRepo.findByIdForUpdate(requestId)
                .orElseThrow(() -> ApiException.notFound("RESEARCH_REQUEST_NOT_FOUND", "Research request not found"));
        if (r.getStatus() != RequestStatus.PENDING_APPROVAL) {
            throw ApiException.conflict("REQUEST_ALREADY_DECIDED",
                    "This request has already been " + r.getStatus().name().toLowerCase());
        }
        if (approve && r.getDataset() != null && r.getDataset().getStatus() != DatasetStatus.READY) {
            throw ApiException.conflict("DATASET_NOT_AVAILABLE",
                    "This dataset is not ready or has been retired, so access cannot be granted");
        }
        r.setStatus(approve ? RequestStatus.APPROVED : RequestStatus.REJECTED);
        r.setReviewedBy(admin);
        r.setReviewedAt(Instant.now());
        r.setDecisionNote(InputNormalizer.blankToNull(note));
        auditService.record(adminId, approve ? AuditAction.RESEARCH_REQUEST_APPROVED : AuditAction.RESEARCH_REQUEST_REJECTED,
                "RESEARCH_REQUEST", r.getId(), "researcherId=" + r.getResearcher().getId());
        return ResearchRequestResponse.from(r, institutionOf(r.getResearcher().getId()));
    }

    private String institutionOf(Long userId) {
        return profileRepo.findByUserId(userId).map(ResearcherProfile::getInstitution).orElse(null);
    }
}
