# MediSynth - Frontend

A React single-page application for **MediSynth**, a hospital management system paired with **Synthetix**, a synthetic patient-data generation engine. This is the **frontend only** — built against nine UML/system-design diagrams (Use Case, Class, Activity, DFD Context + Level-1, two Sequence diagrams, two Swimlane diagrams) as the source of truth for actors, entities, workflows, and navigation. No backend, database, or authentication server is implemented here; everything runs on mock data and mock services so the UI is fully demonstrable on its own.

## 1. Project overview

MediSynth serves two halves of one system:

- **Hospital management**: patients book appointments, doctors run consultations and manage medical records/prescriptions, lab technicians process test requests, and pharmacists dispense medicine.
- **Synthetix**: admins prepare and generate synthetic patient datasets (no real patient data ever appears in the system), evaluate dataset quality, and manage researcher access requests, with every decision written to an audit log.

## 2. Technology stack

- React 18 + Vite
- JavaScript (no TypeScript)
- React Router v6
- Lucide React (icons)
- Plain CSS with custom properties (no UI framework) — see `src/styles/`
- Mock service layer, structured so Axios/Fetch calls to a future Spring Boot API can be dropped in without touching components

## 3. Installation

```bash
npm install
```

## 4. Running the application

```bash
npm run dev
```

Then open the printed local URL (typically `http://localhost:5173`). No backend needs to be running — the app works entirely off mock data.

```bash
npm run build      # production build
npm run preview    # preview the production build locally
```

## 5. Project structure

```
src/
  assets/            static assets
  components/
    common/          Button, Input, Modal, Badge, DataTable's helpers, etc.
    layout/           Sidebar, Topbar
    cards/            StatCard, AppointmentCard, QuickAction, RecentActivity
    tables/           DataTable (search + sort + pagination + responsive cards)
  layouts/            AppLayout (sidebar + topbar + <Outlet/> shell)
  pages/
    patient/ doctor/ lab/ pharmacy/ admin/ researcher/
  routes/             navConfig.js (role -> nav items), ProtectedRoute.jsx
  context/            AuthContext, ToastContext
  services/           one file per domain; all mock for now (see section 8)
  data/               mock datasets, one file per entity
  styles/             variables.css, global.css, layout.css, components.css, forms.css, tables.css, responsive.css
```

## 6. Available demo roles

There is no real backend, so login uses a **mock auth service**. On the login screen, either:

- Enter any demo user's email with any password (mock auth doesn't check the password), or
- Click one of the "demo role" buttons to sign in instantly.

| Role | Demo user | Email |
|---|---|---|
| Patient | Anita Verma | anita.verma@example.com |
| Doctor | Dr. Rohan Mehta | rohan.mehta@medisynth.org |
| Lab Technician | Simran Kaur | simran.kaur@medisynth.org |
| Pharmacist | Arjun Nair | arjun.nair@medisynth.org |
| Admin | Priya Iyer | priya.iyer@medisynth.org |
| Researcher | Dr. Alan Reyes | alan.reyes@research.org |

These six roles are exactly the six actors identified in the Use Case and Swimlane diagrams — no additional roles were invented.

## 7. Mock API architecture

```
Component -> Service (src/services/*.js) -> simulateRequest() -> mock data (src/data/*.js)
```

Every service function returns a Promise (via `simulateRequest` in `src/services/api.js`), so components never know whether they're talking to mock data or a real API. State mutations (booking an appointment, dispensing medicine, approving a request) are held in module-level variables inside each service file, so they persist across navigation for the length of a session.

## 8. Replacing mock services with Spring Boot APIs

Each service file documents the shape of the real call it will become. For example, in `patientService.js`:

```js
// current (mock)
export function getPatients() {
  return simulateRequest(patients);
}

// future (real)
export async function getPatients() {
  const res = await axios.get(`${API_BASE_URL}/patients`);
  return res.data;
}
```

To connect the real backend later:
1. Set `VITE_USE_MOCKS=false` and `VITE_API_BASE_URL` in `.env` (see `.env.example`).
2. Replace each function body in `src/services/*.js` with a real Axios/Fetch call to the corresponding endpoint, keeping the same function signature.
3. No component or page needs to change, since they only ever import from `services/`, never call a URL directly.

Planned endpoint groups (derived only from entities/operations in the Class Diagram and DFD): `/api/auth`, `/api/patients`, `/api/doctors`, `/api/appointments`, `/api/records`, `/api/lab-tests`, `/api/prescriptions`, `/api/bills`, `/api/datasets`, `/api/dataset-requests`, `/api/audit-logs`.

