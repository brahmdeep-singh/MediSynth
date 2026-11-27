// Central HTTP layer for the MediSynth Spring Boot backend.
//
//  - VITE_USE_MOCKS=false (default): auth, registration, approvals, doctors and
//    appointments call the real backend.
//  - VITE_USE_MOCKS=true: everything runs on in-memory mock data (demo mode).
//
// Features the backend does not have (billing, medical records, pharmacy,
// the pharmacist role) always use the mock services. Lab, researcher access
// and the Synthetix dataset pipeline are real.
//
// SECURITY NOTES
//  - The JWT lives in sessionStorage (cleared when the tab closes). That is
//    readable by any script on the page, so keep the app free of XSS (React
//    escapes output by default; never use dangerouslySetInnerHTML with API data).
//  - Frontend role checks only hide UI. The server enforces every permission.
//  - No secret (JWT secret, admin bootstrap secret) is ever stored in this code
//    or in any VITE_* variable. VITE_* values are public in the built bundle.

function resolveBase(raw) {
  // Accept "http://localhost:8080" or the older "http://localhost:8080/api".
  return (raw || "http://localhost:8080").replace(/\/+$/, "").replace(/\/api$/, "");
}

export const API_BASE_URL = resolveBase(import.meta.env.VITE_API_BASE_URL);
export const USE_MOCKS = import.meta.env.VITE_USE_MOCKS === "true";

const TOKEN_KEY = "medisynth.accessToken";

export function getToken() {
  try { return sessionStorage.getItem(TOKEN_KEY); } catch { return null; }
}
export function setToken(token) {
  try {
    if (token) sessionStorage.setItem(TOKEN_KEY, token);
    else sessionStorage.removeItem(TOKEN_KEY);
  } catch { /* storage unavailable: session just will not survive a refresh */ }
}

let unauthorizedHandler = null;
export function setUnauthorizedHandler(fn) { unauthorizedHandler = fn; }

export class ApiError extends Error {
  constructor(status, code, message, fieldErrors) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors || null;
  }
}

/**
 * Calls the backend. `path` starts with /api/...
 * Options: method, body (object -> JSON), headers, auth (default true), query (object).
 * Resolves with parsed JSON (or null for empty bodies); rejects with ApiError.
 */
export async function http(path, { method = "GET", body, headers = {}, auth = true, query } = {}) {
  let url = `${API_BASE_URL}${path}`;
  if (query) {
    const qs = new URLSearchParams();
    Object.entries(query).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== "") qs.append(k, v); });
    const s = qs.toString();
    if (s) url += `?${s}`;
  }

  const finalHeaders = { Accept: "application/json", ...headers };
  if (body !== undefined) finalHeaders["Content-Type"] = "application/json";
  const token = getToken();
  if (auth && token) finalHeaders.Authorization = `Bearer ${token}`;

  let res;
  try {
    res = await fetch(url, { method, headers: finalHeaders, body: body !== undefined ? JSON.stringify(body) : undefined });
  } catch {
    throw new ApiError(0, "NETWORK_ERROR", "Cannot reach the server. Check your connection and that the backend is running.");
  }

  let data = null;
  const text = await res.text();
  if (text) {
    try { data = JSON.parse(text); } catch { data = null; }
  }

  if (!res.ok) {
    // A 401 on an authenticated call means the token is missing/expired/revoked.
    // (Login itself passes auth:false errors through untouched.)
    if (res.status === 401 && auth && token && unauthorizedHandler) unauthorizedHandler();
    const message =
      data?.message ||
      (res.status === 429 ? "Too many attempts. Please wait a moment and try again." : `Request failed (${res.status}).`);
    throw new ApiError(res.status, data?.code || `HTTP_${res.status}`, message, data?.fieldErrors);
  }
  return data;
}

/**
 * Downloads a file the backend serves behind the Bearer token (an <a href> cannot send the header).
 * The server's Content-Disposition filename is used when the browser exposes it, else fallbackName.
 */
export async function downloadFile(path, fallbackName = "download.csv") {
  const token = getToken();
  let res;
  try {
    res = await fetch(`${API_BASE_URL}${path}`, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
  } catch {
    throw new ApiError(0, "NETWORK_ERROR", "Cannot reach the server. Check your connection and that the backend is running.");
  }
  if (!res.ok) {
    let data = null;
    try { data = await res.json(); } catch { data = null; }
    if (res.status === 401 && token && unauthorizedHandler) unauthorizedHandler();
    throw new ApiError(res.status, data?.code || `HTTP_${res.status}`, data?.message || `Download failed (${res.status}).`);
  }
  const blob = await res.blob();
  const match = /filename="?([^";]+)"?/.exec(res.headers.get("Content-Disposition") || "");
  saveBlob(blob, (match && match[1]) || fallbackName);
}

export function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

// ------------------------------------------------------------------ mock helpers

const MOCK_DELAY = 400;

export function simulateRequest(data, { delay = MOCK_DELAY, failRate = 0 } = {}) {
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      if (failRate > 0 && Math.random() < failRate) {
        reject(new Error("Network error - please try again."));
        return;
      }
      resolve(data === undefined ? data : JSON.parse(JSON.stringify(data)));
    }, delay);
  });
}
