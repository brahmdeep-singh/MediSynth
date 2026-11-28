# MediSynth backend
Spring Boot 4.1.1 · Java 21 · PostgreSQL · Flyway · Spring Security (stateless JWT) · Maven.
Structure and conventions follow the CRUMB reference project, see [docs/CRUMB_STRUCTURE.md](docs/CRUMB_STRUCTURE.md).

> **Status: written but not yet compiled or run.** It was produced in a sandbox with no access to Maven Central,
> so only a Java syntax check was possible there. Run `./mvnw test` yourself first and expect to fix small
> compile/API issues (the Boot 4 and Flyway starter names are the most likely culprits). See the checklist at the end.

## Folder tree

```
medisynth-backend/
├── pom.xml  mvnw  mvnw.cmd  .mvn/wrapper/  .gitignore  .gitattributes
├── .env.example            placeholders only
├── docker-compose.yml      optional local PostgreSQL (+ medisynth_test)
├── docker/init-test-db.sql
├── docs/                   API.md  FRONTEND_INTEGRATION.md  CRUMB_STRUCTURE.md
└── src/
    ├── main/
    │   ├── java/com/personal/medisynth/
    │   │   ├── MediSynthApplication.java
    │   │   ├── DTO/         requests, responses, ApiError, PageResponse
    │   │   ├── config/      SecurityConfig, JWTFilter, RateLimitFilter, 401/403 handlers
    │   │   ├── controller/  AuthorizationController, AdminController, PatientController,
    │   │   │                DoctorController, AppointmentController, HealthController
    │   │   ├── exception/   ApiException, GlobalExceptionHandler
    │   │   ├── model/       Users, RegistrationRequest, DoctorProfile, PatientProfile, Appointment,
    │   │   │                AuditLog, BootstrapState, UserPrincipal + enums
    │   │   ├── repo/        one Spring Data repository per entity
    │   │   ├── service/     UserService, RegistrationService, AdminProvisioningService, AdminAccountService,
    │   │   │                AdminService, DoctorService, PatientService, AppointmentService, AuditService,
    │   │   │                JwtService, MyUserDetailsService, RateLimiter
    │   │   └── validation/  @StrongPassword
    │   └── resources/
    │       ├── application.properties
    │       └── db/migration/V1__initial_schema.sql
    └── test/
        ├── java/com/personal/medisynth/   integration tests (+ service/ and validation/ unit tests)
        └── resources/application-test.properties
```

## 1. Database setup

Requires PostgreSQL 14+ and JDK 21.

**Option A: your own PostgreSQL**
```bash
createdb -U postgres medisynth
createdb -U postgres medisynth_test      # separate database used (and wiped!) by the tests
```
**Option B: Docker**
```bash
export DB_PASSWORD='choose-a-password'
docker compose up -d                      # creates medisynth and medisynth_test
```
Flyway creates all tables on first start. Hibernate only *validates* the schema (`ddl-auto=validate`); nothing is
ever dropped or auto-altered, and no foreign key cascades deletes.

## 2. Configuration

```bash
cp .env.example .env        # .env is git-ignored; edit it
openssl rand -base64 48     # use the output as JWT_SECRET
```
`application.properties` reads `${DB_PASSWORD}` and `${JWT_SECRET}` with **no default**, so the app refuses to start
without them. A weak or non-Base64 `JWT_SECRET` (< 32 bytes) also stops startup. `.env` is picked up automatically
(`spring.config.import=optional:file:.env[.properties]`); real environment variables work too.

| Variable | Meaning |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection |
| `JWT_SECRET` | Base64 of ≥ 32 random bytes (HS256 key) |
| `JWT_EXPIRATION_MS` | token lifetime, default 3600000 (1 h) |
| `CORS_ALLOWED_ORIGINS` | comma-separated exact origins, default `http://localhost:5173`; `*` is rejected |
| `ADMIN_BOOTSTRAP_SECRET` | first-admin secret (≥ 32 chars). **Empty = bootstrap disabled** |
| `BCRYPT_STRENGTH`, `RATE_LIMIT_ENABLED` | optional |

## 3. Run

```bash
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
curl http://localhost:8080/api/health        # {"status":"UP"}
```
(On macOS/Linux export the variables first if you did not use `.env`:
`set -a; source .env; set +a`.)

## 4. Creating the first admin (developer only)

There is no way to become an admin through the public registration form or any request field.

1. Generate a one-time secret and put it in `.env`:
   ```bash
   openssl rand -hex 32        # -> ADMIN_BOOTSTRAP_SECRET=<value>
   ```
2. Start the backend, then create the admin from your terminal (the secret travels only in a header):
   ```bash
   curl -i -X POST http://localhost:8080/api/auth/admin/bootstrap \
     -H "Content-Type: application/json" \
     -H "X-Bootstrap-Secret: $ADMIN_BOOTSTRAP_SECRET" \
     -d '{"name":"Root Admin","email":"admin@example.com","password":"Choose-A-Strong-Pass-1","phone":"+919800000000"}'
   ```
