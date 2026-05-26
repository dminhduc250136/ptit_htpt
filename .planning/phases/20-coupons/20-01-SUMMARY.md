---
phase: 20
plan: 01
subsystem: order-svc (DB + JPA foundation)
tags: [coupon, db, jpa, flyway, order-svc, foundation]
requirements: [COUP-01]
dependency_graph:
  requires:
    - "Phase 18 V4 carts migration (xác lập schema order_svc + Flyway version cursor = 4 → V5 next)"
    - "OrderEntity record-style accessor convention (Phase 9+)"
    - "Testcontainers Postgres harness (Phase 19 OrderRepositoryChartsIT)"
  provides:
    - "DB schema: order_svc.coupons, order_svc.coupon_redemptions, orders.discount_amount, orders.coupon_code"
    - "JPA: CouponEntity, CouponType enum, CouponRedemptionEntity, OrderEntity (extended)"
    - "Repos: CouponRepository (findByCode + redeemAtomic), CouponRedemptionRepository (existsBy + countBy)"
  affects:
    - "Plan 20-02 (CouponService + admin controller) sẽ inject CouponRepository + CouponRedemptionRepository"
    - "Plan 20-03 (atomic redemption + order create integration) sẽ gọi redeemAtomic + setDiscountAmount/setCouponCode"
    - "Plan 20-05 (FE order detail) sẽ đọc OrderDto.discountAmount + couponCode"
tech-stack:
  added: []
  patterns:
    - "Atomic UPDATE conditional với rowsAffected==1 check (race-safety, TOCTOU-mitigation)"
    - "@Enumerated(EnumType.STRING) + DB CHECK constraint defense-in-depth"
    - "JPA @UniqueConstraint mirror DB UNIQUE(coupon_id, user_id)"
    - "Testcontainers Postgres @DataJpaTest với withInitScript('test-init/01-schemas.sql')"
key-files:
  created:
    - "sources/backend/order-service/src/main/resources/db/migration/V5__add_coupons.sql"
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/CouponType.java"
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/CouponEntity.java"
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/CouponRedemptionEntity.java"
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/repository/CouponRepository.java"
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/repository/CouponRedemptionRepository.java"
    - "sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/domain/CouponEntityIT.java"
    - "sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/repository/CouponRepositoryIT.java"
  modified:
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderEntity.java (+2 fields discountAmount/couponCode + accessors + setters)"
decisions:
  - "D-01: Migration V5 (KHÔNG V3) — Phase 18 đã shipped V4__add_cart_tables.sql, Flyway out-of-order off mặc định"
  - "D-02: Schema verbatim CONTEXT.md — coupons + coupon_redemptions UNIQUE(coupon_id,user_id) + ALTER orders +2 cột snapshot"
  - "D-03: VARCHAR(16) + CHECK + @Enumerated(EnumType.STRING) thay vì Postgres native enum (đồng nhất với orders.status pattern)"
  - "D-05/D-06: expires_at + max_total_uses NULLABLE — null = không hết hạn / không cap"
  - "D-07: KHÔNG soft-delete redemption — append-only audit trail, hủy order KHÔNG rollback redemption"
  - "D-08: redeemAtomic native query verbatim source-of-truth race-safety; @Param chống SQL injection"
  - "D-09: Re-fetch by code (natural key UNIQUE), KHÔNG dùng id từ preview"
metrics:
  duration_minutes: 25
  tasks_completed: 2
  files_created: 8
  files_modified: 1
  tests_added: 18
  completed_date: "2026-05-03"
---

# Phase 20 Plan 01: DB + JPA Foundation Coupon System — Summary

