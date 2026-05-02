---
phase: 22-ai-chatbot-claude-api-mvp
plan: 05
subsystem: frontend
tags: [chat, ui, sse, streaming, react, markdown]
requires:
  - "22-02 (POST /api/chat/stream — SSE wire D-14)"
  - "22-03 (GET /api/chat/sessions, /api/chat/sessions/[id]/messages)"
  - "lib/chat/* (Claude SDK, sliding-window, env)"
provides:
  - "FloatingChatButton (global FAB + guest CTA)"
  - "ChatPanel (modal-style chat surface)"
  - "useChat hook (SSE consumer + sessions + messages)"
  - "services/chat.ts (REST wrappers cho sessions/messages)"
  - "MessageBubble, QuickReplyChips, ChatComposer, SessionsSidebar"
affects:
  - "src/app/layout.tsx (mounts <FloatingChatButton/> sibling of ConditionalShell)"
tech-stack:
  added:
    - "react-markdown@^10.1.0 (already installed Wave 1) — used for safe assistant rendering"
  patterns:
    - "Stream consumer: fetch().body.getReader() + TextDecoder + buffer accumulator (split on \\n) parsing JSON-line SSE events"
    - "Optimistic UI: append user + empty assistant bubble before stream starts; deltas mutate last bubble"
    - "AbortController per stream; aborted on panel close (T-22-07 mitigation)"
    - "Modal a11y mirrors components/ui/Modal (ESC handler + body scroll lock + role=dialog + aria-modal)"
key-files:
  created:
    - "sources/frontend/src/services/chat.ts"
    - "sources/frontend/src/components/chat/useChat.ts"
    - "sources/frontend/src/components/chat/MessageBubble/MessageBubble.tsx"
    - "sources/frontend/src/components/chat/MessageBubble/MessageBubble.module.css"
    - "sources/frontend/src/components/chat/QuickReplyChips/QuickReplyChips.tsx"
    - "sources/frontend/src/components/chat/QuickReplyChips/QuickReplyChips.module.css"
    - "sources/frontend/src/components/chat/ChatComposer/ChatComposer.tsx"
    - "sources/frontend/src/components/chat/ChatComposer/ChatComposer.module.css"
    - "sources/frontend/src/components/chat/SessionsSidebar/SessionsSidebar.tsx"
    - "sources/frontend/src/components/chat/SessionsSidebar/SessionsSidebar.module.css"
    - "sources/frontend/src/components/chat/ChatPanel/ChatPanel.tsx"
    - "sources/frontend/src/components/chat/ChatPanel/ChatPanel.module.css"
    - "sources/frontend/src/components/chat/FloatingChatButton/FloatingChatButton.tsx"
    - "sources/frontend/src/components/chat/FloatingChatButton/FloatingChatButton.module.css"
  modified:
    - "sources/frontend/src/app/layout.tsx (import + render FloatingChatButton)"
decisions:
  - "Used relative-origin fetch in services/chat.ts (not shared httpGet) because /api/chat/* lives on Next.js, not the Spring api-gateway pointed to by NEXT_PUBLIC_API_BASE_URL"
  - "Aborted in-flight stream on panel close (extra useEffect calling chat.abortStream) — proactive T-22-07 mitigation, originally deferred by plan"
  - "retryLast pops both the user + empty-assistant bubble before re-sending so sendMessage's optimistic append doesn't duplicate"
metrics:
  duration_min: 6
  completed: 2026-05-02
---

# Phase 22 Plan 05: Customer Chat UI Summary

Built the full customer-facing chat surface: a globally-mounted floating button (D-09 with guest fallback to login), a bottom-right anchored ChatPanel (fullscreen on mobile per D-10), a SSE-consuming useChat hook that parses JSON-line stream events (D-14), and a typed services layer for sessions/messages REST. Three quick-reply chips populate the empty state (D-11); a streaming cursor + "Đang trả lời…" indicator handle in-flight UX (D-12); a Vietnamese error banner with "Thử lại" handles failures (D-13). Markdown is rendered exclusively through react-markdown with safe defaults — zero `dangerouslySetInnerHTML` anywhere in the chat tree (T-22-01 mitigation).

## Tasks Completed

| Task | Name | Commit | Key Files |
| ---- | ---- | ------ | --------- |
| 1 | services/chat.ts + useChat hook + 4 child components (MessageBubble, QuickReplyChips, ChatComposer, SessionsSidebar) | `dcbe0ec` | useChat.ts, services/chat.ts, 4 component dirs |
| 2 | ChatPanel + FloatingChatButton + layout.tsx mount | `c2b615f` | ChatPanel.tsx, FloatingChatButton.tsx, app/layout.tsx |

## Acceptance Criteria

All `<acceptance_criteria>` from the plan passed:

