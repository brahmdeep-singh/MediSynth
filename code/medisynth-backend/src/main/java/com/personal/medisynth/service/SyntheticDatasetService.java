package com.personal.medisynth.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personal.medisynth.DTO.CatalogueItemResponse;
import com.personal.medisynth.DTO.CreateDatasetRequest;
import com.personal.medisynth.DTO.DatasetColumnResponse;
import com.personal.medisynth.DTO.DatasetResponse;
import com.personal.medisynth.DTO.DatasetSummaryResponse;
import com.personal.medisynth.DTO.PageResponse;
import com.personal.medisynth.exception.ApiException;
import com.personal.medisynth.model.AuditAction;
import com.personal.medisynth.model.DatasetStatus;
import com.personal.medisynth.model.RequestStatus;
import com.personal.medisynth.model.ResearchAccessRequest;
import com.personal.medisynth.model.SyntheticDataset;
import com.personal.medisynth.model.Users;
import com.personal.medisynth.repo.ResearchAccessRequestRepo;
import com.personal.medisynth.repo.SyntheticDatasetRepo;
import com.personal.medisynth.repo.UserRepo;
import com.personal.medisynth.synthetix.DatasetEvaluator;
import com.personal.medisynth.synthetix.EvaluationReport;
import com.personal.medisynth.synthetix.SourceProfile;
import com.personal.medisynth.synthetix.SyntheticDataGenerator;
import com.personal.medisynth.synthetix.SyntheticRecord;
import com.personal.medisynth.synthetix.SyntheticSchema;

/**
 * Synthetix pipeline (activity diagram part 2): prepare -> generate -> evaluate -> READY, or regenerate and
 * evaluate again. Admins run the pipeline; approved researchers preview and download READY datasets.
 * Every step is audited. Retiring a dataset immediately ends all researcher access to it.
 */
@Service
public class SyntheticDatasetService {

    static final int DEFAULT_PREVIEW = 20;
    static final int MAX_PREVIEW = 100;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SyntheticDatasetRepo datasetRepo;
    private final SyntheticRecordStore store;
    private final SourceProfileService profileService;
    private final ResearchAccessRequestRepo requestRepo;
    private final UserRepo userRepo;
    private final AuditService auditService;

    public SyntheticDatasetService(SyntheticDatasetRepo datasetRepo, SyntheticRecordStore store,
            SourceProfileService profileService, ResearchAccessRequestRepo requestRepo, UserRepo userRepo,
            AuditService auditService) {
        this.datasetRepo = datasetRepo;
        this.store = store;
        this.profileService = profileService;
        this.requestRepo = requestRepo;
        this.userRepo = userRepo;
        this.auditService = auditService;
    }

    /** A generated CSV file ready to send. */
    public record CsvFile(String filename, byte[] bytes) {
    }

    // ------------------------------------------------------------------ admin pipeline

