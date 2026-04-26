/**
 * Authentication service — wraps user-service auth endpoints.
 *
 * IMPORTANT DEVIATION (Rule 1/Pitfall 7): user-service does NOT currently expose
 * /auth/login, /auth/register, or /auth/refresh endpoints (verified via
 * users.generated.ts — paths only include /users/profiles, /users/addresses,
 * /admin/users, /__contract/*, /ping). Calls to these endpoints will 404 at
 * runtime until backend Phase 2/5 exposes them. This module compiles today and
 * will function once the backend adds the routes.
 *
 * Types are hand-narrowed against the existing UI shapes in @/types (LoginRequest,
 * RegisterRequest, AuthResponse) per RESEARCH Pitfall 7 (hand-narrow at call site
 * when generated type is `never` or missing). We still import `paths` from the
 * generated module so this service remains coupled to the OpenAPI surface and
 * will start using generated types the moment the backend publishes them.
 *
 * Phase 6 update:
 * - login/register: gọi setUserRole(data.user.roles) sau khi nhận token (D-08)
 * - register: auto-login check chỉ cần accessToken (không check refreshToken — D-04)
 * - logout: fire-and-forget POST /api/users/auth/logout (D-05)
 * - setTokens: refresh param là optional — tránh localStorage "undefined" (Pitfall 3)
 */

import type { paths as _UsersPaths } from '@/types/api/users.generated';
import type { LoginRequest, RegisterRequest, AuthResponse } from '@/types';
import { httpPost } from './http';
import { setTokens, clearTokens, setUserRole } from './token';

// _UsersPaths is intentionally referenced via type import only — pins this module
// to the generated OpenAPI surface. When auth endpoints exist, swap the hand-narrowed
// types below for paths['/auth/login']['post']['requestBody']['content']['application/json']
// and paths['/auth/login']['post']['responses']['200']['content']['application/json'].
export type _PathsSurface = _UsersPaths;

export async function login(body: LoginRequest): Promise<AuthResponse> {
  const data = await httpPost<AuthResponse>('/api/users/auth/login', body);
  setTokens(data.accessToken, data.refreshToken ?? undefined);
  // Set user_role cookie so middleware.ts can check admin access (D-08)
  if (data.user?.roles) {
    setUserRole(data.user.roles);
  }
  return data;
}

export async function register(body: RegisterRequest): Promise<AuthResponse> {
  const data = await httpPost<AuthResponse>('/api/users/auth/register', body);
  // D-04: auto-login ngay sau register — backend trả accessToken (refreshToken không cần)
  if (data?.accessToken) {
    setTokens(data.accessToken, data.refreshToken ?? undefined);
    if (data.user?.roles) {
      setUserRole(data.user.roles);
    }
  }
  return data;
}

export function logout(): void {
  clearTokens(); // clearTokens đã gọi clearUserRole() nội bộ (D-05)
  // Fire-and-forget backend logout (D-05: client-side discard chính, backend không blacklist)
  httpPost('/api/users/auth/logout').catch(() => { /* no-op */ });
}

// NOTE: refreshToken() intentionally NOT exported. users.generated.ts does not
// expose a refresh endpoint (Q2 from RESEARCH). D-08 fallback (silent redirect on 401)
// covers the missing refresh flow. Re-enable here when the backend ships POST
// /api/users/auth/refresh.
