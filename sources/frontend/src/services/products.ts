/**
 * Product service API — listProducts, getProductById, listCategories, etc.
 *
 * Source: 04-RESEARCH.md §Pattern 2. Types derived from generated products.paths.
 * Gateway prefix /api/products/ + inner controller paths (verified via
 * products.generated.ts keys: /products, /products/{id}, /products/categories,
 * /products/categories/{id}).
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
  return httpGet<PaginatedResponse<Product>>(`/api/products/products${suffix}`);
}

export function getProductById(id: string): Promise<Product> {
  return httpGet<Product>(`/api/products/products/${encodeURIComponent(id)}`);
}

/**
 * Slug-based lookup. Backend does not (yet) expose /products/slug/{slug} per the
 * generated paths keys (only /products/{id}). Implementation fetches by slug as a
 * query parameter against the list endpoint and returns the first match. When
 * backend exposes a dedicated slug route, swap to a single GET call.
 */
export async function getProductBySlug(slug: string): Promise<Product | null> {
  const qs = new URLSearchParams({ slug });
  const page = await httpGet<PaginatedResponse<Product>>(`/api/products/products?${qs}`);
  return page?.content?.[0] ?? null;
}

export function listCategories(): Promise<PaginatedResponse<Category>> {
  return httpGet<PaginatedResponse<Category>>(`/api/products/products/categories`);
}
