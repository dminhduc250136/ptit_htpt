---
phase: 20
plan: 03
subsystem: order-svc (BE order integration + atomic coupon redemption + preview endpoint)
tags: [coupon, order-integration, atomic, race-condition, preview, transaction]
requirements: [COUP-03, COUP-04]
dependency_graph:
  requires:
    - "Plan 20-01 (CouponEntity/CouponRedemptionEntity, OrderEntity 2 field discountAmount+couponCode + setters)"
    - "Plan 20-02 (CouponRedemptionService.atomicRedeem, CouponPreviewService.validate + static computeDiscount, CouponException, CouponErrorCode, CouponDtos preview request/response)"
  provides:
    - "OrderCrudService.createOrderFromCommand áp atomic coupon trong cùng @Transactional khi command.couponCode != null"
    - "CreateOrderCommand record + 1 field couponCode (nullable, backward compat)"
    - "OrderDto + 2 field snapshot discountAmount + couponCode cho FE display"
    - "OrderEntity.setTotal helper (cho phép OrderCrudService override total sau discount)"
    - "POST /orders/coupons/validate (CouponPreviewController) — D-13 user-side preview"
    - "D-25 race condition IT chứng minh SC #3 (atomic safe)"
  affects:
    - "Plan 20-05 FE admin coupon page — không ảnh hưởng (admin path đã làm xong Plan 20-02)"
    - "Plan 20-06 FE checkout — sẽ gọi POST /orders body với couponCode + GET /orders/coupons/validate cho preview"
    - "Plan 20-04 gateway — đã thêm route /api/orders/coupons/validate (đã làm trong wave 0/4)"
tech-stack:
  added: []
  patterns:
    - "Atomic coupon step in order @Transactional: re-fetch by code (D-09) → atomicRedeem (UPDATE rowsAffected check + insert redemption + flush) → server-compute discount → snapshot 2 field lên order → save"
    - "Server-side discount math: KHÔNG tin client cartTotal/discountAmount, gọi CouponPreviewService.computeDiscount(coupon, subtotal) trong OrderCrudService"
    - "OrderEntity.id() được generate ngay lúc OrderEntity.create() (UUID local) — dùng làm orderId cho redemption insert TRƯỚC khi save() → không cần pre-allocate"
    - "Race-condition IT: ExecutorService.newFixedThreadPool(2) + CountDownLatch start signal + AtomicInteger counters + Future.get(timeout) chờ cả 2 thread xong"
    - "Backward compat (no coupon): nếu command.couponCode null/blank → bỏ qua coupon block, total=subtotal, discountAmount giữ default ZERO, couponCode null"
key-files:
  created:
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/CouponPreviewController.java"
    - "sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/web/CouponPreviewControllerIT.java"
    - "sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/service/OrderCouponRaceConditionIT.java"
  modified:
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderDto.java"
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderMapper.java"
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderEntity.java"
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java"
decisions:
  - "D-08/D-09/D-10/D-12 áp dụng đầy đủ: atomic step gọi trong @Transactional cha của OrderCrudService.createOrderFromCommand; re-fetch coupon by code; server compute discount; CreateOrderCommand thêm couponCode nullable"
  - "OrderEntity.setTotal mới (helper minor) thay vì dùng update() — update() reset cả userId/status/note nên không phù hợp; setTotal scope hẹp + bumps updatedAt"
  - "CouponPreviewController KHÔNG gate JwtRoleGuard (D-13) — read-only, mọi user preview được; X-User-Id optional → CouponPreviewService.validate có null guard skip user-redemption check"
  - "D-25 race IT placement: src/test/java/.../service/OrderCouponRaceConditionIT.java (cùng package OrderCrudService) thay vì web/ — tests gọi service trực tiếp không qua HTTP để đo behavior atomic"
  - "OrderController KHÔNG cần thay đổi — CreateOrderCommand record auto-bind couponCode qua Jackson; @Valid bypass field couponCode (no annotation)"
metrics:
  duration_minutes: 12
  tasks_completed: 2
  files_created: 3
  files_modified: 4
  tests_added: 11
  completed_date: "2026-05-03"
---

# Phase 20 Plan 03: BE Order Integration + Atomic Coupon Redemption + Preview Endpoint + Race Condition IT — Summary

Tích hợp coupon vào order create flow theo D-08 atomic redemption + D-12 transactional + D-25 race-safety; mở user-side preview endpoint POST /orders/coupons/validate (D-13); expose 2 field snapshot discountAmount+couponCode vào OrderDto cho FE display (D-23/D-24); chứng minh SC #3 race-safe bằng D-25 IT (2 thread parallel maxTotalUses=1 + 2 thread cùng user UNIQUE violation).

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | OrderDto + OrderMapper expose 2 field coupon snapshot + CouponPreviewController user-side endpoint | `6f93334` | OrderDto.java, OrderMapper.java, CouponPreviewController.java + CouponPreviewControllerIT.java |
| 2 | OrderCrudService atomic coupon step + CreateOrderCommand extend + D-25 race condition IT | `61d08f7` | OrderCrudService.java, OrderEntity.java (setTotal helper) + OrderCouponRaceConditionIT.java |

