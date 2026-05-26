---
phase: 18-storage-audit-cart-db
plan: "05"
subsystem: frontend-auth-cart
tags:
  - frontend
  - auth
  - cart-merge
  - react-query
dependency_graph:
  requires:
    - 18-03  # mergeGuestCartToServer + clearLocalCart exports
  provides:
    - auth-cart-lifecycle  # login merge + logout clear wired
  affects:
    - providers/AuthProvider.tsx
    - app/login/page.tsx
    - app/register/page.tsx
    - components/ui/Toast/Toast.tsx
    - app/layout.tsx
tech_stack:
  added:
    - "@tanstack/react-query — QueryClientProvider + useQueryClient trong AuthProvider"
    - "ReactQueryProvider wrapper — client-only singleton QueryClient"
  patterns:
    - "window CustomEvent bridge: AuthProvider → ToastProvider (cross-provider toast)"
    - "async login() với await merge: guarantee cart sync trước router.push"
    - "ReactQueryProvider > AuthProvider > ToastProvider wrap order"
key_files:
  created:
    - sources/frontend/src/providers/ReactQueryProvider.tsx
  modified:
    - sources/frontend/src/providers/AuthProvider.tsx
    - sources/frontend/src/app/layout.tsx
    - sources/frontend/src/components/ui/Toast/Toast.tsx
    - sources/frontend/src/components/ui/Toast/Toast.module.css
    - sources/frontend/src/app/login/page.tsx
    - sources/frontend/src/app/register/page.tsx
    - sources/frontend/src/services/auth.ts
decisions:
  - "Dùng window CustomEvent 'cart:merge-failed' thay vì useToast() trực tiếp trong AuthProvider vì AuthProvider nằm NGOÀI ToastProvider trong cây component"
  - "Thêm ReactQueryProvider wrapper riêng (client component) để layout.tsx Server Component không cần 'use client'"
  - "login() await merge trước router.push — chấp nhận ~200-500ms latency để đảm bảo correctness (T-18-20 ACCEPT)"
  - "Toast type mở rộng thêm 'warning' với amber style (#fef9c3 / #b45309)"
metrics:
  duration: "~10 phút"
  completed: "2026-05-02T15:40:21Z"
  tasks_completed: 2
  files_changed: 7
---

# Phase 18 Plan 05: Auth Cart Lifecycle Summary

**One-liner:** AuthProvider.login() async merge guest cart → DB với toast warning on fail; logout() clear localStorage cart + React Query cache (D-13, D-14, D-15).

## Login Flow — Cart Merge Sequence

```
User submit login form
  → services/auth.ts.login()
      → httpPost /api/users/auth/login
      → setTokens(accessToken) ← Bearer token set đây
      → return data
  → login/page.tsx: await authLogin(data.user)
      → AuthProvider.login(u) [async]
          → setUser(u), setIsAuthenticated(true)
          → localStorage.setItem('userProfile', ...)
          → mergeGuestCartToServer() ← httpPost /merge với Bearer token
              success: _localClear() bên trong helper
              fail: console.warn + dispatch CustomEvent 'cart:merge-failed'
          → queryClient.invalidateQueries(['cart']) ← refetch DB cart
  → router.replace(returnTo) ← chạy SAU merge hoàn tất
```

## Logout Flow — Cart Cleanup

```
User click logout
  → AuthProvider.logout()
      → clearTokensHelper() ← xóa accessToken + refreshToken + userRole cookie
      → localStorage.removeItem('userProfile')
      → clearLocalCart() ← xóa localStorage['cart'] (D-15, T-18-17 mitigated)
      → queryClient.removeQueries(['cart']) ← xóa React Query cache
      → setUser(null), setIsAuthenticated(false)
```

## Toast Event Pattern (Cross-Provider Bridge)

Vấn đề: `AuthProvider` nằm NGOÀI `ToastProvider` trong cây component (`ReactQueryProvider > AuthProvider > ToastProvider`). `useToast()` chỉ hoạt động bên trong `ToastProvider` — không thể gọi trực tiếp từ `AuthProvider`.

Giải pháp: `window.dispatchEvent(CustomEvent 'cart:merge-failed')` từ AuthProvider. `ToastProvider` có `useEffect` lắng nghe event này và gọi `showToast(message, 'warning')`.

```
AuthProvider.login merge fail
  → window.dispatchEvent(new CustomEvent('cart:merge-failed', { detail: { message } }))
      → ToastProvider.useEffect handler
          → showToast('Không đồng bộ được giỏ hàng cũ — vui lòng kiểm tra lại', 'warning')
              → Toast UI xuất hiện với amber styling
```

## Deviation từ Plan

### Auto-fixed Issues (Rule 3 — Blocking)

**1. [Rule 3 - Blocking] Thiếu QueryClientProvider trong app shell**

- **Found during:** Task 1 — verify AuthProvider nested inside QueryClientProvider
- **Issue:** `layout.tsx` không có `QueryClientProvider`. `useQueryClient()` trong `AuthProvider` sẽ throw `"No QueryClient set, use QueryClientProvider to set one"` tại runtime.
- **Fix:** Tạo `ReactQueryProvider.tsx` (client component wrapper, singleton pattern) và thêm vào `layout.tsx` bao quanh `AuthProvider`. `layout.tsx` là Server Component nên không thể trực tiếp dùng `QueryClient` — cần wrapper riêng.
- **Files modified:** `providers/ReactQueryProvider.tsx` (new), `app/layout.tsx`
- **Commits:** `fb7d115`

**2. [Rule 2 - Missing] Toast không có 'warning' type/style**

- **Found during:** Task 1 — kiểm tra Toast.tsx signature
- **Issue:** `ToastItem.type` chỉ có `'success' | 'error' | 'info'`. Plan yêu cầu toast warning cho merge fail.
- **Fix:** Mở rộng type union thêm `'warning'`, thêm `⚠` icon, thêm CSS class `.warning` với amber color.
- **Files modified:** `Toast.tsx`, `Toast.module.css`
- **Commits:** `fb7d115`

## Self-Check

### Files exist
- `sources/frontend/src/providers/AuthProvider.tsx` — modified
- `sources/frontend/src/providers/ReactQueryProvider.tsx` — created
- `sources/frontend/src/app/layout.tsx` — modified
- `sources/frontend/src/components/ui/Toast/Toast.tsx` — modified
- `sources/frontend/src/app/login/page.tsx` — modified
- `sources/frontend/src/app/register/page.tsx` — modified
- `sources/frontend/src/services/auth.ts` — modified

### Commits
- `fb7d115` — feat(18-05): wire cart merge vào AuthProvider login/logout lifecycle
- `922691c` — feat(18-05): await async login() tại caller pages + comment giải thích dependency

### TypeScript
- `npx tsc --noEmit` exits 0 — PASSED

## Self-Check: PASSED
