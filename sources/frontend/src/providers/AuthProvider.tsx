'use client';

/**
 * AuthProvider — React Context for auth state.
 *
 * Source: 04-PATTERNS.md §AuthProvider (copies ToastProvider skeleton).
 * Hydrates from localStorage on mount (SSR-safe per Pitfall 2). Exposes
 * useAuth() hook with { isAuthenticated, user, login, logout }.
 *
 * Pairs with services/token.ts (tokens) and services/auth.ts (login/register).
 */

import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { getAccessToken, clearTokens as clearTokensHelper } from '@/services/token';

interface AuthState {
  isAuthenticated: boolean;
  user: { id: string; email: string; name: string } | null;
}

const AuthContext = createContext<AuthState & {
  login: (user: AuthState['user']) => void;
  logout: () => void;
}>({
  isAuthenticated: false,
  user: null,
  login: () => {},
  logout: () => {},
});

export const useAuth = () => useContext(AuthContext);

export function AuthProvider({ children }: { children: React.ReactNode }) {
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

  const login = useCallback((u: AuthState['user']) => {
    setUser(u);
    setIsAuthenticated(true);
    if (u && typeof window !== 'undefined') {
      window.localStorage.setItem('userProfile', JSON.stringify(u));
    }
  }, []);

  const logout = useCallback(() => {
    clearTokensHelper();
    if (typeof window !== 'undefined') {
      window.localStorage.removeItem('userProfile');
    }
    setUser(null);
    setIsAuthenticated(false);
  }, []);

  return (
    <AuthContext.Provider value={{ isAuthenticated, user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}