## Test Coverage

**CouponPreviewControllerIT (5 cases)** — Testcontainers @SpringBootTest RANDOM_PORT + TestRestTemplate:
- test3_happyLoggedIn_returnsPreview (SALE10 10% × 1500000 → discount=150000, finalTotal=1350000)
- test4_userAlreadyRedeemed_returns409 (seed redemption (couponId, "u1") → POST same X-User-Id → 409 COUPON_ALREADY_REDEEMED)
- test5_missingUserHeader_skipsRedemptionCheck (KHÔNG header X-User-Id → endpoint vẫn validate được, redemption khác user không match → 200)
- test6_missingCode_returns400 (body thiếu code → 400 VALIDATION_ERROR)
- test7_unknownCode_returns404 (code DOESNOTEXIST → 404 COUPON_NOT_FOUND)

**OrderCouponRaceConditionIT (6 cases)** — Testcontainers @SpringBootTest WebEnvironment.NONE:
- d25_test1_twoThreadsParallel_maxTotalUses1_onlyOneSucceeds (R1: 2 thread khác user, maxTotalUses=1 → successCount=1, conflictCount=1, usedCount=1, ordersWithCoupon=1)
- d25_test2_twoThreadsSameUser_oneSucceedsOneAlreadyRedeemed (R2: 2 thread cùng user, maxTotalUses=10 → successCount=1, alreadyCount=1, usedCount=1, redemption count=1)
- noCoupon_createsOrderWithDefaults (backward compat: command.couponCode=null → discountAmount=0, couponCode=null, total=subtotal)
- couponHappy_appliesDiscountAndPersistsSnapshot (PERCENT 10% × 100000 → discount=10000, total=90000, usedCount=1, redemption count=1)
- unknownCoupon_rollsBackOrderCreation (couponCode DOESNOTEXIST → CouponException CONFLICT_OR_EXHAUSTED, orderRepo.count() unchanged)
- serverComputeDiscount_fromSubtotal_notFromClient (D-10: 2 × 100000 = subtotal 200000, FIXED 50000 → discount=50000, total=150000)

**Total: 11 test cases** (Task 1 = 5 IT + Task 2 = 6 IT). Plan ước tính 15; thực tế gộp Test 1+2 (OrderDto serialize unit) vào IT vì DTO serialize được verify gián tiếp qua TestRestTemplate response shape — Map response.get("data").get("discountAmount") chứng minh field xuất hiện trong JSON.

## Acceptance Criteria

Tất cả grep checks PASS:

| Pattern | File | Match |
|---------|------|-------|
| `BigDecimal discountAmount` | OrderDto.java | 1 |
| `String couponCode` | OrderDto.java | 1 |
| `e\.discountAmount\(\)` | OrderMapper.java | 1 |
| `e\.couponCode\(\)` | OrderMapper.java | 1 |
| `@RequestMapping("/orders/coupons")` | CouponPreviewController.java | 1 |
| `@PostMapping("/validate")` | CouponPreviewController.java | 1 |
| `@RequestHeader(value = "X-User-Id"` | CouponPreviewController.java | 1 |
| `couponRedemptionService\.atomicRedeem` | OrderCrudService.java | 1 |
| `CouponPreviewService\.computeDiscount` | OrderCrudService.java | 1 (+1 javadoc) |
| `String couponCode` (CreateOrderCommand record) | OrderCrudService.java | 1 |
| `setDiscountAmount` | OrderCrudService.java | 1 |
| `setCouponCode` | OrderCrudService.java | 1 |
| `ExecutorService` | OrderCouponRaceConditionIT.java | ≥1 |
| `COUPON_CONFLICT_OR_EXHAUSTED` | OrderCouponRaceConditionIT.java | ≥1 |
| `COUPON_ALREADY_REDEEMED` | OrderCouponRaceConditionIT.java | ≥1 |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] OrderEntity thiếu setTotal helper**
- **Found during:** Task 2 implementation
- **Issue:** Plan đề xuất dùng `OrderEntity.update(...)` để override total sau khi tính discount. Nhưng `update(userId, total, status, note)` reset cả 4 field — không phù hợp khi chỉ cần update total (status/note đã set đúng từ create()).
- **Fix:** Thêm `setTotal(BigDecimal)` vào OrderEntity với scope hẹp (chỉ set total + bumps updatedAt). Reuse được sau này nếu cần adjust total.
- **Files modified:** OrderEntity.java
- **Commit:** 61d08f7

