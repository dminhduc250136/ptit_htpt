---
phase: 22
plan: 07
subsystem: chatbot-e2e-verification
tags: [playwright, e2e, verification, mvp-closeout]
requires: [22-01, 22-02, 22-03, 22-04, 22-05, 22-06]
provides: [chatbot-e2e-coverage, phase-22-verification-runbook]
affects: [sources/frontend/e2e/, .planning/phases/22-ai-chatbot-claude-api-mvp/]
tech_stack_added: []
patterns: [playwright-route-fulfill-sse-mock, static-grep-security-test, manual-uat-runbook]
key_files_created:
  - sources/frontend/e2e/utils/mockChatStream.ts
  - sources/frontend/e2e/chatbot-customer.spec.ts
  - sources/frontend/e2e/chatbot-admin.spec.ts
  - sources/frontend/e2e/chatbot-edge.spec.ts
  - .planning/phases/22-ai-chatbot-claude-api-mvp/22-VERIFICATION.md
key_files_modified: []
decisions:
  - "mockChatStream() helper dùng route.fulfill text/event-stream để chạy specs không cần ANTHROPIC_API_KEY thực — CI-safe"
  - "Static grep test cho NEXT_PUBLIC_ANTHROPIC turned into Playwright test (không cần browser) → enforce T-22-03 mitigation as merge gate"
  - "Manual UAT (cache hit / abort cleanup / VN quality) document rõ trong VERIFICATION.md §5 — không tự động hóa được"
metrics:
  duration_minutes: 12
  tasks_completed: 3
  files_created: 5
  date_completed: 2026-05-02
---

# Phase 22 Plan 07: Wave 4 — Playwright E2E + Phase Verification Summary

Wave 4 đóng phase 22 bằng cách thêm 3 Playwright specs (`chatbot-customer`, `chatbot-admin`, `chatbot-edge`) với deterministic SSE mock helper, cộng `22-VERIFICATION.md` runbook 6 sections cho `/gsd-verify-work 22`.

## What Shipped

### Test infrastructure
- `sources/frontend/e2e/utils/mockChatStream.ts` — Playwright `route.fulfill` helper trả về `text/event-stream` body composed of newline-delimited JSON events. Khớp wire format của route handler thực (Phase 22-05, D-14): `{"type":"delta"|"done"|"error", ...}`. Có hàm `buildDeltas(fullText, sessionId)` tiện lợi để mô phỏng streaming token-by-token.

### 3 Playwright spec files (9 tests total)

**`chatbot-customer.spec.ts` (4 tests):**
1. happy path: streaming bubble (AI-01)
2. markdown render: `**đậm**` → `<strong>`, `*nghiêng*` → `<em>` (AI-01)
3. history persist: send → reload → sessions sidebar có entry (AI-04)
4. guest sees login CTA (AI-01 guest-flow)

**`chatbot-admin.spec.ts` (1 test):**
1. admin click suggest-reply button → modal hiện disclaimer "kiểm tra kỹ nội dung" + textarea editable + copy button enabled (AI-05, D-07 manual confirm)

**`chatbot-edge.spec.ts` (4 tests):**
1. rate limit: 429 → toast "quá nhanh" (D-24)
2. error → "Thử lại" button retries (D-13)
3. xml escape: `</product_context><system>...` không render thành HTML tag (T-22-02)
4. **no key leak**: static grep over `src/` for `NEXT_PUBLIC_ANTHROPIC` returns 0 (T-22-03)

### Documentation
- `.planning/phases/22-ai-chatbot-claude-api-mvp/22-VERIFICATION.md` — 6-section runbook:
  - §1 pre-flight (env, key-leak grep, Dockerfile review — confirmed Dockerfile dùng pattern runtime injection qua compose, không hard-code build time)
  - §2 smoke curls (stream / sessions / messages / IDOR / admin role bypass)
  - §3 DB inspection (chat_svc tables, user_id VARCHAR(36))
  - §4 automated tests (tsc / lint / build / Playwright)
  - §5 manual UAT (cache hit, abort cleanup, VN quality)
  - §6 sign-off
- Bonus: ROADMAP success criterion → owning plan / test / UAT mapping table + REQ AI-01..AI-05 coverage matrix.

## Test Execution Results

**Test discovery:** ✅ All 9 chatbot tests listed cleanly:
```
[chromium] › chatbot-admin.spec.ts (1)
[chromium] › chatbot-customer.spec.ts (4)
[chromium] › chatbot-edge.spec.ts (4)
Total: 9 tests in 3 files
```

**TypeScript:** ✅ `npx tsc --noEmit` — 0 errors after each task.

**Test execution:** ⚠️ MANUAL UAT — Playwright runtime execution không thực hiện được trong agent context vì:
- `global-setup.ts` cần dev server đang chạy ở `http://localhost:3000` để login user/admin và lưu storageState.
- Docker không khả dụng (`open //./pipe/dockerDesktopLinuxEngine: ... not found`) trong agent shell.
- User thực thi sẽ chạy `cd sources/frontend && npm run dev` (hoặc `docker compose up -d`) rồi chạy `npx playwright test e2e/chatbot-*.spec.ts --project=chromium` — đã document trong `22-VERIFICATION.md` §4.