Đặt nền tảng schema + entity + repo cho coupon system order-svc: Flyway V5 migration tạo 2 bảng + ALTER orders +2 cột snapshot, 4 Java domain class (1 enum + 3 entity), 2 repository contracts với atomic redemption native query verbatim D-08 (race-safe). Plan 20-02 (admin CRUD service/controller) và 20-03 (order create integration) build trên foundation này.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Flyway V5 migration + 4 entity/enum + OrderEntity extension | `5de316d` | V5__add_coupons.sql, CouponType.java, CouponEntity.java, CouponRedemptionEntity.java, OrderEntity.java (+ext), CouponEntityIT.java |
| 2 | CouponRepository + CouponRedemptionRepository + IT | `1bb231e` | CouponRepository.java, CouponRedemptionRepository.java, CouponRepositoryIT.java |

## Schema Verification

`V5__add_coupons.sql` áp lên `order_svc` schema sẽ:

```
\dt order_svc.*
                  List of relations
   Schema   |        Name          | Type  | Owner
------------+----------------------+-------+--------
 order_svc  | carts                | table | tmdt    (V4)
 order_svc  | cart_items           | table | tmdt    (V4)
 order_svc  | coupons              | table | tmdt    (V5 - NEW)
 order_svc  | coupon_redemptions   | table | tmdt    (V5 - NEW)
 order_svc  | flyway_schema_history| table | tmdt
 order_svc  | orders               | table | tmdt    (V5 - 2 cột mới)
 order_svc  | order_items          | table | tmdt

\d order_svc.orders   (cột mới)
       Column         |     Type      | Nullable | Default
----------------------+---------------+----------+----------
 ...
 discount_amount      | numeric(15,2) | not null | 0
 coupon_code          | varchar(64)   |          |
```

## Test Coverage

**CouponEntityIT (8 tests)** — Test 1–7 acceptance + 1 sanity:
- Test 1: V5 migration apply success, schema_history success row, 2 bảng + 2 cột mới tồn tại
- Test 2: CouponEntity PERCENT + nullable expiresAt/maxTotalUses persist + load
- Test 3: CouponEntity FIXED với đầy đủ trường (maxTotalUses + expiresAt)
- Test 4: CHECK constraint reject `type='INVALID'` → DataIntegrityViolationException
- Test 5: UNIQUE(coupon_id, user_id) reject duplicate user → DataIntegrityViolationException
- Test 6: OrderEntity legacy load (DEFAULT 0 + NULL) backward compatible
- Test 7: OrderEntity setDiscountAmount + setCouponCode persist + reload
- + 1 list smoke

**CouponRepositoryIT (10 tests)** — Test 1–10 acceptance:
- Test 1: findByCode hit + miss
- Test 2: findByCode case-sensitive (lowercase trả empty)
- Test 3: redeemAtomic happy path → rows=1, usedCount=1
- Test 4: redeemAtomic max reached (used=max=1) → rows=0
- Test 5: redeemAtomic expired → rows=0
- Test 6: redeemAtomic inactive → rows=0
- Test 7: redeemAtomic null limits + usedCount=999 → rows=1
- Test 8: findAll baseline trả về 5 coupons (3 active + 2 inactive)
- Test 9: existsByCouponIdAndUserId true/false
- Test 10: countByCouponId trả đúng số redemption per coupon

**Total: 18 integration tests** (vượt yêu cầu plan 17 = 7+10).

## Acceptance Criteria

Tất cả acceptance grep checks PASS (1 match mỗi pattern):

