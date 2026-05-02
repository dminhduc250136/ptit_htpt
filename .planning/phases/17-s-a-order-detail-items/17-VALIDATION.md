---
phase: 17
slug: s-a-order-detail-items
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-02
---

# Phase 17 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Source: `17-RESEARCH.md` §Validation Architecture.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Playwright `^1.59.1` (E2E only — no Jest/Vitest) + TypeScript compiler + ESLint |
| **Config file** | `sources/frontend/playwright.config.ts` |
| **Quick run command** | `cd sources/frontend && npx tsc --noEmit && npm run lint` |
| **Full suite command** | `cd sources/frontend && npx playwright test e2e/order-detail.spec.ts e2e/admin-orders.spec.ts` |
| **Estimated runtime** | ~10s (quick) / ~30-60s (E2E subset) |

---

## Sampling Rate

- **After every task commit:** Run `npx tsc --noEmit && npm run lint` (~10s)
- **After every plan wave:** Run `npx playwright test e2e/order-detail.spec.ts e2e/admin-orders.spec.ts` (~30-60s)
- **Before `/gsd-verify-work`:** Full Playwright suite green + manual UAT 2 cases (empty order, soft-deleted product)
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 17-01-* | 01 (lib helpers) | 1 | ORDER-01, ADMIN-06 | — | N/A | static (tsc) | `cd sources/frontend && npx tsc --noEmit` | ✅ existing | ⬜ pending |
| 17-02-* | 02 (admin page rewrite) | 2 | ADMIN-06 | — | Type-safe parsing of envelope (xóa `as any`) | static + E2E | `npx tsc --noEmit && npx playwright test e2e/admin-orders.spec.ts` | ✅ extend existing | ⬜ pending |
| 17-03-* | 03 (user page extend) | 2 | ORDER-01 | — | Render image + brand từ enriched data | E2E | `npx playwright test e2e/order-detail.spec.ts -g "ORD-DTL-2"` | ✅ extend existing | ⬜ pending |
| 17-04-* | 04 (E2E extend) | 3 | ORDER-01, ADMIN-06 | — | KHÔNG còn placeholder string | E2E | Same Playwright commands above | ❌ W0 — extend assertion | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Extend `sources/frontend/e2e/admin-orders.spec.ts` — thêm assertion `expect(page).not.toContainText('khả dụng sau khi Phase 8')` cho `/admin/orders/[id]` (REQ ADMIN-06).
- [ ] Extend `sources/frontend/e2e/order-detail.spec.ts` ORD-DTL-2 — thêm assertion table có ≥1 row + (nice-to-have) thumbnail `<img>` xuất hiện (REQ ORDER-01).
- [ ] Document trong SUMMARY.md các manual UAT cases (empty items + soft-deleted product) cần human verify.

*KHÔNG cần install test framework mới — visible-first defer Jest/Vitest. Playwright + tsc + ESLint đủ.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Empty items state hiển thị "Đơn hàng không có sản phẩm" | ORDER-01 D-05 | KHÔNG có legacy order rỗng trong test data; mock thêm seed phá data baseline | (a) Tạo order qua API gateway với items=[] (admin POST `/admin/orders`) HOẶC (b) Force render bằng tạm thời `order.items = []` trong devtools, screenshot |
| Soft-deleted product → fallback placeholder image + brand "—" | D-01 fallback | Async race với `Promise.allSettled` khó assert deterministic | Soft-delete 1 product trong DB rồi xem order chứa product đó tại `/admin/orders/[id]` — verify thumbnail là placeholder div + brand text "—" |
| Brand subtitle `"—"` khi product.brand null | D-01 fallback | Phụ thuộc seed data brand nullable | Visual check sample 5 products tại catalog → tìm 1 product brand null (nếu có) → check order chứa nó hiển thị "—" |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify (tsc/lint/Playwright) hoặc Wave 0 dependencies (extend E2E)
- [ ] Sampling continuity: tsc/lint chạy sau MỌI task commit (zero gap)
- [ ] Wave 0 covers các MISSING references (Playwright assertions extend)
- [ ] No watch-mode flags (Playwright + tsc one-shot)
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter sau khi planner xác nhận coverage

**Approval:** pending
