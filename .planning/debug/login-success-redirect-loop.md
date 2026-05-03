---
status: resolved
slug: login-success-redirect-loop
trigger: "Khi login thành công hoặc register thành công, app redirect về /login?returnTo=%2Flogin thay vì vào trang chủ. Trong localStorage userProfile đã có tên và email sau khi auth. URL repro: http://localhost:3000/login?returnTo=%2Flogin"
created: 2026-05-03
updated: 2026-05-03
resolved: 2026-05-03
---

## Symptoms

- **Expected**: Login/register thành công → redirect về trang chủ (hoặc giá trị `returnTo` nếu có)
- **Actual**: Sau khi auth thành công, app redirect về `/login?returnTo=%2Flogin` (vòng lặp)
- **Auth state evidence**: `localStorage.userProfile` đã có `name` và `email` ngay sau auth → request auth thực sự thành công, vấn đề nằm ở client-side redirect/guard logic
- **Reproduction URL**: http://localhost:3000/login?returnTo=%2Flogin
- **Affects both flows**: cả login và register (khác với bug cũ `login-redirect-cart-stock` chỉ ảnh hưởng login fail)
- **Timeline**: Phát hiện sau khi merge các phase 19–22 (charts/coupons/reviews/chatbot)

## Suspected Areas (initial)

- Login/register page redirect logic sau success (có thể đọc `returnTo` rồi push về `/login` do parse sai)
- AuthContext / useAuth hook — possibly chưa đồng bộ với localStorage timing
- `returnTo` query param có giá trị `/login` (default fallback?) → redirect về chính nó
- Middleware hoặc guard ở route khiến trang home redirect ngược về `/login`
- Regression từ fix cũ ở `sources/frontend/src/services/http.ts` (AUTH_PATHS_NO_REDIRECT)

## Current Focus

hypothesis: "Login page sanitize `returnTo` chỉ check `startsWith('/')` nhưng không loại trừ chính `/login` và `/register` → khi URL chứa `?returnTo=/login`, sau success page replace về `/login`, vòng lặp không kết thúc"
test: "Trace login page sanitization (lines 17-22) + http.ts 401 redirect (lines 118-130)"
expecting: "returnTo='/login' lọt qua sanitization và được dùng làm redirect target"
next_action: "Fix: từ chối returnTo nếu trỏ về /login hoặc /register; song song hardening http.ts không encode pathname là /login|/register"

## Evidence

- timestamp: 2026-05-03
  source: sources/frontend/src/app/login/page.tsx:17-22, 49
  finding: |
    `rawReturnTo = searchParams.get('returnTo') ?? '/'` rồi sanitize chỉ kiểm
    `startsWith('/') && !startsWith('//')`. Giá trị `/login` đi qua check này
    và được dùng nguyên xi ở `router.replace(returnTo)` (line 49). Khi URL là
    `/login?returnTo=%2Flogin` (decoded `/login`), sau auth thành công router
    replace về chính `/login` → vòng lặp.

- timestamp: 2026-05-03
  source: sources/frontend/src/services/http.ts:118-130
  finding: |
    Khi 401 xảy ra trên endpoint KHÔNG nằm trong AUTH_PATHS_NO_REDIRECT (vd
    cart/profile/chat fetch trong khi user đang ở /login với token cũ), code
    encode `window.location.pathname` (= `/login`) thành `returnTo`, build URL
    `/login?returnTo=%2Flogin`. Đây là nguồn gốc tạo ra returnTo trỏ về login.

- timestamp: 2026-05-03
  source: sources/frontend/src/app/register/page.tsx:59
  finding: |
    Register page hardcode `router.replace('/')` — KHÔNG đọc returnTo. Tuy nhiên
    bug report nói "ảnh hưởng cả login và register" — có thể user thực hiện
    register từ link trên trang `/login?returnTo=/login`, sau register thành công
    AuthProvider login() chạy, không loop ở register, nhưng nếu user nhấn back hoặc
    flow chuyển qua login lại sẽ rơi vào loop. Fix gốc ở login + http.ts đủ để chặn.

## Eliminated

- AuthContext không sai: đã setUser + setIsAuthenticated đúng, ghi userProfile vào
  localStorage trước khi return từ authLogin().
- Middleware không tự thêm returnTo='/login' (chỉ thêm pathname cho route được
  matcher cover; /login không match). Nguồn returnTo='/login' là từ http.ts.

## Resolution

root_cause: |
  Login page (`sources/frontend/src/app/login/page.tsx`) sanitize `returnTo` chỉ
  kiểm tra prefix `/` mà không loại trừ chính bản thân các trang auth (`/login`,
  `/register`). Kết hợp với `services/http.ts` 401 handler: khi user đang ở `/login`
  và một request không-auth nào đó trả 401 (token cũ trong localStorage), handler
  encode `window.location.pathname` = `/login` thành `returnTo`, redirect về
  `/login?returnTo=%2Flogin`. Sau khi auth thành công, login page replace về
  `returnTo` = `/login` → vòng lặp tự duy trì cho mọi lần submit kế tiếp.

fix: |
  1) `sources/frontend/src/app/login/page.tsx`: tách hàm `sanitizeReturnTo()` —
     loại bỏ returnTo trỏ về `/login` hoặc `/register` (case-insensitive, có/không
     trailing slash, có/không query string) → fallback `/`.
  2) `sources/frontend/src/services/http.ts`: thêm `isAuthPagePath()` helper. Trong
     401 handler, nếu `window.location.pathname` đã là `/login` hoặc `/register`
     (hoặc nested) thì redirect về `/login` không kèm `returnTo` để tránh sinh
     chuỗi self-loop.

verification: |
  Manual test plan:
  - Visit `/login?returnTo=/login`, đăng nhập đúng → expect redirect về `/`.
  - Visit `/login?returnTo=/login%2F`, đăng nhập đúng → expect redirect về `/`.
  - Visit `/login?returnTo=/cart`, đăng nhập đúng → expect redirect về `/cart` (returnTo hợp lệ vẫn hoạt động).
  - Có token cũ trong localStorage, vào `/login`, để cart/profile fetch trả 401 →
    expect URL trở thành `/login` (không có returnTo), không phải `/login?returnTo=%2Flogin`.