## 9. Environment variables

See `.env.example`:

- `VITE_API_BASE_URL` — base URL for the future Spring Boot REST API (unused while mocks are on).
- `VITE_USE_MOCKS` — set to `false` once real services are implemented.

## 10. Main frontend modules

| Module | Pages | Primary UML source |
|---|---|---|
| Patient | Dashboard, Book Appointment, My Appointments, Medical Reports, Billing | Use Case (Patient), Sequence Diagram 1, Activity Diagram (appointment + billing branches) |
| Doctor | Dashboard, Consultations, Consultation Detail, Patient Records, Prescriptions | Use Case (Doctor), Activity Diagram (consultation/lab/prescription branches), Swimlane 1 |
| Lab Technician | Dashboard, Lab Test Queue | Use Case (Lab Technician), Swimlane 1 |
| Pharmacist | Dashboard, Prescription Queue | Use Case (Pharmacist), Swimlane 1 |
| Admin | Dashboard, Dataset Preparation, Access Requests (+ audit log) | Use Case (Admin), Activity Diagram Part 2 (Synthetix), Sequence Diagram 2, Swimlane 2 |
| Researcher | Dashboard, Request Access, My Requests | Use Case (Researcher), Swimlane 2 |

## 11. UML -> Frontend mapping

| UML element | What it drove in the frontend |
|---|---|
| **Actors** (Use Case Diagram): Patient, Doctor, Lab Technician, Pharmacist, Admin, Researcher | The six roles in `data/users.js`, the six route groups in `App.jsx`, and `routes/navConfig.js` (no other roles exist) |
| **Use cases**: Book Appointment, View Medical Reports, Conduct Consultation, Update Medical Record, Manage Lab Test, Dispense Medicine, Prepare/Generate/Evaluate Synthetic Dataset, Request/Review Dataset Access | Each has a dedicated page (see table in section 10) rather than being folded into a generic CRUD screen |
| **`<<includes>>`/`<<extends>>`** (Book Appointment includes Login; View Medical Reports extends Login) | Reflected structurally: both flows require an authenticated session via `ProtectedRoute`, and "View Medical Reports" is reachable only from within the authenticated shell, not as a standalone public action |
| **Class Diagram entities**: Patient, Doctor, Appointment, MedicalRecord, LabTest, Prescription, Medicine, Bill, Payment, SyntheticDataset, DatasetRequest, AuditLog | One mock data file per entity in `src/data/`, with the same field names (e.g. `appointmentId`-style `id`, `status`, `diagnosis`) so future backend DTOs map cleanly |
| **Activity Diagram** (patient/hospital half): registration -> login -> book appointment -> consultation -> lab-test decision -> prescription decision -> billing -> payment (with retry-on-failure loop) | `BookAppointment.jsx` (3-step form), `ConsultationDetail.jsx` (sequential decision UI for lab test + prescription), `Billing.jsx` (payment with a real failure/retry path, not just a happy path) |
| **Activity Diagram** (Synthetix half): prepare dataset -> generate -> evaluate -> regenerate loop -> researcher request -> admin approve/reject -> audit log | `DatasetPreparation.jsx` implements the generate/evaluate/regenerate loop exactly (regenerating re-triggers evaluation); `AccessRequests.jsx` implements approve/reject with an audit log tab |
| **Sequence Diagram 1** (Book Appointment) | The step order and loading/response states in `BookAppointment.jsx` (availability check before confirmation) |
| **Sequence Diagram 2** (Generate Synthetic Dataset) | The order of calls in `datasetService.prepareAndGenerateDataset` / `evaluateDataset` |
| **Swimlane 1** (Patient/Doctor/Lab/Pharmacy) | Which role owns which action — e.g., only Lab Technician can submit a lab result, only Pharmacist can mark a prescription dispensed, enforced via `ProtectedRoute` + role-specific nav |
| **Swimlane 2** (Admin/Synthetix/Researcher) | Only Researcher can request access; only Admin can approve/reject and sees the audit log |

## Notes on the demo

- This sandbox has no network access, so dependencies have not actually been installed/run here — `npm install && npm run dev` has not been executed against a live npm registry as part of building this. The code has been reviewed for balanced syntax and correct import paths, but please run it locally to confirm before presenting it.
- Payments fail ~10% of the time by design, so the retry path in the Activity Diagram is reachable in the demo without special setup.
- Dataset evaluation scores are randomized within a range that sometimes fails the quality threshold, so the regenerate loop is reachable without special setup too.
