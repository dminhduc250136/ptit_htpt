---
phase: 22
slug: ai-chatbot-claude-api-mvp
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-05-02
---

# Phase 22 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Source: `22-RESEARCH.md > ## Validation Architecture`. See research for full requirement→test rationale.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Playwright `^1.59.1` (E2E, already installed). No jest/vitest in project. |
| **Config file** | `sources/frontend/playwright.config.ts` |
| **Quick run command** | `cd sources/frontend && npx tsc --noEmit && npm run lint` |
| **Full suite command** | `cd sources/frontend && npx playwright test --project=chromium e2e/chatbot-*.spec.ts` |
| **Estimated runtime** | ~10s (quick) / ~2-5min (full chatbot suite) |

**Decision (from research §Test Framework):** Skip jest/vitest install. Use Playwright + `tsc --noEmit` as validation surface. Pure helpers (rate-limit, vn-text, buildContextXml) tested via Playwright in-process or deferred to manual unit if planner sees fit.

---

## Sampling Rate

- **After every task commit:** Run `cd sources/frontend && npx tsc --noEmit && npm run lint`
- **After every plan wave:** Run `cd sources/frontend && npx playwright test e2e/chatbot-*.spec.ts`
- **Before `/gsd-verify-work`:** Full Playwright suite green + 3 manual UAT items below
- **Max feedback latency:** 10s (per task) / 5min (per wave)

---

## Per-Task Verification Map

> Re-anchored 2026-05-02 to actual plans 22-01..22-07 (no plans 08-10 in this phase). Task IDs follow `22-NN-MM` where NN = plan number, MM = task index within plan.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 22-01-01 | 01 | 1 | AI-04 (schema, chat_svc DB) | T-22-04 (IDOR) | user_id NOT NULL, owner-bound rows | tsc + manual psql | `npx tsc --noEmit` + `psql -c "\dt chat_svc.*"` | ✅ | ⬜ pending |
| 22-02-01 | 02 | 1 | AI-02 (lib helpers: anthropic, auth, rate-limit, vn-text) | T-22-02 (prompt injection), T-22-03 (key leak) | escapeXml on user content; API key server-only | unit-via-playwright | `npx playwright test -g "xml escape"` | ❌ W0 | ⬜ pending |
| 22-03-01 | 03 | 1 | AI-03 (product search lib) | — | ILIKE param-bound (no SQL injection via REST) | tsc + manual | `npx tsc --noEmit` | ✅ | ⬜ pending |
| 22-04-01 | 04 | 2 | AI-05 (admin suggest-reply route) | T-22-05 (admin role bypass), T-22-02 | requireAdmin(claims) server-side; escapeXml order data | Playwright E2E | `npx playwright test -g "admin suggest reply"` | ❌ W0 | ⬜ pending |
| 22-05-01 | 05 | 3 | AI-01 (chat stream route), AI-04 (sessions/messages REST + customer service) | T-22-03 (key leak), T-22-04 (IDOR) | API key server-only env, JWT verify per request, owner check on session_id | Playwright E2E | `npx playwright test -g "happy path"` & `-g "history persist"` & `-g "idor"` & `-g "no key leak"` | ❌ W0 | ⬜ pending |
| 22-06-01 | 06 | 3 | AI-05 (admin UI: SuggestReplyModal + admin-chat service) | T-22-05 | Server-side admin guard primary; UI defense-in-depth | Playwright E2E | `npx playwright test -g "admin suggest reply ui"` | ❌ W0 | ⬜ pending |
| 22-06-02 | 06 | 3 | AI-05 (wire button + modal into admin order page) | T-22-05 | Manual confirm disclaimer present (D-07) | Playwright E2E | `npx playwright test -g "admin manual confirm"` | ❌ W0 | ⬜ pending |
| 22-07-01 | 07 | 4 | AI-01 (FloatingChatButton + ChatPanel customer UI) | — | No `dangerouslySetInnerHTML` raw, only via react-markdown sanitized | Playwright E2E | `npx playwright test -g "happy path"` & `-g "markdown render"` & `-g "guest sees login cta"` & `-g "rate limit"` & `-g "vietnamese response"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

*Coverage check: AI-01 → 22-05, 22-07 · AI-02 → 22-02 · AI-03 → 22-03 · AI-04 → 22-01, 22-05 · AI-05 → 22-04, 22-06. All requirements covered.*

---

## Wave 0 Requirements

- [ ] `sources/frontend/e2e/chatbot-customer.spec.ts` — stubs for AI-01, AI-03 happy path, AI-04 history persist, sliding window, auto-title
- [ ] `sources/frontend/e2e/chatbot-admin.spec.ts` — stubs for AI-05 (admin suggest-reply happy + manual-confirm assertion)
- [ ] `sources/frontend/e2e/chatbot-edge.spec.ts` — stubs for guest CTA, rate limit (21st msg → 429), key-leak grep, prompt injection (XML escape), markdown render
- [ ] **Test fixture:** Helper `mockChatStream(page, deltaSequence)` using Playwright `route.fulfill()` with `text/event-stream` body — enables deterministic tests without live Anthropic API in CI.
- [ ] **Test fixture:** Seeded user account + valid JWT (reuse existing v1.2 smoke E2E login flow). Add `seedAdminUser()` if not present.
- [ ] **DB cleanup helper:** `truncate chat_svc.chat_messages, chat_svc.chat_sessions` between specs (or use per-spec random user_id).
- [ ] **No vitest install** — Playwright-only.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Prompt cache hit (`cache_read_input_tokens > 0` on 2nd request) | AI-02 | Anthropic API metadata, hard to assert from Playwright | Run 2 chat turns back-to-back, inspect server log line `cache_read_input_tokens=N` (logger emitted in stream route). |
| Stream abort cleanup (close modal mid-stream → server abort within 1s) | AI-01 | Requires server-log inspection + timing | Open chat, send long-response prompt, close modal at first delta, tail FE container log for `[chat] abort signal received`. |
| Vietnamese response quality (no mid-sentence English) | AI-02 | LLM output review, subjective | Run 5 representative queries from §Specifics, read responses for code-switching. |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (3 spec files in `e2e/` + 2 fixtures + DB cleanup helper)
- [x] No watch-mode flags (Playwright runs once per CI job)
- [x] Feedback latency < 10s per task / < 5min per wave
- [x] `nyquist_compliant: true` set in frontmatter
- [x] Per-Task Verification Map re-anchored to actual plans 22-01..22-07 (plans 08-10 do not exist)
- [x] testDir paths corrected to `e2e/` (Playwright config) — no `tests/` references remain

**Approval:** signed 2026-05-02
