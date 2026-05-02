---
phase: 17-s-a-order-detail-items
plan: 04
subsystem: e2e
tags: [frontend, e2e, playwright, regression, vietnamese-i18n]

requires:
  - phase: 17
    plan: 02
    provides: "Admin /admin/orders/[id] render thật (placeholder gone, headings 'Thông tin giao hàng' + 'Sản phẩm')"
  - phase: 17
    plan: 03
    provides: "User /profile/orders/[id] items table render với thumbnail+brand"
provides:
  - "E2E regression-guard cho ADMIN-06 (admin detail no-placeholder) + ORDER-01 (items table rows)"
affects: [Phase 18-22 (regression locks giữ cho order detail không bị break khi modify)]

tech-stack:
  added: []
  patterns:
    - "Playwright test.skip graceful khi seed thiếu (assumption A2 RESEARCH)"
    - "toHaveCount(0) assertion cho regression-guard placeholder text removal"
    - "Reuse existing storageState admin/user (KHÔNG đụng global-setup)"

key-files:
  created: []
  modified:
    - sources/frontend/e2e/admin-orders.spec.ts
    - sources/frontend/e2e/order-detail.spec.ts

key-decisions:
  - "ADM-ORD-3 dùng `getByRole('heading', { name: 'Sản phẩm' })` thay vì `getByText` để tránh match trùng với product name trong items rows"
  - "ADM-ORD-3 dùng `getByText('Thông tin giao hàng')` (không filter role heading) — vì có thể là <h3> plain text — match flexible"
  - "ORD-DTL-2 extend KHÔNG assert <img> thumbnail src (Open Question #2 — Promise.allSettled async race khó deterministic, defer manual UAT)"
  - "ORD-DTL-2 extend KHÔNG assert brand text exact ('—' fallback hợp lệ) — chỉ check first row visible"
  - "Phase gate Playwright run: BE microservices không sẵn local → global-setup login fail, test infra gap → tests SKIP at globalSetup level (KHÔNG fail test code). Document UAT pending — KHÔNG fake green."

requirements-completed: [ORDER-01, ADMIN-06]

duration: 4min
completed: 2026-05-02
---

# Phase 17 Plan 04: E2E Regression Gate Summary

**Extend 2 Playwright spec để regression-guard cho Phase 17 fixes — admin-orders.spec.ts thêm ADM-ORD-3 assert `/admin/orders/[id]` KHÔNG còn placeholder "khả dụng sau khi Phase 8" + 2 card headings render; order-detail.spec.ts extend ORD-DTL-2 thêm assertion items table có ≥1 row visible. Locked-in protection chống regression khi Phase 18-22 modify order pages.**

## Performance

- **Duration:** ~4 min
- **Started:** 2026-05-02 (sau Plan 17-03 merge)
- **Completed:** 2026-05-02
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- **ADM-ORD-3 added** (`admin-orders.spec.ts` line 62) — navigation reuse pattern ADM-ORD-2 (storageState admin + click `[aria-label="Xem chi tiết đơn hàng"]` → waitForURL `/\/admin\/orders\/[^/]+$/`). Sau đó assert:
  - `expect(page.getByText('khả dụng sau khi Phase 8')).toHaveCount(0)` — strict regression-guard (text PHẢI gone hoàn toàn).
  - `expect(page.getByText('Thông tin giao hàng')).toBeVisible()` — shipping card render (Plan 17-02 D-04 output).
  - `expect(page.getByRole('heading', { name: 'Sản phẩm' })).toBeVisible()` — items section render với role filter để tránh match product name trùng.
  - Graceful `test.skip(true, 'Chưa có đơn hàng — cần seed trước')` nếu admin orders empty.
- **ORD-DTL-2 extended** (`order-detail.spec.ts` lines 75-80) — append sau 4 column header + 2 info card assertions:
  - `const rowCount = await page.locator('table tbody tr').count()` → if `rowCount > 0` → assert first row visible.
  - Empty state (D-05 `<p>Đơn hàng không có sản phẩm</p>`) cũng valid → skip block, test pass.
  - KHÔNG assert thumbnail `<img>` src (per plan rationale: Promise.allSettled async race khó deterministic — defer manual UAT).
  - KHÔNG assert brand exact ("—" fallback hợp lệ).
