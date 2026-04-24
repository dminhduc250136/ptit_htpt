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
  const [user, setUser] = useState<AuthState['user']>(null);
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  useEffect(() => {
    // Hydrate from localStorage on mount (SSR-safe; Pitfall 2).
    const token = getAccessToken();
    if (token) {
      setIsAuthenticated(true);
      try {
        const raw = window.localStorage.getItem('userProfile');
        if (raw) setUser(JSON.parse(raw));
      } catch {
        /* ignore malformed userProfile */
      }
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
