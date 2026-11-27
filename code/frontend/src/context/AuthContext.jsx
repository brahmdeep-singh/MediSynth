import { createContext, useContext, useState, useCallback, useEffect } from "react";
import * as authService from "../services/authService";
import { setUnauthorizedHandler, getToken, USE_MOCKS } from "../services/api";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  // True while a stored token is being re-validated with the server on first load.
  const [initializing, setInitializing] = useState(() => !USE_MOCKS && !!getToken());

  // Restore the session from a stored token, but trust only what the server says.
  useEffect(() => {
    if (!initializing) return;
    let cancelled = false;
    authService.restoreSession()
      .then((u) => { if (!cancelled) setUser(u); })
      .catch(() => { /* server unreachable: stay signed out, user can retry from /login */ })
      .finally(() => { if (!cancelled) setInitializing(false); });
    return () => { cancelled = true; };
  }, [initializing]);

  // Any 401 from an authenticated call (expired/revoked/suspended) signs the user out.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      authService.logout();
      setUser(null);
    });
    return () => setUnauthorizedHandler(null);
  }, []);

  const login = useCallback(async (email, password) => {
    setLoading(true);
    setError(null);
    try {
      const loggedInUser = await authService.login(email, password);
      setUser(loggedInUser);
      return loggedInUser;
    } catch (err) {
      setError(err.message);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  const loginAsDemoRole = useCallback(async (userId) => {
    setLoading(true);
    setError(null);
    try {
      const loggedInUser = await authService.loginAsDemoRole(userId);
      setUser(loggedInUser);
      return loggedInUser;
    } finally {
      setLoading(false);
    }
  }, []);

  /** Registers; a patient is signed in immediately, a doctor is not (pending approval). */
  const register = useCallback(async (form) => {
    const result = await authService.register(form);
    if (result.outcome === "ACTIVE" && result.user) setUser(result.user);
    return result;
  }, []);

  const logout = useCallback(async () => {
    await authService.logout();
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider value={{ user, loading, error, initializing, login, loginAsDemoRole, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within an AuthProvider");
  return ctx;
}
