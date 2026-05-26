/**
 * Phase 19 / Plan 19-04 (ADMIN-01..05). Chart fetchers cho admin dashboard.
 * Endpoints backend Plans 19-01/02/03 — gateway routes /api/{orders,users,products}/admin/**.
 * Bearer token + ApiResponse envelope unwrap auto-handled bởi http.ts.
 */
import { httpGet } from './http';

export interface RevenuePoint {
  date: string;
  value: number;
}

export interface TopProductPoint {
  productId: string;
  name: string;
  brand: string | null;
  thumbnailUrl: string | null;
  qtySold: number;
}

export interface StatusPoint {
  status: string;
  count: number;
}

export interface SignupPoint {
  date: string;
  count: number;
}

export interface LowStockItem {
  id: string;
  name: string;
  brand: string | null;
  thumbnailUrl: string | null;
  stock: number;
}

export type Range = '7d' | '30d' | '90d' | 'all';

export const fetchRevenueChart = (range: Range) =>
  httpGet<RevenuePoint[]>(`/api/orders/admin/charts/revenue?range=${range}`);

export const fetchTopProducts = (range: Range) =>
  httpGet<TopProductPoint[]>(`/api/orders/admin/charts/top-products?range=${range}`);

export const fetchStatusDistrib = () =>
  httpGet<StatusPoint[]>(`/api/orders/admin/charts/status-distribution`);

export const fetchUserSignups = (range: Range) =>
  httpGet<SignupPoint[]>(`/api/users/admin/charts/signups?range=${range}`);

export const fetchLowStock = () =>
  httpGet<LowStockItem[]>(`/api/products/admin/charts/low-stock`);
