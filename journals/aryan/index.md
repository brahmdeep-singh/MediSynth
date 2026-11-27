# Aryan's Journal

Roll No. 1024030787

Name: Aryan Sharma

Frontend track: React web app, backend integration, and test execution.

---

## Week 1 — Phase 1: Roles and screens

**Status:** Complete

### What I did
- With the team, listed the user roles (patient, doctor, lab technician, researcher, administrator; pharmacist screens as a demo) and the screens each role needs.
- Agreed the split of work (see `docs/journals/index.md`): I take the frontend and its integration with the backend, and I execute the test cases.

### Key decisions & reasoning
- **Decision:** One dashboard and menu per role instead of one shared screen with hidden buttons.
  **Why:** A role should never even see actions it cannot perform, and the server still enforces the rule underneath.

### Next steps
- Set up the React app and its structure.

---

## Week 3 — Phase 2: React app setup

**Status:** Complete

### What I did
- Created the app with React 18, Vite 5, react-router 6, lucide-react icons and plain CSS.
- Built the shell: layout, sidebar and top bar, route guards per role, and `routes/navConfig.js` that holds the menu for each role.
- Built shared components such as `Modal` (with a `wide` option for large reports), tables, badges and form controls.

### Key decisions & reasoning
- **Decision:** Plain CSS instead of a UI framework.
  **Why:** Fewer dependencies for a student project, and full control of the look.
- **Decision:** One navigation config file.
  **Why:** Adding a page for a role means one new entry, not edits in several components.

### Next steps
- A service layer so the screens can run before the backend exists.

---

## Week 4 — Phase 3: Mock service layer

**Status:** Complete

### What I did
- Every service file has two halves: a mock one with in-memory data and a real one that calls the API. A single flag picks which to use:
  `const impl = USE_MOCKS ? mock : real; export const fn = (...a) => impl.fn(...a)`.
- Built the patient, doctor, pharmacy and billing screens against mock data so the team could demo the whole flow early.

### Key decisions & reasoning
- **Decision:** Pages never call `fetch` directly; they call the service functions.
  **Why:** Switching from mock to real is one flag, and no page needs to change.

### Next steps
- Connect the real services to the Spring Boot backend.

---

## Week 6 — Phase 4: Connecting to the backend

**Status:** Complete

### What I did
- Wrote `services/api.js`: one request helper that adds the Bearer token, sends JSON, and turns the backend's error shape into a message for the user.
- Set `VITE_USE_MOCKS=false` as the default so the app talks to the real API.
- Connected registration (including the "pending approval" message for doctors, lab technicians and researchers), login, patient and doctor lists, and appointment booking. A 409 slot conflict shows a clear "slot unavailable" message.

### Key decisions & reasoning
- **Decision:** The frontend never sends or decides a role or status.
  **Why:** The server is the only authority; anything the client sends can be forged.
- **Decision:** No secret in any `VITE_*` variable.
  **Why:** Vite copies those into the public bundle.

### Challenges & how I solved them
- Cross-origin errors on the first connection came from the backend's CORS list. Adding the Vite origin to `CORS_ALLOWED_ORIGINS` fixed it.

### Next steps
- Lab technician and researcher screens.

---

## Week 7 — Phase 5: Lab technician and researcher screens

**Status:** Complete

### What I did
- Lab technician: queue of pending tests, Start, and Complete with a result. Patient and doctor views show the result.
- Researcher: registration, request status and request list.
- Added the two roles to the route guards and menus.

### Next steps
- The synthetic dataset screens.

---

## Week 9 — Phase 6: Synthetic dataset screens

**Status:** Complete

### What I did
- `DatasetPreparation` (admin): a generate form, an Evaluate button, a Regenerate modal with a record count, and a report modal with preview, download and retire. New components `QualityReport` and `RecordsPreview` show the scores, the relationship checks and the first rows.
- Researcher side: `DatasetCatalogue` (new), a rewritten `RequestAccess` (pick a dataset from the catalogue or use free text, with `?dataset=` preselect), and a Download button in `MyRequests`.
- `downloadFile()` in `api.js` fetches the CSV as a blob with the Bearer token and saves it. The admin dashboard now shows the real dataset count.

### Key decisions & reasoning
- **Decision:** Download through `fetch` with the token, not a plain link.
  **Why:** A plain link cannot send the Authorization header, so the server would refuse it.

### Next steps
- Run the test cases.

---

## Week 10 — Phase 7: Executing the test cases

**Status:** Complete

### What I did
- Executed the test cases written to the course template (designed by Tanu Bansal) on 17 November 2026: 1.1 admin bootstrap and access control, 2.1 doctor registration, approval and login, 3.1 appointment booking and slot conflicts, and 4.1 the lab test flow. The final report includes sheets 1.1 to 3.1.
- Recorded the outcome and a comment for every step.

### Challenges & how I solved them
- A step in 1.1 was removed after the team agreed it duplicated another; the sheet and its PDF were regenerated to match.

### Next steps
- Frontend README, and a full pass through every role before the demo.

---

## Week 10 — Phase 8: Frontend documentation and hand-over

**Status:** Complete

### What I did
- Updated the frontend README: routes per role, the mock and real switch, and the run steps (`npm install`, `npm run dev`).
- Checked the new frontend with `tsc` for syntax and undefined names. No new packages were needed for the dataset screens.
