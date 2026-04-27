/**
 * Phase 9 / Plan 09-04 (UI-02). Stats wrappers cho admin dashboard.
 * Endpoints backend: Plan 09-02 (per-svc /admin/stats with manual JWT role check).
 * Bearer token auto-attached bởi http.ts.
 */
import { httpGet } from './http';

export interface ProductStats {
  totalProducts: number;
}

export interface OrderStats {
  totalOrders: number;
  pendingOrders: number;
}

export interface UserStats {
  totalUsers: number;
}

export function fetchProductStats(): Promise<ProductStats> {
  return httpGet<ProductStats>('/api/products/admin/stats');
}

export function fetchOrderStats(): Promise<OrderStats> {
  return httpGet<OrderStats>('/api/orders/admin/stats');
}

export function fetchUserStats(): Promise<UserStats> {
  return httpGet<UserStats>('/api/users/admin/stats');
}
