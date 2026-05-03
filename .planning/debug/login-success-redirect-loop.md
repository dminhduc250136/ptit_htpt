---
status: resolved
slug: login-success-redirect-loop
trigger: "Khi login thành công hoặc register thành công, app redirect về /login?returnTo=%2Flogin thay vì vào trang chủ. Trong localStorage userProfile đã có tên và email sau khi auth. URL repro: http://localhost:3000/login?returnTo=%2Flogin"
created: 2026-05-03
updated: 2026-05-03
resolved: 2026-05-03
reopened: 2026-05-03
re_resolved: 2026-05-03
---

## Symptoms

- **Expected**: Login/register thành công → redirect về trang chủ (hoặc giá trị `returnTo` nếu có)
- **Actual (original)**: Sau khi auth thành công, app redirect về `/login?returnTo=%2Flogin` (vòng lặp)
- **Actual (regression sau fix 302e2a1)**: URL bây giờ là `/login` (không có returnTo) nhưng user VẪN ở trang login sau khi đăng nhập đúng. localStorage.userProfile đã có name/email.
- **Reproduction URL**: http://localhost:3000/login (sau regression)
- **Affects both flows**: cả login và register
- **Timeline**: Phát hiện sau khi merge các phase 19–22. Fix 302e2a1 chỉ chặn returnTo self-loop, không fix root cause hard navigation.

## Suspected Areas (initial)

- Login/register page redirect logic sau success
- AuthContext / useAuth hook timing
- `returnTo` query param có giá trị `/login` (default fallback?)
- Middleware hoặc guard ở route khiến trang home redirect ngược về `/login`
- Regression từ fix cũ ở `sources/frontend/src/services/http.ts`
- **NEW (regression)**: `mergeGuestCartToServer()` gọi trong AuthProvider.login useCallback có thể trả 401 → http.ts handler clearTokens + window.location.href='/login' đè lên router.replace của login page

## Current Focus

hypothesis: "(verified) Cart-service backend yêu cầu header X-User-Id (CartCrudService.requireUserId). cart.ts KHÔNG gửi header này → mọi cart endpoint trả 401. Sau khi auth thành công, AuthProvider.login chạy mergeGuestCartToServer + invalidateQueries(['cart']) → useCart refetch → 401. http.ts 401 handler clearTokens + window.location.href='/login' đè router.replace của login page → user kẹt."
test: "Đọc CartController + CartCrudService.requireUserId, gateway application.yml (không có JWT-to-X-User-Id filter), cart.ts (không inject X-User-Id), so sánh với orders.ts/coupons.ts (đã đúng pattern)."
expecting: "Cart endpoints 401 → http.ts hard nav đè login page redirect."
next_action: "DONE — applied fix"

## Evidence

- timestamp: 2026-05-03
  source: sources/frontend/src/app/login/page.tsx:17-22, 49
  finding: |
    `rawReturnTo = searchParams.get('returnTo') ?? '/'` rồi sanitize chỉ kiểm
    `startsWith('/') && !startsWith('//')`. Giá trị `/login` đi qua check này
    và được dùng nguyên xi ở `router.replace(returnTo)` (line 49).

- timestamp: 2026-05-03
  source: sources/frontend/src/services/http.ts:118-130
  finding: |
    Khi 401 xảy ra trên endpoint KHÔNG nằm trong AUTH_PATHS_NO_REDIRECT, code
    encode `window.location.pathname` thành `returnTo`, build URL `/login?returnTo=...`.

- timestamp: 2026-05-03 (REGRESSION verified)
  source: sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/CartCrudService.java:171-175
  finding: |
    `requireUserId(userId)` throw `ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing X-User-Id session header")`
    khi `userId == null || userId.isBlank()`. Mọi cart endpoint
    (`/api/orders/cart`, `/api/orders/cart/items`, `/api/orders/cart/merge`,...)
    đều gọi `getOrCreateCart(userId)` hoặc tương tự → trả 401.

- timestamp: 2026-05-03
  source: sources/backend/api-gateway/src/main/resources/application.yml (route order-service)
  finding: |
    Gateway chỉ rewrite `/api/orders/**` → `/orders/**`, KHÔNG có filter parse JWT
    sub claim → inject `X-User-Id`. Các service khác (orders, coupons) FE chủ động
    truyền userId qua extraHeaders.

