/**
 * Phase 18 / STORE-02: Dual-backend cart service.
 *
 * Routing:
 * - Guest (no accessToken): localStorage['cart'] — preserve behavior cũ qua _localCart() namespace.
 * - User (logged-in): API calls → BE order-svc CartController → DB persist.
 *
 * Public API giữ signature tương đồng v1.2 cho backward-compat tại call sites.
 * Tuy nhiên, vì user-path là async, các mutation function trở thành async — caller PHẢI await.
 *
 * Merge flow (Plan 05 sẽ wire vào AuthProvider.login):
 *   AuthProvider.login → setTokens → mergeGuestCartToServer() → success: clearCart() localStorage
 *
 * SSR-safe: typeof window guard ở mọi localStorage access.
 *
 * BUG-FIX (login-success-redirect-loop, regression sau 302e2a1):
 * Backend cart-service yêu cầu header `X-User-Id` (CartCrudService.requireUserId → 401).
 * Gateway hiện tại KHÔNG tự inject từ JWT, nên mọi cart endpoint phải tự gửi header này.
 * Đọc `userId` từ `localStorage.userProfile` (AuthProvider.login đã ghi trước khi gọi
 * mergeGuestCartToServer). Pattern khớp với `services/orders.ts` & `services/coupons.ts`.
 */

import type { Product } from '@/types';
import { httpGet, httpPost, httpPatch, httpDelete } from './http';
import { getAccessToken } from './token';
import { isApiError } from './errors';

// ===== Types =====

export interface CartItem {
  productId: string;
  name: string;
  thumbnailUrl: string;
  price: number;
  quantity: number;
  stock: number;
}

interface ServerCartItem {
  id: string;
  productId: string;
  quantity: number;
}

interface ServerCartDto {
  id: string;
  userId: string;
  items: ServerCartItem[];
}

// ===== Internal: Local (guest) namespace =====

const CART_KEY = 'cart';

function _localRead(): CartItem[] {
  if (typeof window === 'undefined') return [];
  const raw = window.localStorage.getItem(CART_KEY);
  if (!raw) return [];
  try { return JSON.parse(raw) as CartItem[]; } catch { return []; }
}

function _localWrite(items: CartItem[]): void {
  if (typeof window === 'undefined') return;
  window.localStorage.setItem(CART_KEY, JSON.stringify(items));
  window.dispatchEvent(new CustomEvent('cart:change'));
}

function _localClear(): void {
  if (typeof window === 'undefined') return;
  window.localStorage.removeItem(CART_KEY);
  window.dispatchEvent(new CustomEvent('cart:change'));
}

function _localAdd(product: Pick<Product, 'id' | 'name' | 'thumbnailUrl' | 'price' | 'stock'>, qty: number): void {
  const items = _localRead();
  const existing = items.find(i => i.productId === product.id);
  const stockLimit = product.stock ?? 0;
  if (existing) {
    existing.quantity = Math.min(existing.quantity + qty, stockLimit);
    existing.stock = stockLimit;
  } else {
    items.push({
      productId: product.id,
      name: product.name,
      thumbnailUrl: product.thumbnailUrl,
      price: product.price,
      quantity: Math.min(qty, stockLimit),
      stock: stockLimit,
    });
  }
  _localWrite(items);
}

function _localRemove(productId: string): void {
  _localWrite(_localRead().filter(i => i.productId !== productId));
}

function _localUpdate(productId: string, qty: number): void {
  if (qty <= 0) { _localRemove(productId); return; }
  const items = _localRead().map(i => {
    if (i.productId !== productId) return i;
    const capped = i.stock > 0 ? Math.min(qty, i.stock) : qty;
    return { ...i, quantity: capped };
  });
  _localWrite(items);
}

// ===== Internal: Server (user) namespace =====

/**
 * Đọc `userId` từ `localStorage.userProfile` (AuthProvider ghi khi login/register).
 * Trả `null` khi không có (SSR, chưa login, hoặc parse lỗi). Không throw — caller
 * vẫn có thể gọi endpoint, backend sẽ trả 401 với thông điệp rõ.
 */
function getCurrentUserId(): string | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = window.localStorage.getItem('userProfile');
    if (!raw) return null;
    const parsed = JSON.parse(raw) as { id?: string } | null;
    return parsed?.id ?? null;
  } catch {
    return null;
  }
}

/** Build extra headers cho mọi cart server call — luôn kèm X-User-Id nếu có. */
function _userHeaders(): Record<string, string> | undefined {
  const userId = getCurrentUserId();
  return userId ? { 'X-User-Id': userId } : undefined;
}

/**
 * Server cart trả về chỉ có productId + quantity.
 * Để FE render UI (name, price, image, stock), hydrate từ product-svc.
 * Trade-off: GET /cart sau đó GET /products/{id} cho mỗi item — N+1 calls.
 * MVP acceptable; future optimization: BE cart endpoint join với product-svc.
 */
