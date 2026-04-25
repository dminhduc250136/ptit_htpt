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
 */

import type { paths as _UsersPaths } from '@/types/api/users.generated';
import type { LoginRequest, RegisterRequest, AuthResponse } from '@/types';
import { httpPost } from './http';
import { setTokens, clearTokens } from './token';

// _UsersPaths is intentionally referenced via type import only — pins this module
// to the generated OpenAPI surface. When auth endpoints exist, swap the hand-narrowed
// types below for paths['/auth/login']['post']['requestBody']['content']['application/json']
// and paths['/auth/login']['post']['responses']['200']['content']['application/json'].
export type _PathsSurface = _UsersPaths;

export async function login(body: LoginRequest): Promise<AuthResponse> {
  const data = await httpPost<AuthResponse>('/api/users/auth/login', body);
  setTokens(data.accessToken, data.refreshToken);
  return data;
}

export async function register(body: RegisterRequest): Promise<AuthResponse> {
  const data = await httpPost<AuthResponse>('/api/users/auth/register', body);
  // Backend may or may not return tokens on register. If present, persist them
  // so the user is logged in immediately; otherwise the caller navigates to /login.
  if (data && data.accessToken && data.refreshToken) {
    setTokens(data.accessToken, data.refreshToken);
  }
  return data;
}

export function logout(): void {
  clearTokens();
  // If the backend ever exposes /api/users/auth/logout, fire-and-forget here.
  // We clear tokens unconditionally so the presence cookie is zeroed even on
  // network failure.
}

// NOTE: refreshToken() intentionally NOT exported. users.generated.ts does not
// expose a refresh endpoint (Q2 from RESEARCH). D-08 fallback (silent redirect on 401)
// covers the missing refresh flow. Re-enable here when the backend ships POST
// /api/users/auth/refresh.
