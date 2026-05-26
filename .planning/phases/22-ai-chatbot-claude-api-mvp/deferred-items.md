# Phase 22 — Deferred Items

Items discovered during execution but OUT OF SCOPE for this phase. Logged for future cleanup.

## Pre-existing lint errors (discovered during 22-01 Task 3 lint run)

Both errors pre-date Phase 22 (introduced commit `8af1218 feat(11-05)` — AddressPicker / admin page). They are React lint rule violations (`react-hooks/set-state-in-effect`):

| File | Line | Rule |
|------|------|------|
| `sources/frontend/src/app/admin/page.tsx` | 59 | react-hooks/set-state-in-effect (setState inside useEffect body) |
| `sources/frontend/src/components/ui/AddressPicker/AddressPicker.tsx` | 39 | react-hooks/set-state-in-effect (setState inside useEffect body) |

**Action:** Defer — not caused by 22-01 changes. The plan-acceptance criterion `npm run lint exits 0` is documented as a deviation in 22-01-SUMMARY.md. Lint errors in `src/lib/chat/*` (the only files this plan creates) are 0.

Suggested fix when revisited: refactor each effect to use a derived-state pattern (compute during render or use `useMemo`) per https://react.dev/learn/you-might-not-need-an-effect.
