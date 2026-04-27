/**
 * Admin user service functions.
 * Gateway path: /api/users/admin → /admin/users
 * Bearer token auto-attached bởi http.ts.
 */
import type { User, PaginatedResponse } from '@/types';
import { httpGet, httpPatch, httpDelete, httpPost } from './http';

export interface ListUsersParams {
  page?: number;
  size?: number;
  sort?: string;
}

export interface AdminUserPatchBody {
  fullName?: string;
  phone?: string;
  roles?: string;
}

export function listAdminUsers(params?: ListUsersParams): Promise<PaginatedResponse<User>> {
  const qs = new URLSearchParams();
  if (params?.page !== undefined) qs.set('page', String(params.page));
  if (params?.size  !== undefined) qs.set('size',  String(params.size));
  if (params?.sort)                qs.set('sort',  params.sort);
  const suffix = qs.toString() ? `?${qs}` : '';
  return httpGet<PaginatedResponse<User>>(`/api/users/admin${suffix}`);
}

export function patchAdminUser(id: string, body: AdminUserPatchBody): Promise<User> {
  return httpPatch<User>(`/api/users/admin/${encodeURIComponent(id)}`, body);
}

export function deleteAdminUser(id: string): Promise<void> {
  return httpDelete<void>(`/api/users/admin/${encodeURIComponent(id)}`);
}

// ============================================================
// Phase 9 / Plan 09-04 (AUTH-07). Self-service password change.
// Endpoint backend: POST /api/users/me/password (Plan 09-03).
// Backend trả code: "AUTH_INVALID_PASSWORD" (không phải errorCode).
// ============================================================

export interface ChangePasswordBody {
  oldPassword: string;
  newPassword: string;
}

export function changeMyPassword(body: ChangePasswordBody): Promise<{ changed: true }> {
  return httpPost<{ changed: true }>('/api/users/me/password', body);
}
