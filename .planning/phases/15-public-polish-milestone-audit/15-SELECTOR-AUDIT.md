# Phase 15 — Selector Audit + DELIVERED Strategy

**Audited:** 2026-05-02
**Purpose:** Lock selectors stable cho smoke E2E tests + decide test #3 DELIVERED order data strategy.

## Verified Selectors

### AddressPicker (Smoke #2 — checkout)

- File: `sources/frontend/src/components/ui/AddressPicker/AddressPicker.tsx:109-127`
- Selector chính: `[role="option"]` (verified: `<div role="option" aria-selected={isSelected}>`)
- Playwright usage: `page.locator('[role="option"]').first()`
- Trigger button (mở dropdown nếu cần): `[class*="trigger"]` (line 68)

### ProfileSettings (Smoke #4 — fullName/phone edit)

- File: `sources/frontend/src/app/profile/settings/page.tsx:158,163,239`
- Selectors:
  - Full name input: `#fullName` (line 158)
  - Phone input: `#phone` (line 163)
  - Submit button: fallback `getByRole('button', { name: /lưu|cập nhật|save/i })` — chưa lock `data-testid`
  - Success toast: `[data-testid="successMsg"]` (line 239)

### ReviewSection (Smoke #3 — review submission) — VERIFIED 2026-05-02

- Files:
  - `sources/frontend/src/app/products/[slug]/ReviewSection/StarWidget.tsx:19-22`
  - `sources/frontend/src/app/products/[slug]/ReviewSection/ReviewForm.tsx:65,80`
- Selectors locked:
  - Rating button: `page.getByRole('button', { name: /^5 sao$/ })` (StarWidget render `<button aria-label="{n} sao">` trong `<div role="radiogroup" aria-label="Chọn số sao">`)
  - Content textarea: `page.locator('#review-content')` (textarea id verified line 65-66)
  - Submit button: `page.getByRole('button', { name: /gửi đánh giá/i })` (verified line 80-82, label "Gửi đánh giá")
- Plan 15-03 KHÔNG cần re-grep — selectors lock-down complete.

## DELIVERED Order Strategy (Smoke #3 dependency)

**Status seed:** `db/init/` chỉ có `01-schemas.sql` (CREATE SCHEMA only). KHÔNG có DELIVERED order pre-seeded cho user demo (verified 2026-05-02 bằng grep DELIVERED + ls db/).

**Decision: Strategy A — skip-if-no-data** (precedent `e2e/order-detail.spec.ts:50-53`):

```typescript
const productLink = page.locator('a[href^="/products/"]').first();
const hasProduct = await productLink.isVisible({ timeout: 5000 }).catch(() => false);
if (!hasProduct) {
  test.skip(true, 'Không có product nào — seed empty');
  return;
}
// Mở PDP → check eligibility (verified-buyer)
// Nếu form review không xuất hiện (chưa DELIVERED) → test.skip với message rõ ràng
```

**Lý do chọn Strategy A:**

- D-18 cho phép skip-if-no-data degradation (precedent order-detail.spec.ts).
- Strategy B (test ordering serial place-order → admin update DELIVERED) yêu cầu admin status update endpoint — chưa verified tồn tại + tăng test fragility cao.
- Strategy C (seed DELIVERED order) trái D-18 ("KHÔNG seed mới cho phase 15").

**Trade-off:** Smoke #3 có thể skip trên fresh stack nếu seed không có DELIVERED → vẫn PASS criteria D-19 (PASS hoặc skip với reason rõ ràng — KHÔNG fail). Phase gate accept skip với evidence trong test report.

**Tương lai (defer v1.3):** Add 1 DELIVERED order seed (`V101__seed_delivered_orders.sql` cho dev profile only, KHÔNG production migration) — unblock smoke #3 deterministic.

## Open Items cho Plan 15-03 executor

- [ ] Verify `e2e/storageState/user.json` tồn tại từ global-setup (Phase 9). Nếu không → smoke spec note "TEST-02 phụ thuộc storageState user fixture từ global-setup.ts".
- [ ] Confirm ProfileSettings submit button có `data-testid` hoặc dùng text fallback `getByRole('button', { name: /lưu|cập nhật/i })`.
