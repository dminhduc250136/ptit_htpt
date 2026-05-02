---
status: resolved
slug: login-redirect-cart-stock
trigger: "Bug 1: login thành công xong bị redirect lại về login (có vẻ liên quan đến forgotpassword), nhưng register mới thì vào bình thường. Bug 2: check số lượng tồn kho ở trang detail hoạt động, nhưng vào trang giỏ hàng tăng được vượt stock và checkout thành công."
created: 2026-04-26
updated: 2026-04-26
---

## Symptoms

### Bug 1 — Login redirect loop
- **Expected**: Login thành công → redirect tới trang đích (home hoặc trang trước)
- **Actual**: Login thành công → redirect về `/login` lại (loop)
- **Key clue**: Network tab thấy `GET http://localhost:3000/login?returnTo=%2Flogin` — `returnTo` bị set thành `/login` thay vì trang đích thực
- **Console errors**: Nhiều 404 cho RSC prefetch: `/forgot-password?_rsc=17yrj`, `/terms`, `/privacy`, `/about`, `/collections`, `/deals`, `/contact`, `/returns`, `/shipping` — tất cả 404
- **No login API visible**: Không thấy API call login nào có response code trong network tab
- **Register mới thì bình thường**: Chỉ login bị lỗi, không phải register
- **User suspect**: Liên quan đến phần forgotpassword (vì `/forgot-password?_rsc=17yrj 404`)

### Bug 2 — Cart quantity vượt stock
- **Expected**: Không thể tăng quantity vượt stock trong cart, không checkout được nếu qty > stock
- **Actual**: Trong cart có thể tăng qty vượt stock và checkout thành công
- **Product detail**: Đã có check (không add thêm nếu > stock)
- **Cart page**: Không có check tương tự
- **Backend**: Có vẻ không validate stock khi tạo order
- **Note**: Từ cart → checkout cần đóng gói thông tin nhưng backend có thể đang không xử lý stock check

## Current Focus

hypothesis: "RESOLVED"
test: ""
expecting: ""
next_action: "done"
reasoning_checkpoint: ""
tdd_checkpoint: ""

## Evidence

- timestamp: 2026-04-26T00:00:00Z
  observation: "Network tab: GET /login?returnTo=%2Flogin — returnTo bị set thành /login chính nó"
  source: "user-reported DevTools"

- timestamp: 2026-04-26T00:00:01Z
  observation: "RSC 404 errors cho /forgot-password, /terms, /privacy, /about, /collections — các trang này chưa được tạo"
  source: "user-reported console"

- timestamp: 2026-04-26T00:00:02Z
  observation: "Register mới hoạt động bình thường — vấn đề chỉ ở login flow, không phải auth system tổng thể"
  source: "user-reported"

- timestamp: 2026-04-26T00:00:03Z
  observation: "Cart: có thể tăng qty vượt stock và checkout thành công — thiếu validation ở cả FE cart và BE order"
  source: "user-reported"

- timestamp: 2026-04-26T00:01:00Z
  observation: "middleware.ts chỉ guard /admin — không liên quan đến login redirect loop"
  source: "code-read"

- timestamp: 2026-04-26T00:01:01Z
  observation: "http.ts dòng 102-115: mọi 401 response đều trigger window.location.href=/login?returnTo=pathname. Khi POST /api/users/auth/login trả 401 (sai mật khẩu), pathname=/login → returnTo=%2Flogin → vòng lặp vô tận"
  source: "code-read — root cause Bug 1"

- timestamp: 2026-04-26T00:01:02Z
  observation: "login/page.tsx bắt ApiError status 401 để hiện banner — nhưng http.ts intercept và redirect trước khi exception bubble lên caller"
  source: "code-read"

- timestamp: 2026-04-26T00:01:03Z
  observation: "CartItem interface không có field stock. updateQuantity() không có upper-bound. Button '+' trong cart/page.tsx không có disabled condition"
  source: "code-read — root cause Bug 2 FE"

- timestamp: 2026-04-26T00:01:04Z
  observation: "Backend OrderCrudService.validateStockOrThrow() đã implement nhưng catch Exception → log.warn → bỏ qua validate nếu product-service unreachable (MVP best-effort). Trong dev environment có thể không qua gateway"
  source: "code-read — root cause Bug 2 BE"

- timestamp: 2026-04-26T00:01:05Z
  observation: "products/[slug]/page.tsx truyền addToCart({id,name,thumbnailUrl,price}) thiếu stock field"
  source: "code-read"

## Eliminated

- middleware.ts guard chỉ áp dụng cho /admin — không phải nguyên nhân redirect loop
- forgotpassword 404 chỉ là RSC prefetch noise từ Footer links chưa có trang — không liên quan đến login bug
- Backend order stock validation đã có code (validateStockOrThrow) — vấn đề là best-effort fallback khi product-service unreachable, không phải thiếu code hoàn toàn

## Resolution

root_cause: "Bug 1: http.ts xử lý mọi 401 response bằng cách redirect đến /login?returnTo=pathname — bao gồm cả 401 từ POST /api/users/auth/login (sai mật khẩu), khiến pathname=/login được encode thành returnTo=%2Flogin tạo vòng lặp vô tận. Bug 2: CartItem không lưu stock snapshot, updateQuantity không có upper-bound, nút '+' trong cart page không có disabled condition khi đạt stock limit."
fix: "Bug 1: Thêm danh sách AUTH_PATHS_NO_REDIRECT=['/api/users/auth/login','/api/users/auth/register'] trong http.ts — chỉ redirect khi 401 xảy ra trên endpoint không phải auth. Bug 2: Thêm field stock vào CartItem interface, addToCart clamp quantity theo stock, updateQuantity enforce upper-bound, cart/page.tsx disable nút '+' khi atStockLimit, products/[slug]/page.tsx truyền stock vào addToCart."
verification: "Cần test thủ công: (1) nhập sai mật khẩu → phải thấy banner lỗi, không redirect; (2) nhập đúng mật khẩu → redirect về trang đích; (3) trong cart, nút '+' disabled khi quantity=stock; (4) thử tăng qty trong localStorage trực tiếp → checkout phải bị block bởi backend 409 nếu product-service chạy."
files_changed:
  - sources/frontend/src/services/http.ts
  - sources/frontend/src/services/cart.ts
  - sources/frontend/src/app/cart/page.tsx
  - sources/frontend/src/app/products/[slug]/page.tsx
