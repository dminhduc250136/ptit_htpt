# Phase 17 — Deferred Items

## Pre-existing lint issues (out of scope cho Plan 17-01)

Phát hiện trong khi chạy `npm run lint` cho Plan 17-01 verification — KHÔNG thuộc files mới được tạo (`orderLabels.ts`, `useEnrichedItems.ts`):

| File | Issue | Phase phù hợp |
|------|-------|---------------|
| `src/app/admin/page.tsx:59` | react-hooks/set-state-in-effect (Promise.allSettled trigger setState) | future hardening |
| `src/components/ui/AddressPicker/AddressPicker.tsx:39` | react-hooks/set-state-in-effect (setSelectedId trong useEffect) | future hardening |
| `src/app/admin/orders/page.tsx` | warnings | Plan 17-02 (admin page rewrite) |
| `src/app/admin/orders/[id]/page.tsx` | warnings | Plan 17-02 (admin page rewrite — file sẽ bị refactor) |
| `e2e/admin-orders.spec.ts` | warnings | Phase 17 test extension wave |
| `e2e/global-setup.ts` | warnings | future test infra cleanup |

**Decision:** Per Scope Boundary rule — chỉ auto-fix issues trực tiếp do task hiện tại gây ra. Pre-existing lint warnings/errors trong files khác KHÔNG fix trong Plan 17-01. Plan 17-02 sẽ rewrite `admin/orders/[id]/page.tsx` (cleanup tự nhiên qua refactor).
