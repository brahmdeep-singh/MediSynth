# Keerat's Journal

Roll No. 1024030793

Name: Keerat Singh Brar

Backend track: Spring Boot API, security, database, and the Synthetix synthetic data engine.

---

## Week 1 — Phase 1: Problem framing and pitch

**Status:** Complete

### What I did
- Worked with the team to settle the problem: real patient data is protected (HIPAA, India's DPDP Act), simple masking can be reversed, and student teams end up testing on tiny hand-typed mock data.
- Wrote the pitch speech and fixed the project shape: a role-based Hospital Management System (MediSynth) paired with a synthetic patient data engine (Synthetix).
- Agreed the split of work with Aryan and Brahmdeep (see `docs/journals/index.md`): I take the backend and the Synthetix engine.

### Key decisions & reasoning
- **Decision:** Build the HMS and the synthetic engine as one project instead of two.
  **Why:** The HMS needs realistic data to be demonstrated, and the engine needs a real system to serve. Each justifies the other.

### Next steps
- Choose the stack and design the first database schema.

---

## Week 3 — Phase 2: Backend scaffold and database (migration V1)

**Status:** Complete

### What I did
- Scaffolded the backend in Java 21 with Spring Boot 4.1.1 under the package `com.personal.medisynth`, using a strict `controller -> service -> repository -> model` layering with request and response DTOs and bean validation at the boundary.
- Wrote Flyway migration V1: `users`, `patient_profiles`, `doctor_profiles`, `registration_requests`, `appointments`, `research_access_requests`, `audit_logs` and `bootstrap_state`.
- Set Hibernate to `ddl-auto=validate` so the schema only ever changes through migrations.
- Added `.env` handling for `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `ADMIN_BOOTSTRAP_SECRET` and `CORS_ALLOWED_ORIGINS`.

### Key decisions & reasoning
- **Decision:** Flyway owns the schema; Hibernate only validates it.
  **Why:** A silent `update` mode can hide drift between code and database. A failed validation at start-up is better than a wrong table at run time.
- **Decision:** One JSON error shape with a machine-readable code.
  **Why:** The frontend needs one error path, and stack traces must never leave the server.

### Challenges & how I solved them
- The first runs failed on missing environment variables and a JWT secret that was too short. The secret must be Base64 and at least 32 bytes; I documented this in the README and fixed the `.env`.
- A set of compile errors from the Spring Boot 4 / Jackson 3 package changes were fixed one by one from the compiler output.

### Next steps
- Authentication, roles and the admin bootstrap.

---

## Week 4 — Phase 3: Authentication, roles and security rules

**Status:** Complete

### What I did
- Implemented stateless JWT login with BCrypt password hashing and Bearer tokens.
- Password rule: 10 to 72 characters with at least one letter and one digit.
- Implemented the one-time admin bootstrap, protected by `ADMIN_BOOTSTRAP_SECRET` (at least 32 characters); once an admin exists the endpoint refuses further attempts.
- Added login rate limiting that answers with HTTP 429, a CORS allow-list, and URL rules in `SecurityConfig` (`/api/admin/**` for ADMIN, `/api/research/**` for RESEARCHER, and so on).

### Key decisions & reasoning
- **Decision:** The role and account status are never trusted from the frontend or a token claim; they are read from the database on each request.
  **Why:** Suspending a user must take effect at once, and a client must not be able to promote itself.
- **Decision:** No provisioning secret ever appears in frontend code or a `VITE_*` variable.
  **Why:** Anything in the browser bundle is public.

### Next steps
- Registration approval and appointments.

---

## Week 5 — Phase 4: Registration approval and appointments

**Status:** Complete

### What I did
- Patients register and are active at once. Doctors register as `PENDING_APPROVAL`, get a reference number, and cannot log in until an admin approves.
- Appointment booking in 30-minute slots with a conflict check (HTTP 409) and a unique index so two simultaneous bookings cannot both succeed. Allowed state changes: pending, confirmed, completed, cancelled.
- A patient asking for someone else's appointment gets 404, not 403.

### Key decisions & reasoning
- **Decision:** 404 for records that belong to another user.
  **Why:** A 403 confirms the record exists; 404 does not.
- **Decision:** Enforce slot uniqueness in the database as well as in the service.
  **Why:** A service-level check alone has a race window.

### Next steps
- Lab technician and researcher roles.

---

## Week 6 — Phase 5: LAB_TECHNICIAN and RESEARCHER roles (migration V2)

**Status:** Complete

### What I did
- Added V2: `lab_technician_profiles`, `researcher_profiles` and `lab_test_requests`.
- Lab flow: a doctor orders a test for a confirmed appointment (the patient comes from the appointment, not from the request body); a technician starts it, which assigns it to them, and completes it with a result. A different technician gets 403 `NOT_YOUR_TEST`.
- Researcher registration with admin approval, and the research access request endpoints.

### Key decisions & reasoning
- **Decision:** Only the assigned technician may complete a test.
  **Why:** Results need an accountable owner, and the audit trail should show who produced each one.

### Next steps
- The Synthetix engine.

---

## Week 8 — Phase 6: Synthetix generator

**Status:** Complete

### What I did
- Wrote the engine as pure Java in package `synthetix`, with no framework dependencies so it can be run and unit-tested alone.
- It is a seeded causal sampler: age band and gender, then BMI and smoking, then diagnosis (softmax over ten ICD-10 codes), then vitals and labs, then visits and admission.
- The only real-data input is `SourceProfile`: grouped counts by ten-year age band and gender. Groups under 5 are suppressed, samples under 50 patients fall back to built-in priors, and real counts are shrunk toward the priors (weight 50).
- Ages of 90 and over are top-coded to 90; identifiers are a shuffled `SYN-0000001` sequence.

### Key decisions & reasoning
- **Decision:** A rule-based model instead of machine learning.
  **Why:** There is no real data to train on, and the grouped-counts rule makes it easy to argue that nothing individual is learned. The Python or ML engine stays as future work behind the same service.

### Challenges & how I solved them
- The first calibration produced far too many routine check-ups (62% of patients) and almost no chronic disease. I raised the chronic and acute scores until the mix was realistic (routine about 24%, hypertension about 16%, type 2 diabetes about 6.5%).

### Next steps
- Store datasets, evaluate them, and control who can download them.

---

## Week 9 — Phase 7: Dataset service, lifecycle and access (migration V3)

**Status:** Complete

### What I did
- V3 adds `synthetic_datasets` and `synthetic_patient_records` and a `dataset_id` on research requests. Bulk rows go in through JDBC batch inserts.
- Lifecycle: EVALUATING, then READY or NEEDS_REGENERATION; regenerate uses a new seed and increases the version; any state can be RETIRED.
- Access: an approved request for that dataset is required to preview or download; retiring ends access at once; datasets that are not READY return 404; duplicate pending or approved requests are refused by a partial unique index; approving a request for a non-READY dataset returns 409.
- Admin endpoints under `/api/admin/datasets` and researcher endpoints under `/api/research/datasets`. CSV download, with `Content-Disposition` exposed through CORS. Six new audit actions.

### Key decisions & reasoning
- **Decision:** Access is per dataset, not per researcher.
  **Why:** One approval should not unlock datasets the admin has never reviewed.

### Next steps
- Tests and documentation.

---

## Week 10 — Phase 8: Tests and API documentation

**Status:** Tests written; full-suite run on my machine still to do

### What I did
- Wrote the integration and unit tests: 108 test methods in 14 classes, including `SyntheticEngineTest` and the dataset integration tests, running against a separate `medisynth_test` database.
- Wrote `docs/API.md` (all 56 endpoints) and the README, including the synthetic dataset section.

### Challenges & how I solved them
- Maven could not run in the environment where much of this was written, so I ran the engine and evaluator standalone with `javac` and `java` and used those runs to set thresholds. The Spring tests still need a full `./mvnw test` run locally.

### Next steps
- Run the whole suite and record the result.

---

## Week 10 — Phase 9: Recovering from a deleted database

**Status:** Complete

### What I did
- I accidentally deleted the `medisynth` database. I recreated an empty one with `createdb` and restarted the backend; Flyway rebuilt every table from V1 to V3.
- Recreated the admin through the bootstrap endpoint and re-created the other users.

### Key decisions & reasoning
- **Decision:** Recreate the empty database and let Flyway rebuild it, instead of running the SQL by hand.
  **Why:** Running the scripts by hand would leave Flyway's history table out of step with the schema.

### Challenges & how I solved them
- All demo users and datasets were lost with the database. Lesson: keep a small seed script for demo accounts so recovery takes minutes.
