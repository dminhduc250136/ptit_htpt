---
phase: 4
slug: frontend-contract-alignment-e2e-validation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-24
---

# Phase 4 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Derived from `04-RESEARCH.md` §Validation Architecture.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | None installed (no jest / vitest / @testing-library in `sources/frontend/package.json`). Primary validation is the manual UAT checklist (D-13); TypeScript compile via `next build` provides contract type-check enforcement. |
| **Config file** | `sources/frontend/tsconfig.json` (compile check); `.planning/phases/04-frontend-contract-alignment-e2e-validation/04-UAT.md` (manual checklist — produced by P3) |
| **Quick run command** | `cd sources/frontend && npm run build` |
| **Full suite command** | `cd sources/frontend && npm run build && npm run lint` + manual UAT walkthrough |
| **Estimated runtime** | ~30s for `next build` on warm cache; UAT walkthrough ~15–25 min |

---

## Sampling Rate

- **After every task commit:** Run `npm run build` (surfaces type contract drift between `src/types/api/*.generated.ts` and call sites in `services/*` + pages)
- **After every plan wave:** Run `npm run build && npm run lint`
- **Before `/gsd-verify-work`:** Full suite must be green AND `04-UAT.md` rows A1..A6 (happy path) + B1..B5 (failure cases) must all be filled with observations
- **Max feedback latency:** 30 seconds (build) / 25 minutes (UAT gate before phase close)

---

## Per-Task Verification Map

> **Note:** Task IDs are finalized by `/gsd-plan-phase`. Expected plan split from research: `04-01` foundation, `04-02` pages + UI, `04-03` UAT deliverable. Per-task rows below are scoped to requirement buckets the planner must cover; planner fills `Task ID` column when PLAN.md files are written.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| TBD | 04-01 | 1 | FE-01 | T-04-01 | `services/http.ts` unwraps envelope exactly once; throws typed `ApiError` on non-2xx | compile | `npm run build` | ❌ Wave 0 | ⬜ pending |
| TBD | 04-01 | 1 | FE-01 | — | `scripts/gen-api.mjs` produces 6× `src/types/api/*.generated.ts` that compile | compile | `npm run gen:api && npm run build` | ❌ Wave 0 | ⬜ pending |
| TBD | 04-01 | 1 | FE-02 | T-04-02 | `middleware.ts` (or `proxy.ts`) redirects unauthenticated `/checkout`, `/profile`, `/admin/*` to `/login?returnTo=…` via presence-cookie check | manual | UAT B4 — delete token from localStorage, navigate to `/checkout` | ❌ Wave 0 | ⬜ pending |
| TBD | 04-01 | 1 | FE-02 | T-04-03 | `returnTo` query param rejects absolute URLs (open-redirect hardening) | manual | UAT B4 — try `?returnTo=https://evil.example.com/` and verify it falls back to `/` | ❌ Wave 0 | ⬜ pending |
| TBD | 04-02 | 2 | FE-02 | — | Validation banner + inline field errors render from `ApiError.fieldErrors[]` on blank checkout submit | manual | UAT B1 — submit empty checkout form | ❌ Wave 0 | ⬜ pending |
| TBD | 04-02 | 2 | FE-02 | — | Stock-shortage modal opens with affected items and "Cập nhật số lượng" / "Xóa khỏi giỏ" buttons wired | manual | UAT B2 — reduce stock in DB, retry checkout | ❌ Wave 0 | ⬜ pending |
| TBD | 04-02 | 2 | FE-02 | — | Payment-failure modal opens with "Thử lại" / "Đổi phương thức thanh toán" buttons wired | manual | UAT B3 — force payment mock failure | ❌ Wave 0 | ⬜ pending |
| TBD | 04-02 | 2 | FE-02 | — | `INTERNAL_ERROR`/network triggers toast + inline RetrySection; POST/PUT never auto-retry | manual | UAT B5 — stop order-service mid-checkout | ❌ Wave 0 | ⬜ pending |
| TBD | 04-02 | 2 | FE-01/02 | — | `ToastProvider` is mounted in `app/layout.tsx` so `useToast()` calls do not no-op | manual | UAT A2 — observe any toast fires during happy path | ❌ Wave 0 | ⬜ pending |
| TBD | 04-03 | 3 | FE-01/02 | — | `04-UAT.md` A1..A6 (register → browse → add → checkout → payment mock → confirmation) all filled with pass/observation | manual | UAT A1..A6 | ❌ Wave 0 | ⬜ pending |
| TBD | 04-03 | 3 | FE-01/02 | — | `04-UAT.md` B1..B5 (validation, 401 redirect, stock conflict, payment fail, 5xx retry) all filled | manual | UAT B1..B5 | ❌ Wave 0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `sources/frontend/scripts/gen-api.mjs` — npm script runner that fetches each service's `/v3/api-docs` and invokes `openapi-typescript` (required before any call site can typecheck)
- [ ] `sources/frontend/.env.example` (committed) — template declaring `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080` and any codegen env vars
- [ ] `sources/frontend/.env.local` (git-ignored) — developer copy
- [ ] `sources/frontend/src/types/api/` — directory created by first `gen:api` run
- [ ] `ToastProvider` mounted in `sources/frontend/src/app/layout.tsx` — required for `useToast()` to work (today it is defined but not mounted)
- [ ] `sources/frontend/src/providers/AuthProvider.tsx` — not strictly required if per-page `useEffect` reads token, but recommended for header user state + cart badge
- [ ] `04-UAT.md` — committed as part of P3, ships as the phase deliverable

