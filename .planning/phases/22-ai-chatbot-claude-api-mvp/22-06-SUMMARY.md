---
phase: 22-ai-chatbot-claude-api-mvp
plan: 06
subsystem: frontend
tags: [chat, admin, ui, modal, vietnamese, manual-confirm]
requires:
  - "22-04 (POST /api/admin/orders/[id]/suggest-reply — 1-shot Claude VN reply)"
provides:
  - "services/admin-chat.ts fetchSuggestReply(orderId) wrapper (admin-only, NEW file separate from customer services/chat.ts)"
  - "SuggestReplyModal component (editable textarea + Sao chép + manual-review disclaimer)"
  - "Admin order detail extended: 'AI gợi ý phản hồi' button + modal wiring"
affects:
  - "src/app/admin/orders/[id]/page.tsx (Phase 17 baseline extended — Phase 17 line items feature intact)"
tech-stack:
  added: []
  patterns:
    - "Manual-confirm UX (D-07): modal with editable textarea + copy button — NO auto-send button anywhere"
    - "Defense-in-depth: server-side requireAdmin in Plan 04 route is authoritative gate; UI relies on /admin/* middleware route guard"
    - "T-22-01 mitigation: SuggestReplyModal renders AI text via controlled textarea `value` only — never dangerouslySetInnerHTML"
    - "Clipboard API with VN toast fallback: success or 'Trình duyệt không cho phép sao chép tự động'"
    - "Service ownership separation: services/admin-chat.ts (admin) vs services/chat.ts (customer, Plan 05) — avoids same-wave write race"
key-files:
  created:
    - "sources/frontend/src/services/admin-chat.ts"
    - "sources/frontend/src/components/chat/SuggestReplyModal/SuggestReplyModal.tsx"
    - "sources/frontend/src/components/chat/SuggestReplyModal/SuggestReplyModal.module.css"
  modified:
    - "sources/frontend/src/app/admin/orders/[id]/page.tsx (added imports + 4 state slots + 2 handlers + 'Phản hồi khách hàng' card + modal render)"
decisions:
  - "Created services/admin-chat.ts as a dedicated admin file rather than extending services/chat.ts (Plan 05 ownership) — eliminates write-race risk in Wave 3 parallel execution and signals admin/customer concern separation"
  - "SuggestReplyModal is a fresh component (not a reuse of components/ui/Modal) because the textarea-centric UX needs full vertical real estate + monospace styling that the Modal action-row pattern didn't fit cleanly; reused the same a11y idioms (role=dialog + aria-modal + ESC + backdrop click)"
  - "Did NOT add UI-level role gating in admin/orders/[id]/page.tsx — middleware already protects /admin/* and Plan 04 server route enforces requireAdmin (T-22-05). Adding a UI-only role check would duplicate the existing contract"
metrics:
  duration_min: 4
  completed: 2026-05-02
  tasks: 2
  files: 4
  commits: 2
---

# Phase 22 Plan 06: Admin Suggest-Reply UI Summary

One-liner: Admin tại `/admin/orders/[id]` thấy nút "AI gợi ý phản hồi" — click mở modal hiển thị text gợi ý tiếng Việt từ Claude (Plan 04 endpoint) trong textarea editable, có nút "Sao chép" và disclaimer mandate review thủ công (D-07: KHÔNG auto-send).

## Tasks Completed

