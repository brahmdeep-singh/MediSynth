-- V2: lab technician + researcher roles, lab test requests, dataset access requests.
-- V1 is never edited (Flyway checksums it); this migration only adds to it.

-- Two more roles can now be granted. Public registration can still never request ADMIN.
ALTER TABLE users DROP CONSTRAINT ck_users_role;
ALTER TABLE users ADD CONSTRAINT ck_users_role
    CHECK (granted_role IN ('PATIENT', 'DOCTOR', 'ADMIN', 'LAB_TECHNICIAN', 'RESEARCHER'));

ALTER TABLE registration_requests DROP CONSTRAINT ck_reg_requested_role;
ALTER TABLE registration_requests ADD CONSTRAINT ck_reg_requested_role
    CHECK (requested_role IN ('PATIENT', 'DOCTOR', 'LAB_TECHNICIAN', 'RESEARCHER'));

CREATE TABLE lab_technician_profiles (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users (id),
    department   VARCHAR(100) NOT NULL,
    employee_id  VARCHAR(50)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_lab_user UNIQUE (user_id),
    CONSTRAINT uq_lab_employee_id UNIQUE (employee_id)
);

CREATE TABLE researcher_profiles (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users (id),
    institution    VARCHAR(150) NOT NULL,
    research_area  VARCHAR(200) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_researcher_user UNIQUE (user_id)
);

CREATE TABLE lab_test_requests (
    id               BIGSERIAL PRIMARY KEY,
    patient_id       BIGINT        NOT NULL REFERENCES users (id),
    ordered_by       BIGINT        NOT NULL REFERENCES users (id),
    appointment_id   BIGINT        REFERENCES appointments (id),
    test_type        VARCHAR(100)  NOT NULL,
    clinical_notes   VARCHAR(500),
    status           VARCHAR(20)   NOT NULL,
    processed_by     BIGINT        REFERENCES users (id),
    result_summary   VARCHAR(1000),
    started_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_lab_status CHECK (status IN ('REQUESTED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    -- a started/finished test must say who handled it; a finished one must carry a result
    CONSTRAINT ck_lab_processed CHECK (status IN ('REQUESTED', 'CANCELLED') OR processed_by IS NOT NULL),
    CONSTRAINT ck_lab_result CHECK (status <> 'COMPLETED' OR (result_summary IS NOT NULL AND completed_at IS NOT NULL))
);
CREATE INDEX idx_lab_status_created ON lab_test_requests (status, created_at);
CREATE INDEX idx_lab_patient ON lab_test_requests (patient_id, created_at DESC);
CREATE INDEX idx_lab_ordered_by ON lab_test_requests (ordered_by, created_at DESC);

-- Researchers ask for access to synthetic data. There is no dataset catalogue yet, so a request names
-- the data wanted in free text; the admin's decision is recorded here and audited.
CREATE TABLE research_access_requests (
    id             BIGSERIAL PRIMARY KEY,
    researcher_id  BIGINT        NOT NULL REFERENCES users (id),
    dataset_title  VARCHAR(150)  NOT NULL,
    purpose        VARCHAR(1000) NOT NULL,
    data_scope     VARCHAR(500),
    status         VARCHAR(30)   NOT NULL,
    reviewed_by    BIGINT        REFERENCES users (id),
    reviewed_at    TIMESTAMPTZ,
    decision_note  VARCHAR(500),
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_research_status CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_research_review CHECK (
        (status = 'PENDING_APPROVAL' AND reviewed_at IS NULL AND reviewed_by IS NULL)
        OR (status <> 'PENDING_APPROVAL' AND reviewed_at IS NOT NULL AND reviewed_by IS NOT NULL))
);
CREATE INDEX idx_research_status ON research_access_requests (status, created_at DESC);
CREATE INDEX idx_research_researcher ON research_access_requests (researcher_id, created_at DESC);