*These items are prerequisites that must exist before per-task automated commands can run green.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Happy-path shopping flow (register → browse → add to cart → checkout → payment mock → confirmation) | FE-01, FE-02 | D-13 explicitly excludes automated E2E; success criterion #2 is `04-UAT.md` | Run docker-compose up; follow `04-UAT.md` rows A1..A6; record observations |
| 401 silent redirect with `returnTo` preservation | FE-02 | Requires localStorage manipulation + routing observation across tabs | UAT row B4: in devtools, `localStorage.removeItem('accessToken')`; navigate to `/profile`; confirm redirect to `/login?returnTo=/profile`; login again; confirm landing back on `/profile` |
| Stock-shortage CONFLICT recovery | FE-02 | Needs backend stock mutation + UI observation | UAT row B2: as admin, reduce a product's stock to below cart quantity; attempt checkout; confirm stock-shortage modal opens with correct item names + current available quantity |
| Payment mock CONFLICT recovery | FE-02 | Needs payment mock to be forced into failure mode | UAT row B3: use whatever mock trigger is available (payment-service force-fail flag or a specific amount) to induce `CONFLICT` during checkout; confirm payment-fail modal + both recovery buttons work |
| 5xx toast + retry (no auto-retry on mutations) | FE-02 | Needs service to be killed mid-flow | UAT row B5: start checkout; stop `order-service` in docker-compose; submit; confirm toast appears, no duplicate POSTs in gateway log, inline RetrySection CTA present; restart service; confirm retry succeeds |
| Validation banner + inline field errors | FE-02 | Needs form state observation | UAT row B1: submit checkout form with blank required fields; confirm both top banner ("Vui lòng kiểm tra các trường bị lỗi") and inline error on each empty field |
| Open-redirect hardening | FE-02, security | Needs URL manipulation | UAT row B4 extension: navigate directly to `/login?returnTo=https://evil.example.com/`; confirm post-login lands on `/` (not the external URL) |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify (compile via `npm run build`) or Wave 0 manual UAT entry
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify — satisfied because each wave ends with a full build
- [ ] Wave 0 covers all MISSING references listed above
- [ ] No watch-mode flags (`next build` is one-shot, not `next dev`)
- [ ] Feedback latency < 30s for build; manual UAT acceptable at phase gate per D-13
- [ ] `nyquist_compliant: true` set in frontmatter once planner fills `Task ID` column

**Approval:** pending (awaiting `/gsd-plan-phase` to finalize task IDs and then phase completion)
