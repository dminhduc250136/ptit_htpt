# Phase 9: Residual Closure & Verification - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-26
**Phase:** 09-residual-closure-verification
**Areas discussed:** Middleware consolidation, Stats endpoints design, Admin dashboard scope, TEST-01 re-baseline strategy, AUTH-07 session UX

---

## Gray Area Selection

| Option | Description | Selected |
|--------|-------------|----------|
| Middleware consolidation | Hai file middleware.ts đang tồn tại (root + src/) | ✓ |
| Stats endpoints design | /api/{products,orders,users}/stats chưa tồn tại | ✓ |
| Admin dashboard scope | totalRevenue + recent orders table + lowStock ngoài 4 KPI | ✓ |
| TEST-01 re-baseline strategy | Suite hiện chỉ có uat.spec.ts (Phase 4) | ✓ |

**User's choice:** Tất cả 4 areas

---

## Middleware consolidation

| Option | Description | Selected |
|--------|-------------|----------|
| Giữ src/, xóa root (Recommended) | Canonical = src/middleware.ts. Mở rộng matcher tại src/. Xóa root. | ✓ |
| Giữ root, xóa src/ | Port logic /403 sang root. | |
| Merge thủ công trước khi quyết định | | |

| Option | Description | Selected |
|--------|-------------|----------|
| Matcher không include /api/* (Recommended) | Đơn giản nhất | ✓ |
| Negative lookahead trong matcher | | |
| Early-return trong middleware function | | |

---

## Stats endpoints design

| Option | Description | Selected |
|--------|-------------|----------|
| Per-svc tự định nghĩa shape (Recommended) | Endpoint trả đúng field UI cần | ✓ |
| Generic {count: N} + multi-call | | |
| Rich payload (extras cho future) | | |

| Option | Description | Selected |
|--------|-------------|----------|
| Admin-only @PreAuthorize ROLE_ADMIN (Recommended) | Consistent với pattern admin CRUD v1.1 | ✓ |
| Authenticated only | | |
| Public (không gate) | | |

| Option | Description | Selected |
|--------|-------------|----------|
| Chỉ PENDING (Recommended) | Rõ ràng nhất — admin thấy đơn cần confirm | ✓ |
| PENDING + SHIPPING | | |
| PENDING + PAID + SHIPPING | | |

---

## Admin dashboard scope

| Option | Description | Selected |
|--------|-------------|----------|
| Trim về đúng 4 KPI (Recommended) | Xóa totalRevenue + recent orders + quick stats | ✓ |
| Giữ extras + wire real data | | |
| Giữ extras nhưng marked TODO | | |

| Option | Description | Selected |
|--------|-------------|----------|
| Per-card independent (Recommended) | Promise.allSettled, mỗi card 3 state | ✓ |
| Page-level all-or-nothing | | |
| Suspense + ErrorBoundary per card | | |

---

## TEST-01 re-baseline strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Rewrite + storageState fixture (Recommended) | Tách auth/admin-products/admin-orders/admin-users/order-detail | ✓ |
| Fix-in-place uat.spec.ts | | |
| Wipe + start fresh | | |

| Option | Description | Selected |
|--------|-------------|----------|
| Auth + admin CRUD + order detail (Recommended) | ~12 tests đúng REQ TEST-01 | ✓ |
| Thêm happy-path shopping flow | | |
| Smoke-only (1 test per flow) | | |

| Option | Description | Selected |
|--------|-------------|----------|
| Manual local docker + observations.json (Recommended) | Pattern v1.1 | ✓ |
| GitHub Actions CI integration | | |
| Local docker + script wrapper | | |

---

## AUTH-07 password change UX

| Option | Description | Selected |
|--------|-------------|----------|
| Giữ session, chỉ toast (Recommended) | JWT cũ vẫn valid | ✓ |
| Force logout + redirect /login | | |
| Rotate JWT + keep session | | |

---

## Claude's Discretion

- Cookie/header (Cache-Control, ETag) cho stats endpoints
- Skeleton component design (CSS shimmer style)
- Error fallback icon/copy
- Zod password schema (min 8, ít nhất 1 letter + 1 number)

## Deferred Ideas

- Token rotation sau password change → future milestone
- Admin dashboard extras (totalRevenue, recent orders, lowStock) → v1.3+
- CI/GitHub Actions Playwright → v1.3+
- Suspense + ErrorBoundary RSC pattern
- Email change verification flow (đã defer trong REQUIREMENTS)
</content>
</invoke>