3. Remove `ADMIN_BOOTSTRAP_SECRET` from `.env` / the environment and restart. The endpoint is then disabled.

How it is protected:
- The secret exists only in the server environment (never in React or a `VITE_*` variable); it is compared in
  constant time; wrong, missing or "feature off" all return the same `403`.
- One-time: the single row in `bootstrap_state` is locked with `SELECT … FOR UPDATE`, so concurrent calls
  are serialised and only one can succeed. After success the flag is set; the endpoint also refuses if any ADMIN already exists.
  A validation error (e.g. weak password) does not use up the bootstrap.
- Every success and failure is written to `audit_logs` (reason codes only, never secrets or passwords).
- Later admins: an existing admin calls `POST /api/admin/users/admins`.
- The bootstrap endpoint is rate limited (default 5 attempts / 15 min per client address).

## 5. How registration works

**Patient.** `POST /api/auth/register` with `role: "PATIENT"` → validated → email must be unique → BCrypt hash →
`Users` row saved with `grantedRole=PATIENT`, `accountStatus=ACTIVE` (decided by the server) → JWT returned. No approval.

**Doctor (pending).** `role: "DOCTOR"` + specialization, qualifications, experience, license number →
in one transaction the server saves a `Users` row (`PENDING_APPROVAL`, baseline `PATIENT` role, cannot log in), a
`DoctorProfile` (`verificationStatus=PENDING`) and a `RegistrationRequest` (`PENDING_APPROVAL`,
`requestedRole=DOCTOR`). It returns `202`, **no token**, plus a secret `registrationReference`. Duplicate email, duplicate
pending request and duplicate license are rejected (also enforced by DB unique indexes). While pending: login → `403`,
and even a token signed with the real key is rejected, because status is re-read from the database on every request.
The request is visible to admins at `GET /api/admin/registration-requests?status=PENDING_APPROVAL` and survives restarts
because it lives only in PostgreSQL. The applicant can check progress with `GET /api/auth/registration-status?reference=…`
(one request, minimal fields; only a hash of the reference is stored).

**Approval** (`PATCH /api/admin/registration-requests/{id}/approve`, one transaction): admin must be ACTIVE ADMIN →
request row is locked → must still be `PENDING_APPROVAL` and not the admin's own → role must be in the approval policy
(`DOCTOR` only; ADMIN can never be granted) → request `APPROVED` with reviewer + time → user `ACTIVE` with
`grantedRole = requestedRole` → doctor profile `VERIFIED` → audit row. Any failure rolls everything back.

**Rejection** (`…/reject`, optional `{"reason"}`): request `REJECTED` with reviewer, time, reason; user `REJECTED`; doctor
profile `REJECTED`; nothing granted; the row stays in admin history. A rejected account cannot log in; the applicant sees
the outcome through the status check. (An admin can later reactivate the account via `PATCH /api/admin/users/{id}/status`,
which gives patient-level access only, never DOCTOR.)

**Other roles in future:** add the role to the enum and the DB check, and (deliberately, in code review) to
`RegistrationService.APPROVABLE_ROLES`. Anything not in that set can never be granted by approval.

## 6. Security measures

