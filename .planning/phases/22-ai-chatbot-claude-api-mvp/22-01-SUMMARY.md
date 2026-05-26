---
phase: 22-ai-chatbot-claude-api-mvp
plan: 01
subsystem: frontend/lib-chat
tags: [chatbot, foundations, deps, env, lib]
requires: []
provides:
  - "Singleton pg.Pool (chatPgPool) survives Next.js HMR"
  - "ensureSchema() — idempotent chat_svc schema init (user_id VARCHAR(36))"
  - "verifyJwtFromRequest() / requireAdmin() — jose HS256 auth helper"
  - "checkRateLimit() — 20msg/5min sliding window per user"
  - "anthropicClient + SYSTEM_PROMPT_VN (Vietnamese persona, anti-injection)"
  - "searchProductsForContext() + buildContextXml() — product grounding"
  - "messages-repo — createSession / loadHistory / append* / list* with owner check"
affects:
  - "Wave 2 routes (22-02, 22-03, 22-04) can now import lib/chat/* contracts directly"
tech-stack:
  added:
    - "@anthropic-ai/sdk@^0.92.0 (production dep)"
    - "pg@^8.20.0 + @types/pg@^8.20.0"
    - "jose@^5.10.0 (HS256 JWT verify)"
    - "react-markdown@^10.1.0 (assistant message rendering, used in Wave 3)"
  patterns:
    - "globalThis-cached singleton (anti-HMR-leak)"
    - "to_regclass guard for idempotent schema init"
    - "XML tag isolation + escapeXml for prompt-injection mitigation"
key-files:
  created:
    - "sources/frontend/.env.local.example"
    - ".env.example"
    - "sources/frontend/src/lib/chat/pg.ts"
    - "sources/frontend/src/lib/chat/schema-init.ts"
    - "sources/frontend/src/lib/chat/auth.ts"
    - "sources/frontend/src/lib/chat/rate-limit.ts"
    - "sources/frontend/src/lib/chat/vn-text.ts"
    - "sources/frontend/src/lib/chat/anthropic.ts"
    - "sources/frontend/src/lib/chat/types.ts"
    - "sources/frontend/src/lib/chat/product-context.ts"
    - "sources/frontend/src/lib/chat/messages-repo.ts"
    - ".planning/phases/22-ai-chatbot-claude-api-mvp/deferred-items.md"
  modified:
    - "sources/frontend/package.json"
    - "sources/frontend/package-lock.json"
    - "sources/frontend/.gitignore"
    - "docker-compose.yml"
decisions:
  - "user_id VARCHAR(36) (D-19 corrected) — matches user_svc.users.id type, not BIGINT"
  - "ANTHROPIC_API_KEY server-only, no NEXT_PUBLIC_ prefix (D-26)"
  - "Singleton pg.Pool via globalThis cache — survives Next.js dev HMR"
  - "In-memory rate-limit Map (D-24) — single-instance MVP, threat T-22-06 accepted"
metrics:
  tasks_completed: 3
  files_created: 12
  files_modified: 4
  duration: "~25 min"
  completed: 2026-05-02
---

# Phase 22 Plan 01: AI Chatbot Foundations Summary

**One-liner:** Wave 1 foundation — installed Anthropic/pg/jose/react-markdown deps, scaffolded env vars across docker-compose + .env files, and built 9 server-only `lib/chat/*` helpers (pg singleton, idempotent schema init, JWT verify, rate-limit, VN text utils, Anthropic client, product context, messages repo) that Wave 2 routes will consume.

## Tasks Executed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | Install deps + env scaffolding | `c7facb7` | package.json, package-lock.json, .env.local.example, .env.example, docker-compose.yml, .gitignore |
| 2 | Build core lib/chat helpers | `69fb718` | pg.ts, schema-init.ts, auth.ts, rate-limit.ts, vn-text.ts, anthropic.ts, types.ts |
| 3 | Build product-context + messages-repo | `60861da` | product-context.ts, messages-repo.ts, deferred-items.md |

## Verifications Run

