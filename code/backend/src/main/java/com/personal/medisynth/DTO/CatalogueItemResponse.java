package com.personal.medisynth.DTO;

import java.time.Instant;

import com.personal.medisynth.model.RequestStatus;
import com.personal.medisynth.model.SyntheticDataset;

/**
 * What a researcher sees before asking for access: metadata and headline quality only, never rows.
 * accessStatus is the caller's own latest request for this dataset (null if none).
 */
public record CatalogueItemResponse(Long id, String name, String description, int recordCount, int version,
        Double qualityScore, double privacyScore, int k, Instant evaluatedAt, RequestStatus accessStatus,
        Long accessRequestId) {

    public static CatalogueItemResponse from(SyntheticDataset d, RequestStatus access, Long requestId) {
        return new CatalogueItemResponse(d.getId(), d.getName(), d.getDescription(), d.getRecordCount(), d.getVersion(),
                d.getOverallScore(), d.getPrivacyScore() == null ? 0 : d.getPrivacyScore(),
                com.personal.medisynth.synthetix.DatasetEvaluator.K, d.getEvaluatedAt(), access, requestId);
    }
}