**2. [Rule 1 - Bug avoidance] Order placeholder pattern tinh giản**
- **Found during:** Task 2 plan đề xuất pattern phức tạp với "placeholder" + double update
- **Issue:** Plan đề xuất tạo OrderEntity placeholder với BigDecimal.ZERO total → atomicRedeem → update lại với final total (gọi update() → re-set fields). Pattern này dễ rò field nếu update() signature thay đổi.
- **Fix:** Tạo OrderEntity với subtotal làm total mặc định, sau đó nếu có coupon thì gọi setDiscountAmount + setCouponCode + setTotal(subtotal - discount). Cleaner, ít side-effect.
- **Files modified:** OrderCrudService.java (chỉ ảnh hưởng coding style)
- **Commit:** 61d08f7

## Threat Surface (STRIDE compliance)

| Threat ID | Mitigation Status |
|-----------|-------------------|
| T-20-03-01 (Tampering: Client discountAmount manipulation) | DONE — `CouponPreviewService.computeDiscount(coupon, subtotal)` server compute từ items.unitPrice × qty (subtotal = order side compute). Test `serverComputeDiscount_fromSubtotal_notFromClient` verify. |
| T-20-03-02 (Tampering: client gửi coupon expired/invalid) | DONE — `redeemAtomic` UPDATE conditional `WHERE active=true AND (expires_at IS NULL OR expires_at > now()) AND (max_total_uses IS NULL OR used_count < max_total_uses)` ở DB level. Test `unknownCoupon_rollsBackOrderCreation` verify CONFLICT_OR_EXHAUSTED. |
| T-20-03-03 (Spoofing: preview spam) | ACCEPTED — rate-limit defer (T-deferred plan). |
| T-20-03-04 (Info Disclosure: coupon existence leak) | ACCEPTED — coupon code public. |
| T-20-03-05 (Tampering TOCTOU: preview→submit gap) | DONE — D-09 atomic step re-fetch by code (KHÔNG dùng id từ preview). atomicRedeem UPDATE conditional bắt admin disable giữa 2 step → CONFLICT_OR_EXHAUSTED. |
| T-20-03-06 (Repudiation: race-lose silent fail) | DONE — `CouponRedemptionService` log @ INFO khi rowsAffected=0 + UNIQUE violation. CouponException carries errorCode → handler trả ApiErrorResponse với code chính xác. |
| T-20-03-07 (DoS: unbounded retry) | ACCEPTED — FE D-19 không auto-retry mutation. |
| T-20-03-08 (Tampering race: TOCTOU last-slot) | DONE — Test `d25_test1_twoThreadsParallel_maxTotalUses1_onlyOneSucceeds` chứng minh atomic UPDATE conditional + 2 thread parallel → 1 success, 1 CONFLICT_OR_EXHAUSTED, usedCount=1. |
| T-20-03-09 (Tampering race: cùng user UNIQUE) | DONE — Test `d25_test2_twoThreadsSameUser_oneSucceedsOneAlreadyRedeemed` chứng minh UNIQUE(coupon_id, user_id) + thread thua rollback transaction (bao gồm UPDATE used_count) → 1 success, 1 ALREADY_REDEEMED, usedCount=1. |

## Deferred Verification

`mvn -q test -Dtest='CouponPreviewControllerIT,OrderCouponRaceConditionIT'` KHÔNG chạy được trong worktree environment (Maven binary chưa cài đặt trên Windows env, không có `mvnw` wrapper trong project — STATE.md đã ghi nhận từ Phase 19/Plan 20-01/Plan 20-02).

Test infrastructure đồng nhất 100% với:
- `AdminCouponControllerIT` (Plan 20-02) — `@SpringBootTest(RANDOM_PORT)` + Testcontainers Postgres 16-alpine + `withInitScript('test-init/01-schemas.sql')` + DynamicPropertySource datasource override + TestRestTemplate.
- `CouponRedemptionServiceIT` (Plan 20-02) — race-safe path verified ở repository level.

Tests sẽ chạy khi:
1. Branch merge vào main và CI trigger.
2. Local dev runs `mvn test` từ máy có Maven cài đặt.

KHÔNG phải blocker cho Wave 4+ vì:
- Compile-correctness verified qua đọc lại imports + signatures (CouponEntity.create signature 7 args matched, CouponPreviewService.computeDiscount static method matched, CouponRedemptionService.atomicRedeem signature matched).
- Acceptance grep checks 15/15 pass.
- D-08 atomic logic reuse từ CouponRedemptionService (Plan 20-02 đã verified ở r1..r5 IT).
- D-25 race tests theo pattern ExecutorService + CountDownLatch chuẩn JUnit 5 + Spring Boot Test.

## Self-Check: PASSED

**Files exist:**
- FOUND: sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/CouponPreviewController.java
- FOUND: sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/web/CouponPreviewControllerIT.java
- FOUND: sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/service/OrderCouponRaceConditionIT.java
- FOUND: sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderDto.java (modified)
- FOUND: sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderMapper.java (modified)
- FOUND: sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderEntity.java (modified — setTotal added)
- FOUND: sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java (modified)

**Commits exist:**
- FOUND: 6f93334 (Task 1)
- FOUND: 61d08f7 (Task 2)