- 15 files created/modified as specified.
- `grep ReactMarkdown MessageBubble.tsx` → 3 (≥1 required).
- `grep dangerouslySetInnerHTML chat/` → **0** (T-22-01 gate).
- `grep /api/chat/stream useChat.ts` → 1.
- `grep buf.indexOf useChat.ts` → 1 (buffer accumulator pattern).
- `grep AbortController useChat.ts` → 3 (declaration + per-stream + ref slot).
- `grep "Tư vấn laptop tầm 20 triệu" QuickReplyChips.tsx` → 1.
- `grep maxLength={2000} ChatComposer.tsx` → 1.
- `grep FloatingChatButton layout.tsx` → 2 (import + render).
- `grep aria-modal=\"true\" ChatPanel.tsx` → 1.
- `grep Escape ChatPanel.tsx` → 1.
- `grep data-testid=\"chat-fab\"` and `chat-cta-guest` each → 1.
- `npx tsc --noEmit` → 0 errors.
- `npm run lint` → no NEW errors on chat files (2 pre-existing errors in `app/admin/page.tsx` and `AddressPicker.tsx` are out-of-scope).
- `npm run build` → succeeds (all 24 routes compiled, including new chat dependencies).

## Decisions Made

- **Origin for chat REST:** `services/chat.ts` cannot reuse `httpGet` (which targets the Spring api-gateway via `NEXT_PUBLIC_API_BASE_URL`) because `/api/chat/sessions*` lives on Next.js. Implemented a small `getJson()` helper using relative-path fetch + Bearer token from `services/token.ts` + envelope unwrap. Documented in module header.
- **Abort on close:** Added a second useEffect in ChatPanel that calls `chat.abortStream()` when `open` flips false. This converts the deferred T-22-07 mitigation hinted by the plan into an active mitigation at zero extra cost.
- **retryLast strategy:** Strips both the failed user bubble and its empty assistant bubble before re-sending, so `sendMessage`'s optimistic append produces exactly one new pair (no duplicates).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Acceptance gate `grep dangerouslySetInnerHTML == 0` would have failed because of a JSDoc comment**
- **Found during:** Task 1 verification
- **Issue:** MessageBubble.tsx initial JSDoc explained the mitigation by referencing `dangerouslySetInnerHTML`. Grep counted 1 — the gate requires 0.
- **Fix:** Reworded the comment to "no raw-HTML injection" — same meaning, no false-positive grep hit.
- **Files modified:** `sources/frontend/src/components/chat/MessageBubble/MessageBubble.tsx`
- **Commit:** `dcbe0ec` (folded into Task 1)

**2. [Rule 2 - Critical functionality] Abort stream on panel close**
- **Found during:** Task 2 implementation
- **Issue:** Plan's T-22-07 mitigation said "ChatPanel unmount could be enhanced to call abortStream — defer to Plan 07 if observed leak". This is correctness-adjacent: if a user closes the panel mid-stream, the network read continues and `setMessages` keeps firing on a hidden hook (no-op, but wastes server tokens).
- **Fix:** Added `useEffect(() => { if (!open) chat.abortStream(); }, [open, chat])` so the AbortController triggers immediately on close.
- **Files modified:** `sources/frontend/src/components/chat/ChatPanel/ChatPanel.tsx`
- **Commit:** `c2b615f`

### Out-of-Scope Observations (NOT fixed)

- `src/app/admin/page.tsx:59` and `src/components/ui/AddressPicker/AddressPicker.tsx:39` — pre-existing `react-hooks/set-state-in-effect` ESLint errors. Not introduced by this plan; not in the chat tree.

## Verification Results

```
$ cd sources/frontend && npx tsc --noEmit
(0 errors)

$ npm run lint
✖ 9 problems (2 errors, 7 warnings)   # all pre-existing, NONE in chat/

$ npm run build
✓ Compiled successfully
✓ Generating static pages 19/19
Route (app)
…
├ ƒ /api/chat/sessions
├ ƒ /api/chat/sessions/[id]/messages
├ ƒ /api/chat/stream
…
```

Manual / functional acceptance (deferred to Plan 22-07 E2E):

- FAB renders on all routes except `/login` `/register` (programmatic check via pathname).
- Guest sees "Đăng nhập để chat" linking `/login?next=<encoded>`.
- Authenticated user opens panel → sees sidebar + 3 quick chips.
- Sending a message streams tokens into the assistant bubble (verified by reading API route from 22-02 + wire format).
- ESC + backdrop click both close; body scroll restored.
- Markdown links open in new tab with rel=noopener; images stripped.

## TDD Gate Compliance

`type: execute` plan (not `type: tdd`) — gate sequence not applicable. Each task committed with `feat(22-05)` per conventional-commit style.

## Threat Flags

None — no new threat surface introduced beyond what `<threat_model>` lists. All client-side; auth + ownership enforcement remains in the Plan 22-02/22-03 server routes.

## Self-Check: PASSED

- All 15 listed files exist on disk (verified by `git show --name-only` on `dcbe0ec` + `c2b615f`).
- Both commit hashes resolve in `git log`.
- TS, lint (chat-scoped), and Next.js build all green.