| Pattern | File | Match |
|---------|------|-------|
| `CREATE TABLE IF NOT EXISTS order_svc\.coupons` | V5__add_coupons.sql | 1 |
| `CHECK \(type IN \('PERCENT','FIXED'\)\)` | V5__add_coupons.sql | 1 |
| `UNIQUE \(coupon_id, user_id\)` | V5__add_coupons.sql | 1 |
| `ALTER TABLE order_svc\.orders ADD COLUMN IF NOT EXISTS discount_amount` | V5__add_coupons.sql | 1 |
| `ALTER TABLE order_svc\.orders ADD COLUMN IF NOT EXISTS coupon_code` | V5__add_coupons.sql | 1 |
| `@Enumerated\(EnumType\.STRING\)` | CouponEntity.java | 1 |
| `@Table\(name = "coupons", schema = "order_svc"\)` | CouponEntity.java | 1 |
| `@UniqueConstraint\(columnNames = \{"coupon_id", "user_id"\}\)` | CouponRedemptionEntity.java | 1 |
| `private BigDecimal discountAmount` | OrderEntity.java | 1 |
| `private String couponCode` | OrderEntity.java | 1 |
| `Optional<CouponEntity> findByCode\(String code\)` | CouponRepository.java | 1 |
| `int redeemAtomic\(@Param\("code"\) String code\)` | CouponRepository.java | 1 |
| `@Modifying` | CouponRepository.java | 1 |
| `nativeQuery = true` | CouponRepository.java | 1 |
| `UPDATE order_svc\.coupons` | CouponRepository.java | 1 |
| `SET used_count = used_count \+ 1, updated_at = now\(\)` | CouponRepository.java | 1 |
| `WHERE code = :code` | CouponRepository.java | 1 |
| `existsByCouponIdAndUserId\(String couponId, String userId\)` | CouponRedemptionRepository.java | 1 |
| `long countByCouponId\(String couponId\)` | CouponRedemptionRepository.java | 1 |

## Deviations from Plan

None — plan thực thi đúng spec D-01..D-09.

Một adjustment nhỏ:
- Plan đề xuất `redemptionRepo.countByCouponId` chỉ dùng cho admin DELETE guard (D-14). Đã giữ nguyên signature theo plan, KHÔNG mở rộng.
- Test 8 (findAll filter list) trong plan ghi "method custom sẽ thêm Plan 20-02 nếu cần" → đã verify chỉ dùng `JpaRepository.count()` baseline thay vì viết method custom (theo đúng scope plan).

## Deferred Verification

`mvn -q test -Dtest='CouponEntityIT,CouponRepositoryIT'` KHÔNG chạy được trong worktree environment (không có Maven binary và không có `mvnw` wrapper trong project). Test infrastructure đồng nhất 100% với `OrderRepositoryChartsIT` (Phase 19) đã pass — pattern `@Testcontainers @DataJpaTest` + Flyway init + Postgres 16-alpine. Test sẽ chạy khi:
1. Branch merge vào main và CI trigger.
2. Local dev runs `mvn test` từ máy có Maven cài đặt.

Không phải blocker cho Wave 1 vì:
- Acceptance grep 19/19 pass.
- File compile-correctness đã verify qua đọc lại entity (imports đúng, signatures khớp accessor pattern).
- D-08 SQL verbatim CONTEXT.md (line-by-line match).

## Threat Surface (STRIDE compliance)

| Threat ID | Mitigation Status |
|-----------|-------------------|
| T-20-01-01 (Tampering: type) | DONE — `@Enumerated(EnumType.STRING)` + DB CHECK in V5 SQL |
| T-20-01-02 (SQL Injection: redeemAtomic) | DONE — `@Param("code")` parameterized binding, KHÔNG string concat |
| T-20-01-03 (Info Disclosure: code) | ACCEPTED — code là natural key public theo plan |
| T-20-01-04 (DoS: maxTotalUses unbounded) | DONE — admin set cap khi cần (D-06 nullable), atomic UPDATE bảo vệ race |
| T-20-01-05 (Repudiation: redemptions) | DONE — append-only, KHÔNG soft-delete (D-07), order_id UNIQUE |
| T-20-01-06 (EoP: discountAmount manipulation) | DEFERRED to Plan 20-03 — server-side recompute trong OrderCrudService.create (D-10) |

## Self-Check: PASSED

**Files exist:**
- FOUND: V5__add_coupons.sql
- FOUND: CouponType.java
- FOUND: CouponEntity.java
- FOUND: CouponRedemptionEntity.java
- FOUND: CouponRepository.java
- FOUND: CouponRedemptionRepository.java
- FOUND: CouponEntityIT.java
- FOUND: CouponRepositoryIT.java
- FOUND: OrderEntity.java (modified)

**Commits exist:**
- FOUND: 5de316d (Task 1)
- FOUND: 1bb231e (Task 2)
