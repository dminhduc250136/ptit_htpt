/**
 * Token storage — localStorage accessors + auth_present cookie management.
 *
 * Source: 04-RESEARCH.md §Pattern 1 + §D-11/D-12 middleware/localStorage conflict
 * resolution. The access token lives in localStorage (D-11, XSS tradeoff accepted).
 * A minimal non-httpOnly "auth_present" cookie is written alongside so middleware.ts
 * can see the user is logged in (cookie value is "1", no PII, no JWT).
 *
 * All readers are SSR-safe (typeof window guard per Pitfall 2).
 * clearTokens zeroes both the localStorage keys AND the cookie — mitigates T-04-04
 * (stale-token-after-logout).
 */

const ACCESS_KEY = 'accessToken';
const REFRESH_KEY = 'refreshToken';
const PRESENCE_COOKIE = 'auth_present';

export function getAccessToken(): string | null {
  if (typeof window === 'undefined') return null;
  return window.localStorage.getItem(ACCESS_KEY);
}

export function getRefreshToken(): string | null {
  if (typeof window === 'undefined') return null;
  return window.localStorage.getItem(REFRESH_KEY);
}

export function setTokens(access: string, refresh: string): void {
  if (typeof window === 'undefined') return;
  window.localStorage.setItem(ACCESS_KEY, access);
  window.localStorage.setItem(REFRESH_KEY, refresh);
  // Non-httpOnly presence cookie so middleware.ts can see the user is logged in.
  // Value is intentionally minimal ('1') — no PII, no JWT. Backend never reads this cookie.
  // Max-Age=2592000 = 30 days.
  document.cookie = `${PRESENCE_COOKIE}=1; Path=/; SameSite=Lax; Max-Age=2592000`;
}

export function clearTokens(): void {
  if (typeof window === 'undefined') return;
  window.localStorage.removeItem(ACCESS_KEY);
  window.localStorage.removeItem(REFRESH_KEY);
  document.cookie = `${PRESENCE_COOKIE}=; Path=/; SameSite=Lax; Max-Age=0`;
}
