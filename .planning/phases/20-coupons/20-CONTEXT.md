# Phase 20: Hệ Thống Coupon - Context

**Gathered:** 2026-05-03
**Status:** Ready for planning
**Mode:** `--auto --chain` (gray areas auto-resolved theo recommended defaults; chain to plan-phase + execute-phase)

<domain>
## Phase Boundary

Triển khai hệ thống coupon end-to-end:
- **DB:** order-svc thêm 2 bảng mới (`coupons`, `coupon_redemptions`) + bổ sung 2 cột snapshot trên `orders` (`discount_amount`, `coupon_code`).
- **BE order-svc:** CRUD admin coupons + 1 endpoint preview validate (`POST /orders/coupons/validate`) + tích hợp atomic redemption vào order create flow.
- **Gateway:** thêm 2 nhánh route `/api/orders/coupons/**` (user) và `/api/orders/admin/coupons/**` (admin).
- **FE checkout:** input mã + nút "Áp dụng" + preview discount/total mới trước khi confirm + thông báo lỗi rõ ràng theo mã lỗi BE.
- **FE admin:** trang `/admin/coupons` với list + form create/edit + disable (soft) + delete (chỉ khi `used_count=0`).
- **FE order detail:** hiển thị `coupon_code` + `discount_amount` ở `/account/orders/[id]` và `/admin/orders/[id]` nếu order có áp dụng coupon.

**Race condition safety:** atomic UPDATE conditional + UNIQUE(coupon_id, user_id) constraint cho 1-lần/user; tất cả trong cùng transaction với order create.

**KHÔNG bao gồm (out of scope phase này):**
- Coupon stacking (chỉ 1 mã/đơn — locked PROJECT.md).
- Auto-apply coupon (best-coupon-finder, banner promo).
- Coupon analytics chart trên admin dashboard (đã skip Phase 19, defer).
- Per-product / per-category coupon (chỉ áp lên cart total).
- Coupon usage history page cho user (defer — order detail đã đủ).
- First-time-buyer / referral coupons (defer).
- Currency / FX (VND BigDecimal đồng nhất với orders.total).

</domain>

<decisions>
## Implementation Decisions

### Database Schema (order-svc Flyway)

- **D-01:** **Migration version = `V5`** (KHÔNG `V3` như ROADMAP pre-phase reservation).
  - **Why:** Phase 18 đã shipped `V4__add_cart_tables.sql`. Flyway mặc định KHÔNG cho out-of-order → nếu dùng V3 sẽ bị skip hoặc fail. V5 là next available, an toàn nhất.
  - **Action:** planner phải patch `.planning/ROADMAP.md` Pre-Phase Setup table (đổi `order-svc V3` → `order-svc V5` cho coupons).

- **D-02:** **Schema chính** (`V5__add_coupons.sql`):
  ```sql
  CREATE TABLE IF NOT EXISTS order_svc.coupons (
    id                 VARCHAR(36)   PRIMARY KEY,
    code               VARCHAR(64)   NOT NULL UNIQUE,
    type               VARCHAR(16)   NOT NULL CHECK (type IN ('PERCENT','FIXED')),
    value              NUMERIC(15,2) NOT NULL CHECK (value > 0),
    min_order_amount   NUMERIC(15,2) NOT NULL DEFAULT 0,
    max_total_uses     INT           NULL,
    used_count         INT           NOT NULL DEFAULT 0,
    expires_at         TIMESTAMPTZ   NULL,
    active             BOOLEAN       NOT NULL DEFAULT true,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
  );

  CREATE TABLE IF NOT EXISTS order_svc.coupon_redemptions (
    id           VARCHAR(36)   PRIMARY KEY,
    coupon_id    VARCHAR(36)   NOT NULL REFERENCES order_svc.coupons(id) ON DELETE RESTRICT,
    user_id      VARCHAR(36)   NOT NULL,
    order_id     VARCHAR(36)   NOT NULL UNIQUE,
    redeemed_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (coupon_id, user_id)
  );
  CREATE INDEX idx_redemptions_coupon ON order_svc.coupon_redemptions(coupon_id);
  CREATE INDEX idx_redemptions_user ON order_svc.coupon_redemptions(user_id);

  ALTER TABLE order_svc.orders ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(15,2) NOT NULL DEFAULT 0;
  ALTER TABLE order_svc.orders ADD COLUMN IF NOT EXISTS coupon_code VARCHAR(64) NULL;
  ```
  - **Why:** `coupon_redemptions.UNIQUE(coupon_id, user_id)` thực thi 1-lần/user ở DB level (COUP-01). `order_id UNIQUE` chống double-insert nếu retry. Snapshot 2 cột trên `orders` cho display nhanh không cần JOIN (COUP-05) + bảo toàn lịch sử nếu sau này coupon bị xoá/đổi giá trị.

