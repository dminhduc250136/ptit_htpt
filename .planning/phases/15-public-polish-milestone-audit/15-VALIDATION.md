---
phase: 15
slug: public-polish-milestone-audit
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-02
---

# Phase 15 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Playwright `@playwright/test` 1.x (existing Phase 9) + Next.js `next build` (TypeScript + lint compile gate) |
| **Config file** | `sources/frontend/playwright.config.ts` (existing) |
| **Quick run command** | `cd sources/frontend && npm run build` (TypeScript compile + Next build, ~30-60s) |
| **Full suite command** | `cd sources/frontend && npm run test:e2e` (toàn bộ baseline + smoke) |
| **Estimated runtime** | ~30-60s quick (build), ~2-4 min full (Playwright fresh stack) |

---

## Sampling Rate

- **After every task commit:** Run `cd sources/frontend && npm run build` (TS compile + Next build)
- **After every plan wave:** Run `cd sources/frontend && npx playwright test e2e/smoke.spec.ts --reporter=list` (smoke 4 tests, ~30-60s với storageState reuse)
- **Before `/gsd-verify-work`:** Full suite `npm run test:e2e` green + manual visual checklist completed + Lighthouse LCP < 2.5s confirmed
- **Max feedback latency:** 60s (build gate per task), 4 min (full suite phase gate)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 15-W0-01 | wave-0 | 0 | PUB-04 | — | N/A | grep | `grep -E "\-\-success\|\-\-warning" sources/frontend/src/app/globals.css` | ✓ | ⬜ pending |
| 15-W0-02 | wave-0 | 0 | TEST-02 | — | DELIVERED order data exists hoặc strategy locked | grep | `grep -i "DELIVERED" db/init/V100__seed_dev_data.sql` | ✓ | ⬜ pending |
| 15-W0-03 | wave-0 | 0 | TEST-02 | — | AddressPicker selector ổn định | grep | `grep -rn "data-testid\|className.*[Pp]icker" sources/frontend/src/components/ui/AddressPicker/` | ✓ | ⬜ pending |
| 15-W0-04 | wave-0 | 0 | PUB-01 | — | Hero WebP assets sẵn sàng | file exists | `test -f sources/frontend/public/hero/hero-primary.webp && test -f sources/frontend/public/hero/hero-secondary.webp` | ❌ wave-0 creates | ⬜ pending |
| 15-01-XX | 01-homepage | 1 | PUB-01, PUB-02 | — | Hero render + Featured/NewArrivals dedupe | unit + manual visual | `cd sources/frontend && npm run build` + manual checklist | ✓ build / ❌ checklist Wave 0 | ⬜ pending |
| 15-02-XX | 02-pdp | 2 | PUB-03, PUB-04 | — | Breadcrumb + Stock badge + Thumbnail | unit + manual visual | `cd sources/frontend && npm run build` + manual checklist | ✓ build / ❌ checklist Wave 0 | ⬜ pending |
| 15-03-XX | 03-smoke-e2e | 3 | TEST-02 | — | 4 smoke tests PASS fresh stack | E2E | `docker compose up -d --build && cd sources/frontend && npx playwright test e2e/smoke.spec.ts` | ❌ smoke.spec.ts wave-3 creates | ⬜ pending |
| 15-04-XX | 04-milestone-audit | 4 | (closure) | — | Milestone audit completes + tag v1.2 created | manual + git | `/gsd-audit-milestone v1.2` + `git tag --list v1.2` | ❌ wave-4 creates | ⬜ pending |

*Status: ⬜ pending · ✓ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `sources/frontend/e2e/smoke.spec.ts` — covers TEST-02 (4 tests: homepage nav, address-checkout, review submission, profile editing)
- [ ] `sources/frontend/public/hero/hero-primary.webp` — asset cho PUB-01 hero primary image
- [ ] `sources/frontend/public/hero/hero-secondary.webp` — asset cho PUB-01 hero secondary image
- [ ] `sources/frontend/.planning/phases/15-public-polish-milestone-audit/15-MANUAL-CHECKLIST.md` — manual visual smoke list (LCP measurement, Featured/NewArrivals dedupe inspection, thumbnail swap, stock badge 3-tier visual)
- [ ] Verify `--success`/`--warning` tokens exist trong `sources/frontend/src/app/globals.css` — nếu thiếu, plan addition trong `Badge.module.css` Wave 0 với fallback hex `#16a34a / #f59e0b / #dc2626`
- [ ] Confirm DELIVERED order existence cho test #3 (grep `db/init/V100__seed_dev_data.sql`) — nếu không có, decide: (a) test ordering `test.describe.serial` (test 2 → admin update → test 3), (b) seed addition, hoặc (c) admin status update endpoint từ test setup

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| LCP < 2.5s cho hero `<Image priority>` | PUB-01 | Lighthouse cần real browser DevTools, không thể CI tự động ở mức Phase 15 (defer Lighthouse CI v1.3+) | `cd sources/frontend && npm run build && npm start` → mở Chrome DevTools → Lighthouse tab → Mobile preset → Generate report → verify LCP metric < 2500ms |
| Featured + New Arrivals dedupe (no overlap product IDs) | PUB-02 | Visual inspection nhanh hơn unit test cho catalog seed nhỏ | Mở `/`, scroll Featured 8 items + New Arrivals 8 items, verify không product nào lặp lại (so sánh thumbnail/name) |
| PDP thumbnail click → main image swap | PUB-03 | Functional test interaction nhỏ, manual nhanh hơn E2E test phụ | Mở PDP của product có >1 ảnh, click thumbnail thứ 2/3 → verify main image đổi đúng + active border highlight thumbnail đang chọn |
| Stock badge 3-tier color match (green/yellow/red) | PUB-04 | Visual color verification cần human eye, không E2E auto được | Tạo/seed 3 products với `stock` ∈ {0, 5, 100} → mở 3 PDP → verify badge color (green ≥10, yellow 1-9, red 0) + verify add-to-cart button HIDDEN khi stock=0 (không chỉ disabled) |
| PDP breadcrumb `Home > {Brand} > {Name}` | PUB-03 | Visual + click flow check, covered indirectly bởi smoke test #3 nhưng nên manual verify cho 2-3 products khác brand | Mở PDP product có brand → verify breadcrumb đúng format → click brand segment → verify navigate sang `/products?brand={brand}` + FilterSidebar pre-check brand đó |
| CSS scroll-snap carousel touch behavior mobile | PUB-01 | Touch UX cần real device hoặc DevTools mobile mode | Mở `/` trong Chrome DevTools mobile mode → swipe Featured carousel → verify scroll-snap-align bắt từng card đúng |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (`smoke.spec.ts`, hero WebPs, manual checklist doc, token verification)
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s per task commit, < 4 min per wave
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
