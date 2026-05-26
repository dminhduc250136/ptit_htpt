/**
 * Phase 13 — Reviews & Ratings service.
 * Phase 21 — Mở rộng cho REV-04 (author edit/delete), REV-05 (sort), REV-06 (admin moderation).
 *
 * Endpoints (qua api-gateway):
 *   GET    /api/products/{productId}/reviews?page=&size=&sort=newest|rating_desc|rating_asc
 *   GET    /api/products/{productId}/reviews/eligibility     — Bearer required
 *   POST   /api/products/{productId}/reviews                 — Bearer required
 *   PATCH  /api/products/{productId}/reviews/{reviewId}      — Bearer required (author edit, 24h window)
 *   DELETE /api/products/{productId}/reviews/{reviewId}      — Bearer required (author soft-delete)
 *
 *   Admin (gateway rewrite /api/products/admin/** → /admin/products/**):
 *   GET    /api/products/admin/reviews?page=&size=&filter=all|visible|hidden|deleted
 *   PATCH  /api/products/admin/reviews/{reviewId}/visibility
 *   DELETE /api/products/admin/reviews/{reviewId}            — hard delete
 */
import type { Review, AdminReview, SortKey } from '@/types';
import { httpGet, httpPost, httpPatch, httpDelete } from './http';

export interface ReviewListResponse {
  content: Review[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
  isFirst: boolean;
  isLast: boolean;
  config?: { editWindowHours: number };       // NEW Phase 21 (D-02): BE expose để FE disable button đúng
}

export function listReviews(
  productId: string,
  page = 0,
  size = 10,
  sort: SortKey = 'newest',
): Promise<ReviewListResponse> {
  const qs = new URLSearchParams();
  qs.set('page', String(page));
  qs.set('size', String(size));
  if (sort !== 'newest') qs.set('sort', sort);   // D-13: default newest KHÔNG ghi vào URL/qs
  return httpGet<ReviewListResponse>(
    `/api/products/${encodeURIComponent(productId)}/reviews?${qs.toString()}`,
  );
}

export function checkEligibility(productId: string): Promise<{ eligible: boolean }> {
  return httpGet<{ eligible: boolean }>(
    `/api/products/${encodeURIComponent(productId)}/reviews/eligibility`,
  );
}

export function submitReview(
  productId: string,
  body: { rating: number; content?: string },
): Promise<Review> {
  return httpPost<Review>(
    `/api/products/${encodeURIComponent(productId)}/reviews`,
    body,
  );
}

/** Phase 21 REV-04: author edit (BE re-check 24h window + ownership). */
export function editReview(
  productId: string,
  reviewId: string,
  body: { rating?: number; content?: string },
): Promise<Review> {
  return httpPatch<Review>(
    `/api/products/${encodeURIComponent(productId)}/reviews/${encodeURIComponent(reviewId)}`,
    body,
  );
}

/** Phase 21 REV-04: author soft-delete (set deleted_at). */
export function softDeleteReview(productId: string, reviewId: string): Promise<void> {
  return httpDelete<void>(
    `/api/products/${encodeURIComponent(productId)}/reviews/${encodeURIComponent(reviewId)}`,
  );
}

// === Admin endpoints (Finding 1: gateway rewrite /api/products/admin/** → /admin/products/**) ===

export interface AdminReviewListResponse {
  content: AdminReview[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
  isFirst: boolean;
  isLast: boolean;
}

/** Phase 21 REV-06: admin list (admin role required). */
export function listAdminReviews(
  page = 0,
  size = 20,
  filter: 'all' | 'visible' | 'hidden' | 'deleted' = 'all',
): Promise<AdminReviewListResponse> {
  const qs = new URLSearchParams({ page: String(page), size: String(size), filter });
  return httpGet<AdminReviewListResponse>(`/api/products/admin/reviews?${qs.toString()}`);
}

/** Phase 21 REV-06: admin hide/unhide. */
export function setReviewVisibility(reviewId: string, hidden: boolean): Promise<void> {
  return httpPatch<void>(
    `/api/products/admin/reviews/${encodeURIComponent(reviewId)}/visibility`,
    { hidden },
  );
}

/** Phase 21 REV-06: admin hard-delete (xoá row khỏi DB). */
export function hardDeleteReview(reviewId: string): Promise<void> {
  return httpDelete<void>(
    `/api/products/admin/reviews/${encodeURIComponent(reviewId)}`,
  );
}
