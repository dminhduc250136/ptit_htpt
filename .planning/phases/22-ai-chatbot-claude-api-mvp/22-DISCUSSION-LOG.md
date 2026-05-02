# Phase 22: AI Chatbot Claude API MVP - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-02
**Phase:** 22-ai-chatbot-claude-api-mvp
**Mode:** `--auto --chain` (Claude auto-selected recommended option per question; user reviews CONTEXT.md before plan/execute)
**Areas discussed:** Chat UI Placement & States, Streaming Wire Format, Context Injection, DB Schema, API Route Shape, Cost & Safety Guards

---

## Inherited Locks (không re-discuss — từ STATE.md v1.3 locks)

| Lock | Value |
|------|-------|
| Provider / Model | Anthropic Claude API, `claude-haiku-4-5`, SDK `@anthropic-ai/sdk@0.92.0` |
| Streaming | Native `ReadableStream` (KHÔNG Vercel AI SDK) |
| Persistence | `chat_svc` schema, raw `pg` driver trong Next.js route, KHÔNG Flyway |
| Prompt caching | Bắt buộc từ ngày 1, `cache_control: ephemeral` |
| Auth gate | Login required (no guest) |
| History window | Sliding 10 turns |
| Admin suggest-reply | Manual confirm, KHÔNG auto |
| Agentic tools | KHÔNG (out of scope MVP) |

---

## Chat UI Placement & States

| Option | Description | Selected |
|--------|-------------|----------|
| FloatingChatButton mount global ở `app/layout.tsx` qua client wrapper | 1 chỗ mount, có cả guest fallback | ✓ (recommended) |
| Mount per-route (chỉ hiển thị trên `/products`, `/checkout`) | Hạn chế chat trang admin | |
| Mount qua provider riêng | Overkill cho MVP | |

**Auto-selected:** Global mount (D-09).
**Notes:** Modal reuse `components/ui/Modal` (D-10). Empty state với 3 quick-reply chips (D-11). Loading inline indicator (D-12). Error toast + retry button (D-13).

---

## Streaming Wire Format

| Option | Description | Selected |
|--------|-------------|----------|
| SSE-style chunks (`text/event-stream`, JSON events `{type, text}`) | Dễ debug, dễ extend metadata | ✓ (recommended) |
| Plain text streaming (raw bytes) | Đơn giản nhất nhưng khó extend error path | |
| WebSocket | Overkill, MVP không cần bidirectional | |

**Auto-selected:** SSE-style JSON events (D-14, D-15).

---

## Context Injection (Top-N products)

| Option | Description | Selected |
|--------|-------------|----------|
| Keyword ILIKE match trên name/brand/category, top 5 + fallback bán chạy | Đơn giản, không cần infra mới | ✓ (recommended) |
| Embedding/semantic search (pgvector) | Cần migration + cost cao MVP | |
| Full-text search (Postgres `tsvector`) | Trung gian — vẫn cần migration tiếng Việt | |

**Auto-selected:** Keyword ILIKE (D-16).
**Notes:** Inject vào user message với XML tag `<product_context>` (D-17). Query qua product-service REST, không cross-schema (D-18).

---

## DB Schema — `chat_svc`

| Option | Description | Selected |
|--------|-------------|----------|
| 2 bảng (`chat_sessions`, `chat_messages`) BIGSERIAL PK, soft FK user_id | Match REQ-AI-04 chuẩn | ✓ (recommended) |
| Single denormalized table `chat_history` JSONB | Khó query lịch sử + index | |
| UUID PK | Defer — convention dự án dùng BIGSERIAL | |

**Auto-selected:** 2 bảng BIGSERIAL (D-19). Title auto-generate từ message đầu (D-20). Idempotent init qua `to_regclass` 1 lần/process (D-21).

---

## API Route Shape

| Option | Description | Selected |
|--------|-------------|----------|
| 4 routes: `/api/chat/stream`, `/api/chat/sessions`, `/api/chat/sessions/[id]/messages`, `/api/admin/orders/[id]/suggest-reply` | Tách stream khỏi REST CRUD | ✓ (recommended) |
| 1 mega route với action param | Khó extend, vi phạm REST | |
| GraphQL endpoint | Out of scope dự án | |

**Auto-selected:** 4 routes (D-22). Auth tái dùng helper hoặc decode JWT trong route handler (D-23).

---

## Cost & Safety Guards

| Option | Description | Selected |
|--------|-------------|----------|
| Per-user rate limit 20msg/5min in-memory + max input 2000 chars + max_tokens 1024 | Đủ cho MVP single instance | ✓ (recommended) |
| Redis-backed rate limit | Cần infra mới, defer khi có >1 instance | |
| Không rate limit | Rủi ro cost runaway | |

**Auto-selected:** In-memory rate limit + caps (D-24, D-25). Env `ANTHROPIC_API_KEY` server-only (D-26).

---

## Claude's Discretion

- Component folder layout (`components/chat/*` vs `app/_chat/*`) — planner pick theo convention thực tế.
- Markdown render lib — `react-markdown` ưu tiên, MUST sanitize.
- System prompt wording cuối — researcher draft, user duyệt khi review plan.
- Test strategy cho stream — planner quyết.

## Deferred Ideas

Xem `22-CONTEXT.md > <deferred>` — agentic tools, RAG embedding, helpful-vote, multilingual, guest chat, auto-confirm, voice/multimodal, cost dashboard.