- **Test counts giữ nguyên/tăng:** admin-orders.spec.ts 2 → 3 tests (ADM-ORD-1/2/3); order-detail.spec.ts 2 tests (ORD-DTL-1/2) — KHÔNG xóa test cũ.
- **TypeScript compile**: `npx tsc --noEmit` PASS exit 0 cho cả 2 spec.

## Task Commits

1. **Task 1: Append ADM-ORD-3 e2e admin detail no-placeholder + headings** — `a1369b3` (test)
2. **Task 2: Extend ORD-DTL-2 assert items table có ≥1 row** — `971f5bf` (test)

## Files Created/Modified

- `sources/frontend/e2e/admin-orders.spec.ts` (MODIFIED, +22 / -0) — append ADM-ORD-3 test sau ADM-ORD-2.
- `sources/frontend/e2e/order-detail.spec.ts` (MODIFIED, +8 / -0) — append rowCount assertion vào cuối ORD-DTL-2.

## Verification Status

### Static checks (PASS)
- `npx tsc --noEmit` (sources/frontend) → **PASS** exit 0 sau cả 2 task. Spec files compile sạch.

### Acceptance grep checks (8/8 PASS)
- `grep "ADM-ORD-3" admin-orders.spec.ts` → match (line 62) ✓
- `grep "khả dụng sau khi Phase 8" admin-orders.spec.ts` → match ✓
- `grep "toHaveCount(0)" admin-orders.spec.ts` → match ✓
- `grep -c "test('ADM-ORD-" admin-orders.spec.ts` = 3 (≥3) ✓
- `grep "table tbody tr" order-detail.spec.ts` → match (lines 77, 79) ✓
- `grep "Phase 17" order-detail.spec.ts` → match (line 75 comment) ✓
- `grep -c "test('ORD-DTL-" order-detail.spec.ts` = 2 (KHÔNG giảm) ✓
- Heading 'Sản phẩm' assertion dùng `getByRole('heading')` ✓

### Playwright run (DEFERRED — infra gap)

**Run command:** `cd sources/frontend && npx playwright test e2e/admin-orders.spec.ts e2e/order-detail.spec.ts`

**Result:** **DEFERRED — infra gap, NOT test failure.**

`global-setup.ts` line 39 timeout 15s khi cố `loginAndSave` — `page.waitForURL` chờ navigate khỏi `/login` không xảy ra vì backend microservices (user-svc, gateway, postgres) KHÔNG running local lúc execute. Đây là infrastructure prerequisite, không phải bug trong test code mới.

**Manual replay khi infra sẵn:**
```bash
cd sources/backend && docker-compose up -d
# Đợi healthcheck pass cho gateway + user-svc + postgres
cd ../frontend && npm run dev &
# Đợi http://localhost:3000 ready
npx playwright test e2e/admin-orders.spec.ts e2e/order-detail.spec.ts --reporter=line
```

**Confidence test sẽ pass:**
- ADM-ORD-3 selectors đã verified từ Plan 17-02 SUMMARY (line 90: "Thông tin giao hàng match", "Sản phẩm" heading rendered, "khả dụng sau khi Phase 8" count = 0).
- ORD-DTL-2 extension non-breaking: `rowCount > 0` block skip-friendly nếu seed rỗng.
- Visible-first per project memory (`feedback_priority.md`): defer flaky CI test budget — manual UAT đủ closure cho regression spec compile + grep checks.

## Decisions Made

- **ADM-ORD-3 selector dual mode:** `getByText` cho "Thông tin giao hàng" (flex match heading hoặc plain text) + `getByRole('heading', { name: 'Sản phẩm' })` cho "Sản phẩm" (tránh match product name trong items rows). Mismatched intentionally.
- **ORD-DTL-2 extension minimal:** chỉ 1 assertion mới (`first().toBeVisible()` if rowCount > 0). KHÔNG add waitForTimeout — Playwright auto-wait đủ.
- **KHÔNG run Playwright qua sleep retry loop:** infra gap là prerequisite, không phải transient. Document trong SUMMARY thay vì waste cycles.

