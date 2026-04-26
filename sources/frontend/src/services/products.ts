/**
 * Product service API — listProducts, getProductById, listCategories, etc.
 *
 * Source: 04-RESEARCH.md §Pattern 2. Types derived from generated products.paths.
 * Gateway paths (corrected Phase 5 Plan 09 — Rule 1 bug fix):
 *   Gateway /api/products   → product-service /products   (list)
 *   Gateway /api/products/{id} → product-service /products/{id}   (detail)
 *   Gateway /api/products/categories → product-service /products/categories
 * Previous paths had double /api/products/products which caused 404 (gateway
 * strips /api/products prefix, forwarding /products/{seg} to product-service).
 *
 * Pitfall 7 note: springdoc emits `never` for several response bodies because
 * ApiResponseAdvice wraps the data field invisibly. http.ts unwraps one envelope
 * level; list/detail shapes are therefore hand-narrowed to the UI-owned Product
 * and PaginatedResponse types from @/types. When backend controllers add
 * @ApiResponse(content=@Content(schema=...)) annotations, swap hand-narrow for
 * paths[...] accessors.
 */

// ===== PRODUCT SERVICE API =====

import type { paths as _ProductsPaths } from '@/types/api/products.generated';
import type { Product, Category, PaginatedResponse } from '@/types';
import { httpGet } from './http';

export type _PathsSurface = _ProductsPaths;

export interface ListProductsParams {
  page?: number;
  size?: number;
  sort?: string;
  categoryId?: string;
  keyword?: string;
}

export function listProducts(params?: ListProductsParams): Promise<PaginatedResponse<Product>> {
  const qs = new URLSearchParams();
  if (params?.page !== undefined) qs.set('page', String(params.page));
  if (params?.size !== undefined) qs.set('size', String(params.size));
  if (params?.sort)               qs.set('sort', params.sort);
  if (params?.categoryId)         qs.set('categoryId', params.categoryId);
  if (params?.keyword)            qs.set('keyword', params.keyword);
  const suffix = qs.toString() ? `?${qs}` : '';
  return httpGet<PaginatedResponse<Product>>(`/api/products${suffix}`);
}

export function getProductById(id: string): Promise<Product> {
  return httpGet<Product>(`/api/products/${encodeURIComponent(id)}`);
}

/**
 * Slug-based lookup. Backend does not expose a dedicated /products/slug/{slug}
 * endpoint — it ignores unknown query params and returns all products.
 * Strategy: fetch full first page (size=50), then filter client-side by slug.
 * Phase 7 UI-01: when backend adds slug filter support, simplify to single call.
 */
export async function getProductBySlug(slug: string): Promise<Product | null> {
  const page = await httpGet<PaginatedResponse<Product>>(`/api/products?size=50`);
  return page?.content?.find(p => p.slug === slug) ?? null;
}

export function listCategories(): Promise<PaginatedResponse<Category>> {
  return httpGet<PaginatedResponse<Category>>(`/api/products/categories`);
}
