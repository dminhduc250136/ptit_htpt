'use client';

/**
 * Phase 18 / STORE-02: React Query hooks cho cart state.
 *
 * Pattern (D-12, Pattern 4 research):
 * - useCart: useQuery(['cart']) — staleTime: Infinity, manual invalidate.
 *   enabled CHO CẢ guest và user (cả 2 đều cần fetch — guest từ localStorage, user từ API).
 *   fetchCart() trong cart.ts đã handle routing.
 * - useAddToCart / useUpdateCartItem / useRemoveCartItem / useClearCart: useMutation
 *   với onSuccess → invalidateQueries(['cart']).
 * - Error 409 STOCK_SHORTAGE → toast vi: "Số lượng vượt quá tồn kho".
 *
 * SSR-safe: hooks chỉ chạy client-side ('use client' parent component).
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  fetchCart,
  addToCart as cartServiceAdd,
  updateQuantity as cartServiceUpdate,
  removeFromCart as cartServiceRemove,
  clearCart as cartServiceClear,
  type CartItem,
} from '@/services/cart';
import type { Product } from '@/types';
import { isApiError } from '@/services/errors';

const CART_QUERY_KEY = ['cart'] as const;

interface CartErrorContext {
  code: string;
  message: string;
  shortageItems?: Array<{ productId: string; productName: string; requested: number; available: number }>;
}

function parseCartError(err: unknown): CartErrorContext {
  if (isApiError(err)) {
    if (err.code === 'STOCK_SHORTAGE') {
      const details = err.details as { items?: CartErrorContext['shortageItems'] } | undefined;
      return {
        code: 'STOCK_SHORTAGE',
        message: 'Số lượng vượt quá tồn kho',
        shortageItems: details?.items,
      };
    }
    if (err.code === 'UNAUTHORIZED') {
      return { code: 'UNAUTHORIZED', message: 'Phiên đăng nhập hết hạn' };
    }
    return { code: err.code, message: err.message || 'Không thực hiện được thao tác giỏ hàng' };
  }
  return { code: 'INTERNAL_ERROR', message: 'Lỗi mạng — vui lòng thử lại' };
}

/** Subscribe cart state. Cả guest và user đều dùng — service layer route theo getAccessToken(). */
export function useCart() {
  return useQuery<CartItem[]>({
    queryKey: CART_QUERY_KEY,
    queryFn: fetchCart,
    staleTime: Infinity, // chỉ refetch khi explicit invalidate
    refetchOnWindowFocus: false,
  });
}

export function useAddToCart() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ product, qty }: {
      product: Pick<Product, 'id' | 'name' | 'thumbnailUrl' | 'price' | 'stock'>;
      qty?: number;
    }) => cartServiceAdd(product, qty ?? 1),
    onSuccess: () => qc.invalidateQueries({ queryKey: CART_QUERY_KEY }),
  });
}

export function useUpdateCartItem() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ productId, qty }: { productId: string; qty: number }) =>
      cartServiceUpdate(productId, qty),
    onSuccess: () => qc.invalidateQueries({ queryKey: CART_QUERY_KEY }),
  });
}

export function useRemoveCartItem() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (productId: string) => cartServiceRemove(productId),
    onSuccess: () => qc.invalidateQueries({ queryKey: CART_QUERY_KEY }),
  });
}

export function useClearCart() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => cartServiceClear(),
    onSuccess: () => qc.invalidateQueries({ queryKey: CART_QUERY_KEY }),
  });
}

export { CART_QUERY_KEY, parseCartError };
export type { CartErrorContext };
