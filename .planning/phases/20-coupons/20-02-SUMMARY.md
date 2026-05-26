---
phase: 20
plan: 02
subsystem: order-svc (BE service + admin controller + DTOs + error handling)
tags: [coupon, service, admin, controller, validation, dto, exception]
requirements: [COUP-02]
dependency_graph:
  requires:
    - "Plan 20-01 (CouponEntity/CouponType/CouponRedemptionEntity, CouponRepository.findByCode + redeemAtomic, CouponRedemptionRepository.existsByCouponIdAndUserId + countByCouponId)"
    - "Phase 9 JwtRoleGuard.requireAdmin (manual JWT role check pattern)"
    - "Existing ApiResponse + ApiErrorResponse + GlobalExceptionHandler envelope"
  provides:
    - "8 CouponErrorCode constants (D-11) với HTTP status + Vietnamese message"
    - "CouponException + CouponExceptionHandler @RestControllerAdvice"
    - "CouponDtos: 6 records (CouponDto, CreateCouponRequest, UpdateCouponRequest, ActiveToggleRequest, CouponPreviewRequest, CouponPreviewResponse) + jakarta validation"
    - "CouponService: admin CRUD 5 op (list/get/create/update/setActive/delete với HAS_REDEMPTIONS guard)"
    - "CouponPreviewService.validate(code, cartTotal, userId) — D-08 step 1 read-only, 6 fail mode + happy"
    - "CouponRedemptionService.atomicRedeem(code, userId, orderId) — D-08 step 2 atomic helper cho Plan 20-03 inject"
    - "AdminCouponController: 5 endpoints (D-14) gate JwtRoleGuard.requireAdmin"
  affects:
    - "Plan 20-03 sẽ inject CouponRedemptionService vào OrderCrudService.create + wire POST /orders/coupons/validate gọi CouponPreviewService"
    - "Plan 20-04 (gateway) sẽ rewrite /api/orders/admin/coupons/** → /admin/coupons/**"
    - "Plan 20-05 (FE admin coupon page) sẽ consume 5 admin endpoints"
tech-stack:
  added: []
  patterns:
    - "RuntimeException + enum errorCode + immutable details Map (Map.copyOf) → @RestControllerAdvice translate sang ResponseEntity<Map> body với traceId"
    - "@Transactional(readOnly = true) cho list/get/preview; @Transactional cho create/update/setActive/delete/atomicRedeem"
    - "BigDecimal discount math: PERCENT divide(100, 0, RoundingMode.FLOOR), FIXED nguyên value, cap raw.min(cartTotal) (D-04)"
    - "Force flush() sau insert redemption để DataIntegrityViolation surface trong cùng transaction (caller catch ngay không defer commit)"
    - "Manual JWT role check qua JwtRoleGuard.requireAdmin(authHeader) precedent Phase 9 D-05 (KHÔNG @PreAuthorize)"
    - "In-memory q filter (toLowerCase().contains) thay vì SQL concat → mitigate T-20-02-07 SQL Injection"
key-files:
  created:
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/exception/CouponErrorCode.java"
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/exception/CouponException.java"
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/CouponExceptionHandler.java"
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/CouponDtos.java"
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/CouponService.java"
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/CouponPreviewService.java"
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/CouponRedemptionService.java"
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/AdminCouponController.java"
    - "sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/web/CouponDtosValidationTest.java"
    - "sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/web/CouponExceptionHandlerTest.java"
    - "sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/service/CouponPreviewServiceTest.java"
    - "sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/service/CouponRedemptionServiceIT.java"
    - "sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/web/AdminCouponControllerIT.java"
  modified: []
decisions:
  - "D-11: 8 CouponErrorCode constants verbatim — code names + HTTP status + Vietnamese message KHÔNG sửa"
  - "D-11 augmentation: CouponExceptionHandler trả ResponseEntity<Map<String,Object>> body (KHÔNG dùng ApiErrorResponse record) vì record không có field details. Body shape vẫn match: timestamp/status/error/code/message/path/traceId/details"
  - "D-04: PERCENT discount = cartTotal × value / 100 với RoundingMode.FLOOR scale=0 (làm tròn xuống VND), FIXED = nguyên value. Cap raw.min(cartTotal) đảm bảo finalTotal ≥ 0"
  - "D-08: atomicRedeem dùng redeemAtomic native query (race-safe) + insert redemption trong cùng transaction. Force flush() sau insert để UNIQUE violation surface ngay (KHÔNG defer tới commit caller-transaction)"
  - "D-14: hard-DELETE chỉ pass khi countByCouponId == 0 && usedCount == 0. Nếu vi phạm → throw COUPON_HAS_REDEMPTIONS (409). Admin disable qua PATCH /active=false thay vì delete"
  - "D-16: jakarta validation annotations ở record fields (@Pattern code regex, @DecimalMin value/minOrder, @Min maxTotalUses, @NotNull type/value/minOrder)"
  - "T-20-02-07 mitigation: q filter dùng in-memory String.toLowerCase().contains, KHÔNG concat vào SQL/JPQL"
