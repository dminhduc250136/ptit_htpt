/**
 * Order service API — createOrder, listMyOrders, getOrderById.
 *
 * Source: 04-RESEARCH.md §Pattern 2. Gateway /api/orders/ + inner /orders
 * controller path (verified via orders.generated.ts keys: /orders, /orders/{id},
 * /admin/orders, /cart, /cart/{id}).
 *
 * Pitfall 7 note: response shapes hand-narrowed to UI types from @/types
 * (Order, CreateOrderRequest, PaginatedResponse). Swap to generated paths[...]
 * once backend annotates @ApiResponse content for the orders controller.
 */

// ===== ORDER SERVICE API =====

import type { paths as _OrdersPaths } from '@/types/api/orders.generated';
import type { Order, CreateOrderRequest, PaginatedResponse } from '@/types';
import { httpGet, httpPost, httpPatch } from './http';

export type _PathsSurface = _OrdersPaths;

export interface ListOrdersParams {
  page?: number;
  size?: number;
  sort?: string;
  // Phase 11 / ACCT-02 (D-10, D-11, D-12, D-13, D-14, D-15): filter params
  /** Status filter: 'ALL' | 'PENDING' | 'CONFIRMED' | 'SHIPPING' | 'DELIVERED' | 'CANCELLED' */
  status?: string;
  /** Date from (YYYY-MM-DD). Backend interpret full day UTC+7 (D-14). */
  from?: string;
  /** Date to (YYYY-MM-DD). Backend interpret full day UTC+7 (D-14). */
  to?: string;
  /** Keyword tìm theo order ID (ILIKE). D-13: order ID only. */
  q?: string;
}

/**
 * Create an order. Per backend CreateOrderCommand (04-05): userId is derived
 * server-side from the X-User-Id header (Phase 5 will move to JWT-claim
 * verification at the gateway). Each item carries its unitPrice snapshot
 * from the cart so the backend can compute totalAmount.
 */
export function createOrder(body: CreateOrderRequest, userId?: string): Promise<Order> {
  const headers: Record<string, string> = {};
  if (userId) headers['X-User-Id'] = userId;
  return httpPost<Order>(`/api/orders/orders`, body, headers);
}

export function listMyOrders(params?: ListOrdersParams): Promise<PaginatedResponse<Order>> {
  const qs = new URLSearchParams();
  if (params?.page !== undefined) qs.set('page', String(params.page));
  if (params?.size  !== undefined) qs.set('size',  String(params.size));
  if (params?.sort)                qs.set('sort',  params.sort);
  // Phase 11 / ACCT-02: filter params
  if (params?.status && params.status !== 'ALL') qs.set('status', params.status);
  if (params?.from)  qs.set('from', params.from);
  if (params?.to)    qs.set('to',   params.to);
  if (params?.q)     qs.set('q',    params.q);
  const suffix = qs.toString() ? `?${qs}` : '';
  return httpGet<PaginatedResponse<Order>>(`/api/orders/orders${suffix}`);
}

export function getOrderById(id: string): Promise<Order> {
  return httpGet<Order>(`/api/orders/orders/${encodeURIComponent(id)}`);
}

// Admin order functions — gateway: /api/orders/admin → /admin/orders

export function listAdminOrders(params?: ListOrdersParams): Promise<PaginatedResponse<Order>> {
  const qs = new URLSearchParams();
  if (params?.page !== undefined) qs.set('page', String(params.page));
  if (params?.size  !== undefined) qs.set('size',  String(params.size));
  if (params?.sort)                qs.set('sort',  params.sort);
  const suffix = qs.toString() ? `?${qs}` : '';
  return httpGet<PaginatedResponse<Order>>(`/api/orders/admin${suffix}`);
}

export function getAdminOrderById(id: string): Promise<Order> {
  return httpGet<Order>(`/api/orders/admin/${encodeURIComponent(id)}`);
}

export function updateOrderState(id: string, status: string): Promise<Order> {
  return httpPatch<Order>(`/api/orders/admin/${encodeURIComponent(id)}/state`, { state: status });
}
