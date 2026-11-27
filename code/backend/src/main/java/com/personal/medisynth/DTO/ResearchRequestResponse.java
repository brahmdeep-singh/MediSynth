package com.personal.medisynth.DTO;

import java.time.Instant;

import com.personal.medisynth.model.RequestStatus;
import com.personal.medisynth.model.ResearchAccessRequest;

public record ResearchRequestResponse(Long id, Long datasetId, Long researcherId, String researcherName, String researcherEmail,
        String researcherInstitution, String datasetTitle, String purpose, String dataScope, RequestStatus status,
        Instant createdAt, Instant reviewedAt, String reviewedByName, String decisionNote) {

    public static ResearchRequestResponse from(ResearchAccessRequest r, String institution) {
        return new ResearchRequestResponse(r.getId(), r.getDataset() == null ? null : r.getDataset().getId(),
                r.getResearcher().getId(), r.getResearcher().getName(),
                r.getResearcher().getEmail(), institution, r.getDatasetTitle(), r.getPurpose(), r.getDataScope(),
                r.getStatus(), r.getCreatedAt(), r.getReviewedAt(),
                r.getReviewedBy() == null ? null : r.getReviewedBy().getName(), r.getDecisionNote());
    }
}