- **D-03:** **`type` là VARCHAR + CHECK constraint** (KHÔNG enum riêng) — đồng nhất pattern với `orders.status`. JPA map qua `@Enumerated(EnumType.STRING)` với enum `CouponType { PERCENT, FIXED }`.

- **D-04:** **`value` semantics:**
  - `PERCENT`: 0 < value ≤ 100 → discount = `cart_total * value / 100`, làm tròn xuống đến đồng (`setScale(0, RoundingMode.FLOOR)`).
  - `FIXED`: value > 0 (đơn vị VND) → discount = `min(value, cart_total)`.
  - Cap chung: `discount ≤ cart_total` (KHÔNG để total âm).

- **D-05:** **`expires_at` nullable** — null = không hết hạn. So sánh trong validation: `expires_at IS NULL OR expires_at > NOW()`.

- **D-06:** **`max_total_uses` nullable** — null = không giới hạn lượt dùng tổng. Check `max_total_uses IS NULL OR used_count < max_total_uses` trong atomic UPDATE.

- **D-07:** **KHÔNG soft-delete coupon_redemptions** — redemption là sự kiện lịch sử, gắn với order. Hủy order KHÔNG rollback redemption (đơn giản giai đoạn này; nếu cần "trả lại lượt" thì là phase khác).

### Backend Validation + Redemption Flow

- **D-08:** **Validation 2 bước (preview + atomic redemption):**
  1. **Preview** — `POST /orders/coupons/validate` body `{code, cartTotal}`, header `X-User-Id` (Phase 9 pattern). BE check: tồn tại + `active=true` + `expires_at` + `min_order_amount` + chưa hết lượt tổng + user chưa dùng → trả `{couponId, code, discountAmount, finalTotal, type, value}`. Đây là **read-only preview**, KHÔNG khoá row, KHÔNG tăng `used_count`.
  2. **Atomic redemption** (trong `OrderCrudService.create`) — chỉ chạy nếu request order có `couponCode`:
     ```sql
     UPDATE order_svc.coupons
     SET used_count = used_count + 1, updated_at = now()
     WHERE code = :code
       AND active = true
       AND (expires_at IS NULL OR expires_at > now())
       AND (max_total_uses IS NULL OR used_count < max_total_uses);
     ```
     - Check `rowsAffected = 1`. Nếu `0` → throw `CouponException(CONFLICT_OR_EXHAUSTED)`.
     - Sau đó `INSERT INTO coupon_redemptions(...)`. Nếu vi phạm UNIQUE(coupon_id,user_id) → catch `DataIntegrityViolationException` → throw `CouponException(ALREADY_REDEEMED)` + rollback toàn transaction (order tạo cũng rollback).
     - Tính lại `discount_amount` server-side (KHÔNG tin client) → set `orders.discount_amount` + `orders.coupon_code` + giảm `orders.total`.
  - **Why:** preview cho UX tốt (admin/user thấy ngay discount), atomic redemption là source of truth chống TOCTOU + race.

- **D-09:** **Re-fetch coupon by code (KHÔNG dùng id từ preview)** ở bước atomic — phòng case admin disable/delete coupon giữa preview và submit. Mã `code` là natural key (UNIQUE).

- **D-10:** **Server tính `cart_total` server-side trong order create** (đã có pattern Phase 18 — `OrderCrudService` đọc cart từ DB) — KHÔNG tin `cartTotal` field client gửi cho atomic step. Preview thì có thể nhận `cartTotal` client tính nhanh để giảm round-trip, nhưng atomic re-tính.

