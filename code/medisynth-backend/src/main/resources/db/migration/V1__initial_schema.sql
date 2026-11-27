-- MediSynth initial schema. Flyway owns the schema; Hibernate only validates it.
-- No ON DELETE CASCADE anywhere: users, appointments and audit history are never deleted automatically.

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    email           VARCHAR(254) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    granted_role    VARCHAR(20)  NOT NULL,
    account_status  VARCHAR(30)  NOT NULL,
    phone           VARCHAR(20)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_lower CHECK (email = lower(email)),
    CONSTRAINT ck_users_role CHECK (granted_role IN ('PATIENT', 'DOCTOR', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (account_status IN ('ACTIVE', 'PENDING_APPROVAL', 'REJECTED', 'SUSPENDED'))
);
CREATE INDEX idx_users_role_status ON users (granted_role, account_status);
CREATE INDEX idx_users_created_at ON users (created_at DESC);

CREATE TABLE registration_requests (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT       NOT NULL REFERENCES users (id),
    requested_role     VARCHAR(20)  NOT NULL,
    status             VARCHAR(30)  NOT NULL,
    -- SHA-256 (hex) of the secret reference handed to the applicant; the raw reference is never stored.
    status_token_hash  VARCHAR(64)  NOT NULL,
    submitted_at       TIMESTAMPTZ  NOT NULL,
    reviewed_at        TIMESTAMPTZ,
    reviewed_by        BIGINT REFERENCES users (id),
    rejection_reason   VARCHAR(500),
    CONSTRAINT uq_reg_status_token UNIQUE (status_token_hash),
    -- ADMIN can never be requested through registration.
    CONSTRAINT ck_reg_requested_role CHECK (requested_role IN ('PATIENT', 'DOCTOR')),
    CONSTRAINT ck_reg_status CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
    -- A decided request must carry reviewer + timestamp; a pending one must not.
    CONSTRAINT ck_reg_review_consistency CHECK (
        (status = 'PENDING_APPROVAL' AND reviewed_at IS NULL AND reviewed_by IS NULL)
        OR (status <> 'PENDING_APPROVAL' AND reviewed_at IS NOT NULL AND reviewed_by IS NOT NULL))
);
-- At most one pending request per applicant (duplicate-pending protection at the database level).
CREATE UNIQUE INDEX uq_reg_one_pending_per_user ON registration_requests (user_id) WHERE status = 'PENDING_APPROVAL';
CREATE INDEX idx_reg_status_role ON registration_requests (status, requested_role, submitted_at DESC);

CREATE TABLE doctor_profiles (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT        NOT NULL REFERENCES users (id),
    specialization       VARCHAR(100)  NOT NULL,
    qualifications       VARCHAR(500)  NOT NULL,
    experience_years     INTEGER       NOT NULL,
    license_number       VARCHAR(50)   NOT NULL,
    consultation_fee     NUMERIC(10,2) NOT NULL DEFAULT 0,
    availability         VARCHAR(500),
    verification_status  VARCHAR(20)   NOT NULL,
    verified_at          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_doctor_user UNIQUE (user_id),
    CONSTRAINT uq_doctor_license UNIQUE (license_number),
    CONSTRAINT ck_doctor_experience CHECK (experience_years BETWEEN 0 AND 70),
    CONSTRAINT ck_doctor_fee CHECK (consultation_fee >= 0),
    CONSTRAINT ck_doctor_verification CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED'))
);
CREATE INDEX idx_doctor_verification ON doctor_profiles (verification_status);
CREATE INDEX idx_doctor_specialization ON doctor_profiles (lower(specialization));

CREATE TABLE patient_profiles (
    id                       BIGSERIAL PRIMARY KEY,
    user_id                  BIGINT       NOT NULL REFERENCES users (id),
    date_of_birth            DATE,
    gender                   VARCHAR(20),
    address                  VARCHAR(300),
    emergency_contact_name   VARCHAR(100),
    emergency_contact_phone  VARCHAR(20),
    created_at               TIMESTAMPTZ  NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_patient_user UNIQUE (user_id),
    CONSTRAINT ck_patient_gender CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'OTHER'))
);

CREATE TABLE appointments (
    id                   BIGSERIAL PRIMARY KEY,
    patient_id           BIGINT       NOT NULL REFERENCES users (id),
    doctor_id            BIGINT       NOT NULL REFERENCES doctor_profiles (id),
    start_time           TIMESTAMPTZ  NOT NULL,
    reason               VARCHAR(500) NOT NULL,
    status               VARCHAR(20)  NOT NULL,
    cancellation_reason  VARCHAR(500),
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_appt_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED'))
);
-- Double-booking protection enforced by the database, independent of application logic:
-- one live (PENDING/CONFIRMED) appointment per doctor per slot, and per patient per slot.
CREATE UNIQUE INDEX uq_appt_doctor_slot ON appointments (doctor_id, start_time) WHERE status IN ('PENDING', 'CONFIRMED');
CREATE UNIQUE INDEX uq_appt_patient_slot ON appointments (patient_id, start_time) WHERE status IN ('PENDING', 'CONFIRMED');
CREATE INDEX idx_appt_patient ON appointments (patient_id, start_time DESC);
CREATE INDEX idx_appt_doctor ON appointments (doctor_id, start_time DESC);
CREATE INDEX idx_appt_status ON appointments (status);

CREATE TABLE audit_logs (
    id           BIGSERIAL PRIMARY KEY,
    actor_id     BIGINT REFERENCES users (id),
    action       VARCHAR(60)  NOT NULL,
    target_type  VARCHAR(50),
    target_id    BIGINT,
    -- Safe metadata only (ids, status names). Never passwords, tokens or secrets.
    metadata     VARCHAR(1000),
    occurred_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_audit_occurred ON audit_logs (occurred_at DESC);
CREATE INDEX idx_audit_action ON audit_logs (action);
CREATE INDEX idx_audit_actor ON audit_logs (actor_id);

-- Single-row state used to make the first-admin bootstrap one-time and concurrency-safe
-- (the row is locked with SELECT ... FOR UPDATE during provisioning).
CREATE TABLE bootstrap_state (
    id                    INTEGER PRIMARY KEY,
    consumed              BOOLEAN     NOT NULL,
    consumed_at           TIMESTAMPTZ,
    consumed_by_user_id   BIGINT REFERENCES users (id),
    CONSTRAINT ck_bootstrap_single_row CHECK (id = 1)
);
INSERT INTO bootstrap_state (id, consumed) VALUES (1, FALSE);