| Task | Name | Commit | Key Files |
| ---- | ---- | ------ | --------- |
| 1 | Create services/admin-chat.ts (fetchSuggestReply) + SuggestReplyModal component (.tsx + .module.css) | `1e2416c` | services/admin-chat.ts, components/chat/SuggestReplyModal/* |
| 2 | Wire button + modal into admin order detail page | `8aac3e7` | app/admin/orders/[id]/page.tsx |

## Acceptance Criteria

All `<acceptance_criteria>` from the plan passed:

- File `services/admin-chat.ts` exists (NEW — separate from customer chat.ts).
- File `SuggestReplyModal.tsx` + `SuggestReplyModal.module.css` exist.
- `grep fetchSuggestReply services/admin-chat.ts` → 2 (declaration + export).
- `grep "/api/admin/orders/" services/admin-chat.ts` → 2.
- `grep fetchSuggestReply services/chat.ts` → **0** (customer chat.ts untouched — Plan 05 ownership preserved).
- `grep "kiểm tra kỹ" SuggestReplyModal.tsx` → 1 (manual-review disclaimer).
- `grep 'data-testid="suggest-reply-copy"'` → 1.
- `grep 'data-testid="suggest-reply-textarea"'` → 1.
- `grep "AI gợi ý phản hồi" admin/orders/[id]/page.tsx` → 1.
- `grep SuggestReplyModal admin/orders/[id]/page.tsx` → 2 (import + JSX usage).
- `grep fetchSuggestReply admin/orders/[id]/page.tsx` → 2 (import + call).
- `grep "@/services/admin-chat" admin/orders/[id]/page.tsx` → 1 (NOT @/services/chat).
- `grep navigator.clipboard.writeText admin/orders/[id]/page.tsx` → 1.
- `grep 'data-testid="suggest-reply-button"'` → 1.
- `grep useEnrichedItems admin/orders/[id]/page.tsx` → 2 (Phase 17 feature intact, no regression).
- `grep -E "sendReply|autoSend" 22-06 files` → 0 (D-07 honored — no auto-send anywhere).
- `npx tsc --noEmit` → 0 errors.
- `npm run build` → succeeds; `/admin/orders/[id]` route compiles cleanly.

## Decisions Made

- **Dedicated admin-chat.ts file:** Splitting fetchSuggestReply into its own file (instead of extending customer `services/chat.ts`) was a planned T-22-05/concurrency hygiene decision — it eliminates write-race risk if 22-05 and 22-06 ran in parallel, and signals admin-only surface area at the import site.
- **Custom modal vs ui/Modal reuse:** The reusable `components/ui/Modal` is built around a primary/secondary action footer pattern that didn't fit a textarea-dominated body. Built a slim purpose-specific shell that mirrors the same a11y idioms (role=dialog, aria-modal, ESC, backdrop click).
- **No UI role-gating:** `/admin/*` is protected by middleware (`user_role` cookie) and the underlying API enforces `requireAdmin`. A UI-level role check would duplicate the existing contract for no defense-in-depth value beyond what middleware already provides.

## Deviations from Plan

None — plan executed exactly as written. All acceptance gates green on first pass.

## Verification Results

```
$ cd sources/frontend && npx tsc --noEmit
(0 errors)

$ npm run build
✓ Compiled successfully
ƒ /admin/orders/[id]                       (dynamic SSR)
ƒ /api/admin/orders/[id]/suggest-reply     (dynamic API)
…
```

Lint shows 2 pre-existing errors in `app/admin/page.tsx` and `components/ui/AddressPicker/AddressPicker.tsx` — both out-of-scope, documented previously in 22-05 SUMMARY.

Manual / functional verification (deferred to Plan 22-07 E2E):

1. Admin opens `/admin/orders/[id]` → sees "Phản hồi khách hàng" card above "Cập nhật trạng thái" with "AI gợi ý phản hồi" button.
2. Click button → loading state, modal opens.
3. After fetch → editable textarea populated with VN reply from Claude; disclaimer ("⚠️ Vui lòng kiểm tra kỹ nội dung trước khi gửi…") visible.
4. "Sao chép" → clipboard write + success toast.
5. ESC / backdrop / ✕ close modal.
6. Phase 17 line items table + status update card render unchanged.

## TDD Gate Compliance

`type: execute` plan (not `type: tdd`) — RED/GREEN gate sequence not applicable. Each task committed with `feat(22-06)` per conventional-commit style.

## Threat Mitigation Status

| Threat ID | Status | Notes |
|-----------|--------|-------|
| T-22-05 elevation-of-privilege | mitigated (server-side primary) | Plan 04 route enforces requireAdmin; UI button just convenience — non-admin direct URL → middleware redirect; if bypassed → API returns 403 |
| T-22-01 XSS via assistant content | mitigated | SuggestReplyModal textarea uses controlled `value` only; zero `dangerouslySetInnerHTML` in 22-06 file tree |
| T-22-07 resource leak (modal close mid-fetch) | accepted | 1-shot suggest-reply (~2-5s) — closing modal discards result; no stream to abort. Documented in plan threat model. |

## Threat Flags

None — no new threat surface introduced beyond what `<threat_model>` lists. All client-side; auth + role enforcement remains in Plan 22-04 server route.

## Self-Check: PASSED

- FOUND: `sources/frontend/src/services/admin-chat.ts`
- FOUND: `sources/frontend/src/components/chat/SuggestReplyModal/SuggestReplyModal.tsx`
- FOUND: `sources/frontend/src/components/chat/SuggestReplyModal/SuggestReplyModal.module.css`
- FOUND: `sources/frontend/src/app/admin/orders/[id]/page.tsx` (modified)
- FOUND: commit `1e2416c` (Task 1)
- FOUND: commit `8aac3e7` (Task 2)
- TS clean, build green.
