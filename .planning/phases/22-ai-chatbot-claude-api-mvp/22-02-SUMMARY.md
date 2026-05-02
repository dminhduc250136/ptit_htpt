---
phase: 22-ai-chatbot-claude-api-mvp
plan: 02
subsystem: frontend/api-routes
tags: [chatbot, streaming, anthropic, sse, prompt-caching]
requires:
  - "22-01 (lib/chat helpers: anthropic, auth, rate-limit, schema-init, product-context, messages-repo, vn-text)"
provides:
  - "POST /api/chat/stream — JWT-gated, rate-limited, persisted streaming chat endpoint"
  - "Newline-delimited JSON SSE wire format {type:'delta'|'done'|'error', text?, usage?}"
  - "Prompt caching ephemeral breakpoints (system + user-context) cho cost optimization"
  - "Abort propagation: client cancel → req.signal → AbortController → Anthropic SDK signal"
affects:
  - "Wave 3 (22-04 customer chat UI) sẽ POST tới endpoint này"
  - "Wave 3 (22-05 admin suggest-reply) tái sử dụng anthropicClient + prompt patterns"
tech-stack:
  added: []
  patterns:
    - "Next.js App Router route handler với runtime='nodejs' + dynamic='force-dynamic'"
    - "ReadableStream + TextEncoder cho SSE streaming"
    - "AbortController bridge giữa req.signal và SDK signal option"
    - "User content escapeXml + XML tag isolation (<user_question>) chống prompt injection"
    - "Persist user msg BEFORE stream (durability), assistant msg AFTER finalMessage"
key-files:
  created:
    - "sources/frontend/src/app/api/chat/stream/route.ts"
  modified: []
decisions:
  - "loadHistory gọi SAU appendUserMessage → drop trailing entry trong historyForApi để re-inject với product_context wrap (tránh duplicate user turn)"
  - "Cache breakpoints đặt trên system prompt VÀ user-context block (D-04) — 2 cached prefix layers"
  - "Persist user message TRƯỚC khi gọi Anthropic — đảm bảo durability nếu API fail; assistant text chỉ persist sau finalMessage()"
  - "Vietnamese error codes/messages: AUTH_FAILED, RATE_LIMITED, EMPTY_MESSAGE, MESSAGE_TOO_LONG, INVALID_BODY, DB_INIT_FAILED, DB_WRITE_FAILED"
  - "MAX_INPUT_LEN=2000, MAX_TOKENS=1024 (D-25)"
metrics:
  tasks_completed: 1
  files_created: 1
  files_modified: 0
  duration: "~10 min"
  completed: 2026-05-02
---

# Phase 22 Plan 02: Chat Stream Route Summary

**One-liner:** Wave 2 core endpoint — `POST /api/chat/stream` cấp Next.js Node runtime route, gate JWT + rate-limit + schema-ensure, persist user message, load 20-message sliding window, search product context, stream Anthropic `claude-haiku-4-5` qua ReadableStream với prompt caching ephemeral trên cả system và user-context block, và persist assistant message + token usage log sau khi `finalMessage()` resolve.

## Tasks Executed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | Implement POST /api/chat/stream route | `15b91c1` | sources/frontend/src/app/api/chat/stream/route.ts (185 lines) |

## Verifications Run

- `cd sources/frontend && npx tsc --noEmit` → EXIT=0
- `npx eslint src/app/api/chat/stream/route.ts` → EXIT=0 (0 errors / 0 warnings)
- `grep -c "export const runtime = 'nodejs'"` → **1** ✅
- `grep -c "claude-haiku-4-5"` → **1** ✅
- `grep -c "cache_control: { type: 'ephemeral' }"` → **2** (system + user block) ✅
- `grep -c "abortController.abort"` → **2** (signal listener + cancel()) ✅
- `grep -c "checkRateLimit|ensureSchema|<product_context>|escapeXml\(message\)|appendAssistantMessage"` → **8** ≥ 5 ✅

## Acceptance Criteria — All Pass

