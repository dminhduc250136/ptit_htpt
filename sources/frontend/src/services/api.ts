/**
 * Utility helpers re-exported for backward-compat with legacy importers.
 * Mock-backed data helpers REMOVED in Phase 5 Plan 09 (mock-data cleanup).
 * Admin/* pages now use stub empty arrays (Phase 7 UI-02..04 will wire real API).
 *
 * Remaining consumers of this module (formatPrice only):
 *   - src/app/cart/page.tsx
 *   - src/app/checkout/page.tsx
 *   - src/app/profile/page.tsx
 *   - src/app/products/[slug]/page.tsx
 *   - src/app/profile/orders/[id]/page.tsx
 *   - src/components/ui/ProductCard/ProductCard.tsx
 */

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