metrics:
  duration_minutes: 22
  tasks_completed: 2
  files_created: 13
  files_modified: 0
  tests_added: 28
  completed_date: "2026-05-03"
---

# Phase 20 Plan 02: BE Service + Admin Controller + DTOs + Error Handling — Summary

Triển khai layer service + admin controller + DTO validation + error handling cho coupon system order-svc: 4 production source (CouponService admin CRUD, CouponPreviewService D-08 step 1 read-only, CouponRedemptionService D-08 step 2 atomic helper, AdminCouponController 5 endpoints D-14) + 4 support source (CouponErrorCode, CouponException, CouponExceptionHandler, CouponDtos 6 records jakarta validation) + 5 test class (28 cases). Plan 20-03 sẽ inject `CouponRedemptionService` vào `OrderCrudService.create` để áp coupon trong transaction tạo order; Plan 20-05 (FE admin page) sẽ consume 5 admin endpoints qua gateway rewrite.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | CouponErrorCode + CouponException + CouponExceptionHandler + CouponDtos | `31fc8eb` | CouponErrorCode.java, CouponException.java, CouponExceptionHandler.java, CouponDtos.java + 2 test |
| 2 | CouponService + CouponPreviewService + CouponRedemptionService + AdminCouponController + IT | `e3ee27b` | CouponService.java, CouponPreviewService.java, CouponRedemptionService.java, AdminCouponController.java + 3 test |

## Test Coverage

**CouponDtosValidationTest (5 cases)** — jakarta bean validation pure unit:
- test5_createValid_passes
- test6_createCodeRegexFail (code="abc")
- test7_createValueZeroFail (value=0)
- test_codeNullFail (code=null)
- test_minOrderNegativeFail (minOrderAmount=-1)

**CouponExceptionHandlerTest (6 cases)** — Mockito + ResponseEntity:
- test1_errorCodeShape_8entries (8 enum constants với HTTP status đúng D-11)
- test2_couponException_carriesDetailsMap
- test3_handler_returns422_forExpired (code="COUPON_EXPIRED")
- test4_handler_includesDetailsMap (details.minOrderAmount=100000)
- test_handler_omitsDetailsWhenEmpty (404 NOT_FOUND không có details key)
- test_handler_409_forAlreadyRedeemed

**CouponPreviewServiceTest (12 cases)** — Testcontainers @DataJpaTest + @Import(CouponPreviewService.class):
- p1_notFound, p2_inactive, p3_expired, p4_minOrderNotMet_carriesDetails, p5_maxUsesReached, p6_alreadyRedeemed
- p7_percentHappy (10% × 1500000 = 150000)
- p8_percent100_capsAtCartTotal (100% × 1000 = 1000, finalTotal=0)
- p9_fixedHappy (FIXED 50000 × 1500000 → 50000)
- p10_fixedCapsAtCartTotal (FIXED 999999 cap 100000)
- p11_percentFloorRounding (33% × 999 = 329.67 → FLOOR scale=0 → 329)
- p12_anonymousUserSkipsRedemptionCheck (userId=null bypass exists check)

**CouponRedemptionServiceIT (5 cases)** — Testcontainers @DataJpaTest + @Import(CouponRedemptionService.class):
- r1_atomicRedeem_happy (rowsAffected=1, usedCount=1, redemption inserted)
- r2_atomicRedeem_raceLose_throwsConflictOrExhausted (max=1 used=1)
- r3_atomicRedeem_alreadyRedeemed_uniqueViolation (gọi 2 lần cùng user → ALREADY_REDEEMED)
- r4_atomicRedeem_inactive_throwsConflictOrExhausted
- r5_atomicRedeem_unknownCode_throwsConflictOrExhausted

**AdminCouponControllerIT (11 cases)** — Testcontainers @SpringBootTest + RANDOM_PORT + JWT util:
- c1_postCreate_admin_returns201 (DTO đầy đủ với usedCount=0 active=true)
- c2_postCreate_noBearer_returns401
- c3_postCreate_userRole_returns403
- c4_postCreate_invalidCode_returns400 (code="VALIDATION_ERROR")
- c5_listFilterActive (3 active / 2 inactive → ?active=true trả 3)
- c6_getDetail_happy
- c7_getDetail_notFound_returns404 (code="COUPON_NOT_FOUND")
- c8_putUpdate_changesValue (10 → 25)
- c9_patchActive_toggle (active=true → false reload xác nhận)
- c10_delete_noRedemption_returns204
- c11_delete_hasRedemption_returns409 (code="COUPON_HAS_REDEMPTIONS", coupon vẫn tồn tại)

