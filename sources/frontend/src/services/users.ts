/**
 * Admin user service functions.
 * Gateway path: /api/users/admin → /admin/users
 * Bearer token auto-attached bởi http.ts.
 */
import type { User, PaginatedResponse, SavedAddress, AddressBody } from '@/types';
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

// ============================================================
// Phase 10 / ACCT-03. Self-service profile read & update.
// Endpoints backend: GET /api/users/me, PATCH /api/users/me.
// ============================================================

export interface UpdateMeBody {
  fullName?: string;
  phone?: string;
}

export function getMe(): Promise<User> {
  return httpGet<User>('/api/users/me');
}

export function patchMe(body: UpdateMeBody): Promise<User> {
  return httpPatch<User>('/api/users/me', body);
}

// ============================================================
// Phase 11 / ACCT-05. Address book CRUD.
// Endpoints backend: /api/users/me/addresses/**
// Gateway route: user-service-me/** → /users/me/**
// ============================================================

/**
 * Lấy danh sách addresses của user hiện tại.
 * Sort: is_default DESC, created_at DESC (backend sort).
 */
export function listAddresses(): Promise<SavedAddress[]> {
  return httpGet<SavedAddress[]>('/api/users/me/addresses');
}

/** Tạo address mới. Nếu > 10 → backend trả 422 ADDRESS_LIMIT_EXCEEDED. */
export function createAddress(body: AddressBody): Promise<SavedAddress> {
  return httpPost<SavedAddress>('/api/users/me/addresses', body);
}

/** Cập nhật address theo id (chỉ owner). */
export function updateAddress(id: string, body: AddressBody): Promise<SavedAddress> {
  return httpPatch<SavedAddress>(`/api/users/me/addresses/${encodeURIComponent(id)}`, body);
}

/** Xóa address theo id (hard-delete, chỉ owner). */
export function deleteAddress(id: string): Promise<void> {
  return httpDelete<void>(`/api/users/me/addresses/${encodeURIComponent(id)}`);
}

/** Đặt address là mặc định (clear others). */
export function setDefaultAddress(id: string): Promise<SavedAddress> {
  return httpPatch<SavedAddress>(`/api/users/me/addresses/${encodeURIComponent(id)}/default`, {});
}
