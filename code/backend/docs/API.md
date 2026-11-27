# MediSynth REST API

Base URL (local): `http://localhost:8080`. All bodies are JSON. Protected calls send
`Authorization: Bearer <accessToken>`.

## Conventions

**Roles:** `PATIENT`, `DOCTOR`, `ADMIN`. **Account status:** `ACTIVE`, `PENDING_APPROVAL`, `REJECTED`, `SUSPENDED`.
**Request status:** `PENDING_APPROVAL`, `APPROVED`, `REJECTED`. **Appointment status:** `PENDING`, `CONFIRMED`,
`CANCELLED`, `COMPLETED`.

**Errors** always have this shape (never a stack trace):

```json
{
  "timestamp": "2026-11-16T12:00:00.123Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "One or more fields are invalid",
  "path": "/api/auth/register",
  "fieldErrors": { "password": "Password must be 10-72 characters and contain at least one letter and one digit" }
}
```

| HTTP | When |
|---|---|
| 400 | validation / malformed input |
| 401 | no token, invalid token, expired token, account no longer active (`UNAUTHENTICATED`), bad login (`INVALID_CREDENTIALS`) |
| 403 | authenticated but not allowed (role), or login blocked by account status |
| 404 | not found, **or** not yours (appointments) |
| 409 | duplicate email / pending request / license / slot, or invalid state change |
| 429 | rate limited (`Retry-After` header) |

**Pagination:** `?page=0&size=20` (size capped at 100). Response:

```json
{ "content": [ ... ], "page": 0, "size": 20, "totalElements": 42, "totalPages": 3 }
```

---

## Auth (`/api/auth`)

### POST `/api/auth/register` (public)
Patient (201, active immediately, token returned):
```json
// request
{ "name": "Asha Rao", "email": "asha@example.com", "password": "Str0ngPassw0rd!", "phone": "+919876543210", "role": "PATIENT" }
// 201
{ "outcome": "ACTIVE", "message": "Registration successful. You can use MediSynth now.",
  "user": { "id": 7, "name": "Asha Rao", "email": "asha@example.com", "role": "PATIENT" },
  "accessToken": "eyJ...", "tokenType": "Bearer" }
```
Doctor (202, saved as `PENDING_APPROVAL`, **no token**):
```json
// request
{ "name": "Dr Meera Shah", "email": "meera@example.com", "password": "Str0ngPassw0rd!", "phone": "+919812345678",
  "role": "DOCTOR", "specialization": "Cardiology", "qualifications": "MBBS, MD (Cardiology)",
  "experienceYears": 8, "licenseNumber": "MCI-12345", "consultationFee": 500.00, "availability": "Mon-Fri 09:00-17:00" }
// 202
{ "outcome": "PENDING_APPROVAL",
  "message": "Your registration request was submitted and is awaiting admin approval. Keep your registration reference to check the status.",
  "registrationReference": "k3J...43-char-secret..." }
```
Show `registrationReference` to the applicant **once** (the server stores only its hash).
`role: "ADMIN"` → `403 ADMIN_REGISTRATION_FORBIDDEN`; unknown role → `400 INVALID_ROLE`; extra fields such as
`grantedRole`/`accountStatus` are ignored. Other errors: `409 EMAIL_ALREADY_REGISTERED`,
`409 REGISTRATION_ALREADY_PENDING`, `409 LICENSE_ALREADY_REGISTERED`, `400 VALIDATION_FAILED`.

Password rule: 10–72 bytes, at least one letter and one digit. Phone: 7–15 digits, optional leading `+`.

### POST `/api/auth/login` (public, rate limited)
```json
// request
{ "email": "asha@example.com", "password": "Str0ngPassw0rd!" }
// 200
{ "accessToken": "eyJ...", "tokenType": "Bearer", "expiresInMs": 3600000,
  "user": { "id": 7, "name": "Asha Rao", "email": "asha@example.com", "role": "PATIENT" } }
```
Errors: `401 INVALID_CREDENTIALS`; `403 ACCOUNT_PENDING_APPROVAL`, `403 ACCOUNT_REJECTED`, `403 ACCOUNT_SUSPENDED`
(only revealed after the password is correct). No token is issued in any error case.

### GET `/api/auth/me` (any signed-in user)
```json
{ "id": 7, "name": "Asha Rao", "email": "asha@example.com", "role": "PATIENT" }
```

