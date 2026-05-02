---
phase: 22
slug: ai-chatbot-claude-api-mvp
status: draft
nyquist_compliant: false
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
| **Full suite command** | `cd sources/frontend && npx playwright test --project=chromium tests/chatbot-*.spec.ts` |
| **Estimated runtime** | ~10s (quick) / ~2-5min (full chatbot suite) |

**Decision (from research §Test Framework):** Skip jest/vitest install. Use Playwright + `tsc --noEmit` as validation surface. Pure helpers (rate-limit, vn-text, buildContextXml) tested via Playwright in-process or deferred to manual unit if planner sees fit.

---

## Sampling Rate

- **After every task commit:** Run `cd sources/frontend && npx tsc --noEmit && npm run lint`
- **After every plan wave:** Run `cd sources/frontend && npx playwright test tests/chatbot-*.spec.ts`
- **Before `/gsd-verify-work`:** Full Playwright suite green + 3 manual UAT items below
- **Max feedback latency:** 10s (per task) / 5min (per wave)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 22-01-* | 01 | 1 | AI-04 (schema) | T-22-01 (XSS via stored content) | Content stored as plain text, sanitized only at render | tsc | `npx tsc --noEmit` | ✅ | ⬜ pending |
| 22-02-* | 02 | 1 | AI-02 (lib helpers) | T-22-02 (prompt injection) | XML-escape user content in `<product_context>` | unit-via-playwright | `npx playwright test -g "xml escape"` | ❌ W0 | ⬜ pending |
| 22-03-* | 03 | 1 | AI-03 (product search) | — | ILIKE param-bound (no SQL injection via REST) | tsc + manual | `npx tsc --noEmit` | ✅ | ⬜ pending |
| 22-04-* | 04 | 2 | AI-01, AI-02 (stream route) | T-22-03 (key leak) | API key server-only env, JWT verify per request | Playwright E2E | `... -g "happy path"` & `-g "no key leak"` | ❌ W0 | ⬜ pending |
| 22-05-* | 05 | 2 | AI-04 (sessions/messages REST) | T-22-04 (IDOR) | Owner check on session_id before list | Playwright E2E | `... -g "history persist"` & `-g "idor"` | ❌ W0 | ⬜ pending |
| 22-06-* | 06 | 2 | AI-05 (admin suggest-reply) | T-22-05 (admin role bypass) | Reuse existing admin guard middleware | Playwright E2E | `... -g "admin suggest reply"` | ❌ W0 | ⬜ pending |
| 22-07-* | 07 | 3 | AI-01 (FloatingChatButton + ChatPanel) | — | No `dangerouslySetInnerHTML` raw, only via react-markdown sanitized | Playwright E2E | `... -g "happy path"` & `-g "markdown render"` | ❌ W0 | ⬜ pending |
| 22-08-* | 08 | 3 | AI-01 guest fallback + AI-04 history UI | — | Guest button has `href=/login?next=…`, no chat state leak | Playwright E2E | `... -g "guest sees login cta"` | ❌ W0 | ⬜ pending |
| 22-09-* | 09 | 4 | AI-05 admin button wiring | — | Button hidden for non-admin (server-side guard primary) | Playwright E2E | `... -g "admin suggest reply"` | ❌ W0 | ⬜ pending |
| 22-10-* | 10 | 4 | Cross — rate limit + key leak + Vietnamese | T-22-03/04/05 | All cross-cutting guards | Playwright E2E | `... -g "rate limit"` & `-g "no key leak"` & `-g "vietnamese response"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

*Note: Plan IDs 01-10 above are seed estimates from RESEARCH §14 — actual plan numbering set by gsd-planner. Map will be re-anchored to real task IDs once plans are created.*

---

## Wave 0 Requirements

- [ ] `sources/frontend/tests/chatbot-customer.spec.ts` — stubs for AI-01, AI-03 happy path, AI-04 history persist, sliding window, auto-title
- [ ] `sources/frontend/tests/chatbot-admin.spec.ts` — stubs for AI-05 (admin suggest-reply happy + manual-confirm assertion)
- [ ] `sources/frontend/tests/chatbot-edge.spec.ts` — stubs for guest CTA, rate limit (21st msg → 429), key-leak grep, prompt injection (XML escape), markdown render
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

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (3 spec files + 2 fixtures + DB cleanup helper)
- [ ] No watch-mode flags (Playwright runs once per CI job)
- [ ] Feedback latency < 10s per task / < 5min per wave
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
