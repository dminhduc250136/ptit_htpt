# Phase 20: Hệ Thống Coupon - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-03
**Phase:** 20-coupons
**Mode:** `--auto --chain` (Claude auto-resolved all gray areas with recommended defaults)
**Areas discussed:** Schema, Validation flow, Atomic redemption, Order persistence, Admin CRUD scope, FE checkout UX, Error messaging, Gateway routing

---

## Schema (DB design)

| Option | Description | Selected |
|--------|-------------|----------|
| Use V3 (per ROADMAP) | Out-of-order Flyway, conflict với V4 Phase 18 đã ship | |
| Use V5 (next available) | Forward-only Flyway, an toàn nhất, patch ROADMAP table | ✓ |
| Combine 1 migration | Tất cả schema 1 file | ✓ (implicit) |
| Split into 2 migrations | coupons + alter orders riêng | |

**Auto-selected:** V5 + 1-file migration. **Why:** Flyway default forward-only; Phase 18 đã shipped V4 → V3 fail. 1 file đủ atomic.

---

## Validation flow

| Option | Description | Selected |
|--------|-------------|----------|
| Validate-only-at-checkout | Chỉ validate khi tạo order, không preview | |
| 2-step (preview + atomic) | Preview endpoint riêng + re-validate atomic ở order create | ✓ |
| Real-time validate khi gõ | Debounced validate khi user typing | |

**Auto-selected:** 2-step. **Why:** COUP-03 explicit yêu cầu preview before confirm + re-validate chống TOCTOU. Real-time gõ là premature.

---

## Atomic redemption mechanism

| Option | Description | Selected |
|--------|-------------|----------|
| SELECT-then-UPDATE | Đọc rồi update — race condition unsafe | |
| Optimistic UPDATE conditional + rowsAffected check | Single UPDATE WHERE active+expiry+max_uses + check kết quả | ✓ |
| Pessimistic SELECT FOR UPDATE | Lock row trước khi update | |
| Database advisory lock | pg_advisory_lock theo coupon_id | |

**Auto-selected:** Optimistic UPDATE conditional. **Why:** SQL trong COUP-04 đã specify chính xác pattern này; race-safe, no deadlock, scale tốt.

---

## Per-user usage limit

| Option | Description | Selected |
|--------|-------------|----------|
| Application-level check (SELECT trước) | Race-unsafe | |
| UNIQUE(coupon_id, user_id) ở coupon_redemptions | DB-level enforcement | ✓ |
| Counter ở user metadata | Phức tạp, cross-svc | |

**Auto-selected:** UNIQUE constraint. **Why:** REQUIREMENTS COUP-01 đã chỉ định.

---

## Order discount persistence

| Option | Description | Selected |
|--------|-------------|----------|
| JOIN qua coupon_redemptions cho display | KHÔNG snapshot, mất nếu coupon bị xoá | |
| Snapshot 2 cột (`discount_amount`, `coupon_code`) trên orders | Đơn giản display, bảo toàn lịch sử | ✓ |
| Snapshot full coupon JSON | Quá nhiều data, không cần | |

**Auto-selected:** Snapshot 2 cột. **Why:** SC #4 chỉ cần code + amount; snapshot đơn giản, bảo toàn lịch sử; KHÔNG join cho display.

---

## Admin CRUD scope

| Option | Description | Selected |
|--------|-------------|----------|
| List + Create + Edit | Không có disable/delete | |
| Full CRUD + soft-disable + hard-delete có điều kiện | Soft-disable luôn được; hard-delete chỉ khi used_count=0 | ✓ |
| Full CRUD + hard-delete không điều kiện | Phá lịch sử redemptions | |

**Auto-selected:** Full + soft-disable + conditional hard-delete. **Why:** COUP-02 yêu cầu full lifecycle; conditional delete bảo toàn FK integrity.

---

## FE checkout UX

| Option | Description | Selected |
|--------|-------------|----------|
| Inline section trong checkout single page | Đơn giản, scroll-friendly | ✓ |
| Tách step riêng trong multi-step checkout | Out of scope (multi-step deferred PROJECT.md) | |
| Modal popup | Phức tạp UX cho mã code | |

**Auto-selected:** Inline section. **Why:** Single-page checkout đã có; PROJECT.md "multi-step checkout" defer.

---

## Error messaging

| Option | Description | Selected |
|--------|-------------|----------|
| Generic "Mã không hợp lệ" | UX kém | |
| Specific reason codes (NOT_FOUND/EXPIRED/...) → message Vietnamese | Match SC #1 "lỗi rõ ràng" | ✓ |

**Auto-selected:** Specific reason codes. **Why:** SC #1.

---

## Gateway routing

| Option | Description | Selected |
|--------|-------------|----------|
| 1 route `/api/orders/coupons/**` cho cả user + admin | Mix path patterns | |
| 2 routes user + admin riêng (mirror Phase 19) | Match pattern hiện có | ✓ |

**Auto-selected:** 2 routes. **Why:** Mirror Phase 19 pattern admin-charts.

---

## Race condition verification

| Option | Description | Selected |
|--------|-------------|----------|
| KHÔNG test, dựa vào DB constraint | Risky, SC #3 yêu cầu chứng minh | |
| Integration test 2-thread parallel với cùng coupon | Bằng chứng race-safe | ✓ |
| E2E Playwright multi-tab | Phức tạp, không deterministic | |

**Auto-selected:** Integration test BE. **Why:** SC #3 must pass; deterministic, fast.

---

## Claude's Discretion

- CSS module structure cho coupon section trong checkout (planner quyết).
- Có Playwright E2E hay không (giữ TEST-02 deferred policy — minimum integration test BE).
- Service/repository naming convention.
- JPA mapping nullable types.
- Rate-limit `/validate` endpoint (defer).

## Deferred Ideas

- Coupon stacking, auto-apply, per-product coupon, first-time-buyer, analytics chart, "Mã của tôi" page, rate-limit, rollback used_count khi hủy, multi-currency, code generator UI, email notify expired.
