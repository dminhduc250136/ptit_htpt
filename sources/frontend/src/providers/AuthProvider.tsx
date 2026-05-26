'use client';

/**
 * AuthProvider — React Context for auth state.
 *
 * Source: 04-PATTERNS.md §AuthProvider (copies ToastProvider skeleton).
 * Hydrates from localStorage on mount (SSR-safe per Pitfall 2). Exposes
 * useAuth() hook with { isAuthenticated, user, login, logout }.
 *
 * Pairs with services/token.ts (tokens) and services/auth.ts (login/register).
 *
 * Phase 18 / D-13, D-14, D-15:
 * - login() trở thành async — gọi mergeGuestCartToServer() sau setTokens.
 *   Merge fail → log + window event 'cart:merge-failed' (toast warning), KHÔNG block login.
 * - logout() gọi clearLocalCart() để cart user A không leak sang guest session tiếp theo.
 *   Cũng invalidate queryClient(['cart']) để tránh stale UI.
 * - Requires ReactQueryProvider wrap NGOÀI AuthProvider trong app shell.
 */

import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { getAccessToken, clearTokens as clearTokensHelper } from '@/services/token';
import { mergeGuestCartToServer, clearLocalCart } from '@/services/cart';

interface AuthState {
  isAuthenticated: boolean;
  user: { id: string; email: string; name: string } | null;
}

const AuthContext = createContext<AuthState & {
  login: (user: AuthState['user']) => Promise<void>; // ASYNC — Phase 18 D-13
  logout: () => void;
}>({
  isAuthenticated: false,
  user: null,
  login: async () => {},
  logout: () => {},
});

export const useAuth = () => useContext(AuthContext);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const queryClient = useQueryClient();

  // Hydrate synchronously on first client render — same pattern as SSR-safe
  // localStorage readers in services/token.ts. The lazy initializer runs once,
  // and because it guards on `typeof window`, SSR renders the default (null /
  // false) and the client's first paint picks up the stored value without a
  // setState-inside-useEffect round-trip.
  const [user, setUser] = useState<AuthState['user']>(() => {
    if (typeof window === 'undefined') return null;
    try {
      const raw = window.localStorage.getItem('userProfile');
      return raw ? (JSON.parse(raw) as AuthState['user']) : null;
    } catch {
      return null;
    }
  });
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(() => {
    if (typeof window === 'undefined') return false;
    return getAccessToken() !== null;
  });

  // Subscribe to cross-tab storage events so logout in one tab propagates.
  useEffect(() => {
    function onStorage(e: StorageEvent) {
      if (e.key === 'accessToken' || e.key === 'userProfile' || e.key === null) {
        const token = getAccessToken();
        setIsAuthenticated(token !== null);
        try {
          const raw = typeof window !== 'undefined'
            ? window.localStorage.getItem('userProfile')
            : null;
          setUser(raw ? (JSON.parse(raw) as AuthState['user']) : null);
        } catch {
          setUser(null);
        }
      }
    }
    if (typeof window !== 'undefined') {
      window.addEventListener('storage', onStorage);
      return () => window.removeEventListener('storage', onStorage);
    }
  }, []);

  /**
   * D-13, D-14: login() async — sau khi setUser + setIsAuthenticated,
   * gọi mergeGuestCartToServer() để merge guest cart vào DB.
   *
   * Caller (services/auth.ts.login) đã gọi setTokens(accessToken) TRƯỚC khi return,
   * sau đó caller gọi authProvider.login(user) — nên httpPost trong
   * mergeGuestCartToServer sẽ có Bearer token sẵn.
   *
   * Merge fail → log + dispatch CustomEvent 'cart:merge-failed', KHÔNG throw,
   * KHÔNG block login (D-14). ToastProvider listen event này và showToast warning.
   *
   * queryClient.invalidateQueries(['cart']): refetch DB cart mới sau merge
   * (hoặc fresh login với cart rỗng).
   */
  const login = useCallback(async (u: AuthState['user']) => {
    setUser(u);
    setIsAuthenticated(true);
    if (u && typeof window !== 'undefined') {
      window.localStorage.setItem('userProfile', JSON.stringify(u));
    }

    // Phase 18 / D-13: merge guest cart → DB
    const result = await mergeGuestCartToServer();
    if (!result.ok) {
      // D-14: log + warning toast, KHÔNG block login
      console.warn('[auth] cart merge failed during login:', result.error);
      // Dispatch CustomEvent — ToastProvider (Toast.tsx) lắng nghe và hiển thị warning.
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent('cart:merge-failed', {
          detail: { message: 'Không đồng bộ được giỏ hàng cũ — vui lòng kiểm tra lại' },
        }));
      }
    }

    // Invalidate cart query để useCart() refetch DB cart mới (sau merge hoặc fresh login).
    queryClient.invalidateQueries({ queryKey: ['cart'] });
  }, [queryClient]);

  /**
   * D-15: logout — clear localStorage cart để cart user A không leak sang
   * guest session tiếp theo cùng browser. Cũng xóa ['cart'] query cache.
   */
  const logout = useCallback(() => {
    clearTokensHelper();
    if (typeof window !== 'undefined') {
      window.localStorage.removeItem('userProfile');
    }

    // Phase 18 / D-15: clear cart localStorage + React Query cache
    clearLocalCart();
    queryClient.removeQueries({ queryKey: ['cart'] });

    setUser(null);
    setIsAuthenticated(false);
  }, [queryClient]);

  return (
    <AuthContext.Provider value={{ isAuthenticated, user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}
