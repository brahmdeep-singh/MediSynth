package com.personal.medisynth.DTO;

import java.time.Instant;
import java.util.List;

import com.personal.medisynth.model.DatasetStatus;
import com.personal.medisynth.model.SyntheticDataset;
import com.personal.medisynth.synthetix.EvaluationReport;

/** A dataset with its latest evaluation. report is null until the dataset has been evaluated. */
public record DatasetResponse(Long id, String name, String description, DatasetStatus status, int recordCount,
        int version, String profileSource, String profileSampleBucket, Double qualityScore, Instant evaluatedAt,
        String createdByName, Instant createdAt, Report report) {

    public record Report(double overallScore, double marginalScore, double relationshipScore, double validityScore,
            double privacyScore, int k, double kAnonymityShare, int minClassSize, double uniqueShare, boolean passed,
            List<EvaluationReport.ColumnScore> columns, List<EvaluationReport.Check> checks, List<String> failureReasons) {
    }

    public static DatasetResponse from(SyntheticDataset d) {
        Report report = null;
        if (d.getOverallScore() != null) {
            report = new Report(d.getOverallScore(), nz(d.getMarginalScore()), nz(d.getRelationshipScore()),
                    nz(d.getValidityScore()), nz(d.getPrivacyScore()), com.personal.medisynth.synthetix.DatasetEvaluator.K,
                    nz(d.getKAnonymityShare()), d.getMinClassSize() == null ? 0 : d.getMinClassSize(), nz(d.getUniqueShare()),
                    d.getStatus() == DatasetStatus.READY,
                    EvaluationReport.columnsFromText(d.getColumnScores()),
                    EvaluationReport.checksFromText(d.getRelationshipChecks()),
                    EvaluationReport.reasonsFromText(d.getFailureReasons()));
        }
        return new DatasetResponse(d.getId(), d.getName(), d.getDescription(), d.getStatus(), d.getRecordCount(),
                d.getVersion(), d.getProfileSource(), d.getProfileSampleBucket(), d.getOverallScore(), d.getEvaluatedAt(),
                d.getCreatedBy() == null ? null : d.getCreatedBy().getName(), d.getCreatedAt(), report);
    }

    private static double nz(Double v) {
        return v == null ? 0 : v;
    }
}