### GET `/api/auth/registration-status?reference=<registrationReference>` (public, rate limited)
```json
{ "status": "PENDING_APPROVAL", "requestedRole": "DOCTOR", "submittedAt": "2026-11-16T12:00:00Z",
  "reviewedAt": null, "message": "Your request is awaiting admin review. You cannot log in yet." }
```
`APPROVED` → "You can now log in." `REJECTED` → also returns `rejectionReason` (written by the admin for the
applicant). Unknown/malformed reference → `404 REGISTRATION_NOT_FOUND` (same answer for every bad value).
It reveals nothing else: no name, email, phone or license, and requests cannot be enumerated.

### POST `/api/auth/admin/bootstrap` (developer only, one time)
See "First admin" in the README. Header `X-Bootstrap-Secret: <server secret>`:
```json
// request
{ "name": "Root Admin", "email": "admin@example.com", "password": "Str0ngPassw0rd!", "phone": "+919800000000" }
// 201
{ "id": 1, "name": "Root Admin", "email": "admin@example.com", "role": "ADMIN" }
```
`403 ADMIN_PROVISIONING_FORBIDDEN` (disabled or wrong/missing secret, indistinguishable), `409 BOOTSTRAP_ALREADY_USED`.

---

## Admin (`/api/admin/**`, ACTIVE ADMIN only)

| Method & path | Purpose |
|---|---|
| `GET /api/admin/users?role=&status=&page=&size=` | all accounts |
| `GET /api/admin/patients?status=` | patient accounts |
| `GET /api/admin/doctors?verificationStatus=PENDING\|VERIFIED\|REJECTED` | doctor profiles incl. license |
| `GET /api/admin/appointments?status=` | all appointments |
| `GET /api/admin/registration-requests?status=&requestedRole=` | requests (history when no filter) |
| `GET /api/admin/registration-requests/{id}` | one request |
| `PATCH /api/admin/registration-requests/{id}/approve` | approve (no body) |
| `PATCH /api/admin/registration-requests/{id}/reject` | body optional `{ "reason": "..." }` |
| `PATCH /api/admin/users/{id}/status` | `{ "status": "ACTIVE" \| "SUSPENDED", "reason": "..." }` |
| `POST /api/admin/users/admins` | create another ADMIN (same body as bootstrap) |

Pending requests for the dashboard: `GET /api/admin/registration-requests?status=PENDING_APPROVAL&requestedRole=DOCTOR`
```json
{ "content": [ {
    "id": 3, "applicantId": 9, "applicantName": "Dr Meera Shah", "applicantEmail": "meera@example.com",
    "applicantPhone": "+919812345678", "applicantAccountStatus": "PENDING_APPROVAL",
    "requestedRole": "DOCTOR", "status": "PENDING_APPROVAL", "submittedAt": "2026-11-16T12:00:00Z",
    "reviewedAt": null, "reviewedById": null, "reviewedByName": null, "rejectionReason": null,
    "doctorProfile": { "id": 2, "userId": 9, "name": "Dr Meera Shah", "specialization": "Cardiology",
      "qualifications": "MBBS, MD (Cardiology)", "experienceYears": 8, "licenseNumber": "MCI-12345",
      "consultationFee": 500.00, "availability": "Mon-Fri 09:00-17:00", "verificationStatus": "PENDING" } } ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1 }
```
Approve/reject return the updated request in the same shape. Decision errors: `409 REQUEST_ALREADY_DECIDED`,
`403 SELF_APPROVAL_FORBIDDEN`, `404 REQUEST_NOT_FOUND`, `409 DOCTOR_PROFILE_MISSING` (nothing is changed).
Status errors: `403 SELF_STATUS_CHANGE_FORBIDDEN`, `409 USE_REGISTRATION_WORKFLOW`, `400 INVALID_STATUS`.

Approval grants **only** the requested role, and only if it is in the server-side approval policy
(`RegistrationService.APPROVABLE_ROLES`, currently `DOCTOR`).

---

## Patient (`/api/patients/**`, PATIENT only; always the caller, no ids in URLs)

`GET /api/patients/me`, `PUT /api/patients/me`, `GET /api/patients/me/appointments?status=&page=&size=`

```json
// PUT /api/patients/me
{ "name": "Asha Rao", "phone": "+919876543210", "dateOfBirth": "1995-04-12", "gender": "FEMALE",
  "address": "12 Park Street, Patiala", "emergencyContactName": "Ravi Rao", "emergencyContactPhone": "+919811111111" }
// 200
{ "userId": 7, "name": "Asha Rao", "email": "asha@example.com", "phone": "+919876543210",
  "dateOfBirth": "1995-04-12", "gender": "FEMALE", "address": "12 Park Street, Patiala",
  "emergencyContactName": "Ravi Rao", "emergencyContactPhone": "+919811111111" }
```
Email, role and status cannot be changed here.

## Doctors (`/api/doctors/**`)

