# Connecting the existing React (Vite) frontend

The backend allows CORS for `http://localhost:5173` (change with `CORS_ALLOWED_ORIGINS`). Nothing in the browser
is trusted for security: route guards only choose which screen to show; the server enforces every rule.

> The frontend zip I built earlier uses mock services (`VITE_USE_MOCKS`) and had no registration page. These
> snippets are meant to be adapted into it; I have not changed or run that frontend against this backend.

## 1. Environment (frontend `.env`, public values only)

```
VITE_API_BASE_URL=http://localhost:8080
VITE_USE_MOCKS=false
```
**Never** put `ADMIN_BOOTSTRAP_SECRET`, `JWT_SECRET` or any key in a `VITE_*` variable or in React source: everything
in the bundle is public.

## 2. API client (`src/services/api.js`)

```js
import axios from "axios";

const api = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL });

api.interceptors.request.use((config) => {
  const token = sessionStorage.getItem("medisynth_token");
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (res) => res,
  (err) => {
    // 401 = missing/expired/revoked token -> back to login
    if (err.response?.status === 401 && !err.config.url.includes("/api/auth/login")) {
      sessionStorage.removeItem("medisynth_token");
      window.location.assign("/login");
    }
    return Promise.reject(err);
  }
);

export default api;
```
Every error body is `{ status, code, message, fieldErrors? }`: show `fieldErrors[field]` under inputs and
`message` otherwise; switch on `code` for special cases.

## 3. Endpoints the frontend needs

| Screen / action | Call |
|---|---|
| Patient registration | `POST /api/auth/register` with `role: "PATIENT"` |
| Doctor registration | `POST /api/auth/register` with `role: "DOCTOR"` + professional fields |
| "Check my request" page | `GET /api/auth/registration-status?reference=…` |
| Login | `POST /api/auth/login` |
| Restore session / who am I | `GET /api/auth/me` |
| Developer/admin option | `POST /api/auth/admin/bootstrap` (header `X-Bootstrap-Secret`) |
| Admin dashboard: pending requests | `GET /api/admin/registration-requests?status=PENDING_APPROVAL` |
| Admin approve / reject | `PATCH …/{id}/approve`, `PATCH …/{id}/reject` |
| Admin lists | `/api/admin/users`, `/patients`, `/doctors`, `/appointments` |
| Suspend / reactivate | `PATCH /api/admin/users/{id}/status` |
| Patient profile | `GET`/`PUT /api/patients/me` |
| Doctor directory & booking | `GET /api/doctors`, `POST /api/appointments` |
| My appointments | `GET /api/appointments/my` |
| Cancel / confirm / complete | `POST /api/appointments/{id}/cancel`, `PATCH …/{id}/status` |
| Doctor profile & schedule | `GET`/`PUT /api/doctors/me`, `GET /api/doctors/me/appointments` |

## 4. Registration outcomes

```js
// authService.js
export async function register(form) {
  const { data, status } = await api.post("/api/auth/register", form);
  if (data.outcome === "ACTIVE") {
    // patient: save token, go to dashboard
    sessionStorage.setItem("medisynth_token", data.accessToken);
    return { next: "/patient", user: data.user };
  }
  // PENDING_APPROVAL (HTTP 202): there is NO token. Show the "awaiting approval" page and the
  // reference (shown once; the applicant keeps it to check status later).
  return { next: "/registration-pending", reference: data.registrationReference };
}
```

| Result | UI |
|---|---|
| `201 outcome=ACTIVE` | store token → patient dashboard |
| `202 outcome=PENDING_APPROVAL` | "Awaiting admin approval" page + reference + link to the status page |
| status check `APPROVED` | "Approved: you can log in now" |
| status check `REJECTED` | "Not approved" + `rejectionReason` |
| login `403 ACCOUNT_PENDING_APPROVAL / ACCOUNT_REJECTED / ACCOUNT_SUSPENDED` | matching message, no dashboard |
| `403 ADMIN_REGISTRATION_FORBIDDEN` | should never happen from the normal form; the form must not offer ADMIN |

## 5. Routing after login

Use the role returned by the server (`user.role`), never one the user picked:

```js
const HOME = { PATIENT: "/patient", DOCTOR: "/doctor", ADMIN: "/admin" };
navigate(HOME[data.user.role] ?? "/login");
```
The earlier frontend also modelled `lab_technician`, `pharmacist` and `researcher`; this backend only has
`PATIENT`, `DOCTOR`, `ADMIN`, so those demo roles have no real login yet.
The backend returns upper-case roles; the mock layer used lower-case, so lower-case them in one place if needed.

## 6. "I am a developer and want to create an admin account"

- Show it as a separate option on the registration page. Selecting it proves nothing.
- When selected, add a **password-type** input "Developer bootstrap secret" (typed by the developer at runtime,
  never prefilled, never stored in state longer than the request, never in localStorage).
- Submit to the dedicated endpoint, and show success only after the server answers `201`:

```js
await api.post("/api/auth/admin/bootstrap",
  { name, email, password, phone },
  { headers: { "X-Bootstrap-Secret": bootstrapSecret } });
```
- `403` → "Not authorized" (the same answer whether the secret is wrong or the feature is switched off).
  `409 BOOTSTRAP_ALREADY_USED` → "An administrator already exists. Ask an existing admin to create your account."
- Safer alternative: create the first admin with `curl` (README) so the secret never touches a browser. After the
  first admin exists the form is dead anyway; further admins are created by an admin via `POST /api/admin/users/admins`.

## 7. Token storage

The example uses `sessionStorage` (cleared when the tab closes). Any JavaScript-readable storage is exposed to XSS;
keep the React app free of `dangerouslySetInnerHTML`, and keep tokens short-lived (`JWT_EXPIRATION_MS`).
There is no refresh-token endpoint, so users log in again when the token expires (default 1 hour).