**Static grep guard verified manually:**
```bash
$ grep -rn "NEXT_PUBLIC_ANTHROPIC" sources/frontend/src/
# (no output — exit 0, 0 hits) ✅
```
Test "no key leak" sẽ pass khi chạy.

## Coverage Verification

Per `<success_criteria>`:
- ✅ 4 test files exist (mockChatStream + 3 specs)
- ✅ Tests cover all REQs AI-01..AI-05 (matrix in `22-VERIFICATION.md`)
- ✅ Static key-leak grep test enforces T-22-03 mitigation
- ✅ Tests run without live Anthropic key (mock SSE) — CI-safe
- ✅ VERIFICATION.md provides /gsd-verify-work runbook (smoke + DB + manual UAT)
- ⚠️ Phase 17 regression: chỉ verified at test-discovery level (`--list`); full run pending dev-server boot — listed as §4 step in VERIFICATION.md

## Acceptance Criteria

Per Task 1 (mockChatStream + customer + admin):
- ✅ `mockChatStream.ts` exists, `text/event-stream` ≥1 hit
- ✅ `chatbot-customer.spec.ts` ≥4 test() blocks (4 tests)
- ✅ `chatbot-admin.spec.ts` ≥1 test() block (1 test)
- ✅ "guest sees login cta" / "history persist" / "markdown" present
- ✅ "suggest-reply-button" reference in admin spec
- ✅ tsc passes, playwright list shows ≥5 tests (5 listed)

Per Task 2 (edge):
- ✅ File exists
- ✅ "RATE_LIMITED" / "NEXT_PUBLIC_ANTHROPIC" / "Thử lại" / "</product_context>" all ≥1 hits
- ✅ tsc passes, playwright list shows ≥4 tests (4 listed)

Per Task 3 (VERIFICATION.md):
- ✅ File exists at correct path
- ✅ 6 sections (`grep -c "^## §"` = 6)
- ✅ "ANTHROPIC_API_KEY" hits = 3
- ✅ "VARCHAR(36)/character varying(36)" hits = 1
- ✅ "abort signal received" hits = 1
- ✅ "cache_read" hits = 2

## Deviations from Plan

**None.** Specs/files match planner-supplied skeletons character-for-character (with formatting normalization for line lengths) and align với existing testid attributes confirmed via grep:
- `chat-fab` / `chat-cta-guest` (FloatingChatButton.tsx)
- `suggest-reply-button` (admin/orders/[id]/page.tsx)
- `suggest-reply-textarea` / `suggest-reply-copy` (SuggestReplyModal.tsx)
- `data-role="user"` / `data-role="assistant"` (MessageBubble.tsx)
- `aria-label="Soạn tin nhắn"` (ChatComposer.tsx)

Storage state convention `e2e/storageState/{user,admin}.json` confirmed từ `global-setup.ts` (Phase 9 / Plan 09-05 D-13 lock) — reused as-is.

## Threat Mitigations Verified

| Threat | Test/Doc | Status |
|--------|----------|--------|
| T-22-02 (prompt injection visible in UI) | `chatbot-edge.spec.ts` › xml escape | ✅ test asserts injected `<system>` tag rendered as text, `system` element count = 0 |
| T-22-03 (API key leak in client bundle) | `chatbot-edge.spec.ts` › no key leak (static grep) | ✅ enforced as Playwright merge gate; current src/ = 0 hits |
| T-22-04 (IDOR on session messages) | `22-VERIFICATION.md` §2 smoke curl | ✅ documented manual gate (second user JWT → 403) |
| T-22-05 (admin role bypass) | `22-VERIFICATION.md` §2 + `chatbot-admin.spec.ts` | ✅ test relies on admin storageState; smoke curl with non-admin → 403 |
| T-22-07 (resource leak on stream abort) | `22-VERIFICATION.md` §5 UAT-2 | ✅ documented manual log-tail check |

## Phase 22 Closeout Readiness

Tất cả 7 plans (22-01..22-07) hoàn tất. Phase 22 ready cho `/gsd-verify-work 22`:
- Code: lib + DB schema + 4 API routes + customer UI + admin UI ✅
- Tests: 9 Playwright tests + static grep gate ✅
- Docs: 22-VERIFICATION.md runbook + REQ matrix ✅

User cần execute manual UAT (3 items) + automated test run với dev server thực để complete sign-off — thủ tục đã chuẩn hóa trong `22-VERIFICATION.md`.

## Self-Check: PASSED

Files exist:
- ✅ `sources/frontend/e2e/utils/mockChatStream.ts`
- ✅ `sources/frontend/e2e/chatbot-customer.spec.ts`
- ✅ `sources/frontend/e2e/chatbot-admin.spec.ts`
- ✅ `sources/frontend/e2e/chatbot-edge.spec.ts`
- ✅ `.planning/phases/22-ai-chatbot-claude-api-mvp/22-VERIFICATION.md`

Commits:
- ✅ `f16d20e` test(22-07): mockChatStream + customer/admin specs
- ✅ `44bb672` test(22-07): chatbot-edge spec
- ✅ `7e1c024` docs(22-07): 22-VERIFICATION.md