| Endpoint | Who | Notes |
|---|---|---|
| `GET /api/doctors?specialization=&page=&size=` | any signed-in user | only VERIFIED + ACTIVE doctors |
| `GET /api/doctors/{id}` | any signed-in user | public fields only; non-bookable doctors → 404 (except owner/admin) |
| `GET /api/doctors/me` | DOCTOR | includes license number and verification status |
| `PUT /api/doctors/me` | DOCTOR | `name, phone, specialization, qualifications, experienceYears, consultationFee, availability`; license and verification are not editable |
| `GET /api/doctors/me/appointments?status=` | DOCTOR | only appointments assigned to this doctor |

```json
// GET /api/doctors → content[i]
{ "id": 2, "name": "Dr Meera Shah", "specialization": "Cardiology", "qualifications": "MBBS, MD (Cardiology)",
  "experienceYears": 8, "consultationFee": 500.00, "availability": "Mon-Fri 09:00-17:00" }
```
`id` here is the **doctor profile id** used when booking.

## Appointments (`/api/appointments/**`)

Slots are 30 minutes and start on `:00` or `:30` (UTC epoch), up to 180 days ahead.

```json
// POST /api/appointments   (PATIENT only; the patient is the caller)
{ "doctorId": 2, "appointmentTime": "2026-12-01T10:00:00Z", "reason": "Chest pain on exertion" }
// 201
{ "id": 11, "patientId": 7, "patientName": "Asha Rao", "doctorId": 2, "doctorName": "Dr Meera Shah",
  "specialization": "Cardiology", "appointmentTime": "2026-12-01T10:00:00Z", "reason": "Chest pain on exertion",
  "status": "PENDING", "cancellationReason": null, "createdAt": "...", "updatedAt": "..." }
```
Errors: `409 SLOT_UNAVAILABLE`, `409 PATIENT_SLOT_CONFLICT`, `400 INVALID_SLOT`, `400 APPOINTMENT_IN_PAST`,
`400 APPOINTMENT_TOO_FAR`, `404 DOCTOR_NOT_FOUND` (unknown, unverified or suspended doctor).

| Endpoint | Who |
|---|---|
| `GET /api/appointments/my?status=` | PATIENT (own) / DOCTOR (assigned to them). Admins use `/api/admin/appointments` |
| `GET /api/appointments/{id}` | the patient, the assigned doctor, or an admin; anyone else gets **404** |
| `PATCH /api/appointments/{id}/status` | body `{ "status": "CONFIRMED", "reason": "..." }` |
| `POST /api/appointments/{id}/cancel` | optional body `{ "reason": "..." }` |

Status rules: `PENDING → CONFIRMED | CANCELLED`, `CONFIRMED → COMPLETED | CANCELLED`; `CANCELLED`/`COMPLETED` are final.
Patients may only cancel (before the start time). Doctors may confirm, complete (after the start time) or cancel
(before the start time) their own appointments. Admins may apply any valid transition (audited). Invalid →
`409 INVALID_STATUS_TRANSITION`; wrong role → `403 ROLE_CANNOT_SET_STATUS`.

## Health
`GET /api/health` → `{ "status": "UP" }` (public).

---

## Lab technicians and researchers (added in V2)

### Registration
`POST /api/auth/register` now also accepts `role: "LAB_TECHNICIAN"` or `"RESEARCHER"`. Like doctors they get
`202 PENDING_APPROVAL`, a one-time `registrationReference`, and **no token**; an admin must approve them
(`PATCH /api/admin/registration-requests/{id}/approve`) before they can log in. `ADMIN` can still never be requested.

| Role | Extra required fields |
|---|---|
| `LAB_TECHNICIAN` | `department`, `employeeId` (unique, case-insensitive) |
| `RESEARCHER` | `institution`, `researchArea` |

Admin request lists include `applicantDetails` (`{department, employeeId}` or `{institution, researchArea}`).

### Lab tests
| Method & path | Who | Purpose |
|---|---|---|
| `POST /api/lab-tests` `{appointmentId, testType, notes?}` | DOCTOR | order a test. The patient is taken from the doctor's **own** appointment (someone else's → `404`); not allowed for cancelled appointments (`409`) |
| `GET /api/lab-tests/my?status=` | PATIENT, DOCTOR | patient: tests ordered for me; doctor: tests I ordered |
| `POST /api/lab-tests/{id}/cancel` | DOCTOR (orderer) | only while `REQUESTED` |
| `GET /api/lab/tests?status=` | LAB_TECHNICIAN | work queue, oldest first |
| `PATCH /api/lab/tests/{id}/start` | LAB_TECHNICIAN | `REQUESTED → IN_PROGRESS`, records who |
| `PATCH /api/lab/tests/{id}/complete` `{resultSummary}` | LAB_TECHNICIAN | `IN_PROGRESS → COMPLETED`; only the technician who started it (`403 NOT_YOUR_TEST`) |