    @Transactional
    public DatasetResponse create(Long adminId, CreateDatasetRequest body) {
        Users admin = userRepo.findById(adminId)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "Authentication is required"));
        SourceProfile profile = profileService.currentProfile();
        long seed = body.seed() != null ? body.seed() : RANDOM.nextLong();

        SyntheticDataset d = new SyntheticDataset();
        d.setName(InputNormalizer.text(body.name()));
        d.setDescription(InputNormalizer.blankToNull(body.description()));
        d.setStatus(DatasetStatus.EVALUATING);
        d.setRecordCount(body.recordCount());
        d.setSeed(seed);
        d.setVersion(1);
        d.setProfileSource(profile.source());
        d.setProfileSampleBucket(profile.sampleSizeBucket());
        d.setProfileAgeWeights(join(profile.ageBandWeights()));
        d.setProfileGenderWeights(join(profile.genderWeights()));
        d.setCreatedBy(admin);
        datasetRepo.saveAndFlush(d);

        store.insertAll(d.getId(), SyntheticDataGenerator.generate(d.getRecordCount(), seed, profile));
        auditService.record(adminId, AuditAction.DATASET_GENERATED, "SYNTHETIC_DATASET", d.getId(),
                "records=" + d.getRecordCount() + ", profile=" + profile.source());
        return DatasetResponse.from(d);
    }

    @Transactional
    public DatasetResponse evaluate(Long datasetId, Long adminId) {
        SyntheticDataset d = lock(datasetId);
        if (d.getStatus() != DatasetStatus.EVALUATING) {
            throw ApiException.conflict("DATASET_NOT_EVALUABLE",
                    "Only a dataset awaiting evaluation can be evaluated (current status: " + d.getStatus() + ")");
        }
        List<SyntheticRecord> rows = store.loadAll(d.getId());
        if (rows.size() != d.getRecordCount()) {
            throw ApiException.conflict("DATASET_INCOMPLETE", "The dataset rows are incomplete; regenerate it");
        }
        SourceProfile profile = new SourceProfile(d.getProfileSource(), d.getProfileSampleBucket(),
                parse(d.getProfileAgeWeights()), parse(d.getProfileGenderWeights()));
        EvaluationReport report = DatasetEvaluator.evaluate(rows, profile);

        d.setOverallScore(report.overallScore());
        d.setMarginalScore(report.marginalScore());
        d.setRelationshipScore(report.relationshipScore());
        d.setValidityScore(report.validityScore());
        d.setPrivacyScore(report.privacyScore());
        d.setKAnonymityShare(report.kAnonymityShare());
        d.setMinClassSize(report.minClassSize());
        d.setUniqueShare(report.uniqueShare());
        d.setColumnScores(EvaluationReport.columnsToText(report.columns()));
        d.setRelationshipChecks(EvaluationReport.checksToText(report.checks()));
        d.setFailureReasons(EvaluationReport.reasonsToText(report.failureReasons()));
        d.setEvaluatedAt(Instant.now());
        d.setStatus(report.passed() ? DatasetStatus.READY : DatasetStatus.NEEDS_REGENERATION);
        datasetRepo.saveAndFlush(d);

        auditService.record(adminId, AuditAction.DATASET_EVALUATED, "SYNTHETIC_DATASET", d.getId(),
                String.format(Locale.ROOT, "score=%.3f, result=%s", report.overallScore(), d.getStatus()));
        return DatasetResponse.from(d);
    }

    /** Replaces the rows with a fresh sample (new seed, optionally a new size) and sends it back for evaluation. */
    @Transactional
    public DatasetResponse regenerate(Long datasetId, Long adminId, Integer newCount) {
        SyntheticDataset d = lock(datasetId);
        if (d.getStatus() != DatasetStatus.NEEDS_REGENERATION) {
            throw ApiException.conflict("DATASET_NOT_REGENERABLE",
                    "Only a dataset that failed evaluation can be regenerated (current status: " + d.getStatus() + ")");
        }
        SourceProfile profile = profileService.currentProfile();
        long seed = RANDOM.nextLong();
        int count = newCount != null ? newCount : d.getRecordCount();

        store.deleteAll(d.getId());
        store.insertAll(d.getId(), SyntheticDataGenerator.generate(count, seed, profile));
        d.setRecordCount(count);
        d.setSeed(seed);
        d.setVersion(d.getVersion() + 1);
        d.setProfileSource(profile.source());
        d.setProfileSampleBucket(profile.sampleSizeBucket());
        d.setProfileAgeWeights(join(profile.ageBandWeights()));
        d.setProfileGenderWeights(join(profile.genderWeights()));
        d.clearEvaluation();
        d.setStatus(DatasetStatus.EVALUATING);
        datasetRepo.saveAndFlush(d);

        auditService.record(adminId, AuditAction.DATASET_REGENERATED, "SYNTHETIC_DATASET", d.getId(),
                "version=" + d.getVersion() + ", records=" + count);
        return DatasetResponse.from(d);
    }

    @Transactional
    public DatasetResponse retire(Long datasetId, Long adminId) {
        SyntheticDataset d = lock(datasetId);
        if (d.getStatus() == DatasetStatus.RETIRED) {
            throw ApiException.conflict("DATASET_ALREADY_RETIRED", "This dataset is already retired");
        }
        d.setStatus(DatasetStatus.RETIRED);
        datasetRepo.saveAndFlush(d);
        auditService.record(adminId, AuditAction.DATASET_RETIRED, "SYNTHETIC_DATASET", d.getId(), null);
        return DatasetResponse.from(d);
    }

    @Transactional(readOnly = true)
    public PageResponse<DatasetResponse> list(DatasetStatus status, int page, int size) {
        Pageable p = AdminService.pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<SyntheticDataset> result = status == null ? datasetRepo.findAllBy(p) : datasetRepo.findByStatus(status, p);
        return PageResponse.of(result.map(DatasetResponse::from));
    }

    @Transactional(readOnly = true)
    public DatasetResponse get(Long id) {
        return DatasetResponse.from(datasetRepo.findWithCreatorById(id).orElseThrow(SyntheticDatasetService::notFound));
    }

    @Transactional(readOnly = true)
    public DatasetSummaryResponse summary() {
        long evaluating = datasetRepo.countByStatus(DatasetStatus.EVALUATING);
        long ready = datasetRepo.countByStatus(DatasetStatus.READY);
        long needs = datasetRepo.countByStatus(DatasetStatus.NEEDS_REGENERATION);
        long retired = datasetRepo.countByStatus(DatasetStatus.RETIRED);
        return new DatasetSummaryResponse(evaluating + ready + needs + retired, evaluating, ready, needs, retired);
    }

    // ------------------------------------------------------------------ rows: admin and approved researchers

    @Transactional
    public List<SyntheticRecord> adminPreview(Long datasetId, Long adminId, int size) {
        SyntheticDataset d = datasetRepo.findById(datasetId).orElseThrow(SyntheticDatasetService::notFound);
        return preview(d, adminId, size);
    }

    @Transactional
    public CsvFile adminDownload(Long datasetId, Long adminId) {
        SyntheticDataset d = datasetRepo.findById(datasetId).orElseThrow(SyntheticDatasetService::notFound);
        return download(d, adminId);
    }

    @Transactional
    public List<SyntheticRecord> researcherPreview(Long datasetId, Long researcherId, int size) {
        return preview(requireAccess(datasetId, researcherId), researcherId, size);
    }

    @Transactional
    public CsvFile researcherDownload(Long datasetId, Long researcherId) {
        return download(requireAccess(datasetId, researcherId), researcherId);
    }

    private List<SyntheticRecord> preview(SyntheticDataset d, Long actorId, int size) {
        int n = Math.min(Math.max(size, 1), MAX_PREVIEW);
        auditService.record(actorId, AuditAction.DATASET_PREVIEWED, "SYNTHETIC_DATASET", d.getId(), "rows=" + n);
        return store.loadFirst(d.getId(), n);
    }

    private CsvFile download(SyntheticDataset d, Long actorId) {
        List<SyntheticRecord> rows = store.loadAll(d.getId());
        StringBuilder sb = new StringBuilder(rows.size() * 110 + 256);
        sb.append(SyntheticSchema.csvHeader());
        for (SyntheticRecord r : rows) {
            SyntheticSchema.appendCsvRow(sb, r);
        }
        auditService.record(actorId, AuditAction.DATASET_DOWNLOADED, "SYNTHETIC_DATASET", d.getId(),
                "rows=" + rows.size() + ", version=" + d.getVersion());
        return new CsvFile("synthetix-dataset-" + d.getId() + "-v" + d.getVersion() + ".csv",
                sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** The dataset must be READY and the caller must hold an APPROVED request for it. Anything else is refused. */
    private SyntheticDataset requireAccess(Long datasetId, Long researcherId) {
        SyntheticDataset d = readyDataset(datasetId);
        if (!requestRepo.existsByResearcherIdAndDatasetIdAndStatus(researcherId, datasetId, RequestStatus.APPROVED)) {
            throw ApiException.forbidden("DATASET_ACCESS_NOT_GRANTED", "You do not have approved access to this dataset");
        }
        return d;
    }

    // ------------------------------------------------------------------ researcher catalogue

    @Transactional(readOnly = true)
    public PageResponse<CatalogueItemResponse> catalogue(Long researcherId, int page, int size) {
        Pageable p = AdminService.pageable(page, size, Sort.by(Sort.Direction.DESC, "evaluatedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<SyntheticDataset> result = datasetRepo.findByStatus(DatasetStatus.READY, p);
        Map<Long, ResearchAccessRequest> mine = latestRequests(researcherId);
        return PageResponse.of(result.map(d -> item(d, mine)));
    }

    @Transactional(readOnly = true)
    public CatalogueItemResponse catalogueItem(Long datasetId, Long researcherId) {
        return item(readyDataset(datasetId), latestRequests(researcherId));
    }

    public List<DatasetColumnResponse> columns() {
        return SyntheticSchema.COLUMNS.stream()
                .map(c -> new DatasetColumnResponse(c.name(), c.type(), c.role(), c.description())).toList();
    }

    private static CatalogueItemResponse item(SyntheticDataset d, Map<Long, ResearchAccessRequest> mine) {
        ResearchAccessRequest r = mine.get(d.getId());
        return CatalogueItemResponse.from(d, r == null ? null : r.getStatus(), r == null ? null : r.getId());
    }

    /** The researcher's most relevant request per dataset: an approved one wins, then pending, then the newest rejected. */
    private Map<Long, ResearchAccessRequest> latestRequests(Long researcherId) {
        Map<Long, ResearchAccessRequest> out = new HashMap<>();
        List<ResearchAccessRequest> all = new java.util.ArrayList<>(requestRepo.findByResearcherIdAndDatasetIsNotNull(researcherId));
        all.sort(Comparator.comparing(ResearchAccessRequest::getCreatedAt));
        for (ResearchAccessRequest r : all) {
            Long dsId = r.getDataset().getId();
            ResearchAccessRequest current = out.get(dsId);
            if (current == null || rank(r.getStatus()) >= rank(current.getStatus())) {
                out.put(dsId, r);
            }
        }
        return out;
    }

    private static int rank(RequestStatus s) {
        return switch (s) {
            case APPROVED -> 3;
            case PENDING_APPROVAL -> 2;
            case REJECTED -> 1;
        };
    }

    // ------------------------------------------------------------------ helpers

    private SyntheticDataset readyDataset(Long datasetId) {
        SyntheticDataset d = datasetRepo.findById(datasetId).orElseThrow(SyntheticDatasetService::notFound);
        if (d.getStatus() != DatasetStatus.READY) {
            // do not reveal that a not-ready or retired dataset exists
            throw notFound();
        }
        return d;
    }

    private SyntheticDataset lock(Long id) {
        return datasetRepo.findByIdForUpdate(id).orElseThrow(SyntheticDatasetService::notFound);
    }

    private static ApiException notFound() {
        return ApiException.notFound("DATASET_NOT_FOUND", "Dataset not found");
    }

    private static String join(double[] w) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < w.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(Locale.ROOT, "%.8f", w[i]));
        }
        return sb.toString();
    }

    private static double[] parse(String csv) {
        String[] parts = csv.split(",");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Double.parseDouble(parts[i]);
        }
        return out;
    }
}