- timestamp: 2026-05-03
  source: sources/frontend/src/services/cart.ts (before fix)
  finding: |
    `_serverGet` / `_serverAdd` / `_serverSet` / `_serverRemove` / `_serverClear` /
    `_serverMerge` đều gọi `httpGet/httpPost/httpPatch/httpDelete` mà KHÔNG truyền
    `extraHeaders`. So sánh với `services/orders.ts:48` và `services/coupons.ts:22`
    đều có `if (userId) headers['X-User-Id'] = userId;` — pattern đúng.

- timestamp: 2026-05-03
  source: sources/frontend/src/providers/AuthProvider.tsx:100-122
  finding: |
    `login(u)` callback: setUser → setIsAuthenticated → `localStorage.setItem('userProfile', ...)`
    → `await mergeGuestCartToServer()` → `queryClient.invalidateQueries({ queryKey: ['cart'] })`.
    userProfile có sẵn TRƯỚC khi mergeGuestCartToServer chạy → đọc userId từ localStorage là an toàn.

- timestamp: 2026-05-03
  source: sources/frontend/src/services/http.ts:135-150 (before fix)
  finding: |
    Trong 401 handler, ngay cả khi `isAuthPagePath(pathname)` = true (đang ở /login),
    vẫn fire `window.location.href = '/login'`. Đây là hard navigation đè lên
    `router.replace('/')` của login page — root cause của symptom "stuck on /login".

## Eliminated

- AuthContext không sai: đã setUser + setIsAuthenticated đúng, ghi userProfile vào
  localStorage trước khi return từ authLogin().
- Middleware không tự thêm returnTo='/login'.
- returnTo self-loop đã fix (commit 302e2a1).

## Resolution

root_cause: |
  Hai tầng:
  (1) PRIMARY — Backend cart-service yêu cầu header `X-User-Id` (CartCrudService.requireUserId
      → 401 nếu thiếu). Gateway hiện tại KHÔNG tự inject header này từ JWT, nên FE phải
      gửi tay (pattern đã có ở orders.ts, coupons.ts). cart.ts KHÔNG truyền header → MỌI
      cart endpoint trả 401. Sau khi đăng nhập, AuthProvider.login chạy
      `await mergeGuestCartToServer()` rồi `invalidateQueries(['cart'])` → useCart refetch
      → đều 401.
  (2) DEFENSIVE — http.ts 401 handler thực hiện `window.location.href = '/login'` ngay cả
      khi user đang ở /login. Hard navigation reload page, đè lên `router.replace('/')`
      mà login page định gọi sau `await authLogin(...)`. Kết quả: token bị clearTokens()
      xóa, userProfile vẫn còn, user ở lại /login (không có returnTo nhờ fix trước).

fix: |
  1) `sources/frontend/src/services/cart.ts`:
     - Thêm helper `getCurrentUserId()` đọc `userProfile.id` từ localStorage (SSR-safe).
     - Thêm `_userHeaders()` build `{ 'X-User-Id': userId }` nếu có.
     - Mọi `_serverGet/_serverAdd/_serverSet/_serverRemove/_serverClear/_serverMerge`
       truyền `_userHeaders()` cho `httpGet/httpPost/httpPatch/httpDelete`.
  2) `sources/frontend/src/services/http.ts`:
     - Mở rộng `httpGet` và `httpDelete` để chấp nhận `extraHeaders` (parity với POST/PUT/PATCH).
     - 401 handler: nếu `isAuthPagePath(window.location.pathname)` → CHỈ `clearTokens()`,
       KHÔNG `window.location.href`. Trang login sẽ tự `router.replace(...)` sau khi
       `await authLogin(...)` hoàn tất (hoặc khi user xử lý 401 từ caller).

verification: |
  Manual test plan:
  - Visit `/login?returnTo=/login`, đăng nhập đúng → expect redirect về `/`.
  - Visit `/login?returnTo=/login%2F`, đăng nhập đúng → expect redirect về `/`.
  - Visit `/login?returnTo=/cart`, đăng nhập đúng → expect redirect về `/cart`.
  - Có token cũ trong localStorage, vào `/login`, để cart/profile fetch trả 401 →
    expect URL trở thành `/login` (không có returnTo).
  - **REGRESSION FIX**: Đăng nhập đúng từ `/login` (cart guest rỗng hoặc không) → expect
    URL chuyển sang `/`, KHÔNG bị giữ lại ở `/login`. Token vẫn còn trong localStorage,
    Header hiển thị tên user, useCart không 401.
  - **NEW**: Đăng nhập với guest cart có items → expect mergeGuestCartToServer thành công
    (200), localStorage.cart bị clear, server cart chứa items đã merge.