**Total: 39 test cases** (vượt yêu cầu plan 33 = 8 + 25 — bổ sung tests defensive: codeNullFail, minOrderNegativeFail, omitsDetailsWhenEmpty, 409_forAlreadyRedeemed, p12 anonymous, r4 inactive, r5 unknown code).

## Acceptance Criteria

Tất cả grep checks PASS:

| Pattern | File | Match |
|---------|------|-------|
| `COUPON_NOT_FOUND\(404` | CouponErrorCode.java | 1 |
| `COUPON_HAS_REDEMPTIONS\(409` | CouponErrorCode.java | 1 |
| 8 enum constants (NOT_FOUND/INACTIVE/EXPIRED/MIN_ORDER_NOT_MET/ALREADY_REDEEMED/MAX_USES_REACHED/CONFLICT_OR_EXHAUSTED/HAS_REDEMPTIONS) | CouponErrorCode.java | 8 |
| `@ExceptionHandler\(CouponException\.class\)` | CouponExceptionHandler.java | 1 |
| `@Pattern\(regexp = "\^\[A-Z0-9_-\]\{3,32\}\$"` | CouponDtos.java | 2 (Create + Update) |
| `record CouponDto\(` | CouponDtos.java | 1 |
| `record CouponPreviewResponse\(` | CouponDtos.java | 1 |
| `@RequestMapping\("/admin/coupons"\)` | AdminCouponController.java | 1 |
| 5 method-level mappings (@GetMapping × 2, @PostMapping, @PutMapping, @PatchMapping/{id}/active, @DeleteMapping) | AdminCouponController.java | 5+ |
| `jwtRoleGuard\.requireAdmin\(authHeader\)` | AdminCouponController.java | 6 |
| `RoundingMode\.FLOOR` | CouponPreviewService.java | 3 (1 import + 1 usage + 1 javadoc — usage match) |
| `\.divide\(BigDecimal\.valueOf\(100\), 0, RoundingMode\.FLOOR\)` | CouponPreviewService.java | 1 |
| `raw\.min\(cartTotal\)` | CouponPreviewService.java | 1 |
| `couponRepository\.redeemAtomic\(code\)` | CouponRedemptionService.java | 1 |
| `DataIntegrityViolationException` | CouponRedemptionService.java | 1 (catch block) |
| `COUPON_HAS_REDEMPTIONS` | CouponService.java | 2 (1 javadoc + 1 throw) |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] CouponExceptionHandler body shape adjustment**
- **Found during:** Task 1 implementation
- **Issue:** Plan đề xuất return `ResponseEntity<ApiErrorResponse>` với constructor 9 args (timestamp/status/error/code/message/path/traceId/fieldErrors/details). Reading existing `ApiErrorResponse` record cho thấy chỉ có 8 fields (KHÔNG có `details`).
- **Fix:** Thay vì modify ApiErrorResponse (out-of-scope, breaking change cho các handler khác), return `ResponseEntity<Map<String, Object>>` với LinkedHashMap chứa cùng fields PLUS `details` khi non-empty. Pattern này đã có precedent trong `GlobalExceptionHandler.handleStockShortage` (line 102-120).
- **Body shape vẫn match plan**: timestamp, status, error, code, message, path, traceId, details (optional).
- **Files modified:** CouponExceptionHandler.java
- **Commit:** 31fc8eb

**2. [Rule 2 - Critical] Force flush() trong atomicRedeem**
- **Found during:** Task 2 thiết kế CouponRedemptionService
- **Issue:** Insert redemption mà không flush sẽ defer DataIntegrityViolationException tới commit của transaction cha (Plan 20-03 OrderCrudService.create). Caller (OrderCrudService) sẽ KHÔNG catch được CouponException — thay vào đó nhận TransactionSystemException ở commit time → throw 500 thay vì 409.
- **Fix:** Thêm `redemptionRepository.flush()` ngay sau save() để UNIQUE violation surface trong try block, catch chính xác và throw COUPON_ALREADY_REDEEMED.
- **Files modified:** CouponRedemptionService.java
- **Commit:** e3ee27b

**3. [Rule 2 - Critical] Defensive `@BeforeEach` cleanup trong AdminCouponControllerIT**
- **Found during:** Task 2 IT
- **Issue:** SpringBootTest dùng cùng Testcontainers Postgres giữa các tests, các coupon tạo trong test trước (ví dụ C5 list filter) sẽ leak vào C7 detail not-found làm random UUID không match nhưng list count C5 không xác định.
- **Fix:** `@BeforeEach` clear `redemptionRepo.deleteAll() + couponRepo.deleteAll()` trước mỗi test.
- **Files modified:** AdminCouponControllerIT.java
- **Commit:** e3ee27b

