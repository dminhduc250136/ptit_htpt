---
phase: 04-frontend-contract-alignment-e2e-validation
plan: 03
subsystem: frontend
status: complete-with-gaps
requirements:
  - FE-01 (gap — runtime contract not aligned)
  - FE-02 (met — error-recovery dispatcher all green)
completed: 2026-04-25
walkthrough_sha: bc5cf680
tags:
  - frontend
  - uat
  - e2e-validation
  - playwright
  - cleanup
  - documentation
dependency_graph:
  requires:
    - 04-01 typed HTTP tier + middleware + codegen pipeline
    - 04-02 error-recovery UI (Banner / Modal / RetrySection / dispatcher) + page rewires
    - docker-compose stack (gateway + 6 services) running locally
  provides:
    - 04-UAT.md phase deliverable (12 rows × Actual Observation × Pass/Fail × Notes) — D-13 satisfied
    - sources/frontend/playwright.config.ts + e2e/uat.spec.ts + e2e/observations.json + 12 PNG screenshots — repeatable audit harness
    - sources/frontend/README.md Phase 4 dev workflow section (Task 1, committed in c6c32d3)
    - documented FE-01 runtime gap → seeds Phase 4.1 gap-closure planning
    - resolved decisions: search/page.tsx deferred to Phase 5 (TODO marker), api.ts kept as-is for admin/* (LEGACY comment), types/index.ts trimmed best-effort
  affects:
    - Phase 4 verification: gsd-verifier expected to return `gaps_found` (FE-01 not met at runtime)
    - Phase 4.1 (recommended): 3 plans to close FE-01 gap + re-run UAT
    - Future backend work: order-service createOrder DTO redesign + product-service DTO enrichment + product-service /products/slug/{slug} 500 fix
tech-stack:
  added:
    - "@playwright/test ^1.59.1 (devDep — runtime never imports it)"
  patterns:
    - "Hybrid 80/20 UAT method: real backend where it works (A1/A3/A4/A5/A6/B1), mock fallback where backend missing (A2 — /auth/* deferred), page.route stubs where backend doesn't yet emit the contract (B2/B3/B4a/B5)"
    - "Per-row screenshot + per-row networked observations.json — audit evidence persists in git history"
    - "Single-worker Playwright config (workers: 1, fullyParallel: false) to keep UAT rows sequential and observable in logs"
key-files:
  created:
    - .planning/phases/04-frontend-contract-alignment-e2e-validation/04-03-SUMMARY.md
    - sources/frontend/playwright.config.ts
    - sources/frontend/e2e/uat.spec.ts
    - sources/frontend/e2e/observations.json
    - sources/frontend/e2e/screenshots/A1.png
    - sources/frontend/e2e/screenshots/A2.png
    - sources/frontend/e2e/screenshots/A3.png
    - sources/frontend/e2e/screenshots/A4.png
    - sources/frontend/e2e/screenshots/A5.png
    - sources/frontend/e2e/screenshots/A6.png
    - sources/frontend/e2e/screenshots/B1.png
    - sources/frontend/e2e/screenshots/B2.png
    - sources/frontend/e2e/screenshots/B3.png
    - sources/frontend/e2e/screenshots/B4a.png
    - sources/frontend/e2e/screenshots/B4b.png
    - sources/frontend/e2e/screenshots/B5.png
  modified:
    - .planning/phases/04-frontend-contract-alignment-e2e-validation/04-UAT.md
    - sources/frontend/.gitignore
    - sources/frontend/package.json
    - sources/frontend/package-lock.json
    - .planning/STATE.md
    - .planning/REQUIREMENTS.md
    - .planning/ROADMAP.md
    - .planning/phases/04-frontend-contract-alignment-e2e-validation/04-WAVE-STATUS.md
decisions:
  - "D-13 satisfied — UAT delivered as committed checklist (12 rows) with Actual Observation + Pass/Fail + per-row PNG screenshot + machine-readable observations.json"
  - "UAT walkthrough run via Playwright headless hybrid 80/20 (not pure manual click-through). User spot-checked 2 visual rows (B1 banner + B2 modal). Both PASS. Hybrid method documented in 04-UAT.md preamble."
  - "Phase 4 NOT marked complete — UAT surfaced FE-01 runtime gap that requires backend work. Status `complete-with-gaps`. Phase verification to be run by gsd-verifier (expected `gaps_found`); follow-up `/gsd-plan-phase 4 --gaps` will produce 4.1 plan structure."
  - "Mock-data audit clean for UAT-path pages (cart, checkout, login, register, products, profile, home) — verified via grep. Residual mocks in admin/*, search/, profile/orders/[id]/ retained per CONTEXT §Deferred Ideas."
  - "Q3 (CONFLICT stock-shortage shape) and Q4 (payment-fail HTTP code) STILL UNRESOLVED — backend doesn't yet emit either. FE dispatcher verified against designed stub shapes only. Real shapes to be captured in Phase 4.1 re-run UAT."
metrics:
  duration_minutes: ~50
  tasks_completed: 2
  files_created: 16
  files_modified: 8
  auto_fixed_deviations: 0
  uat_pass: 7
  uat_fail: 5
  uat_total: 12
---

# Phase 04 Plan 03: UAT walkthrough + cleanup + phase deliverable — Summary

## Outcome

One-liner: Shipped the phase deliverable (`04-UAT.md` with 12 rows × observations + sign-off) via Playwright headless hybrid 80/20 walkthrough; surfaced a documented FE-01 runtime contract gap that prevents Phase 4 from closing as `complete`. FE-02 error-recovery contract is fully verified end-to-end (all 5 dispatcher branches green). Audit harness (`playwright.config.ts` + `e2e/uat.spec.ts` + `e2e/observations.json` + 12 per-row screenshots) is committed as repeatable evidence — Phase 4.1 re-run UAT can re-execute the same suite once backend gaps close.

## Commits

| Task / Step | Hash      | Subject |
|-------------|-----------|---------|
| Task 1 (earlier session) | `c6c32d3` | feat(04-03): add UAT template + mock audit cleanup + Phase 4 README section |
| Step 1 — cleanup + gitignore | `65c29ce` | chore(04-03): cleanup UAT seed artifacts + gitignore Playwright run output |
| Step 2 — Playwright infra + evidence | `08ef751` | test(04-03): add Playwright headless UAT spec + walkthrough evidence (7/12 PASS, 5/12 FAIL) |
| Step 3 — filled UAT.md | `58cfd7b` | docs(04-03): record UAT walkthrough — 7/12 PASS, 5/12 FAIL (FE-01 contract gap) |
| Step 9 — final atomic commit | (this commit) | docs(04-03): complete UAT plan with documented FE-01 gap — phase 4 awaiting verifier |

Branch: `develop`. No uncommitted work after Step 9.

## What was built

### Task 1 — UAT template + audit + README (already committed in `c6c32d3`)

- `04-UAT.md` pre-walkthrough template with frontmatter + 12 rows (A1..A6 happy path, B1..B5 failure cases, B4 split into B4a + B4b for the two T-04-03 / T-04-04 verifications)
- mock-data audit on UAT-path pages — clean (no `@/mock-data` imports in cart, checkout, login, register, products, products/[slug], profile, page.tsx)
- `sources/frontend/README.md` "Phase 4 Dev Workflow" section (gen:api prerequisites, .env.local setup, route protection, error contract)
- `sources/frontend/src/app/search/page.tsx` — TODO marker added (deferred per plan)
- `sources/frontend/src/services/api.ts` — LEGACY comment header
- `sources/frontend/src/types/index.ts` — best-effort cleanup retained

Full Task 1 details in commit `c6c32d3` body and the diff against `8deb64f`.

### Task 2 — UAT walkthrough (this session)

Method: **hybrid 80/20** Playwright headless against live `docker compose up -d` stack + host `next start` build serving on port 3000. Real backend was used wherever it could produce the expected contract; stubs were used wherever backend doesn't yet emit the FE-02 contract (Q3 stock-shortage + Q4 payment-fail + B4a 401 + B5 5xx). User spot-checked 2 visual rows (B1 banner + B2 modal) — both PASS.

Evidence committed:
- `sources/frontend/playwright.config.ts` — chromium, viewport 1280×720, locale vi-VN, single-worker, traces on
- `sources/frontend/e2e/uat.spec.ts` — 12 row test cases (real-backend + mock + stubbed mix)
- `sources/frontend/e2e/observations.json` — machine-readable per-row pass/fail + raw network + storage assertions
- `sources/frontend/e2e/screenshots/{A1..A6, B1..B5, B4a, B4b}.png` — visual audit, 12 files, ~1MB total

## Walkthrough result

**Tally: 7/12 PASS — 5/12 FAIL.**

| ID | Step | Pass/Fail | Why |
|----|------|-----------|-----|
| A1 | Home (real `listProducts`) | **FAIL** | `ProductCard` crashes on `product.category.name` undefined. Backend returns thin DTO. (FE-01 gap) |
| A2 | Register (mock fallback) | PASS | Mock setTimeout flow exercised — `accessToken=mock-access-token`, `auth_present=1`, `userProfile` populated. Confirms /auth/* deferral path still works. |
| A3 | /products list (real) | **FAIL** | Same FE-01 gap as A1 — first product render crashes ProductCard on missing `category.name`. Network call DID succeed (200, 1 product). |
| A4 | /products/[slug] detail + add-to-cart | **FAIL** | Cascade from A3 + backend bug: `/api/products/products/slug/{slug}` returns HTTP 500 INTERNAL_ERROR. Detail page never renders the Add-to-Cart button. See `.planning/debug/products-list-500.md` for root-cause analysis of the same generic 500 mask. |
| A5 | Checkout submit → POST /api/orders | **FAIL** | Backend `OrderUpsertRequest` requires `userId` + `status` (raw entity Upsert DTO) — FE sends `{items, shippingAddress, paymentMethod, note}` (domain command shape). Backend rejects with 400 VALIDATION_ERROR + `fieldErrors=[{field:status},{field:userId}]`. (FE-01 gap) |
| A6 | Profile + listMyOrders | PASS | Profile page renders. `listMyOrders` returns 200. Order list empty (because A5 didn't actually persist). DTO doesn't trip the ProductCard category access. |
| B1 | Checkout submit blank → 400 VALIDATION_ERROR | PASS | **Real backend** triggered the 400. Banner "Vui lòng kiểm tra các trường bị lỗi" visible. Dispatcher mapped `VALIDATION_ERROR` → `setBannerVisible(true)` correctly. (Field-error mapping isn't to address fields because backend complains about userId/status — same FE-01 gap as A5 — but the FE-02 contract is met.) |
| B2 | Stock-shortage CONFLICT (stubbed) | PASS (dispatcher) | Stub returned `details.domainCode='STOCK_SHORTAGE', details.items=[{availableQuantity:2, requestedQuantity:5}]`. Modal title + both buttons visible with exact correct labels. **Q3 unresolved** — backend doesn't yet emit; recommend Phase 4.1 wire `order-service.createOrder` → `inventory-service.reserve`. |
| B3 | Payment failure CONFLICT (stubbed) | PASS (dispatcher) | Stub returned `details.domainCode='PAYMENT_FAILED', reason='CARD_DECLINED'`. Modal title + both buttons visible with exact correct labels. **Q4 unresolved** — payment-service is CRUD-only and not in checkout call chain; recommend Phase 4.1 integrate it. |
| B4a | 401 silent redirect (stubbed) | PASS | Stub `/api/orders/orders*` GET → 401. http.ts cleared BOTH localStorage tokens AND `auth_present` cookie; redirected to `/login?returnTo=%2Fprofile`. T-04-04 verified. |
| B4b | Open-redirect via login returnTo | PASS | Submitted creds with `?returnTo=https://evil.example.com/`. Final URL = `http://localhost:3000/`. T-04-03 verified. |
| B5 | 5xx toast + no auto-retry (stubbed 502) | PASS | Toast "Đã có lỗi, vui lòng thử lại" visible. POST attempts counted: 1 (D-10 verified — no auto-retry on mutations). |

Full per-row Actual Observation + screenshot references: see `04-UAT.md` body tables.

## FE-01 gaps surfaced — the headline finding

Phase 4 SC #1 ("documented endpoints/DTOs surfaced via codegen + typed client + standardized errors") was achieved at **compile-time** (Wave 1 codegen + httpGet/Post wrappers compile clean against the OpenAPI surfaces) but **NOT at runtime**. Three concrete gaps:

### Gap 1 — Product DTO mismatch (thin vs rich)

Backend `product-service` returns:

```json
{
  "id": "...", "name": "Ao thun cotton trang", "slug": "ao-thun-cotton-trang",
  "categoryId": "e6a99377-...", "price": 150000,
  "status": "ACTIVE", "deleted": false,
  "createdAt": "...", "updatedAt": "..."
}
```

Frontend `ProductCard` and the home page consume:

```ts
{
  category: { name: string, slug: string },
  thumbnailUrl: string,
  shortDescription: string,
  rating: number,
  reviewCount: number,
  tags: string[],
  ...all of the above
}
```

`ProductCard.tsx` accesses `product.category.name` — undefined-property access throws at render. Cascading effect: home page (A1) + products list (A3) + the related-products grid on the detail page all crash.

### Gap 2 — Order DTO mismatch (raw entity Upsert vs domain command)

Backend `order-service.createOrder` accepts `OrderUpsertRequest` — a raw Upsert DTO that requires `userId` (should be derived from JWT) + `status` (should default to `PENDING` server-side). Frontend `services/orders.createOrder` sends `{items, shippingAddress, paymentMethod, note}` — a domain command shape per the FE-02 contract. Result: every checkout submit returns 400 VALIDATION_ERROR with `fieldErrors=[{field:'userId'},{field:'status'}]`. **FE-side fix alone is insufficient** — backend must switch to a `CreateOrderCommand` DTO.

### Gap 3 — Backend `/products/slug/{slug}` 500

Deep-link product navigation (e.g., `/products/ao-thun-cotton-trang`) returns HTTP 500 INTERNAL_ERROR from `/api/products/products/slug/{slug}`. Generic body — `.planning/debug/products-list-500.md` already documents that all six service `GlobalExceptionHandler.handleFallback` methods discard the original Throwable, so we cannot see the stack trace. The proximate cause for slug specifically is unknown until the observability fix lands. (Cleanup option: Phase 4.1 fold the slug-endpoint fix together with the Observability fix from products-list-500.md as a single backend chore.)

## FE-02 verified — all green

| Sub-contract | UAT row | Status |
|--------------|---------|--------|
| `VALIDATION_ERROR` → Banner | B1 (real backend 400) | PASS |
| `CONFLICT` (stock) → Stock modal + Cập nhật / Xóa khỏi giỏ | B2 (stubbed) | PASS (dispatcher) |
| `CONFLICT` (payment) → Payment modal + Thử lại / Đổi phương thức | B3 (stubbed) | PASS (dispatcher) |
| `UNAUTHORIZED` (401) → silent redirect + `clearTokens()` | B4a (stubbed) | PASS — T-04-04 verified end-to-end |
| Open-redirect guard via login returnTo | B4b (real) | PASS — T-04-03 verified |
| `INTERNAL_ERROR` / 5xx → toast + no auto-retry on POST | B5 (stubbed 502) | PASS — D-10 verified |

T-04-02 (middleware admit/redirect) was verified production-grade in Wave 1; B4a additionally verifies T-04-04 (stale-token clear) end-to-end.

## Open questions still UNRESOLVED

These are blocked on backend integration work — not on FE.

| # | Question | Status |
|---|----------|--------|
| Q3 | Real CONFLICT stock-shortage response shape — does backend emit `details.domainCode === 'STOCK_SHORTAGE'`, or `Array.isArray(details.items)`, or both, or neither? | UNRESOLVED — backend `order-service.createOrder` does NOT call `inventory-service.reserve` yet. FE dispatcher only verified against the designed stub shape. |
| Q4 | Real payment-failure HTTP code + body — does backend emit 409 CONFLICT, 402 PAYMENT_REQUIRED, 502 BAD_GATEWAY, or something else when the mock declines? | UNRESOLVED — `payment-service` is CRUD-only and not in the checkout call chain. FE dispatcher only verified against the designed stub shape. |

Both will be resolved in Phase 4.1 re-run UAT after backend integration ships.

## Recommended Phase 4.1 plan structure

If user runs `/gsd-plan-phase 4 --gaps` after `gsd-verifier` returns `gaps_found`, suggest planner produce these:

| Plan | Subsystem | Goal |
|------|-----------|------|
| **04.1-01** | backend product-service | Enrich Product DTO (serialize `Category` join → `category.name`/`category.slug`; add fields `thumbnailUrl`, `description`, `shortDescription`, `tags`, `rating`, `reviewCount` with sane defaults if absent in DB). Fix `/products/slug/{slug}` 500 (root-cause + add the Observability logging from `.planning/debug/products-list-500.md` Option A). |
| **04.1-02** | backend order-service | Replace `OrderUpsertRequest` with `CreateOrderCommand` accepting `{items[], shippingAddress, paymentMethod, note}`. Derive `userId` from JWT (or a session header for now). Default `status` to `PENDING` server-side. Update OpenAPI emit so codegen surfaces the new command shape. Optionally wire `order-service.createOrder` → `inventory-service.reserve` to produce the real 409 STOCK_SHORTAGE shape (resolves Q3). |
| **04.1-03** | frontend (re-run UAT) | Re-execute `playwright test e2e/uat.spec.ts` against the new backend (NO stubs for B2/B3/B4a/B5 if backend now emits them). Capture real Q3 + Q4 shapes. Update `04-UAT.md` to a new walkthrough. Expected: 12/12 PASS. |

**Optional:** an extra plan `04.1-00` to ship backend `/auth/login|register|logout` endpoints, which would remove the Wave 2 mock-auth carve-out. This is independent of the FE-01 gap and could also be deferred to Phase 5.

## Decisions made (this plan)

- **D-13 satisfied** — UAT delivered as committed checklist (12 rows) with observations + screenshots + machine-readable JSON. Documented in `04-UAT.md` frontmatter (`status: complete`, `executed_at`, `executed_by`, SHAs).
- **Hybrid 80/20 method documented** — real backend wherever possible; mock fallback for A2 (carry-over deferral); page.route stubs for B2/B3/B4a/B5 (backend doesn't yet emit). User spot-checked B1 + B2 visuals. Future re-runs (Phase 4.1) should aim for "no stubs" once backend gaps close.
- **Phase 4 status downgraded to `complete-with-gaps`** — phase verification will run separately via `gsd-verifier` and is expected to return `gaps_found` for FE-01. The orchestrator will route to `/gsd-plan-phase 4 --gaps` to produce the 4.1 plan list above.
- **`search/page.tsx`** — TODO marker added (Task 1, commit `c6c32d3`); rewire deferred to Phase 5 once search backend contract is finalized.
- **`api.ts` legacy comment** — kept as-is for admin/* pages (Task 1, commit `c6c32d3`); admin/* is documented Phase 5 cleanup candidate per CONTEXT §Deferred Ideas.
- **`types/index.ts`** — best-effort trimmed but no break (Task 1, commit `c6c32d3`); fuller cleanup deferred until all pages migrate to `@/types/api/*.generated`.

## Phase deliverable status

`04-UAT.md` exists with:
- frontmatter (`status: complete`, `executed_at`, `executed_by`, `backend_commit`, `frontend_commit`, `playwright_run`, `screenshots`, `result`)
- 12 rows fully filled (`Step` + `Expected Observation` + `Actual Observation` + `Pass/Fail` + `Notes`)
- §Summary + §What's working + §What's NOT working + §Open questions + §Mock-data audit + §Recommendation
- §Sign-Off all 4 boxes checked

**D-13 mandate met.** This is the phase deliverable.

## Deviations from Plan

### Rule deviations during execution

**None.** No Rule 1 (auto-fix bug), Rule 2 (auto-add missing critical), Rule 3 (auto-fix blocker), or Rule 4 (architectural ask) deviations during this plan's execution.

### Plan vs reality differences (informational, not auto-fixes)

- **Walkthrough was not pure manual click-through.** Plan §Task 2 §how-to-verify described a step-by-step browser-driver walkthrough. We executed it via Playwright headless (per row, captured network + screenshot + storage). User spot-checked 2 visual rows manually (B1 + B2). This is the "hybrid 80/20" method documented in `04-UAT.md` preamble. D-13 is still satisfied because the deliverable is the committed checklist + observations, not the manner of human-vs-headless execution.
- **Plan expected 11 rows, delivered 12.** B4 was split into B4a (401 silent redirect — T-04-04) and B4b (open-redirect guard — T-04-03) because they verify two distinct security threats and need separate observations. Documented in 04-UAT.md frontmatter and template (already in commit `c6c32d3`).
- **5/12 FAIL is expected to translate into Phase 4.1 plan list.** Plan §output line 405 anticipated this path: "No further Phase 4 work expected unless UAT rows fail." UAT rows DID fail; therefore further Phase 4 (4.1) work is the documented next step.

### No auth gates encountered

All work was FE-only + Playwright headless. No CLI / email / 2FA prompts.

## Auth gates during execution

None. Backend `/auth/*` is still deferred (carry-over blocker from Wave 1+2); A2 was executed against the mock fallback and PASSED — confirms the carve-out remains operational.

## Threat verification

| Threat ID | Category | Status | Evidence |
|-----------|----------|--------|----------|
| T-04-02 | Elevation of Privilege (middleware) | **verified** (Wave 1 production smoke + B4a stubbed end-to-end) | `04-UAT.md` row B4a + `04-01-SUMMARY.md` middleware smoke table |
| T-04-03 | Tampering (open-redirect via returnTo) | **verified** (B4b real walk) | `04-UAT.md` row B4b — final URL = `http://localhost:3000/` despite `?returnTo=https://evil.example.com/` |
| T-04-01 | Information Disclosure (XSS via product description) | accept (residual per D-11) | No `dangerouslySetInnerHTML` introduced. Phase 5+ CSP. |
| T-04-04 | Information Disclosure (stale token) | **verified** (B4a stubbed end-to-end) | After 401, both `localStorage.accessToken/refreshToken` AND `auth_present` cookie removed; redirect to `/login?returnTo=%2Fprofile` |
| T-04-05 | Information Disclosure (error copy leak) | **verified** (B1/B2/B3/B5 exact-string match) | All Vietnamese copy strings observed match UI-SPEC literals |

## Self-Check: PASSED

Verified 2026-04-25:

**Files present (16 created, 8 modified):**
- `playwright.config.ts` ✓
- `e2e/uat.spec.ts` ✓
- `e2e/observations.json` ✓
- 12× `e2e/screenshots/*.png` ✓
- `04-UAT.md` (modified) ✓
- `04-03-SUMMARY.md` (this file) ✓
- `.gitignore` (modified) ✓
- `package.json` + `package-lock.json` (modified — `@playwright/test` added) ✓

**Commits present (verified via `git log --oneline`):**
- `c6c32d3` ✓ (Task 1 — earlier session)
- `65c29ce` ✓ (Step 1 — cleanup + gitignore)
- `08ef751` ✓ (Step 2 — Playwright infra + evidence)
- `58cfd7b` ✓ (Step 3 — filled UAT.md)
- Step 9 final commit pending — to be appended after this SUMMARY is written

**UAT deliverable gate:**
- `04-UAT.md` row count: 12 (A1..A6 + B1..B3 + B4a + B4b + B5) ✓
- All 12 rows have non-empty Actual Observation + Pass/Fail ✓
- Frontmatter `status: complete` ✓
- Sign-off 4/4 boxes checked ✓

**Phase 4 success-criteria gate:**
- SC #1 (FE-01 contract alignment) — **NOT met at runtime.** Compile-time achieved Wave 1; runtime gaps documented above.
- SC #2 (E2E shopping flow) — **NOT met end-to-end.** A1 + A3 + A4 + A5 fail per FE-01 gap.
- SC #3 (FE-02 error-recovery clear + recoverable) — **MET.** All 5 dispatcher branches verified (B1 real, B2/B3/B4a/B5 stubbed-but-contract-correct).

**Phase 4 NOT marked complete.** Verifier expected to return `gaps_found`; recommend `/gsd-plan-phase 4 --gaps` to produce 4.1 plan structure listed above.
