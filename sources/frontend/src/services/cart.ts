/**
 * Client-only cart — reads/writes localStorage['cart'] and emits a
 * 'cart:change' CustomEvent so the header badge updates live.
 *
 * Source: 04-RESEARCH.md §Pattern 5 + D-14 (cart is client-only; on "Đặt hàng"
 * the full items[] list is POSTed to /api/orders).
 *
 * All readers are SSR-safe (typeof window guard per Pitfall 2).
 *
 * NOTE: This module defines its own CartItem shape (productId-only, no nested
 * Product) because localStorage persistence does not benefit from embedding full
 * Product snapshots that may go stale. The UI-layer CartItem type in @/types
 * (with nested Product) is a different concern and remains unchanged.
 */

// ===== CART (CLIENT-ONLY) =====

import type { Product } from '@/types';

const CART_KEY = 'cart';

export interface CartItem {
  productId: string;
  name: string;
  thumbnailUrl: string;
  price: number;
  quantity: number;
}

export function readCart(): CartItem[] {
  if (typeof window === 'undefined') return [];
  const raw = window.localStorage.getItem(CART_KEY);
  if (!raw) return [];
  try {
    return JSON.parse(raw) as CartItem[];
  } catch {
    return [];
  }
}

export function writeCart(items: CartItem[]): void {
  if (typeof window === 'undefined') return;
  window.localStorage.setItem(CART_KEY, JSON.stringify(items));
  window.dispatchEvent(new CustomEvent('cart:change'));
}

export function addToCart(
  product: Pick<Product, 'id' | 'name' | 'thumbnailUrl' | 'price'>,
  qty: number = 1,
): void {
  const items = readCart();
  const existing = items.find(i => i.productId === product.id);
  if (existing) {
    existing.quantity += qty;
  } else {
    items.push({
      productId: product.id,
      name: product.name,
      thumbnailUrl: product.thumbnailUrl,
      price: product.price,
      quantity: qty,
    });
  }
  writeCart(items);
}

export function removeFromCart(productId: string): void {
  writeCart(readCart().filter(i => i.productId !== productId));
}

export function updateQuantity(productId: string, qty: number): void {
  if (qty <= 0) {
    removeFromCart(productId);
    return;
  }
  const items = readCart().map(i => (i.productId === productId ? { ...i, quantity: qty } : i));
  writeCart(items);
}

export function clearCart(): void {
  if (typeof window === 'undefined') return;
  window.localStorage.removeItem(CART_KEY);
  window.dispatchEvent(new CustomEvent('cart:change'));
}