async function hydrateServerCartItems(serverItems: ServerCartItem[]): Promise<CartItem[]> {
  if (serverItems.length === 0) return [];
  const hydrated = await Promise.all(serverItems.map(async (si) => {
    try {
      const p = await httpGet<Product>(`/api/products/${si.productId}`);
      return {
        productId: si.productId,
        name: p.name,
        thumbnailUrl: p.thumbnailUrl ?? '',
        price: p.price,
        quantity: si.quantity,
        stock: p.stock ?? 0,
      } satisfies CartItem;
    } catch {
      // Product bị soft-delete hoặc product-svc fail — placeholder render
      return {
        productId: si.productId,
        name: '(Sản phẩm không khả dụng)',
        thumbnailUrl: '',
        price: 0,
        quantity: si.quantity,
        stock: 0,
      } satisfies CartItem;
    }
  }));
  return hydrated;
}

async function _serverGet(): Promise<CartItem[]> {
  const dto = await httpGet<ServerCartDto>('/api/orders/cart', _userHeaders());
  return hydrateServerCartItems(dto.items);
}

async function _serverAdd(productId: string, quantity: number): Promise<CartItem[]> {
  const dto = await httpPost<ServerCartDto>('/api/orders/cart/items', { productId, quantity }, _userHeaders());
  return hydrateServerCartItems(dto.items);
}

async function _serverSet(productId: string, quantity: number): Promise<CartItem[]> {
  const dto = await httpPatch<ServerCartDto>(`/api/orders/cart/items/${productId}`, { quantity }, _userHeaders());
  return hydrateServerCartItems(dto.items);
}

async function _serverRemove(productId: string): Promise<CartItem[]> {
  const dto = await httpDelete<ServerCartDto>(`/api/orders/cart/items/${productId}`, _userHeaders());
  return hydrateServerCartItems(dto.items);
}

async function _serverClear(): Promise<CartItem[]> {
  const dto = await httpDelete<ServerCartDto>('/api/orders/cart', _userHeaders());
  return hydrateServerCartItems(dto.items);
}

async function _serverMerge(items: Array<{ productId: string; quantity: number }>): Promise<ServerCartDto> {
  return httpPost<ServerCartDto>('/api/orders/cart/merge', { items }, _userHeaders());
}

// ===== Public API =====

function isLoggedIn(): boolean {
  return getAccessToken() !== null;
}

/**
 * SYNC read — guest path only (immediate localStorage).
 * Logged-in caller PHẢI dùng useCart() React Query hook để fetch async.
 * Giữ signature sync cho backward compat — return empty array khi user logged-in
 * (caller dùng hook fetch riêng).
 */
export function readCart(): CartItem[] {
  if (isLoggedIn()) return [];
  return _localRead();
}

export function writeCart(items: CartItem[]): void {
  if (isLoggedIn()) return; // server path không dùng writeCart full-replace
  _localWrite(items);
}

/** ASYNC fetch — cho cả guest và user, trả Promise<CartItem[]>. Hook layer wrap. */
export async function fetchCart(): Promise<CartItem[]> {
  if (isLoggedIn()) return _serverGet();
  return _localRead();
}

/** ASYNC add — return updated cart items. */
export async function addToCart(
  product: Pick<Product, 'id' | 'name' | 'thumbnailUrl' | 'price' | 'stock'>,
  qty: number = 1,
): Promise<CartItem[]> {
  if (isLoggedIn()) return _serverAdd(product.id, qty);
  _localAdd(product, qty);
  return _localRead();
}

export async function updateQuantity(productId: string, qty: number): Promise<CartItem[]> {
  if (isLoggedIn()) return _serverSet(productId, qty);
  _localUpdate(productId, qty);
  return _localRead();
}

export async function removeFromCart(productId: string): Promise<CartItem[]> {
  if (isLoggedIn()) return _serverRemove(productId);
  _localRemove(productId);
  return _localRead();
}

export async function clearCart(): Promise<CartItem[]> {
  if (isLoggedIn()) return _serverClear();
  _localClear();
  return [];
}

/** Used by AuthProvider.logout — luôn clear localStorage bất kể state (D-15). */
export function clearLocalCart(): void {
  _localClear();
}

/**
 * Merge guest cart (localStorage) vào server cart sau khi login.
 * Wired vào AuthProvider.login Plan 05.
 *
 * Flow (D-13, D-14):
 *  1. Đọc _localRead() — nếu empty: return early.
 *  2. POST /merge với items array.
 *  3. Success: _localClear() để tránh duplicate sync mutation tiếp theo.
 *  4. Fail: log error, KHÔNG throw, KHÔNG clear localStorage. Caller (AuthProvider) hiển thị toast warning.
 */
export async function mergeGuestCartToServer(): Promise<{ ok: boolean; error?: unknown }> {
  const guestItems = _localRead();
  if (guestItems.length === 0) return { ok: true };

  try {
    await _serverMerge(guestItems.map(i => ({ productId: i.productId, quantity: i.quantity })));
    _localClear();
    return { ok: true };
  } catch (err) {
    if (typeof console !== 'undefined') {
      console.error('[cart-merge] failed', isApiError(err) ? err.code : err);
    }
    return { ok: false, error: err };
  }
}