BCrypt (strength 12) · stateless HS256 JWT with signature, expiry and issuer checked · role and status always re-read
from the DB (suspension takes effect immediately; token role claims are ignored) · DTO-only binding (no mass assignment) ·
URL role rules **plus** ownership checks in services (patients/doctors get `404` for others' appointments) ·
`401` vs `403` as specified · configurable CORS, no wildcards · row locks + DB constraints for approvals, bootstrap and
double booking · audit log for registrations, approvals, rejections, status changes, admin creation, admin appointment
changes · generic error bodies, no stack traces, `Password`/`Login` DTOs redact `toString()` · in-memory rate limiting on
login, register, status check and bootstrap (single instance; use a gateway/Redis if you scale out; behind a reverse proxy
configure `server.forward-headers-strategy` so the real client address is used).
A checkbox, hidden page or frontend validation is **not** part of this list: only the server checks count.

## 7. Tests

The integration tests start the whole application against a **real PostgreSQL database** and delete its rows before
each test, so use the dedicated `medisynth_test` database (never your dev data):

```bash
export DB_PASSWORD='the-postgres-password'            # or TEST_DB_PASSWORD
export TEST_DB_URL=jdbc:postgresql://localhost:5432/medisynth_test     # default shown
./mvnw test
```
JWT and bootstrap secrets are generated randomly per run. Rate limiting is off in tests except `RateLimitTest`.

| Spec requirement | Test |
|---|---|
| Patient registration saves + activates; logs in without approval | `PatientRegistrationAndLoginTest` |
| Public ADMIN registration rejected; duplicate email | `PatientRegistrationAndLoginTest` |
| Doctor saved as PENDING_APPROVAL; survives reload/restart; pending doctor has no privileges | `DoctorRegistrationApprovalTest`, `RestartPersistenceTest` |
| Duplicate pending request / license | `DoctorRegistrationApprovalTest` |
| Admin lists pending; approval transactional; rejection persisted; rejected cannot log in | `DoctorRegistrationApprovalTest` |
| Invalid developer authorization; successful provisioning; one-time; concurrency | `AdminProvisioningTest` |
| Patients/doctors blocked from admin endpoints; invalid/expired JWT | `AccessControlTest`, `service/JwtServiceTest` |
| IDOR on appointments; appointment persistence; double booking (service, concurrency and DB constraint) | `AppointmentTest` |
| Login/bootstrap throttling | `RateLimitTest` |

## 8. Final checklist

**Written and covered by automated tests, but NOT yet executed** (no Maven Central access in the build sandbox, so
nothing has been compiled by Maven or run):
- every row of the test table above;
- JWT: expiry, wrong key, tampering, `alg:none`, wrong issuer, ignored role claim, revocation on suspension;
- CORS preflight allow/deny for the configured origin;
- concurrency: 6 parallel bootstrap calls → exactly 1 admin; 6 parallel bookings of one slot → exactly 1.

**Only syntax-checked** (`javac` parse of all main and test sources; no dependency resolution): everything, i.e. a
green `./mvnw test` is the first real verification.

**Written but not covered by an automated test:**
- bootstrap disabled when `ADMIN_BOOTSTRAP_SECRET` is empty; startup failure on a short bootstrap secret, missing `DB_PASSWORD`, or wildcard CORS;
- rate limits on register and registration-status;
- `GET /api/doctors?specialization=`, `PUT /api/patients/me`, `PUT /api/doctors/me` happy paths;
- Flyway migration on an empty database (runs implicitly in every integration test);
- the React frontend against this API.

**Not implemented (by choice, documented):** refresh tokens; time-limited invitation tokens for extra admins (admins are
created by an existing admin instead); password reset / email notifications; enforcing a doctor's free-text `availability`
when booking; a read endpoint for `audit_logs` (query the table); Testcontainers (a real local PostgreSQL is used);
shared (multi-instance) rate limiting.

**Assumptions to confirm:** the spec was cut off at deliverable 13, read as "tested and untested items"; a rejected
applicant cannot log in at all; the `registrationReference` is the "secure reference" for the public status check.


## Lab technicians and researchers (migration V2)

Two more roles exist: `LAB_TECHNICIAN` (works the lab test queue) and `RESEARCHER` (requests access to synthetic
data). Both register publicly, stay `PENDING_APPROVAL`, and only an admin approval grants the role. See
`docs/API.md`, section "Lab technicians and researchers". The synthetic dataset catalogue/generation is **not**
built yet. A researcher's request is only recorded and decided.

Upgrading an existing database: just restart the backend. Flyway applies `V2__lab_technician_and_researcher.sql`
automatically; existing data is kept. Never edit `V1__initial_schema.sql`.

Like the rest of the backend, the new code was written without being able to compile it here, and the new tests
(`StaffRegistrationTest`, `LabTestTest`, `ResearchRequestTest`) have not been run.


## Synthetic datasets (migration V3)

Run the backend as usual: Flyway applies `V3__synthetic_datasets.sql` on start. Nothing else to configure.

**How it works**
1. An admin generates a dataset (`POST /api/admin/datasets`). The Synthetix generator in `synthetix/` samples synthetic
   patient-years from a small clinical model (age, gender -> BMI, smoking -> diagnosis -> vitals, labs, visits, admission).
2. The only thing it takes from the hospital's real data is a coarse **profile**: ten-year age bands and gender shares,
   read as grouped counts. Cells with fewer than 5 real patients are suppressed, samples under 50 patients are ignored
   (built-in priors are used), and the result is shrunk towards the priors. No real row, name or id ever reaches the generator.
3. The evaluator scores fidelity, clinical relationships, validity and privacy (k-anonymity, k = 5). Pass mark and gates are in
   `DatasetEvaluator`. READY datasets appear in the researcher catalogue.
4. A researcher requests a dataset; the admin approves; only then can that researcher preview and download the CSV.
   Retiring a dataset ends access at once. Every generate, evaluate, regenerate, retire, preview and download is audited.

**What this is and is not.** It is a rule-based statistical generator, good for demos, teaching and pipeline testing. It does
not learn clinical patterns from the real records (the system does not hold clinical records of that kind), so it must not
be presented as a model of the real patient population. The Python ML engine in the proposal is a separate, later step; it
can replace `SyntheticDataGenerator` behind the same service.

**Tests.** `synthetix/SyntheticEngineTest` needs no database. `SyntheticDatasetTest` needs the `medisynth_test` database like the others.
