/**
 * Phase 13 — Reviews & Ratings service.
 * Endpoints (qua api-gateway):
 *   GET    /api/products/{productId}/reviews?page=0&size=10  — list paginated
 *   GET    /api/products/{productId}/reviews/eligibility     — check buyer eligibility (Bearer required)
 *   POST   /api/products/{productId}/reviews                 — submit review (Bearer required)
 */
import type { Review } from '@/types';
import { httpGet, httpPost } from './http';

export interface ReviewListResponse {
  content: Review[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
  isFirst: boolean;
  isLast: boolean;
}

export function listReviews(
  productId: string,
  page = 0,
  size = 10
): Promise<ReviewListResponse> {
  return httpGet<ReviewListResponse>(
    `/api/products/${encodeURIComponent(productId)}/reviews?page=${page}&size=${size}`
  );
}

export function checkEligibility(productId: string): Promise<{ eligible: boolean }> {
  return httpGet<{ eligible: boolean }>(
    `/api/products/${encodeURIComponent(productId)}/reviews/eligibility`
  );
}

export function submitReview(
  productId: string,
  body: { rating: number; content?: string }
): Promise<Review> {
  return httpPost<Review>(
    `/api/products/${encodeURIComponent(productId)}/reviews`,
    body
  );
}