- **D-11:** **Error code shape** — extend `ApiErrorResponse` theo pattern hiện có. Reason codes (string constants):
  - `COUPON_NOT_FOUND` → 404 — "Mã giảm giá không tồn tại"
  - `COUPON_INACTIVE` → 422 — "Mã giảm giá đã bị vô hiệu hoá"
  - `COUPON_EXPIRED` → 422 — "Mã giảm giá đã hết hạn"
  - `COUPON_MIN_ORDER_NOT_MET` → 422 — "Đơn hàng tối thiểu {minOrder} đ để dùng mã này" (kèm `details: {minOrderAmount}`)
  - `COUPON_ALREADY_REDEEMED` → 409 — "Bạn đã sử dụng mã giảm giá này"
  - `COUPON_MAX_USES_REACHED` → 409 — "Mã giảm giá đã hết lượt sử dụng"
  - `COUPON_CONFLICT_OR_EXHAUSTED` → 409 — "Mã giảm giá không còn khả dụng" (race lose từ atomic UPDATE rowsAffected=0)
  - **Why:** FE hiển thị message chính xác theo từng case (SC #1 — "thông báo lỗi rõ ràng"). Code constants ở `CouponErrorCode` enum để FE switch.

- **D-12:** **Integration vào order create:** mở rộng `OrderCrudService.CreateOrderCommand` thêm field `couponCode: String?`. Endpoint `POST /orders` body có thêm `couponCode` optional. Nếu có → atomic redeem trước insert order → set discount fields → save order. Tất cả 1 `@Transactional`. Nếu coupon fail → rollback toàn bộ (order không được tạo).

### Backend API Surface

- **D-13:** **User-side endpoint:**
  - `POST /orders/coupons/validate` (header `X-User-Id`) — preview validation.
  - **Why path:** scope dưới `/orders/coupons` để gateway rewrite `/api/orders/coupons/**` mà KHÔNG đụng `/orders` resource gốc.

- **D-14:** **Admin endpoints** (`/admin/coupons` ở order-svc, JwtRoleGuard.requireAdmin):
  - `GET /admin/coupons?page=&size=&sort=&q=&active=` — list với filter + pagination.
  - `GET /admin/coupons/{id}` — detail.
  - `POST /admin/coupons` — create.
  - `PUT /admin/coupons/{id}` — update full record.
  - `PATCH /admin/coupons/{id}/active` body `{active: true|false}` — toggle soft-disable.
  - `DELETE /admin/coupons/{id}` — hard delete chỉ khi `used_count = 0`. Nếu đã có redemption → 409 `COUPON_HAS_REDEMPTIONS` + gợi ý dùng disable.

- **D-15:** **Gateway routes** (`api-gateway/application.yml`) — thêm 2 routes:
  - `/api/orders/coupons/**` → order-svc (rewrite `/orders/coupons/**`)
  - `/api/orders/admin/coupons/**` → order-svc (rewrite `/admin/coupons/**`) — match Phase 19 pattern admin charts.

- **D-16:** **DTO shapes:**
  - `CouponDto`: id, code, type, value, minOrderAmount, maxTotalUses (nullable), usedCount, expiresAt (nullable, ISO-8601), active, createdAt, updatedAt.
  - `CouponPreviewResponse`: code, type, value, discountAmount (đã tính), finalTotal, message.
  - `CreateCouponRequest` / `UpdateCouponRequest`: validate qua `@Valid` (jakarta annotations: code regex `^[A-Z0-9_-]{3,32}$`, value > 0, type in enum, minOrderAmount ≥ 0).

### Frontend — Checkout Coupon UX

- **D-17:** **Vị trí UI:** thêm 1 section mới trong `checkout/page.tsx` **giữa cart summary và order summary**, KHÔNG tách step riêng.
  - Section title: "Mã giảm giá".
  - Layout: row gồm `<Input placeholder="Nhập mã giảm giá" />` + `<Button>Áp dụng</Button>`. Sau khi áp thành công: hiển thị chip `MÃ_X (-25.000 đ) [Bỏ]` + cập nhật order summary có dòng "Giảm giá: -25.000 đ" và "Tổng tiền" mới.
  - **Why:** đơn giản, scroll-friendly, khớp single-page checkout đã có (PROJECT.md "multi-step checkout" defer).

- **D-18:** **State quản lý qua React Query mutation:**
  - `useApplyCoupon()` mutation gọi `POST /api/orders/coupons/validate` → on success, store `{code, discountAmount, finalTotal}` vào local state component.
  - On error → toast Vietnamese từ message map `COUPON_*` codes.
  - Re-validate trigger: nếu cart items thay đổi (add/remove/update qty) sau khi đã apply coupon → auto re-call validate; nếu fail → clear coupon + toast "Mã giảm giá không còn áp dụng được".

- **D-19:** **Submit order:** khi user click "Đặt hàng", body `POST /api/orders` thêm `couponCode` từ state (null nếu chưa apply). Nếu BE atomic fail → toast lỗi cụ thể, KHÔNG clear cart. User có thể bỏ mã + retry.

- **D-20:** **Hiển thị tổng tiền:** order summary có 3 dòng dọc khi có coupon:
  ```
  Tạm tính: 1.500.000 đ
  Giảm giá (CODE_X): -150.000 đ
  Tổng tiền: 1.350.000 đ
  ```
  Khi không có coupon thì chỉ "Tổng tiền".

### Frontend — Admin Coupons Page

- **D-21:** **Trang mới `/admin/coupons/page.tsx`:** mirror pattern `/admin/products/page.tsx` (CRUD + edit modal).
  - **List view:** table columns: Mã | Loại | Giá trị | Đơn tối thiểu | Đã dùng / Tối đa | Hết hạn | Trạng thái | Hành động (Sửa / Tắt|Bật / Xoá).
  - **Filters:** search box (theo code), select active (Tất cả / Đang bật / Đã tắt).
  - **Create/Edit modal:** form rhf+zod (đã có pattern Phase 11 AddressPicker / Phase 13 review form):
    - Mã (text, auto-uppercase, regex)
    - Loại (radio: Phần trăm / Số tiền cố định)
    - Giá trị (number; nếu PERCENT: max 100 + suffix `%`; nếu FIXED: suffix ` đ`)
    - Đơn tối thiểu (number, default 0)
    - Tối đa lượt dùng (number nullable — checkbox "Không giới hạn")
    - Ngày hết hạn (datetime-local nullable — checkbox "Không hết hạn")
    - Active (checkbox, default true)
  - **Disable / Enable** = PATCH `/active` toggle.
  - **Delete** confirm modal: nếu BE trả `COUPON_HAS_REDEMPTIONS` → hiển thị lỗi "Coupon đã có người dùng — vui lòng tắt thay vì xoá".

- **D-22:** **Nav admin layout:** thêm link "Coupon" vào sidebar admin (`/admin/layout.tsx`) — đặt sau "Đơn hàng", trước "Người dùng" hoặc theo thứ tự alphabet, planner quyết.

### Frontend — Order Detail Display (COUP-05)

- **D-23:** **`/account/orders/[id]` + `/admin/orders/[id]`** — bổ sung block hiển thị coupon nếu `order.couponCode` non-null:
  ```
  Mã giảm giá: CODE_X
  Giảm giá: -150.000 đ
  ```
  Đặt ngay phía trên dòng "Tổng tiền" trong order summary box.
  - **Why:** chỉ cần đọc 2 trường snapshot trên `OrderEntity` (`coupon_code`, `discount_amount`) — KHÔNG join `coupon_redemptions` cho display (đơn giản, an toàn nếu coupon bị xoá sau này).

- **D-24:** **OrderDto BE** — thêm 2 field `discountAmount: BigDecimal` + `couponCode: String?` vào response. FE typed module regenerate qua Swagger codegen pipeline.

### Race Condition Verification

- **D-25:** **Test pattern cho atomic redemption** — viết integration test (`@SpringBootTest` + Testcontainers Postgres nếu có pattern, hoặc embedded H2 fallback):
  - 2 thread parallel cùng gọi `OrderCrudService.create` với cùng coupon `max_total_uses=1`. Assert: chỉ 1 thành công (rowsAffected=1), 1 fail với `COUPON_CONFLICT_OR_EXHAUSTED`. `coupons.used_count = 1` cuối cùng.
  - 2 thread parallel cùng user, cùng coupon → 1 thành công, 1 fail `COUPON_ALREADY_REDEEMED` qua UNIQUE violation.
  - **Why:** SC #3 yêu cầu "race condition safe" — phải có test bằng chứng.

### Claude's Discretion

- Cụ thể CSS module structure cho coupon section trong checkout (planner quyết: extend `checkout/page.module.css` hay tách file riêng).
- Có viết Playwright E2E hay không (giữ TEST-02 deferred policy — tối thiểu integration test BE D-25 + manual UAT).
- Naming chính xác cho service/repository methods phía BE (planner chọn theo convention codebase: `CouponService`, `CouponRepository`, `CouponRedemptionRepository`).
- Cụ thể cách JPA map `expires_at` nullable + `max_total_uses` nullable (Optional<Instant> / Integer wrapper class — planner quyết).
- Có thêm rate-limit ở `POST /orders/coupons/validate` để chống brute-force code không (defer — premature).
- Có sort/index nào extra cần cho coupons table không (basic UNIQUE(code) đủ giai đoạn này, planner thêm nếu thấy cần).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Roadmap & requirements
- `.planning/ROADMAP.md` §"Phase 20: Hệ Thống Coupon" — goal + 4 success criteria + V3 reservation note (V3 → V5 patch cần làm, see D-01).
- `.planning/ROADMAP.md` §"Pre-Phase Setup (v1.3)" — Flyway version table cần update.
- `.planning/REQUIREMENTS.md` lines 84–88 — COUP-01..05 chi tiết (đã đọc, là source-of-truth cho schema + atomic SQL).
- `.planning/PROJECT.md` §"Active (v1.3)" — coupon scope: % off + fixed amount, min order + expiry + max usage/user, 1 mã/đơn.

### Prior phase context (pattern source-of-truth)
- `.planning/phases/18-storage-audit-cart-db/18-CONTEXT.md` — order-svc Flyway pattern + `OrderCrudService` extension pattern + `X-User-Id` header.
- `.planning/phases/19-ho-n-thi-n-admin-charts-low-stock/19-CONTEXT.md` — gateway admin route rewrite pattern (D-15 mirror), Promise.allSettled UI pattern, JwtRoleGuard.requireAdmin reuse.
- `.planning/phases/17-s-a-order-detail-items/17-CONTEXT.md` — order detail UI extension pattern (D-23 mirror).
- `.planning/phases/11-address-book-order-history-filtering/` — admin form CRUD + rhf+zod pattern (D-21 mirror).

### Existing code (must read before extending)

**Backend (order-svc):**
- `sources/backend/order-service/src/main/resources/db/migration/V4__add_cart_tables.sql` — Flyway naming convention + schema `order_svc`.
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderEntity.java` — fields `total/status/paymentMethod` để mở rộng thêm `discountAmount/couponCode`.
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java` — `CreateOrderCommand` cần extend `couponCode`, `create()` flow cần inject atomic redemption step.
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/OrderController.java` — `POST /orders` body cần thêm `couponCode`.
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/JwtRoleGuard.java` — admin gate cho `/admin/coupons`.
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/AdminStatsController.java` — admin controller pattern reference.
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/api/ApiResponse.java` + ApiErrorResponse — error envelope pattern (D-11 reuse).

**Backend (api-gateway):**
- `sources/backend/api-gateway/src/main/resources/application.yml` — thêm 2 routes (D-15).

**Frontend:**
- `sources/frontend/src/app/checkout/page.tsx` — checkout integrate point (D-17, D-19).
- `sources/frontend/src/app/admin/page.tsx` + `/admin/layout.tsx` — sidebar nav extension (D-22).
- `sources/frontend/src/app/admin/products/page.tsx` — admin CRUD page pattern (D-21 mirror).
- `sources/frontend/src/app/account/orders/[id]/page.tsx` — order detail user view (D-23).
- `sources/frontend/src/app/admin/orders/[id]/page.tsx` — order detail admin view (D-23).
- `sources/frontend/src/services/orders.ts` — `createOrder` extend với `couponCode`.
- `sources/frontend/src/services/http.ts` — `httpGet/httpPost` Bearer + X-User-Id pattern.
- `sources/frontend/src/services/errors.ts` — `isApiError` helper, error code mapping.
- `sources/frontend/src/components/ui/Input/Input.tsx`, `Button`, `Modal`, `Banner`, `Toast` — UI primitives.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`OrderCrudService.create` transactional flow** — extend với atomic coupon step trước order persist, cùng @Transactional propagate rollback.
- **`OrderEntity.total: BigDecimal`** — thêm 2 fields mới `discountAmount`, `couponCode` qua `@Column` với DEFAULT trong migration.
- **`JwtRoleGuard.requireAdmin(authHeader)`** — reuse cho 5 admin coupon endpoints.
- **`ApiResponse.of(200, msg, data)`** + `ApiErrorResponse` envelope — chuẩn output.
- **`X-User-Id` header pattern** (Phase 9 + Phase 18) — reuse cho coupon validate user-scope.
- **rhf + zod form pattern** (Phase 10 profile, Phase 11 address) — mirror cho admin coupon form.
- **React Query mutation + toast error pattern** (Phase 18 cart) — mirror cho `useApplyCoupon`.
- **Modal + Banner + Toast UI primitives** đã có ở `components/ui/` — admin form + error display.

### Established Patterns
- Per-svc admin endpoints `/admin/{resource}/...` mapped via gateway `/api/{svc}/admin/{resource}/...` rewrite.
- `ApiResponse.of(...)` envelope cho mọi endpoint, `ApiErrorResponse` cho lỗi với `code` + `message` + `details`.
- `@Transactional` (default propagation REQUIRED, KHÔNG readOnly cho create flow).
- Flyway version mỗi service tăng dần (V1, V2, V4 → V5 cho phase này).
- CSS modules per page (`page.module.css`), CSS vars `--primary/--secondary` cho theming.
- Vietnamese UI labels everywhere (memory: feedback_language).
- Error code constants → FE map sang message Vietnamese.

### Integration Points
- **order-svc** — code mới (Coupon* domain/repo/service/controller) + extend `OrderCrudService` + extend `OrderEntity`/`OrderDto`.
- **api-gateway** — `application.yml` thêm 2 routes user/admin coupon.
- **FE checkout page** — thêm coupon section UI + state + validate mutation + submit body extend.
- **FE admin** — trang mới `/admin/coupons` + nav link sidebar layout.
- **FE order detail** (user + admin) — render coupon snapshot fields nếu non-null.
- **Codegen pipeline** — Swagger emit `CouponDto`, `CouponPreviewResponse`, `CreateCouponRequest`, `UpdateCouponRequest` cho FE typed module.

</code_context>

<specifics>
## Specific Ideas

- User invoke `--auto --chain` → mọi gray area chốt theo recommended option, planner tự do trên Claude's Discretion items, sau đó auto plan + execute.
- **V5 thay V3** (D-01) — KHÔNG dùng V3 vì conflict với V4 đã shipped Phase 18; planner phải patch ROADMAP.md Pre-Phase Setup table.
- **Snapshot 2 cột trên `orders`** (D-02) thay vì JOIN `coupon_redemptions` cho display — giữ lịch sử nếu coupon bị xoá, đơn giản FE.
- **Atomic UPDATE conditional với `rowsAffected=1` check** (D-08) là trái tim của race-safety — KHÔNG dùng SELECT-then-UPDATE pattern.
- **UNIQUE(coupon_id, user_id)** xử lý 1-mã/user ở DB level (KHÔNG check application code).
- **2 endpoint structure** — preview (`POST /orders/coupons/validate`) tách khỏi atomic redeem (in-line `POST /orders` flow). Preview KHÔNG khoá row, chỉ atomic redeem mới mutate state.
- **Race condition test** (D-25) là MUST cho SC #3 — không skip.
- **Soft-disable preferred over hard-delete** (D-14) — bảo toàn lịch sử redemptions; hard delete chỉ khi never used.

</specifics>

<deferred>
## Deferred Ideas

- **Coupon stacking (nhiều mã/đơn)** — locked PROJECT.md "1 mã/đơn", out of scope mãi mãi (hoặc đợi phase v1.4+ nếu có demand).
- **Auto-apply best coupon / banner promo** — defer, cần discovery riêng (UX heavy).
- **Per-product / per-category coupon** — defer, schema cần JOIN bảng `coupon_eligible_products` mới.
- **First-time-buyer coupon / referral coupon** — defer, cần user metadata mở rộng.
- **Coupon analytics chart** ở admin dashboard — defer (Phase 19 đã defer ADMIN-07 custom date picker, coupon chart cùng tier).
- **User-facing "Mã của tôi" page** liệt kê coupon đã/chưa dùng — defer; order detail D-23 đã đủ visibility.
- **Rate-limit `/coupons/validate` endpoint** chống brute-force — defer khi đo được abuse.
- **Rollback `used_count` khi hủy order** — defer; phase này hủy KHÔNG trả lại lượt (đơn giản trước, nếu cần là phase riêng).
- **i18n/multi-currency** — VND-only đủ giai đoạn này.
- **Coupon code generator UI** ở admin (auto-gen random) — defer; admin tự nhập đủ.
- **Email notify khi coupon expired/disable** — defer.

</deferred>

---

*Phase: 20-coupons*
*Context gathered: 2026-05-03*