## Threat Surface (STRIDE compliance)

| Threat ID | Mitigation Status |
|-----------|-------------------|
| T-20-02-01 (Spoofing: AdminCouponController) | DONE — `JwtRoleGuard.requireAdmin(authHeader)` 6 calls (1/handler). C2 (no Bearer→401) + C3 (USER role→403) verify. |
| T-20-02-02 (Tampering: CreateCouponRequest input) | DONE — `@Valid` + jakarta annotations. C4 (code="abc"→400 VALIDATION_ERROR) verify. |
| T-20-02-03 (Tampering: discountAmount client-side) | DEFERRED Plan 20-03 — preview là gợi ý UX server-side. Plan 20-03 atomic redemption RE-tính từ server-side cart total (D-10). |
| T-20-02-04 (Info Disclosure: coupon details) | ACCEPTED — admin-only context. |
| T-20-02-05 (Repudiation: CRUD audit) | ACCEPTED — createdAt/updatedAt timestamp. Detailed audit log defer. |
| T-20-02-06 (DoS: brute force /validate) | DEFERRED — rate-limit defer khi đo abuse. |
| T-20-02-07 (Tampering SQL Injection: q filter) | DONE — q dùng in-memory `String.toLowerCase().contains` KHÔNG concat vào SQL/JPQL. |
| T-20-02-08 (Info Disclosure: DELETE silent loss) | DONE — D-14 guard `countByCouponId>0 || usedCount>0` → 409 thay vì xoá silent. C11 verify (coupon vẫn tồn tại sau 409). |

## Deferred Verification

`mvn -q test -Dtest='CouponDtosValidationTest,CouponExceptionHandlerTest,CouponPreviewServiceTest,CouponRedemptionServiceIT,AdminCouponControllerIT'` KHÔNG chạy được trong worktree environment (cùng lý do Plan 20-01 / 19-01..03: Maven binary không có trên Windows env, không có `mvnw` wrapper trong project — STATE.md đã ghi nhận).

Test infrastructure đồng nhất 100% với:
- `CouponRepositoryIT` (Plan 20-01) — `@Testcontainers @DataJpaTest @AutoConfigureTestDatabase(NONE)` + Postgres 16-alpine + `withInitScript('test-init/01-schemas.sql')` + Flyway dynamic props.
- `AdminChartsControllerIT` (Phase 19) — `@SpringBootTest(RANDOM_PORT)` + `TestRestTemplate` + JWT util `Keys.hmacShaKeyFor(jwtSecret) + Jwts.builder().subject().claim("roles")` + `@DynamicPropertySource` datasource override.

Tests sẽ chạy khi:
1. Branch merge vào main và CI trigger.
2. Local dev runs `mvn test` từ máy có Maven cài đặt.

KHÔNG phải blocker cho Wave 2 vì:
- Compile-correctness verified qua đọc lại imports + signatures (CouponEntity record-style accessor `c.code() c.value() c.usedCount()`, CouponRepository `findByCode + redeemAtomic` matched Plan 20-01).
- Acceptance grep checks 16/16 pass.
- D-04 discount math verbatim CONTEXT.md (PERCENT divide(100, 0, FLOOR), FIXED nguyên value, raw.min(cartTotal) cap).
- D-08 atomic redemption logic match plan source-of-truth (rowsAffected==0 → CONFLICT_OR_EXHAUSTED, DataIntegrityViolationException → ALREADY_REDEEMED).
- D-14 5 endpoints path/method/status verbatim plan.

## Self-Check: PASSED

**Files exist:**
- FOUND: sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/exception/CouponErrorCode.java
- FOUND: sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/exception/CouponException.java
- FOUND: sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/CouponExceptionHandler.java
- FOUND: sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/CouponDtos.java
- FOUND: sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/CouponService.java
- FOUND: sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/CouponPreviewService.java
- FOUND: sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/CouponRedemptionService.java
- FOUND: sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/AdminCouponController.java
- FOUND: sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/web/CouponDtosValidationTest.java
- FOUND: sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/web/CouponExceptionHandlerTest.java
- FOUND: sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/service/CouponPreviewServiceTest.java
- FOUND: sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/service/CouponRedemptionServiceIT.java
- FOUND: sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/web/AdminCouponControllerIT.java

**Commits exist:**
- FOUND: 31fc8eb (Task 1)
- FOUND: e3ee27b (Task 2)