## Deviations from Plan

### [Rule 3 - Infra prerequisite] Playwright run blocked bởi backend stack chưa start

- **Found during:** Task 2 phase gate verify (`npx playwright test e2e/...`).
- **Issue:** `global-setup.ts` cần `http://localhost:3000` (Next.js) + backend microservices để login user/admin và lưu storageState. Local execute KHÔNG có docker-compose running.
- **Fix:** Document trong SUMMARY UAT pending; KHÔNG attempt fake green. Spec code compile + grep checks đủ regression-guard locked-in.
- **Files modified:** N/A (test code đã commit).
- **Commit:** N/A (chỉ document).
- **Justification per plan KEY:** "Make sure E2E test runnable locally (KHÔNG cần CI green — visible-first defer flaky test budget). Nếu test infra (server start/global-setup) không sẵn → use test.skip với note rationale, KHÔNG fake green." → đã follow đúng (KHÔNG fake, document infra gap).

Không có deviation Rule 1/2/4 — KHÔNG bug pre-existing surface, KHÔNG missing critical functionality, KHÔNG architectural change.

## Visual Check / UAT

**Manual UAT checklist (REQUIRED trước /gsd-verify-work):**

1. **Empty items state** — visit order legacy hoặc force devtools `order.items = []`:
   - Expected: `<p>Đơn hàng không có sản phẩm</p>` render (cả admin + user pages).
   - Plan 17-02/17-03 D-05 implementation locked.

2. **Soft-deleted product fallback** — admin soft-delete 1 product trong DB → visit order chứa product đó tại `/admin/orders/[id]` + `/profile/orders/[id]`:
   - Expected: thumbnail = placeholder `<div>` 64×64 background `var(--surface-container-high)` + emoji 📦.
   - Expected: brand subtitle = "—".
   - Plan 17-01 D-01 fallback (Promise.allSettled).

3. **Brand null product** — visit order chứa product có `brand = null`:
   - Expected: brand subtitle = "—".

4. **Playwright suite full run** — sau khi backend stack up:
   - `npx playwright test e2e/admin-orders.spec.ts e2e/order-detail.spec.ts --reporter=line`
   - Expected: 5 tests (ADM-ORD-1/2/3 + ORD-DTL-1/2) pass HOẶC skip-with-reason. 0 failed.

## Issues Encountered

- **Infra gap (documented as deviation Rule 3):** Backend microservices không running → global-setup login fail → Playwright tests skipped at setup level. Resolved bằng cách document UAT pending thay vì fake green.

## Self-Check: PASSED

**Files:**
- FOUND: sources/frontend/e2e/admin-orders.spec.ts (modified, +22 lines, ADM-ORD-3 ở line 62)
- FOUND: sources/frontend/e2e/order-detail.spec.ts (modified, +8 lines, Phase 17 comment ở line 75)

**Commits:**
- FOUND: a1369b3 (Task 1 — admin-orders.spec.ts ADM-ORD-3)
- FOUND: 971f5bf (Task 2 — order-detail.spec.ts ORD-DTL-2 extend)

## Next Phase Readiness

**Phase 17 COMPLETE** — 4/4 plans done:
- Plan 17-01: lib helpers (`@/lib/orderLabels` + `@/lib/useEnrichedItems`) ✓
- Plan 17-02: admin order detail page rewrite ✓
- Plan 17-03: user order detail page extend ✓
- Plan 17-04: E2E regression gate (this plan) ✓

**Ready for `/gsd-verify-work`** — verifier agent sẽ:
- Confirm regression spec lock-in (grep + tsc đã PASS).
- Re-run Playwright suite sau khi backend stack up (manual UAT case 4).
- Verify 3 manual UAT cases (empty items / soft-deleted product / brand null) qua devtools hoặc seed script.

**Blockers:** None (infra gap documented; non-blocking cho merge code-level).

---
*Phase: 17-s-a-order-detail-items*
*Completed: 2026-05-02*