- `npm install --save @anthropic-ai/sdk@0.92.0 pg@^8.20.0 jose@^5 react-markdown@^10` → 98 pkgs added cleanly
- `npm install --save-dev @types/pg` → ok
- `node -e "..."` deps presence check → ok
- `npx tsc --noEmit` → 0 errors after each task
- `npx eslint src/lib/chat/` → 0 errors / 0 warnings
- `grep -c "VARCHAR(36)" schema-init.ts` → 1 (user_id correctness)
- `grep -c "to_regclass" schema-init.ts` → 2 (idempotent guard present)
- `grep -c "globalForPg._chatPgPool" pg.ts` → 2 (singleton via globalThis)
- `grep -c "jwtVerify" auth.ts` → 2 / `HS256` → 2 / `requireAdmin` → 1
- `grep -c "buildContextXml" product-context.ts` → 1 / `escapeXml` → 3
- `grep -c "FORBIDDEN" messages-repo.ts` → 2 (owner check enforced)
- `grep -c "LIMIT 20" messages-repo.ts` → 1 (sliding window per D-06)
- `grep -c "user_id = \$1" messages-repo.ts` → 1 (parameterized)
- `grep -c "ANTHROPIC_API_KEY" docker-compose.yml` → 1
- `grep -rn "NEXT_PUBLIC_ANTHROPIC" sources/frontend/` → 0 hits (D-26 enforced)

## Acceptance Criteria — All Pass

- [x] 7 helpers under `sources/frontend/src/lib/chat/` (Task 2) + 2 more (Task 3) = 9 .ts files (incl. types.ts)
- [x] `tsc --noEmit` clean
- [x] `eslint` clean within `lib/chat/`
- [x] docker-compose frontend service has 5 chat env vars (ANTHROPIC_API_KEY, JWT_SECRET, DB_*, API_GATEWAY_URL)
- [x] No `NEXT_PUBLIC_ANTHROPIC_*` anywhere
- [x] `user_id VARCHAR(36)` (NOT BIGINT) in schema-init — discrepancy R1 resolved

## Threat Model Mitigations Verified

| Threat | Component | Mitigation Status |
|--------|-----------|-------------------|
| T-22-03 (info disclosure — API key leak) | `anthropic.ts` | `process.env.ANTHROPIC_API_KEY` server-only; CI grep gate deferred to Plan 07 |
| T-22-02 (prompt injection via XML) | `vn-text.ts` `escapeXml` + `anthropic.ts` system prompt | `escapeXml` used in `buildContextXml`; system prompt explicitly instructs ignoring directives in `<user_question>` |
| T-22-08 (tampering — search injection) | `product-context.ts` | `encodeURIComponent` on keyword; REST-only path |
| T-22-04 (elevation of privilege — read others' chats) | `messages-repo.ts listMessages` | Owner check throws `FORBIDDEN` before returning rows |
| T-22-06 (DoS — rate limit) | `rate-limit.ts` | Accepted MVP (in-memory single-instance Map, 20/5min) |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocker] `.gitignore` `.env*` rule blocks `.env.local.example`**
- **Found during:** Task 1 (after creating frontend `.env.local.example`)
- **Issue:** `sources/frontend/.gitignore` line 34 (`.env*`) made the new example file untracked-and-ignored, so it could not be committed.
- **Fix:** Added negation `!.env.local.example` next to existing `!.env.example` whitelist line.
- **Files modified:** `sources/frontend/.gitignore`
- **Commit:** `c7facb7` (folded into Task 1 commit)

### Pre-existing Out-of-Scope Issues (NOT fixed)

**Pre-existing lint errors in `admin/page.tsx` + `AddressPicker.tsx`** — `react-hooks/set-state-in-effect` rule violations introduced by commit `8af1218` (Phase 11). These caused `npm run lint` (full repo) to exit 1. Per scope-boundary rule, NOT fixed by this plan. Logged in `.planning/phases/22-ai-chatbot-claude-api-mvp/deferred-items.md`. Plan-level acceptance was met by running `npx eslint src/lib/chat/` directly — 0 errors in the files this plan creates.

## Authentication Gates

None — no API calls required during this plan (foundations only; no Anthropic call yet, no JWT request yet).

## Known Stubs

None. All helpers have full bodies; routes will be wired in Wave 2.

## Self-Check: PASSED

All 12 created files verified present on disk; all 3 task commits verified in `git log`.
