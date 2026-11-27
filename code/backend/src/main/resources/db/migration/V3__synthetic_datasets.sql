-- V3: Synthetix synthetic datasets. V1/V2 are never edited (Flyway checksums them); this migration only adds.

CREATE TABLE synthetic_datasets (
    id                    BIGSERIAL PRIMARY KEY,
    name                  VARCHAR(150)     NOT NULL,
    description           VARCHAR(500),
    status                VARCHAR(30)      NOT NULL,
    record_count          INTEGER          NOT NULL,
    seed                  BIGINT           NOT NULL,
    version               INTEGER          NOT NULL,
    -- what the generator learned from: coarse suppressed aggregates only, never rows
    profile_source        VARCHAR(30)      NOT NULL,
    profile_sample_bucket VARCHAR(20)      NOT NULL,
    profile_age_weights   VARCHAR(300)     NOT NULL,
    profile_gender_weights VARCHAR(100)    NOT NULL,
    -- evaluation (all NULL until the dataset has been evaluated)
    overall_score         DOUBLE PRECISION,
    marginal_score        DOUBLE PRECISION,
    relationship_score    DOUBLE PRECISION,
    validity_score        DOUBLE PRECISION,
    privacy_score         DOUBLE PRECISION,
    k_anonymity_share     DOUBLE PRECISION,
    min_class_size        INTEGER,
    unique_share          DOUBLE PRECISION,
    column_scores         TEXT,
    relationship_checks   TEXT,
    failure_reasons       TEXT,
    evaluated_at          TIMESTAMPTZ,
    created_by            BIGINT           NOT NULL REFERENCES users (id),
    created_at            TIMESTAMPTZ      NOT NULL,
    updated_at            TIMESTAMPTZ      NOT NULL,
    CONSTRAINT ck_dataset_status CHECK (status IN ('EVALUATING', 'READY', 'NEEDS_REGENERATION', 'RETIRED')),
    CONSTRAINT ck_dataset_count CHECK (record_count BETWEEN 500 AND 50000),
    CONSTRAINT ck_dataset_version CHECK (version >= 1),
    -- a dataset can only be READY or flagged for regeneration after an evaluation has produced a score
    CONSTRAINT ck_dataset_evaluated CHECK (status NOT IN ('READY', 'NEEDS_REGENERATION') OR (overall_score IS NOT NULL AND evaluated_at IS NOT NULL))
);
CREATE INDEX idx_dataset_status_created ON synthetic_datasets (status, created_at DESC);

CREATE TABLE synthetic_patient_records (
    id                   BIGSERIAL PRIMARY KEY,
    dataset_id           BIGINT        NOT NULL REFERENCES synthetic_datasets (id),
    synthetic_id         VARCHAR(20)   NOT NULL,
    age_years            SMALLINT      NOT NULL,
    gender               VARCHAR(10)   NOT NULL,
    blood_group          VARCHAR(4)    NOT NULL,
    bmi                  NUMERIC(4, 1) NOT NULL,
    smoker               BOOLEAN       NOT NULL,
    diagnosis_code       VARCHAR(10)   NOT NULL,
    systolic_bp          SMALLINT      NOT NULL,
    diastolic_bp         SMALLINT      NOT NULL,
    heart_rate           SMALLINT      NOT NULL,
    fasting_glucose      SMALLINT      NOT NULL,
    hba1c                NUMERIC(3, 1) NOT NULL,
    hemoglobin           NUMERIC(3, 1) NOT NULL,
    total_cholesterol    SMALLINT      NOT NULL,
    creatinine           NUMERIC(4, 2) NOT NULL,
    visits_last_12m      SMALLINT      NOT NULL,
    admitted             BOOLEAN       NOT NULL,
    length_of_stay_days  SMALLINT      NOT NULL,
    outcome              VARCHAR(12)   NOT NULL,
    CONSTRAINT uq_synth_record UNIQUE (dataset_id, synthetic_id)
);

-- A request can now name a catalogue dataset. Older free-text requests keep dataset_id NULL.
ALTER TABLE research_access_requests ADD COLUMN dataset_id BIGINT REFERENCES synthetic_datasets (id);
-- One live (pending or approved) request per researcher per dataset.
CREATE UNIQUE INDEX uq_research_active_per_dataset ON research_access_requests (researcher_id, dataset_id)
    WHERE dataset_id IS NOT NULL AND status IN ('PENDING_APPROVAL', 'APPROVED');
CREATE INDEX idx_research_dataset ON research_access_requests (dataset_id) WHERE dataset_id IS NOT NULL;
