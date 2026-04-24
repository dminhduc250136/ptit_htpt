/**
 * LEGACY: mock-backed API helpers. Still used by admin/* pages (Phase 5 cleanup).
 * New code MUST import from services/http.ts + services/{domain}.ts instead.
 * formatPrice / formatPriceShort remain canonical and are re-exported from here.
 *
 * Audit (Phase 4-03): remaining non-admin importers of this module —
 *   - src/app/cart/page.tsx        → imports { formatPrice } only
 *   - src/app/checkout/page.tsx    → imports { formatPrice } only
 *   - src/app/profile/page.tsx     → imports { formatPrice } only
 *   - src/app/products/[slug]/page.tsx   → imports { formatPrice } only
 *   - src/app/profile/orders/[id]/page.tsx → imports { formatPrice } only (deferred rewire)
 *   - src/components/ui/ProductCard/ProductCard.tsx → imports { formatPrice } only
 * No non-admin page consumes the mock-backed data helpers (getProducts / getProductBySlug
 * / getProductById / getFeaturedProducts / getNewProducts / getCategories / getCategoryBySlug)
 * anymore — those are safe to delete in a future phase once admin/* is rewired. Leaving them
 * in place now keeps admin/* functional (still on mocks per 04-CONTEXT.md §Deferred Ideas).
 */

import { Product, Category, PaginatedResponse, ProductFilter } from '@/types';
import { mockProducts, mockCategories } from '@/mock-data/products';

// Simulate network delay
const delay = (ms: number = 500) => new Promise(resolve => setTimeout(resolve, ms));

// ===== PRODUCT SERVICE API =====

export async function getProducts(filter?: ProductFilter): Promise<PaginatedResponse<Product>> {
  await delay(300);

  let filtered = [...mockProducts];

  // Filter by category
  if (filter?.categoryId) {
    filtered = filtered.filter(p => p.category.id === filter.categoryId);
  }

  // Filter by keyword
  if (filter?.keyword) {
    const kw = filter.keyword.toLowerCase();
    filtered = filtered.filter(p =>
      p.name.toLowerCase().includes(kw) ||
      p.description.toLowerCase().includes(kw)
    );
  }

  // Filter by price range
  if (filter?.minPrice !== undefined) {
    filtered = filtered.filter(p => p.price >= filter.minPrice!);
  }
  if (filter?.maxPrice !== undefined) {
    filtered = filtered.filter(p => p.price <= filter.maxPrice!);
  }

  // Sort
  switch (filter?.sortBy) {
    case 'price_asc':
      filtered.sort((a, b) => a.price - b.price);
      break;
    case 'price_desc':
      filtered.sort((a, b) => b.price - a.price);
      break;
    case 'newest':
      filtered.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
      break;
    case 'rating':
      filtered.sort((a, b) => b.rating - a.rating);
      break;
    case 'popular':
      filtered.sort((a, b) => b.reviewCount - a.reviewCount);
      break;
  }

  // Pagination
  const page = filter?.page ?? 0;
  const size = filter?.size ?? 12;
  const start = page * size;
  const end = start + size;
  const paged = filtered.slice(start, end);

  return {
    content: paged,
    totalElements: filtered.length,
    totalPages: Math.ceil(filtered.length / size),
    currentPage: page,
    pageSize: size,
    isFirst: page === 0,
    isLast: end >= filtered.length,
  };
}

export async function getProductBySlug(slug: string): Promise<Product | null> {
  await delay(200);
  return mockProducts.find(p => p.slug === slug) || null;
}

export async function getProductById(id: string): Promise<Product | null> {
  await delay(200);
  return mockProducts.find(p => p.id === id) || null;
}

export async function getFeaturedProducts(): Promise<Product[]> {
  await delay(300);
  return mockProducts.filter(p => p.tags?.includes('Bán chạy') || p.tags?.includes('Best Seller'));
}

export async function getNewProducts(): Promise<Product[]> {
  await delay(300);
  return mockProducts
    .filter(p => p.tags?.includes('Mới'))
    .slice(0, 6);
}

export async function getCategories(): Promise<Category[]> {
  await delay(200);
  return mockCategories;
}

export async function getCategoryBySlug(slug: string): Promise<Category | null> {
  await delay(200);
  return mockCategories.find(c => c.slug === slug) || null;
}


// ===== UTILITY: Format Vietnamese currency =====
export function formatPrice(price: number): string {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
  }).format(price);
}

export function formatPriceShort(price: number): string {
  if (price >= 1000000) {
    return `${(price / 1000000).toFixed(1)}tr`;
  }
  if (price >= 1000) {
    return `${(price / 1000).toFixed(0)}k`;
  }
  return price.toString();
}
