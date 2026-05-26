'use client';

/**
 * ReactQueryProvider — wrapper cho QueryClientProvider của @tanstack/react-query.
 *
 * Tách ra file riêng vì layout.tsx là Server Component (không có 'use client').
 * AuthProvider cần nằm BÊN TRONG ReactQueryProvider để useQueryClient() hoạt động.
 *
 * Phase 18 / D-13, D-14, D-15: AuthProvider.login() gọi useQueryClient để
 * invalidate ['cart'] sau merge; AuthProvider.logout() gọi removeQueries(['cart']).
 */

import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 60 * 1000, // 1 phút — override cụ thể trong từng hook
        refetchOnWindowFocus: false,
      },
    },
  });
}

let browserQueryClient: QueryClient | undefined = undefined;

function getQueryClient() {
  if (typeof window === 'undefined') {
    // Server: tạo QueryClient mới mỗi request
    return makeQueryClient();
  }
  // Browser: reuse QueryClient để tránh mất cache khi re-render
  if (!browserQueryClient) browserQueryClient = makeQueryClient();
  return browserQueryClient;
}

export function ReactQueryProvider({ children }: { children: React.ReactNode }) {
  const queryClient = getQueryClient();
  return (
    <QueryClientProvider client={queryClient}>
      {children}
    </QueryClientProvider>
  );
}
