/**
 * Typed HTTP wrapper — reads accessToken from localStorage, attaches
 * Authorization: Bearer, auto-unwraps ApiResponse envelope, throws ApiError
 * on failure. No auto-retry on any method (D-10: mutations must not duplicate;
 * GET retries are driven by the UI RetrySection).
 *
 * Source: 04-RESEARCH.md §Pattern 1. Mitigations in this module:
 * - T-04-03 (open redirect via returnTo): 401 handler validates pathname starts with '/'
 *   AND not '//' before encoding it into the /login redirect.
 * - T-04-04 (stale token): 401 branch calls clearTokens() before redirect.
 *
 * BUG-FIX (login-redirect-loop): Auth endpoints (/auth/login, /auth/register) intentionally
 * return 401 for bad credentials — they must NOT trigger the "session expired" redirect because
 * the caller (login page) handles the 401 itself to show an error banner. Only non-auth 401s
 * (i.e. stale tokens on protected endpoints) should redirect to /login.
 */

import { ApiError, type FieldError } from './errors';
import { getAccessToken, clearTokens } from './token';

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';

// Paths whose 401 responses are intentional credential rejections — callers handle them
// directly. Do NOT redirect to /login for these paths (that would create an infinite loop
// when the user is already on /login and enters wrong credentials).
const AUTH_PATHS_NO_REDIRECT = [
  '/api/users/auth/login',
  '/api/users/auth/register',
];

interface ApiEnvelope<T> {
  timestamp?: string;
  status?: number;
  message?: string;
  data: T;
}

interface ApiErrorBody {
  status: number;
  error?: string;
  message: string;
  code: string;
  path?: string;
  traceId?: string;
  fieldErrors?: FieldError[];
  details?: Record<string, unknown>;
}

async function request<T>(
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE',
  path: string,
  body?: unknown,
  extraHeaders?: Record<string, string>,
): Promise<T> {
  const token = getAccessToken();
  const headers: Record<string, string> = {
    'Accept': 'application/json',
  };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers['Authorization'] = `Bearer ${token}`;
  if (extraHeaders) {
    for (const [k, v] of Object.entries(extraHeaders)) {
      // Skip undefined/empty so callers can pass `userId ? { 'X-User-Id': userId } : undefined`
      if (v) headers[k] = v;
    }
  }

  let res: Response;
  try {
    res = await fetch(`${BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'omit',       // token is in header, not cookie
    });
  } catch {
    // Classify as INTERNAL_ERROR for the dispatcher; no retry for mutations (D-10).
    throw new ApiError('INTERNAL_ERROR', 0, 'Network error', [], undefined, path);
  }

  const text = await res.text();
  // WR-02: wrap JSON.parse in try/catch — non-JSON 5xx (e.g. HTML error page from
  // gateway/Nginx) would otherwise throw SyntaxError and bypass the dispatcher
  // contract. Normalize to ApiError('INTERNAL_ERROR', ...) on parse failure for
  // !res.ok; for OK responses with malformed body, fall back to null data.
  let parsed: unknown = null;
  if (text) {
    try {
      parsed = JSON.parse(text);
    } catch {
      if (!res.ok) {
        throw new ApiError(
          'INTERNAL_ERROR',
          res.status,
          `Request failed (${res.status})`,
          [],
          undefined,
          path,
        );
      }
      parsed = null;
    }
  }

  if (res.ok) {
    // Success envelope: { timestamp, status, message, data }
    // If data is undefined (e.g., 204), return undefined as T.
    return (parsed as ApiEnvelope<T> | null)?.data as T;
  }

  // Failure envelope (identical keys on service-origin and gateway-origin per Phase 3 D-05..D-07)
  const err = (parsed ?? {}) as Partial<ApiErrorBody>;

  // Silent 401: clear tokens and redirect to /login.
  // Exception: auth endpoints (login/register) intentionally return 401 for bad credentials —
  // their callers handle the ApiError to display an error banner. Redirecting here would
  // produce GET /login?returnTo=%2Flogin (infinite loop) because pathname IS /login.
  if (res.status === 401 && !AUTH_PATHS_NO_REDIRECT.includes(path)) {
    clearTokens();
    if (typeof window !== 'undefined') {
      // Open-redirect hardening (T-04-03): validate pathname is a local relative path.
      // Reject protocol-relative ('//evil.com') and absolute URLs.
      const pathname = window.location.pathname;
      if (pathname.startsWith('/') && !pathname.startsWith('//')) {
        const returnTo = encodeURIComponent(pathname);
        window.location.href = `/login?returnTo=${returnTo}`;
      } else {
        window.location.href = `/login`;
      }
    }
  }

  throw new ApiError(
    err.code ?? 'INTERNAL_ERROR',
    err.status ?? res.status,
    err.message ?? `Request failed (${res.status})`,
    err.fieldErrors ?? [],
    err.traceId,
    err.path,
    err.details,
  );
}

export const httpGet    = <T>(path: string) => request<T>('GET', path);
export const httpPost   = <T>(path: string, body?: unknown, extraHeaders?: Record<string, string>) => request<T>('POST', path, body, extraHeaders);
export const httpPut    = <T>(path: string, body?: unknown, extraHeaders?: Record<string, string>) => request<T>('PUT', path, body, extraHeaders);
export const httpPatch  = <T>(path: string, body?: unknown, extraHeaders?: Record<string, string>) => request<T>('PATCH', path, body, extraHeaders);
export const httpDelete = <T>(path: string) => request<T>('DELETE', path);
