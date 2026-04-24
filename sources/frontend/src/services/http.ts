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
 */

import { ApiError, type FieldError } from './errors';
import { getAccessToken, clearTokens } from './token';

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';

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
): Promise<T> {
  const token = getAccessToken();
  const headers: Record<string, string> = {
    'Accept': 'application/json',
  };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers['Authorization'] = `Bearer ${token}`;

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
  const parsed = text ? JSON.parse(text) : null;

  if (res.ok) {
    // Success envelope: { timestamp, status, message, data }
    // If data is undefined (e.g., 204), return undefined as T.
    return (parsed as ApiEnvelope<T> | null)?.data as T;
  }

  // Failure envelope (identical keys on service-origin and gateway-origin per Phase 3 D-05..D-07)
  const err = (parsed ?? {}) as Partial<ApiErrorBody>;

  // Silent 401: clear tokens and redirect. Throw anyway so calling pages stop.
  if (res.status === 401) {
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
export const httpPost   = <T>(path: string, body?: unknown) => request<T>('POST', path, body);
export const httpPut    = <T>(path: string, body?: unknown) => request<T>('PUT', path, body);
export const httpPatch  = <T>(path: string, body?: unknown) => request<T>('PATCH', path, body);
export const httpDelete = <T>(path: string) => request<T>('DELETE', path);
