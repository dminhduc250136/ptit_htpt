# Deferred items — Phase 15

## Pre-existing build error (out-of-scope, không do Phase 15)

- **File:** `sources/frontend/src/app/profile/orders/page.tsx`
- **Error:** `useSearchParams() should be wrapped in a suspense boundary` (Next.js 15 prerender bailout)
- **Phát hiện:** Plan 15-02 Task 2 build verification (2026-05-02)
- **Scope:** Phase 11 ProfileOrders page — KHÔNG do PDP changes của Phase 15
- **Workaround:** TypeScript compile (`tsc --noEmit`) passes clean → code correctness verified độc lập
- **Action:** Defer fix sang Phase 11 hardening hoặc v1.3 polish (wrap orders page với `<Suspense>`)