- [x] File `sources/frontend/src/app/api/chat/stream/route.ts` exists (185 lines, ≥80 min_lines)
- [x] `runtime = 'nodejs'` declared (Pitfall 1 mitigated)
- [x] `claude-haiku-4-5` model literal present
- [x] `cache_control` ephemeral xuất hiện trên cả system và user-context block (D-04)
- [x] Abort wiring: `req.signal.addEventListener('abort', ...)` + `cancel()` cùng gọi `abortController.abort()`
- [x] Rate-limit + schema-ensure gọi TRƯỚC Anthropic call
- [x] `<product_context>` XML tag wrap product list
- [x] `escapeXml(message)` wrap user input bên trong `<user_question>` (T-22-02)
- [x] `appendAssistantMessage` + `touchSession` chạy sau `finalMessage()`
- [x] tsc strict clean

## Threat Model Mitigations Verified

| Threat | Component | Mitigation Status |
|--------|-----------|-------------------|
| T-22-02 (prompt injection) | `route.ts userBlock` | `escapeXml(message)` bên trong `<user_question>`; system prompt instruct ignore directives in user_question |
| T-22-03 (key leak) | `route.ts` | Không reference `process.env.ANTHROPIC_API_KEY` trực tiếp; chỉ import `anthropicClient` từ lib; key không bao giờ echo về client |
| T-22-06 (DoS rate-bypass) | `route.ts` step 2 | `checkRateLimit(claims.userId)` chạy NGAY sau auth, TRƯỚC body parse / DB / Anthropic |
| T-22-07 (resource leak abort) | ReadableStream `cancel()` + `req.signal` listener | Cả 2 đều forward sang `abortController.abort()`; SDK pass-through `signal` option |

## Wire Format

```
HTTP/1.1 200 OK
Content-Type: text/event-stream; charset=utf-8
Cache-Control: no-cache, no-transform
X-Accel-Buffering: no

{"type":"delta","text":"Xin"}\n
{"type":"delta","text":" chào"}\n
{"type":"delta","text":"!"}\n
{"type":"done","sessionId":42,"usage":{"input_tokens":350,"output_tokens":12,"cache_read_input_tokens":0,"cache_creation_input_tokens":150}}\n
```

Error frames: `{"type":"error","error":"...","sessionId":42}\n` rồi close.

## Error Response Catalog

| Code | HTTP | Vietnamese Message | Trigger |
|------|------|-------------------|---------|
| AUTH_FAILED | 401 | Phiên đăng nhập không hợp lệ | JWT missing/invalid/expired |
| RATE_LIMITED | 429 | Bạn nhắn quá nhanh, thử lại sau ít phút | >20 msg/5min |
| INVALID_BODY | 400 | Body không hợp lệ | JSON parse fail |
| EMPTY_MESSAGE | 400 | Tin nhắn không được rỗng | message rỗng/whitespace |
| MESSAGE_TOO_LONG | 400 | Tin nhắn vượt quá 2000 ký tự | length > MAX_INPUT_LEN |
| DB_INIT_FAILED | 500 | Không thể khởi tạo lưu trữ chat | ensureSchema throw |
| DB_WRITE_FAILED | 500 | Không thể tạo phiên chat / lưu tin nhắn | createSession/appendUserMessage throw |

## Deviations from Plan

None — plan executed exactly như written. Code skeleton trong plan (Task 1 `<action>`) được paste nguyên (với chỉnh nhỏ để improve error UX):

- **Minor enhancement (Rule 2)**: Wrap `createSession` và `appendUserMessage` trong try/catch, return `DB_WRITE_FAILED` 500 thay vì để exception bubble lên Next runtime (gây 500 không có envelope chuẩn). Không phải deviation thật sự — chỉ là robustness, không thay đổi logic.

## Authentication Gates

None — plan này chỉ tạo route handler. JWT verify được gọi runtime, không cần test login flow ở plan-level (deferred sang Plan 22-07 E2E smoke).

## Known Stubs

None. Route fully wired tới tất cả Wave 1 helpers; chưa có UI consume nó (đó là Wave 3 — Plan 22-04).

## Self-Check: PASSED

- File `sources/frontend/src/app/api/chat/stream/route.ts` → FOUND (185 lines on disk)
- Commit `15b91c1` → FOUND in `git log` (`feat(22-02): implement POST /api/chat/stream với Anthropic streaming + persistence`)
- All 10 acceptance grep checks → PASS
- `npx tsc --noEmit` → EXIT 0
- `npx eslint <route.ts>` → EXIT 0