Statuses: `REQUESTED, IN_PROGRESS, COMPLETED, CANCELLED`. Illegal transitions → `409`.
```json
{ "id": 4, "appointmentId": 11, "patientId": 7, "patientName": "Asha Rao", "orderedById": 2,
  "orderedByName": "Dr Meera Shah", "testType": "Complete blood count", "clinicalNotes": "Fasting",
  "status": "COMPLETED", "resultSummary": "Hb 13.2 g/dL, normal", "processedById": 9,
  "processedByName": "Lab Tech", "startedAt": "...", "completedAt": "...", "createdAt": "..." }
```

### Researcher data-access requests
A request names a catalogue dataset (`datasetId`, preferred) or, for data that is not in the catalogue, describes it in
free text (`datasetTitle`). Approving a catalogue request is what lets that researcher preview and download the dataset
(see *Synthetic datasets* below); a free-text request only records the decision (audited).

| Method & path | Who | Purpose |
|---|---|---|
| `POST /api/research/requests` `{datasetId? , datasetTitle?, purpose, dataScope?}` | RESEARCHER | submit; one of `datasetId`/`datasetTitle` is required (`400 DATASET_REQUIRED`). `datasetId` must be a READY dataset (`404`), and a second pending/approved request for it is refused (`409 DUPLICATE_DATASET_REQUEST`). Max 10 pending → `409 TOO_MANY_PENDING_REQUESTS` |
| `GET /api/research/requests/my` | RESEARCHER | my requests and decisions |
| `GET /api/admin/research-requests?status=` | ADMIN | all requests |
| `PATCH /api/admin/research-requests/{id}/approve` or `/reject` | ADMIN | optional `{reason}`; already decided → `409` |
| `GET /api/admin/audit-logs?page=&size=` | ADMIN | audit trail (actor name, action, target, safe metadata) |


### Synthetic datasets (Synthetix)
Pipeline: **generate → evaluate → READY**, or **NEEDS_REGENERATION → regenerate → evaluate again**. Status values:
`EVALUATING, READY, NEEDS_REGENERATION, RETIRED`.

| Method & path | Who | Purpose |
|---|---|---|
| `POST /api/admin/datasets` `{name, description?, recordCount (500..50000), seed?}` | ADMIN | generate a dataset (`201`, status `EVALUATING`) |
| `GET /api/admin/datasets?status=` | ADMIN | all datasets with their latest evaluation |
| `GET /api/admin/datasets/summary` | ADMIN | counts per status |
| `GET /api/admin/datasets/columns` | ADMIN | column catalogue (name, type, role, description) |
| `GET /api/admin/datasets/{id}` | ADMIN | one dataset with the full evaluation `report` |
| `POST /api/admin/datasets/{id}/evaluate` | ADMIN | only from `EVALUATING` → `READY` or `NEEDS_REGENERATION` (`409` otherwise) |
| `POST /api/admin/datasets/{id}/regenerate` `{recordCount?}` | ADMIN | only from `NEEDS_REGENERATION`: new seed, optional new size, version+1, back to `EVALUATING` |
| `POST /api/admin/datasets/{id}/retire` | ADMIN | ends all researcher access at once |
| `GET /api/admin/datasets/{id}/preview?size=` , `/download` | ADMIN | first rows (max 100) / full CSV |
| `GET /api/research/datasets` | RESEARCHER | catalogue of READY datasets, each with the caller's `accessStatus` |
| `GET /api/research/datasets/columns` , `/{id}` | RESEARCHER | column catalogue / one READY dataset |
| `GET /api/research/datasets/{id}/preview?size=` , `/download` | RESEARCHER | **only with an APPROVED request** (`403 DATASET_ACCESS_NOT_GRANTED`); dataset must be READY (`404` otherwise). Download is a CSV, audited |

Evaluation `report`: `overallScore, marginalScore, relationshipScore, validityScore, privacyScore, k, kAnonymityShare,
minClassSize, uniqueShare, passed, columns[], checks[], failureReasons[]`.
A dataset is READY only if the overall score is at least 0.90 **and** validity is at least 99.9%, at least 95% of records
sit in a group of 5 or more look-alikes on (age band, gender, diagnosis), no more than 1% are unique on those
fields, and at least 6 of the 8 clinical relationships hold. Small datasets (about 2,000 records or fewer) usually fail
on privacy: regenerate with more records.
