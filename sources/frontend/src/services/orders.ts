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
import { httpGet, httpPost } from './http';

export type _PathsSurface = _OrdersPaths;

export interface ListOrdersParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function createOrder(body: CreateOrderRequest): Promise<Order> {
  return httpPost<Order>(`/api/orders/orders`, body);
}

export function listMyOrders(params?: ListOrdersParams): Promise<PaginatedResponse<Order>> {
  const qs = new URLSearchParams();
  if (params?.page !== undefined) qs.set('page', String(params.page));
  if (params?.size !== undefined) qs.set('size', String(params.size));
  if (params?.sort)               qs.set('sort', params.sort);
  const suffix = qs.toString() ? `?${qs}` : '';
  return httpGet<PaginatedResponse<Order>>(`/api/orders/orders${suffix}`);
}

export function getOrderById(id: string): Promise<Order> {
  return httpGet<Order>(`/api/orders/orders/${encodeURIComponent(id)}`);
}
