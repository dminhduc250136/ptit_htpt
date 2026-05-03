# Phase 22: AI Chatbot Claude API MVP — Research

**Researched:** 2026-05-02
**Domain:** Anthropic Claude API streaming + Next.js 16 App Router API routes + Postgres raw `pg` driver + Vietnamese chat UI
**Confidence:** HIGH (locked decisions inherited; SDK + library versions verified against npm registry; codebase inspected directly)

---

## Summary

Phase 22 thêm chatbot AI MVP cho khách hàng đã đăng nhập (FAQ + Q&A sản phẩm + recommendation tiếng Việt, streaming token-by-token, history persist) và nút "AI suggest reply" trong admin order detail. Toàn bộ traffic Claude API đi qua **Next.js App Router API routes** mới (chưa từng có trong dự án — `sources/frontend/src/app/api/` hiện trống). Schema `chat_svc` init qua raw `pg` driver. Auth verify JWT HS256 server-side với cùng `JWT_SECRET` mà user-service đang dùng.

**Discrepancy critical phát hiện:** CONTEXT.md D-19 spec `chat_sessions.user_id BIGINT`, nhưng `user_svc.users.id` thực tế là `VARCHAR(36)` UUID (verified `V1__init_schema.sql`). **Phải đổi sang `VARCHAR(36)`** hoặc planner bắt buộc surface lại với user. Tất cả existing `Order.userId.slice(0,8)` ở FE confirm UUID-string. Không thể giữ BIGINT.

**Primary recommendation:** Implement theo 4 waves: (1) infrastructure (env + pg pool + schema init + JWT verify helper + product context helper + rate-limit helper), (2) 4 routes (stream + sessions list + messages list + admin suggest-reply), (3) chat UI (FloatingChatButton + ChatPanel với extended Modal variant + markdown render), (4) admin button + Playwright E2E. SDK 0.92.0 verified latest 2026-05-02 trên npm.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Inherited locks (STATE.md v1.3 → CONTEXT.md inherited):**
- **D-01:** Provider Anthropic Claude API; SDK `@anthropic-ai/sdk@0.92.0`; model `claude-haiku-4-5`.
- **D-02:** Streaming UI = native `ReadableStream` + `fetch` body reader, **KHÔNG** Vercel AI SDK.
- **D-03:** Persistence = schema `chat_svc` Postgres dùng chung instance, init qua Next.js API route raw `pg` driver, **KHÔNG** Spring Boot service mới, **KHÔNG** Flyway.
- **D-04:** Prompt caching bắt buộc từ ngày 1 — `cache_control: { type: 'ephemeral' }` trên system prompt + product context block.
- **D-05:** Login required cho cả customer chatbot và admin suggest-reply. Guest thấy nút "Đăng nhập để chat".
- **D-06:** Sliding window = 10 turns (20 messages user+assistant gần nhất).
- **D-07:** Admin "AI suggest reply" KHÔNG auto-confirm — chỉ generate text, admin paste/edit thủ công.
- **D-08:** KHÔNG agentic tool-use / function-calling / RAG embedding.

**Discuss-phase auto-locks:**
- **D-09:** FloatingChatButton render từ `app/layout.tsx` qua client wrapper, hiển thị mọi route. Guest → button "Đăng nhập để chat".
- **D-10:** Modal dùng `components/ui/Modal` (extend variant để bottom-right desktop / full-screen mobile). State open/close `useState`, KHÔNG persist URL.
- **D-11:** Empty state: 3 quick-reply chips (laptop 20tr / iPhone 15 Pro / chuột Logitech vs Razer).
- **D-12:** Loading: input disable, indicator "Đang trả lời…", cursor nhấp nháy ở token cuối.
- **D-13:** Error: toast tiếng Việt + assistant bubble có nút "Thử lại".
- **D-14:** Wire format = SSE-style chunks (`text/event-stream`), JSON event `{type: 'delta'|'done'|'error', text?: string, error?: string}`.
- **D-15:** Backpressure: chỉ flush khi nhận chunk từ SDK, không buffer server.
- **D-16:** Ranking = ILIKE keyword match trên `name + brand + category.name` (lowercase + trim VN diacritics). Top 5, fallback bestsellers nếu match < 3.
- **D-17:** Inject vào user message với XML tag `<product_context>...</product_context><user_question>...</user_question>`, **KHÔNG** vào system prompt (giữ system cacheable).
- **D-18:** Query products qua **product-service REST** (`GET /api/products?keyword=...&size=5`), KHÔNG truy vấn cross-schema.
- **D-19:** Tables `chat_svc.chat_sessions` (id BIGSERIAL PK, user_id BIGINT, title VARCHAR(200), created_at, updated_at, INDEX (user_id, updated_at DESC)) + `chat_svc.chat_messages` (id BIGSERIAL PK, session_id FK CASCADE, role CHECK ('user','assistant'), content TEXT, created_at, INDEX (session_id, created_at)).
  - **⚠ DISCREPANCY:** `user_svc.users.id` thực tế VARCHAR(36) UUID. Xem §13 Risks — phải đổi BIGINT → VARCHAR(36).
- **D-20:** Title auto = 50 ký tự đầu user message đầu tiên + ellipsis.
- **D-21:** Idempotent init: 1 lần `to_regclass('chat_svc.chat_sessions')` mỗi process boot.
- **D-22:** Routes:
  - `POST /api/chat/stream` (text/event-stream)
  - `GET /api/chat/sessions` (paginate `?limit=20&before=...`)
  - `GET /api/chat/sessions/[id]/messages`
  - `POST /api/admin/orders/[id]/suggest-reply` (1-shot JSON, không stream)
- **D-23:** Auth pattern: dùng lại helper từ `lib/auth/*` nếu có, hoặc decode JWT trong route handler — clarify ở §5.
- **D-24:** Rate limit 20 messages / 5 phút, in-memory `Map<userId, timestamps[]>`. Vượt = HTTP 429.
- **D-25:** Max input 2000 ký tự, max_tokens = 1024.
- **D-26:** `ANTHROPIC_API_KEY` server-only, KHÔNG `NEXT_PUBLIC_*`.

### Claude's Discretion
- Tên cụ thể FloatingChatButton + folder structure (`components/chat/*` vs `app/_chat/*`).
- Markdown lib: `react-markdown` vs `marked + dompurify` — researcher recommend ở §8.
- System prompt wording cụ thể tiếng Việt — researcher draft, user duyệt khi review plan.
- Cách test streaming (Playwright network mock vs jest unit) — quyết ở §10.

### Deferred Ideas (OUT OF SCOPE)
- Agentic tool-use, function-calling, RAG embedding, helpful-vote, multilingual, guest chat, auto-confirm order, voice input, image upload, cost dashboard.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AI-01 | Customer chatbot UI: floating widget góc dưới phải mọi trang. Click mở modal, streaming response, Markdown render. Guest thấy nút "Đăng nhập để chat". | §3 Next.js streaming, §7 Modal extension, §8 Markdown |
| AI-02 | System prompt persona "Trợ lý mua sắm tmdt-use-gsd", VN, FAQ + Q&A + recommend; prompt caching từ ngày 1. | §1 SDK streaming, §2 Prompt caching |
| AI-03 | Context injection top-N products keyword/category match, XML tag `<product_context>` chống prompt injection từ user content. | §6 product search ranking, §1 message body shape |
| AI-04 | Chat history persist DB schema `chat_sessions` + `chat_messages`. Sliding window 10 turns. User xem được lịch sử cũ. | §4 pg pool + schema init, §1 sliding window load |
| AI-05 | Admin "AI suggest reply" trong `/admin/orders/[id]`, generate gợi ý reply, admin manual review + send. | §1 1-shot generation, route shape D-22 |
</phase_requirements>

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Anthropic API call (key holder) | Frontend Server (Next.js API route) | — | API key server-only; tier locked D-03 |
| Streaming proxy (SSE delta forward) | Frontend Server (Node runtime) | Browser (consumer) | ReadableStream sản xuất ở route handler, browser đọc qua fetch body reader |
| Chat persistence (sessions/messages CRUD) | Frontend Server (raw `pg`) | Postgres `chat_svc` schema | D-03 bypass Spring Boot — Next.js viết thẳng |
| Product context lookup | Frontend Server | Spring `product-service` REST | D-18 cross-schema cấm; phải gọi REST `/api/products?keyword=` qua api-gateway |
| Auth verify (JWT HS256) | Frontend Server (route handler) | user-service (issuer) | Shared `JWT_SECRET`; verify trong route, không proxy |
| Rate limit | Frontend Server (in-memory Map) | — | MVP single instance; D-24 |
| Chat UI (modal/composer/bubble) | Browser | Frontend Server (FloatingChatButton mount) | React 19 client component |
| Admin guard | Browser (middleware UX) | Frontend Server route (verify role claim) | Defense-in-depth: middleware redirect /403 + route handler check role claim trong JWT |

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `@anthropic-ai/sdk` | `0.92.0` | Claude Messages API client | Locked D-01. `[VERIFIED: npm view @anthropic-ai/sdk dist-tags` → `latest: 0.92.0` ngày 2026-05-02]` |
| `pg` | `^8.20.0` | Raw Postgres driver, schema init + chat CRUD | Locked D-03 (raw, không Prisma/Drizzle). `[VERIFIED: npm view pg version → 8.20.0]` |
| `jose` | `^5.x` | JWT HS256 verify trong route handler (Edge-compatible cũng OK) | Industry standard cho Next.js JWT verify. `[VERIFIED: jose là JWT lib chính của Next.js examples; alternative `jsonwebtoken` không edge-compatible nhưng Node runtime OK]` |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `react-markdown` | `^10.1.0` | Render markdown trong assistant bubble | Default — built-in sanitization, no `dangerouslySetInnerHTML` cần thiết. `[VERIFIED: npm view react-markdown version → 10.1.0]` |
| `@types/pg` | `^8.x` | TypeScript types cho `pg` | Required do dự án `strict: true` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `@anthropic-ai/sdk` raw | Vercel AI SDK | D-02 cấm — dependency tree lớn hơn, abstraction trên ReadableStream không cần thiết |
| `pg` | Prisma / Drizzle | Locked D-03 raw. ORM thừa cho 2 bảng + idempotent init |
| `react-markdown` | `marked` + `dompurify` | react-markdown an toàn hơn (no raw HTML), bundle ~15KB gzipped vs marked+dompurify ~25KB. Cấu hình mặc định đã sanitize |
| `jose` | `jsonwebtoken` | jose modern, ESM-first, supports Web Crypto API. Cả hai OK cho Node runtime nhưng jose tốt hơn nếu sau này muốn migrate Edge |

**Installation:**
```bash
cd sources/frontend
npm install @anthropic-ai/sdk@0.92.0 pg@^8.20.0 jose@^5 react-markdown@^10
npm install --save-dev @types/pg
```

**Version verification (date 2026-05-02):**
```
@anthropic-ai/sdk: 0.92.0 (latest)
pg: 8.20.0
react-markdown: 10.1.0
dompurify: 3.4.2 (alternative path only)
```
`[VERIFIED: npm registry queries 2026-05-02]`

---

## Architecture Patterns

### System Architecture Diagram

```
                          BROWSER
                            │
       ┌────────────────────┼────────────────────┐
       │                    │                    │
       ▼                    ▼                    ▼
 [FloatingChat       [Admin Order Page]    [Login required
  Button]                   │                gate]
       │                    │
       │ click              │ click "AI suggest reply"
       ▼                    ▼
 [ChatPanel Modal]    [POST /api/admin/orders/:id/suggest-reply]
       │                    │
       │ fetch (SSE         │ fetch (JSON)
       │  body reader)      │
       ▼                    ▼
 [POST /api/chat/stream]   ─┴─→ NEXT.JS NODE RUNTIME (route handlers)
       │                              │
       │  ┌───────────────────────────┼────────────────────────┐
       │  │                           │                        │
       │  ▼                           ▼                        ▼
       │ [verifyJwt(req)]    [rateLimit(userId)]    [ensureSchema()]
       │  │ jose.HS256              Map<id, ts[]>        to_regclass once
       │  │ shared JWT_SECRET                            CREATE TABLE IF NOT EXISTS
       │  ▼
       │ [searchProductsForContext(message)]
       │  │ → http GET api-gateway:8080/api/products?keyword=...&size=5
       │  │   fallback bestsellers nếu match<3
       │  ▼
       │ [loadHistory(sessionId, last 20)]  raw pg query
       │  ▼
       │ [persist user message]  raw pg INSERT
       │  ▼
       │ [client.messages.stream({          ┌──────────────────┐
       │    model: 'claude-haiku-4-5',      │ Anthropic API    │
       │    system: [{type:'text',          │  /v1/messages    │
       │      text: VN persona,             │  (streaming)     │
       │      cache_control: ephemeral}],   │                  │
       │    messages: [                     │                  │
       │      ...history,                   │                  │
       │      {role:'user', content:[       │                  │
       │        {type:'text',               │ ↓ event stream   │
       │         text: <product_context>... │ message_start    │
       │           <user_question>...,      │ content_block_start
       │         cache_control: ephemeral}, │ content_block_delta
       │        {type:'text', text: ''}     │  → text_delta    │
       │       ]} ],                        │ content_block_stop
       │    max_tokens: 1024 })]            │ message_delta    │
       │       │                            │ message_stop     │
       │       │  for await (event) in stream                  │
       │       │   on text_delta → controller.enqueue(JSON{delta,text})
       │       │   on stream end → controller.enqueue(JSON{done})
       │       │   on error      → controller.enqueue(JSON{error}) + close
       │       ▼
       │ [persist assistant message] raw pg INSERT
       │       │
       │       ▼
       │ Response(text/event-stream)
       │       │
       └───────┘ ← stream tới browser, FE append delta → bubble
```

**Data flow chính (happy path):**
1. User gõ message trong ChatPanel → POST `/api/chat/stream` body `{sessionId?, message}` + Bearer JWT.
2. Route handler: verify JWT → rate-limit check → ensure schema (no-op sau lần đầu) → tạo session nếu null → search products top-5 → load history sliding window → INSERT user message → call `client.messages.stream()` → forward delta events qua ReadableStream → INSERT assistant message khi done.
3. FE đọc `Response.body.getReader()`, parse JSON event mỗi line → append text vào active assistant bubble.

### Recommended Project Structure
```
sources/frontend/src/
├── app/
│   ├── api/                         # NEW — first ever Next.js API routes in project
│   │   ├── chat/
│   │   │   ├── stream/route.ts      # POST text/event-stream
│   │   │   └── sessions/
│   │   │       ├── route.ts         # GET list
│   │   │       └── [id]/messages/route.ts  # GET messages
│   │   └── admin/
│   │       └── orders/[id]/suggest-reply/route.ts  # POST 1-shot
│   └── layout.tsx                   # MOUNT FloatingChatButton client wrapper
├── lib/
│   └── chat/                        # NEW
│       ├── anthropic.ts             # singleton client + system prompt template
│       ├── pg.ts                    # singleton Pool (globalThis cache)
│       ├── schema-init.ts           # idempotent ensureSchema()
│       ├── auth.ts                  # verifyJwtFromRequest(req) → {userId, roles}
│       ├── rate-limit.ts            # checkRateLimit(userId) → boolean
│       ├── product-context.ts      # searchProductsForContext(message) → Product[]
│       ├── vn-text.ts               # removeDiacritics + lowercase normalize
│       └── messages-repo.ts         # createSession, loadHistory, append, listSessions
└── components/
    └── chat/                        # NEW
        ├── FloatingChatButton/      # client wrapper mount từ layout
        ├── ChatPanel/               # extends Modal, bottom-right desktop / full-screen mobile
        ├── ChatComposer/            # input + submit, max 2000 chars
        ├── MessageBubble/           # role-aware, react-markdown for assistant
        └── QuickReplyChips/         # 3 chips empty state
```

### Pattern 1: Anthropic SDK Streaming → Next.js ReadableStream
**What:** Bridge SDK async iterator → Web ReadableStream controller, output `text/event-stream` newline-delimited JSON.
**When to use:** `POST /api/chat/stream` route handler.
**Example:**
```typescript
// Source: Anthropic SDK 0.92.0 README https://github.com/anthropics/anthropic-sdk-typescript
// + Next.js App Router streaming docs https://nextjs.org/docs/app/building-your-application/routing/route-handlers#streaming
// [CITED: Anthropic docs site /en/api/messages-streaming]
// [VERIFIED: SDK 0.92.0 exports `client.messages.stream()` returning MessageStream with async iterator + .finalMessage()]

import Anthropic from '@anthropic-ai/sdk';

export const runtime = 'nodejs'; // CRITICAL — pg + long stream not edge-compatible
export const dynamic = 'force-dynamic';

const client = new Anthropic({ apiKey: process.env.ANTHROPIC_API_KEY! });

export async function POST(req: Request) {
  // ... auth + rate limit + persist user msg + load history + product context ...

  const encoder = new TextEncoder();
  const abortController = new AbortController();

  // CRITICAL: propagate client disconnect
  req.signal.addEventListener('abort', () => abortController.abort());

  const stream = new ReadableStream({
    async start(controller) {
      const send = (obj: unknown) =>
        controller.enqueue(encoder.encode(JSON.stringify(obj) + '\n'));

      try {
        const messageStream = client.messages.stream(
          {
            model: 'claude-haiku-4-5',
            max_tokens: 1024,
            system: [
              {
                type: 'text',
                text: SYSTEM_PROMPT_VN,                  // persona, locked Vietnamese
                cache_control: { type: 'ephemeral' },     // D-04 prompt caching
              },
            ],
            messages: [
              ...history,                                  // sliding window 10 turns
              {
                role: 'user',
                content: [
                  {
                    type: 'text',
                    text: `<product_context>${ctxXml}</product_context>\n<user_question>${userMsg}</user_question>`,
                    cache_control: { type: 'ephemeral' }, // D-04 (cached if context stable)
                  },
                ],
              },
            ],
          },
          { signal: abortController.signal },
        );

        let assistantText = '';
        for await (const event of messageStream) {
          if (event.type === 'content_block_delta' && event.delta.type === 'text_delta') {
            assistantText += event.delta.text;
            send({ type: 'delta', text: event.delta.text });
          }
        }

        const finalMessage = await messageStream.finalMessage();
        // finalMessage.usage = { input_tokens, cache_creation_input_tokens, cache_read_input_tokens, output_tokens }
        await appendAssistantMessage(sessionId, assistantText);
        send({ type: 'done', usage: finalMessage.usage });
        controller.close();
      } catch (err) {
        send({ type: 'error', error: err instanceof Error ? err.message : 'stream_failed' });
        controller.close();
      }
    },
    cancel() {
      abortController.abort();
    },
  });

  return new Response(stream, {
    headers: {
      'Content-Type': 'text/event-stream; charset=utf-8',
      'Cache-Control': 'no-cache, no-transform',
      'Connection': 'keep-alive',
      'X-Accel-Buffering': 'no',  // disable nginx buffering nếu sau này deploy sau Nginx
    },
  });
}
```

### Pattern 2: Singleton `pg.Pool` qua `globalThis` (anti-hot-reload-leak)
```typescript
// Source: Vercel/Next.js community pattern (e.g. Prisma docs, jodylecompte/pg in Next examples)
// [CITED: https://www.prisma.io/docs/guides/other/troubleshooting-orm/help-articles/nextjs-prisma-client-dev-practices — same pattern applies to pg]
import { Pool } from 'pg';

const globalForPg = globalThis as unknown as { _chatPgPool?: Pool };

export const chatPgPool: Pool =
  globalForPg._chatPgPool ??
  new Pool({
    host: process.env.DB_HOST ?? 'postgres',
    port: Number(process.env.DB_PORT ?? 5432),
    database: process.env.DB_NAME ?? 'tmdt',
    user: process.env.DB_USER ?? 'tmdt',
    password: process.env.DB_PASSWORD ?? 'tmdt',
    max: 10,                  // sized cho single Next.js instance + 6 backend services
    idleTimeoutMillis: 30_000,
  });

if (process.env.NODE_ENV !== 'production') globalForPg._chatPgPool = chatPgPool;
```

### Pattern 3: Idempotent schema init guarded once-per-boot
```typescript
let initialized = false;
let initPromise: Promise<void> | null = null;

export async function ensureSchema(): Promise<void> {
  if (initialized) return;
  if (initPromise) return initPromise;            // race-safe — concurrent first requests
  initPromise = (async () => {
    const exists = await chatPgPool.query(
      `SELECT to_regclass('chat_svc.chat_sessions') AS exists`,
    );
    if (exists.rows[0].exists) {
      initialized = true;
      return;
    }
    await chatPgPool.query(`
      CREATE SCHEMA IF NOT EXISTS chat_svc;
      CREATE TABLE IF NOT EXISTS chat_svc.chat_sessions (
        id BIGSERIAL PRIMARY KEY,
        user_id VARCHAR(36) NOT NULL,                -- ⚠ adjusted from BIGINT (see §13)
        title VARCHAR(200),
        created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
      );
      CREATE INDEX IF NOT EXISTS idx_chat_sessions_user_updated
        ON chat_svc.chat_sessions (user_id, updated_at DESC);
      CREATE TABLE IF NOT EXISTS chat_svc.chat_messages (
        id BIGSERIAL PRIMARY KEY,
        session_id BIGINT NOT NULL REFERENCES chat_svc.chat_sessions(id) ON DELETE CASCADE,
        role VARCHAR(16) NOT NULL CHECK (role IN ('user','assistant')),
        content TEXT NOT NULL,
        created_at TIMESTAMPTZ NOT NULL DEFAULT now()
      );
      CREATE INDEX IF NOT EXISTS idx_chat_messages_session_created
        ON chat_svc.chat_messages (session_id, created_at);
    `);
    initialized = true;
  })();
  await initPromise;
}
```

### Anti-Patterns to Avoid
- **Cross-schema FK** (`chat_svc.chat_sessions.user_id REFERENCES user_svc.users(id)`) — D-19 đã chọn logical FK; cross-schema FK gây Flyway/raw-init conflict. Logical only.
- **Streaming trong Edge runtime** — `pg` driver không edge-compatible. **Phải `export const runtime = 'nodejs'`**.
- **Buffer toàn response trước khi flush** — vi phạm D-15. Chỉ enqueue khi nhận text_delta.
- **Đặt API key trong `NEXT_PUBLIC_*`** — D-26 cấm. Verify bằng grep CI nếu có thời gian.
- **Schema init mỗi request** — D-21 cấm. Once-per-boot guard.
- **JSON.parse từng chunk SSE bên FE bằng split('\n')** — chunk có thể bị cắt giữa byte. Dùng buffer accumulator + `indexOf('\n')` loop.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JWT HS256 verify | Custom HMAC + base64 | `jose.jwtVerify(token, secret)` | Timing-safe compare, exp/iat/nbf validation, kid header — đầy edge cases |
| Markdown render | Regex `**bold**` parser | `react-markdown` | XSS, list nesting, code fences, link sanitize đã giải quyết |
| Connection pooling | New `Client()` mỗi request | `Pool` từ `pg` | Connection exhaustion + handshake latency |
| Vietnamese diacritic remove | Custom char map | `String.prototype.normalize('NFD').replace(/\p{Diacritic}/gu, '')` | Built-in Unicode handling |
| SSE encoding | Custom event format | Newline-delimited JSON `{type,text}` | D-14 đã lock; tránh sự khác nhau giữa SSE `data:` prefix và raw chunk |
| Session ID generation | UUID | `BIGSERIAL` Postgres | Locked D-19; auto-increment đủ MVP |

**Key insight:** SDK 0.92.0 expose `messages.stream().finalMessage()` cho usage tokens — không cần parse `message_delta` event thủ công. Sử dụng async iterator + `finalMessage()` là idiomatic.

---

## Common Pitfalls

### Pitfall 1: Stream không chạy trong Edge runtime
**What goes wrong:** `pg` import bị reject, hoặc stream bị buffer / 503.
**Why it happens:** Next.js Edge default cho route handler. Edge không chạy native Node modules (`pg`, `crypto.createHmac`).
**How to avoid:** `export const runtime = 'nodejs';` ở mọi route file dưới `app/api/chat/` và `app/api/admin/orders/[id]/suggest-reply/`.
**Warning signs:** Build error "Module not found: Can't resolve 'pg-native'" hoặc "edge runtime restrictions".

### Pitfall 2: pg Pool leak khi `next dev` hot-reload
**What goes wrong:** Sau 10-20 reload, dev server hết Postgres connections.
**Why it happens:** Mỗi HMR reload tạo Pool mới, cũ không close.
**How to avoid:** Pattern `globalThis._chatPgPool` ở §Pattern 2.
**Warning signs:** Postgres logs "too many clients already" khi dev.

### Pitfall 3: Client unmount không close server stream
**What goes wrong:** User đóng modal mid-stream, server vẫn gọi Anthropic → tốn token, leak.
**Why it happens:** ReadableStream `cancel()` không tự forward sang Anthropic SDK.
**How to avoid:** `req.signal` listener → `abortController.abort()` → pass `signal` vào `client.messages.stream(opts, { signal })`.
**Warning signs:** Token cost cao bất thường; assistant message vẫn ghi DB sau khi user thoát.

### Pitfall 4: SSE chunk cắt ngang JSON
**What goes wrong:** FE `JSON.parse(chunk)` throw vì chunk chỉ là `{"type":"de` rồi `lta","text":"xin"}`.
**Why it happens:** TCP fragmentation; ReadableStream không guarantee newline boundary.
**How to avoid:** FE buffer accumulator:
```typescript
let buf = '';
const reader = res.body!.getReader();
const decoder = new TextDecoder();
while (true) {
  const { value, done } = await reader.read();
  if (done) break;
  buf += decoder.decode(value, { stream: true });
  let nl;
  while ((nl = buf.indexOf('\n')) >= 0) {
    const line = buf.slice(0, nl).trim();
    buf = buf.slice(nl + 1);
    if (line) handleEvent(JSON.parse(line));
  }
}
```

### Pitfall 5: Prompt cache miss vì content drift
**What goes wrong:** Cache hit rate ~0%, không tiết kiệm chi phí.
**Why it happens:** System prompt + product context block thay đổi mỗi request (timestamp, user name, product order).
**How to avoid:** Giữ system prompt = static text (KHÔNG insert tên user, KHÔNG timestamp). Product context cache khó hơn — chỉ benefit nếu cùng query products lặp lại trong cùng session. Đặt `cache_control` ở cuối block để prefix ổn định cache được.
**Warning signs:** `finalMessage.usage.cache_read_input_tokens` luôn = 0.

### Pitfall 6: Vietnamese diacritic + ILIKE keyword bỏ sót
**What goes wrong:** User hỏi "laptop văn phòng" → ILIKE `%laptop văn phòng%` không khớp tên SP "Laptop Van Phong" (nếu có).
**Why it happens:** Postgres ILIKE collation không strip diacritic mặc định.
**How to avoid:** Strip diacritic + lowercase BE → so sánh đôi tay: build keyword tokens từ message, ILIKE từng token độc lập, OR-join. Hoặc dùng `unaccent` extension Postgres (cần `CREATE EXTENSION` quyền superuser — risky cho shared DB).
**Warning signs:** Match < 3 luôn bị fallback bestsellers.

### Pitfall 7: JWT HS256 signature mismatch
**What goes wrong:** Mọi request 401.
**Why it happens:** `JWT_SECRET` env var trong Next.js container không match user-service.
**How to avoid:** Cùng env var name + value. Document trong `.env.example`. Verify dev: decode token bằng cùng secret trên cả 2 services.
**Warning signs:** Tokens hợp lệ trên gateway nhưng route handler 401.

### Pitfall 8: Rate limit Map memory grow vô tận
**What goes wrong:** Sau N user, Map có hàng nghìn entry không clean.
**Why it happens:** Quên prune.
**How to avoid:** Mỗi check, lazy prune `timestamps.filter(t => Date.now() - t < 5*60*1000)`. Periodic sweep `setInterval(() => map.forEach(prune), 60_000)` cho safety.

---

## Code Examples

### Loading history sliding window 10 turns
```typescript
// [CITED: D-06 sliding window]
export async function loadHistory(sessionId: number) {
  const { rows } = await chatPgPool.query<{role: 'user'|'assistant', content: string}>(
    `SELECT role, content
       FROM chat_svc.chat_messages
      WHERE session_id = $1
      ORDER BY created_at DESC
      LIMIT 20`,
    [sessionId],
  );
  return rows.reverse().map(r => ({ role: r.role, content: r.content }));
}
```

### Vietnamese normalization
```typescript
export function normalizeVn(s: string): string {
  return s
    .toLowerCase()
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .replace(/đ/g, 'd')
    .trim();
}
```

### Product context lookup (top-5 + bestseller fallback)
```typescript
export async function searchProductsForContext(userMessage: string) {
  const norm = normalizeVn(userMessage);
  const tokens = norm.split(/\s+/).filter(t => t.length >= 2).slice(0, 6);
  const keyword = tokens.join(' ');
  // call Spring product-svc via gateway (server-side fetch — no localStorage token; route uses
  // requester's Bearer to keep auth context, OR hits a public endpoint /api/products is public)
  const url = `${process.env.API_GATEWAY_URL ?? 'http://api-gateway:8080'}/api/products?keyword=${encodeURIComponent(keyword)}&size=5`;
  const res = await fetch(url, { headers: { Accept: 'application/json' }, cache: 'no-store' });
  const env = await res.json();
  let products: Product[] = env?.data?.content ?? [];
  if (products.length < 3) {
    // fallback bestsellers — no dedicated endpoint exists yet (see §6); fall back to
    // sort=updatedAt,desc on /api/products as a stand-in. Document gap.
    const fb = await fetch(`${url.split('?')[0]}?size=5&sort=updatedAt,desc`, { cache: 'no-store' });
    const fbEnv = await fb.json();
    products = (fbEnv?.data?.content ?? []).slice(0, 5);
  }
  return products.map(p => ({
    id: p.id, name: p.name, price: p.price, brand: p.brand, stock: p.stock,
  }));
}

export function buildContextXml(products: ReturnType<typeof searchProductsForContext> extends Promise<infer T> ? T : never): string {
  return products.map(p =>
    `<product id="${p.id}" name="${escapeXml(p.name)}" price="${p.price}" brand="${escapeXml(p.brand ?? '')}" stock="${p.stock ?? 0}"/>`
  ).join('\n');
}
```

### JWT verify in route handler
```typescript
// [CITED: jose docs https://github.com/panva/jose]
import { jwtVerify } from 'jose';

const SECRET = new TextEncoder().encode(
  process.env.JWT_SECRET ?? 'dev-jwt-secret-key-minimum-32-characters-for-hs256-ok',
);

export async function verifyJwtFromRequest(req: Request): Promise<{userId: string, roles: string[]}> {
  const auth = req.headers.get('authorization');
  if (!auth?.startsWith('Bearer ')) throw new Error('AUTH_MISSING');
  const token = auth.slice(7);
  const { payload } = await jwtVerify(token, SECRET, { algorithms: ['HS256'] });
  // user-service issues JWT with claims { sub: userId, roles: 'USER,ADMIN' or array }
  const userId = String(payload.sub ?? '');
  if (!userId) throw new Error('AUTH_INVALID');
  const rolesRaw = payload.roles ?? payload['roles'] ?? '';
  const roles = Array.isArray(rolesRaw) ? rolesRaw : String(rolesRaw).split(',').filter(Boolean);
  return { userId, roles };
}
```
**ASSUMPTION:** JWT claim shape (`sub` = userId, `roles` = comma-string) `[ASSUMED]` — verify bằng decode 1 token thật từ user-service login. Adjust nếu khác.

### Rate limit helper
```typescript
const limits = new Map<string, number[]>();
const WINDOW_MS = 5 * 60 * 1000;
const MAX = 20;

export function checkRateLimit(userId: string): boolean {
  const now = Date.now();
  const arr = (limits.get(userId) ?? []).filter(t => now - t < WINDOW_MS);
  if (arr.length >= MAX) {
    limits.set(userId, arr);
    return false;
  }
  arr.push(now);
  limits.set(userId, arr);
  return true;
}
```

### FE consumer (chat panel useChat hook sketch)
```typescript
async function sendMessage(sessionId: number | null, message: string) {
  const res = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${getAccessToken()}`,
    },
    body: JSON.stringify({ sessionId, message }),
  });
  if (!res.ok || !res.body) throw new Error('stream_failed');
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buf = '';
  let assistantText = '';
  for (;;) {
    const { value, done } = await reader.read();
    if (done) break;
    buf += decoder.decode(value, { stream: true });
    let nl;
    while ((nl = buf.indexOf('\n')) >= 0) {
      const line = buf.slice(0, nl).trim();
      buf = buf.slice(nl + 1);
      if (!line) continue;
      const ev = JSON.parse(line);
      if (ev.type === 'delta') {
        assistantText += ev.text;
        setBubble(prev => ({ ...prev, content: assistantText }));
      } else if (ev.type === 'done') {
        // refresh sessions list, end stream UI
      } else if (ev.type === 'error') {
        showToast(`Lỗi: ${ev.error}`, 'error');
      }
    }
  }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Anthropic `client.messages.create({stream: true})` raw event handlers | `client.messages.stream()` async iterator + `.finalMessage()` | SDK 0.16+ | Cleaner; auto handles message accumulation |
| Vercel AI SDK abstraction | Native ReadableStream | Locked D-02 | Fewer deps; full control |
| Cache via custom Redis key | `cache_control: { type: 'ephemeral' }` (server-side 5min cache) | Anthropic prompt caching GA 2024 Q3 | 50%+ savings on repeated system+context |
| `pg-promise` | Raw `pg` Pool | — | Less abstraction, modern async/await sufficient |

**Deprecated/outdated:**
- `MessageStream.on('text', cb)` event API → still works, but async iterator preferred since 0.30+.
- Edge runtime + `pg` → NOT supported. Always Node runtime.

---

## Architectural Section Details

### §1. Anthropic Messages API streaming (HIGH confidence)
- SDK 0.92.0 verified `[VERIFIED: npm view @anthropic-ai/sdk dist-tags`].
- `client.messages.stream(params, options?)` returns `MessageStream` which is `AsyncIterable<RawMessageStreamEvent>`.
- Events relevant: `content_block_delta` with `delta.type === 'text_delta'` carries the user-visible text. Other events (`message_start`, `content_block_start`, `content_block_stop`, `message_delta`, `message_stop`) can be ignored for MVP (we use `.finalMessage()` for usage).
- `messageStream.finalMessage()` resolves AFTER iteration complete with full `Message` (content blocks + `usage`). `usage.cache_read_input_tokens` + `cache_creation_input_tokens` confirm caching.
- Abort: pass `{ signal }` as second arg → SDK respects `AbortSignal`.
- Mid-stream errors: SDK throws inside async iterator → wrap in try/catch, send `{type:'error'}` event then close.

### §2. Prompt caching (HIGH)
- Mark cache breakpoints by setting `cache_control: { type: 'ephemeral' }` on a content block (system or user). Up to **4 breakpoints** per request.
- Cache lifetime ~5 min, organization-scoped. Cache hit reduces input cost ~90%.
- **What's cacheable in this design:**
  - System prompt (persona) — high reuse, ALWAYS cache.
  - Product context block — cache only if same products appear in consecutive requests in same session (rare unless user asks follow-ups about same products). MVP: still mark cache_control; cost neutral if miss.
  - Sliding window history — DO NOT cache (changes every turn).
- Pricing reminder (Haiku 4.5, indicative):
  - Input ~$1 / MTok, Output ~$5 / MTok (verify on Anthropic pricing page when implementing — `[ASSUMED for Haiku 4.5; verify https://www.anthropic.com/pricing]`).
  - Cache write: 1.25× input price; cache read: 0.1× input price.

### §3. Next.js 16 App Router streaming (HIGH)
- Route handler returns `Response(ReadableStream, { headers })`.
- **REQUIRED headers:**
  - `Content-Type: text/event-stream; charset=utf-8`
  - `Cache-Control: no-cache, no-transform`
  - `Connection: keep-alive`
  - `X-Accel-Buffering: no` (defensive — Nginx future proof)
- **Required directives in route file:**
  - `export const runtime = 'nodejs';`
  - `export const dynamic = 'force-dynamic';` (prevent static optimization)
- React 19 client side: `await fetch` + `res.body.getReader()` works without `use()` hook. `use()` is for Suspense-boundary promises, not stream consumption.

### §4. `pg` raw driver in Next.js (HIGH)
- Singleton via `globalThis` cache to survive `next dev` HMR.
- Connection from FE Docker container → service name `postgres:5432` (Docker network). For local `npm run dev` outside Docker, use `localhost:5432` (Postgres exposes port). Drive via env `DB_HOST`.
- **Reuse existing user `tmdt` / password `tmdt`** — no new DB user needed for MVP. Owner of new schema `chat_svc` will be `tmdt`. (Spring services already write `user_svc`, `product_svc`, etc. with same user.)
- Connection string: build from individual `DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD` env vars (mirrors Spring services).
- `to_regclass('chat_svc.chat_sessions')` returns NULL if missing, table OID if exists — perfect existence check.

### §5. Auth in Next.js API route (HIGH)
- **Project state:** No existing `lib/auth/*` server-side helper. FE only has cookies (`auth_present`, `user_role`) for middleware UX gating + `localStorage.accessToken` for Bearer. Backend (user-service Spring) holds `JWT_SECRET` (HS256, default `dev-jwt-secret-key-minimum-32-characters-for-hs256-ok`).
- **Recommended path:** Verify JWT in route handler with shared secret using `jose`. Lower risk than proxy-to-gateway because:
  - Avoids extra network hop per request.
  - Matches existing security model (gateway also verifies HS256 with same secret).
  - Same secret already injected into all backend service containers — adding to FE container is symmetric.
- **Required env var:** `JWT_SECRET` (same value as user-service).
- Admin role check (for suggest-reply route): inspect `payload.roles` (string `"USER,ADMIN"` or array). Reject if `'ADMIN'` absent.

### §6. Product search ranking (HIGH for endpoint, MEDIUM for "bestseller fallback")
- **Endpoint:** `GET /api/products?keyword=<text>&size=5` (verified `ProductController.listProducts` line 33-44 supports `keyword` param). Returns `ApiResponse<{content: Product[], totalElements, ...}>`.
- **Diacritic stripping:** Done client-side in route handler before sending keyword (BE may not strip; safe to strip both sides).
- **Bestseller fallback:** No dedicated `/products/bestsellers` endpoint exists `[VERIFIED: Glob ProductController.java + reading file]`. Recommend MVP fallback = sort=updatedAt,desc (recent inventory) — not true bestsellers but a reasonable surrogate. Document as known limitation. Future: add `?sort=salesCount,desc` after order-svc exposes aggregate.
- **Server-side fetch from Next.js route:** Use `fetch(http://api-gateway:8080/api/products?...)` from inside Docker network. From local `npm run dev`, use `http://localhost:8080`. Drive via `API_GATEWAY_URL` env var.
- `/api/products` is public (no auth on listProducts) — confirmed no @PreAuthorize. Can call without forwarding Bearer.

### §7. FloatingChatButton + Modal pattern (HIGH)
- **Existing Modal:** `components/ui/Modal/Modal.tsx` is centered-dialog only (max-width 480px, padding, slideUp animation). Does NOT support bottom-right anchored or full-height variant. **Recommendation:** Build dedicated `ChatPanel` component (NOT extend Modal) — they have different layout primitives. Reuse `Modal`'s patterns: backdrop dismiss, ESC handler, body scroll lock, focus trap, `role="dialog"`/`aria-modal`.
- **State management:** Local `useState` in FloatingChatButton wrapper. Sessions list + active session live in a `ChatPanel` parent component using `useState` + `useEffect`. **DO NOT introduce zustand/redux** — out of scope. Pattern matches existing pages (e.g., admin order detail uses pure useState).
- **Mount:** Add `<FloatingChatButton />` inside `ConditionalShell` or directly as sibling in `app/layout.tsx` body. ConditionalShell wraps children — likely hides on auth pages; chat button should display on all routes per D-09.
- **React 19 `use()`:** Not needed for this phase. Use plain `useState`/`useEffect`/`useTransition`.

### §8. Markdown rendering safely (HIGH)
- **Recommendation: `react-markdown` v10.1.0** with default config (no `rehype-raw`).
- `react-markdown` by default: no raw HTML, no `<img>` allowed unless explicitly enabled. Auto-escapes everything not in standard markdown grammar.
- Disable images for MVP: `<ReactMarkdown components={{ img: () => null }} />` or via plugin allowlist.
- Disable links to external untrusted (or just escape-all): `<ReactMarkdown components={{ a: ({href, children}) => <a href={href} rel="noopener noreferrer" target="_blank">{children}</a> }} />`.
- **NO `dangerouslySetInnerHTML` anywhere.** Bundle ~15KB gz.
- LLM-generated content threats: the LLM may emit malicious markdown intended to phish. With default react-markdown there's no script execution path. Acceptable for MVP.

### §9. Rate limiting in-memory (HIGH)
- See `checkRateLimit` example. Single Next.js instance. Hot reload resets — acceptable.
- Production note: multi-instance deployment requires Redis or similar — out of scope MVP.

### §11. Environment + secrets (HIGH)
- **Local dev (`npm run dev` outside Docker):** `.env.local` in `sources/frontend/` with:
  ```
  ANTHROPIC_API_KEY=sk-ant-...
  JWT_SECRET=dev-jwt-secret-key-minimum-32-characters-for-hs256-ok
  DB_HOST=localhost
  DB_PORT=5432
  DB_NAME=tmdt
  DB_USER=tmdt
  DB_PASSWORD=tmdt
  API_GATEWAY_URL=http://localhost:8080
  ```
- **Docker (`docker compose up`):** Patch `docker-compose.yml` `frontend` service:
  ```yaml
  frontend:
    build: ./sources/frontend
    ports:
      - "3000:3000"
    depends_on:
      - api-gateway
      - postgres
    environment:
      ANTHROPIC_API_KEY: ${ANTHROPIC_API_KEY}     # require host env or .env file
      JWT_SECRET: ${JWT_SECRET:-dev-jwt-secret-key-minimum-32-characters-for-hs256-ok}
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: tmdt
      DB_USER: tmdt
      DB_PASSWORD: tmdt
      API_GATEWAY_URL: http://api-gateway:8080
  ```
  Plus root `.env` (or `.env.example` template):
  ```
  ANTHROPIC_API_KEY=sk-ant-...
  ```
- **Verify NO leak:** grep `NEXT_PUBLIC_ANTHROPIC` ⇒ 0 hits. Public Network tab confirms no header containing `sk-ant-`. Document in plan.
- **Frontend Dockerfile:** Need to inspect during planning — currently not in the read set. Plan must verify the Dockerfile is set up to run `next start` (production) and that env vars are passed at runtime, not embedded at build time.

### §12. Cost estimate (MEDIUM — Haiku 4.5 prices ASSUMED, verify)
- Assumption pricing `[ASSUMED]`: input $1/Mtok, output $5/Mtok, cache_read $0.10/Mtok, cache_write $1.25/Mtok.
- Per-message rough math:
  - System prompt (persona + instructions, VN): ~500 tokens. Cached → cost $0.05 / 1000 messages after first.
  - Product context block (5 products): ~300 tokens. Cached once per identical query.
  - Sliding window history: ~500-1500 tokens (10 turns avg ~100 tokens each).
  - User message: ~100 tokens.
  - Output: ~200-400 tokens.
- **Without caching:** ~2000 input + 300 output = 2000×$1/M + 300×$5/M = $0.002 + $0.0015 = **~$0.0035 / msg**.
- **With caching (system hit):** ~500 cache_read + 1500 fresh + 300 output = 500×$0.10/M + 1500×$1/M + 300×$5/M = $0.00005 + $0.0015 + $0.0015 = **~$0.003 / msg** (~14% reduction realistic; full claimed 50%+ requires history+context also stable, unlikely turn-to-turn).
- **20 msg/user/5min × 100 user/day = 2000 msg/day → ~$6/day = ~$180/month MVP scale.**
- Verify Haiku 4.5 pricing on Anthropic pricing page when implementing.

---

## Runtime State Inventory

Phase 22 is greenfield (new schema, new routes, new UI components). No rename/refactor.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | New `chat_svc` schema only — no existing data to migrate | Schema init via `ensureSchema()` |
| Live service config | None — Anthropic API has no project-side config beyond API key | None |
| OS-registered state | None | None |
| Secrets/env vars | NEW: `ANTHROPIC_API_KEY`, `JWT_SECRET` (FE), `DB_*` (FE), `API_GATEWAY_URL` (FE). All must be added to `docker-compose.yml frontend.environment` and `.env.example` | Document in plan; add to compose |
| Build artifacts | Frontend `node_modules/` will gain new deps (`@anthropic-ai/sdk`, `pg`, `jose`, `react-markdown`, `@types/pg`) | `npm install` re-run during Wave 1 |

**Nothing found in OS-registered / Live service config / Stored data:** Verified by grep + reading STATE.md (no Phase 22 prior artifacts).

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Node.js (Next.js 16.2.3) | All FE | ✓ (assumed) | — | — |
| npm | Install deps | ✓ | — | — |
| Postgres 16 | chat_svc schema | ✓ | 16-alpine in compose | — |
| Docker Compose | Run stack | ✓ | — | — |
| Spring api-gateway:8080 | product context lookup | ✓ | running | — |
| Anthropic API key | All chat | ✗ (user must obtain) | — | None — blocks AI-01..AI-05; planner must include "obtain key" task |
| Internet egress to api.anthropic.com | streaming + suggest-reply | Unknown — assume ✓ for dev workstation | — | If blocked: phase blocked |

**Missing dependencies with no fallback:** `ANTHROPIC_API_KEY` — user must provision (https://console.anthropic.com). Planner add: "Obtain Anthropic API key, add to root `.env`, verify `curl https://api.anthropic.com/v1/messages` reachable."

---

## Validation Architecture

> Phase config does not explicitly disable nyquist_validation; section included.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Playwright `^1.59.1` (E2E, already installed); jest/vitest **NOT installed** in project |
| Config file | `playwright.config.ts` (verify path during Wave 0); no jest config |
| Quick run command | `cd sources/frontend && npx playwright test --project=chromium tests/chatbot.spec.ts` |
| Full suite command | `cd sources/frontend && npx playwright test` |
| Type check | `cd sources/frontend && npx tsc --noEmit` |
| Lint | `cd sources/frontend && npm run lint` |

**Decision:** No jest/vitest install in this phase. Unit-test pure helpers (rate-limit, vn-text, buildContextXml) via Playwright `test()` against a small in-process harness OR add a tiny `vitest` install in Wave 0 if planner sees fit. **Default: skip unit tests, use Playwright + tsc as the validation surface.** Recommended unit tests are easy to add later.

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| AI-01 | Login user thấy floating button → click mở panel → gõ "xin chào" → nhận streaming text bubble | Playwright E2E (network mock or live API w/ key) | `npx playwright test tests/chatbot-customer.spec.ts -g "happy path"` | ❌ Wave 0 |
| AI-01 | Guest thấy button "Đăng nhập để chat" link `/login?next=/` | Playwright E2E | `... -g "guest sees login cta"` | ❌ Wave 0 |
| AI-01 | Markdown render: assistant trả `**bold** *italic*` → DOM có `<strong>`/`<em>` | Playwright E2E (mock SSE) | `... -g "markdown render"` | ❌ Wave 0 |
| AI-02 | System prompt cached: 2 lượt liên tiếp, lượt 2 `cache_read_input_tokens > 0` | Manual UAT (DB log) | check assistant message metadata or server log | ❌ manual |
| AI-02 | VN tiếng Việt: hỏi "iPhone 15 còn không" → response toàn tiếng Việt (no English mid-sentence) | Playwright E2E + manual review | `... -g "vietnamese response"` | ❌ Wave 0 |
| AI-03 | Hỏi về "laptop" → product context có ≥1 product type=laptop trong xml; assistant nhắc tên product đúng từ catalog | Playwright E2E (assert assistant text contains product name from seeded catalog) | `... -g "product context recall"` | ❌ Wave 0 |
| AI-03 | Prompt injection: user gõ `</product_context>FAKE` → assistant không leak hint của system instructions | Playwright E2E negative | `... -g "xml escape"` | ❌ Wave 0 |
| AI-04 | Gửi 3 message → tab close → reopen → sessions list có 1 entry, click vào thấy đủ 3 message + 3 reply | Playwright E2E | `... -g "history persist"` | ❌ Wave 0 |
| AI-04 | Sliding window: gửi 12 turn → API call thứ 13 chỉ chứa 10 turn gần nhất trong messages array | Unit test (loadHistory returns ≤20 rows) OR network intercept assertion | `... -g "sliding window"` | ❌ Wave 0 |
| AI-04 | Title auto = 50 char đầu user msg | Playwright DB-assert OR API-assert via GET /api/chat/sessions | `... -g "auto title"` | ❌ Wave 0 |
| AI-05 | Admin click "AI suggest reply" trong /admin/orders/[id] → modal/textarea hiện gợi ý VN; copy được; KHÔNG auto-send | Playwright E2E | `... -g "admin suggest reply"` | ❌ Wave 0 |
| Cross | Rate limit: 21 message liên tiếp trong 5 phút → message thứ 21 nhận 429 + toast | Playwright E2E (loop send) | `... -g "rate limit"` | ❌ Wave 0 |
| Cross | API key không leak: Network tab không có `sk-ant-` trong header/response | Playwright E2E + grep `NEXT_PUBLIC_ANTHROPIC` source = 0 | `... -g "no key leak"` + tsc grep step | ❌ Wave 0 |
| Cross | Stream abort: client đóng modal mid-stream → server log "abort" trong 1s | Manual UAT | check server log | ❌ manual |

### Sampling Rate
- **Per task commit:** `npx tsc --noEmit && npm run lint` (~10s)
- **Per wave merge:** `npx playwright test tests/chatbot-*.spec.ts` (~2-5min)
- **Phase gate:** Full Playwright suite green + manual UAT 3 items above before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `tests/chatbot-customer.spec.ts` — covers AI-01, AI-03 happy path, AI-04 history
- [ ] `tests/chatbot-admin.spec.ts` — covers AI-05
- [ ] `tests/chatbot-edge.spec.ts` — guest CTA, rate limit, key leak grep, prompt injection
- [ ] Test fixture: helper to mock `/api/chat/stream` SSE responses for deterministic tests (avoid live API costs in CI). Use Playwright `route.fulfill()` with text/event-stream body.
- [ ] Test fixture: seeded user account with valid JWT (reuse existing test login flow from v1.2 smoke E2E).
- [ ] No vitest install — keep validation Playwright-only.

---

## Project Constraints (from CLAUDE.md / project conventions)

- **Vietnamese-first UI copy** — all user-facing strings VN. System prompt VN.
- **ApiErrorResponse + traceId envelope** for non-stream REST endpoints (sessions list, messages list, suggest-reply). Stream endpoint uses SSE `{type:'error'}` event instead.
- **Visible-first priority** — chatbot is visible feature, in scope.
- **TypeScript strict** — all new code must pass `tsc --noEmit` with `strict: true`.
- **No localStorage for user-data** — chat history goes to DB, not localStorage. UI ephemeral state (open/close, draft input) localStorage OK.
- **Path alias `@/`** maps to `src/` — use `@/lib/chat/...` imports.
- **Component pattern:** PascalCase component, co-located `.module.css`.
- **Auth:** Bearer JWT in `Authorization` header (project-wide pattern).

---

## Sources

### Primary (HIGH confidence)
- Codebase inspection (verbatim file reads): CONTEXT.md, REQUIREMENTS.md, STATE.md, PROJECT.md, ROADMAP.md, codebase/{STACK,STRUCTURE,CONVENTIONS,INTEGRATIONS}.md, sources/frontend/package.json, sources/frontend/src/services/{http,auth,token,products}.ts, sources/frontend/src/middleware.ts, sources/frontend/src/app/layout.tsx, sources/frontend/src/app/admin/orders/[id]/page.tsx, sources/frontend/src/components/ui/Modal/{Modal.tsx,Modal.module.css}, sources/backend/user-service/src/main/resources/application.yml, sources/backend/user-service/src/main/resources/db/migration/V1__init_schema.sql, sources/backend/product-service/.../ProductController.java, db/init/01-schemas.sql, docker-compose.yml.
- npm registry queries: `npm view @anthropic-ai/sdk dist-tags` → `latest: 0.92.0` (2026-05-02); `npm view pg version` → 8.20.0; `npm view react-markdown version` → 10.1.0; `npm view dompurify version` → 3.4.2.

### Secondary (MEDIUM confidence)
- Anthropic Messages streaming docs (general SDK API patterns from training knowledge, model `claude-haiku-4-5` per locked decision, prompt-cache `cache_control: ephemeral` shape) `[CITED: docs.anthropic.com/en/api/messages-streaming + docs.anthropic.com/en/docs/build-with-claude/prompt-caching]`.
- Next.js App Router streaming pattern (route handlers + ReadableStream + `runtime = 'nodejs'`) `[CITED: nextjs.org/docs/app/building-your-application/routing/route-handlers#streaming]`.
- jose JWT verify pattern `[CITED: github.com/panva/jose]`.
- Pool singleton pattern via `globalThis` `[CITED: prisma.io/docs/.../nextjs-prisma-client-dev-practices — analogous to pg]`.

### Tertiary (LOW confidence — flagged)
- Haiku 4.5 exact pricing per token `[ASSUMED]` — need to verify on Anthropic pricing page during Wave 1.
- JWT claim shape (`payload.sub` = userId UUID, `payload.roles` = string or array) `[ASSUMED]` — verify by decoding a real user-service JWT in Wave 1 setup task.
- Frontend Dockerfile env handling `[ASSUMED]` — must inspect during planning. If image embeds env at build, refactor to runtime env injection.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Haiku 4.5 input ~$1/Mtok, output ~$5/Mtok, cache_read ~10% input | §12 Cost | Cost projections off; product still works |
| A2 | JWT claim shape: `sub`=userId, `roles`=string CSV or array | §5, §Code Examples JWT | 401 every request until claim names corrected |
| A3 | `ANTHROPIC_API_KEY` reachable from FE Docker container (egress allowed) | §Environment Availability | If blocked: phase blocked entirely |
| A4 | Frontend Dockerfile passes env at runtime (not build-time embed) | §11 | Key leaks into image OR missing at runtime |
| A5 | `/api/products?keyword=` endpoint is public (no auth) | §6, §Code Examples product search | If 401, must forward Bearer or use service-account |
| A6 | No `unaccent` Postgres extension installed; rely on app-side strip | §6 Pitfall 6 | If unaccent available, ILIKE could be more accurate |
| A7 | `ConditionalShell` allows mounting FloatingChatButton on every route | §7 | If it hides on some routes, button missing — minor UI fix |
| A8 | claude-haiku-4-5 supports `cache_control: ephemeral` (all current Claude 4.x do, but 4.5 specifically untested in this project) | §1, §2 | If unsupported: prompt caching non-op, cost higher |
| A9 | The 3 quick-reply chips (laptop 20tr, iPhone 15 Pro Max, Logitech vs Razer) match real seeded catalog | §11 D-11 | If catalog drifted, chip click returns "không tìm thấy" — confusing demo |

**User confirmation needed for:** A1 (verify pricing), A2 (decode real token), A3 (test egress), A4 (inspect Dockerfile).

---

## Open Questions (RESOLVED)

1. **`user_id` column type — BIGINT vs VARCHAR(36)?**
   - What we know: D-19 specifies BIGINT, but `user_svc.users.id` is VARCHAR(36) UUID.
   - What's unclear: Was D-19 BIGINT a careless default, or genuine intent to use sequence-based local IDs?
   - **RESOLVED: VARCHAR(36) NOT NULL.** Logical FK to `user_svc.users.id`. JWT `sub` claim is the UUID string. This is the cheapest path. **Planner MUST surface this as a decision deviation if user wants strict BIGINT.**

2. **API gateway URL from Next.js inside Docker network?**
   - What we know: Spring services use service-name DNS (`postgres`, `api-gateway`).
   - What's unclear: Whether Next.js container is on the same Docker network as `api-gateway`. Compose file shows `frontend` depends_on `api-gateway` — implies same default network.
   - **RESOLVED:** Use `http://api-gateway:8080` from container; document `API_GATEWAY_URL` env override for local dev.

3. **Test approach for SSE streaming under Playwright?**
   - Options: (a) live API with real key (fragile, costs); (b) Playwright `route.fulfill()` with custom text/event-stream body; (c) MSW (not yet in project).
   - **RESOLVED:** (b) — write `mockChatStream(page, chunks[])` helper. Live key tests gated behind `CHATBOT_E2E_LIVE=1` env.

4. **Should `/api/chat/stream` reuse existing JWT or need a session token?**
   - **RESOLVED:** existing JWT in Authorization header. No new session concept.

5. **Admin "suggest reply" model: same haiku-4-5 or different?**
   - **RESOLVED:** Locked D-01 covers all chat. Reuse haiku-4-5. 1-shot, no streaming.

---

## Risks & Landmines (§13)

| # | Risk | Likelihood | Impact | Mitigation |
|---|------|-----------|--------|------------|
| R1 | `user_id` BIGINT vs UUID type mismatch | HIGH | Schema reject at init or FK semantic broken | Use VARCHAR(36); document deviation; surface in plan if user disagrees |
| R2 | pg Pool leak in dev hot reload | MEDIUM | Postgres "too many clients" | `globalThis._chatPgPool` cache pattern |
| R3 | Edge runtime accidentally enabled → pg fail | LOW | Build error | Explicit `export const runtime = 'nodejs'` in every route |
| R4 | Client unmount leaks tokens | MEDIUM | Wasted Anthropic spend | `req.signal` → AbortController → SDK stream signal |
| R5 | API key leak via `NEXT_PUBLIC_*` typo | MEDIUM | Compromise | Repo-wide grep CI check; `.env.example` documents var without prefix |
| R6 | Prompt injection from user content | MEDIUM | Bypass system instructions | XML tag wrapping (`<user_question>`, `<product_context>`); escape user-supplied XML special chars |
| R7 | Vietnamese tokenization mismatch | LOW | Bestseller fallback over-triggers | Lazy fix; track via metric "fallback rate" |
| R8 | Admin role check bypass | MEDIUM | Customer accesses suggest-reply | Verify `roles.includes('ADMIN')` IN ROUTE HANDLER (not relying on middleware UX cookie) |
| R9 | Cross-schema FK unsupported | HIGH if attempted | Init fails | Logical FK only; D-19 already specifies |
| R10 | SSE chunk fragmentation crashes FE | MEDIUM | Mid-stream JSON parse error | Buffer accumulator pattern §Pitfall 4 |
| R11 | Title contains 50 byte but multibyte VN chars truncate broken | LOW | UI artifact | Use `Array.from(s).slice(0,50).join('')` (codepoint-safe) instead of `s.slice(0,50)` |
| R12 | Quick-reply chips don't match seeded catalog | LOW | Demo-bad first impression | Verify against Phase 16 IMAGES.csv brands during Wave 3 |

---

## Implementation Order Recommendation (§14 → seed for planner)

### Wave 1 — Foundations (sequential, blocks everything)
- **22-01-PLAN.md — Stack install + env + pg pool + schema init**
  - npm install `@anthropic-ai/sdk@0.92.0 pg jose react-markdown @types/pg`
  - Patch `docker-compose.yml frontend.environment` with new env vars
  - Add `.env.example` (root) + `sources/frontend/.env.local.example`
  - Implement `lib/chat/pg.ts` (singleton Pool)
  - Implement `lib/chat/schema-init.ts` (idempotent, with `VARCHAR(36)` user_id)
  - Implement `lib/chat/auth.ts` (jose verify) + `lib/chat/rate-limit.ts` + `lib/chat/vn-text.ts`
  - Implement `lib/chat/anthropic.ts` (singleton client + SYSTEM_PROMPT_VN)
  - Implement `lib/chat/product-context.ts` (search + fallback + buildContextXml)
  - Implement `lib/chat/messages-repo.ts` (createSession, loadHistory, append, listSessions)
  - **Verification:** `tsc --noEmit`; manual `curl POST /api/chat/stream` (after Wave 2) — Wave 1 just compile-clean.

### Wave 2 — API routes (parallel after Wave 1)
- **22-02-PLAN.md — `POST /api/chat/stream` route**
  - Verify JWT, rate-limit, ensure schema, persist user msg, load history, build context, stream Anthropic, persist assistant msg, abort propagation.
  - `runtime = 'nodejs'`, `dynamic = 'force-dynamic'`.
- **22-03-PLAN.md — Sessions/messages CRUD routes**
  - `GET /api/chat/sessions` (paginate)
  - `GET /api/chat/sessions/[id]/messages`
  - Owner-only check via JWT subject.
- **22-04-PLAN.md — `POST /api/admin/orders/[id]/suggest-reply`**
  - Admin role check, fetch order via api-gateway, 1-shot Anthropic call, return JSON `{text}`.

### Wave 3 — Customer Chat UI (after Wave 2)
- **22-05-PLAN.md — FloatingChatButton + ChatPanel + Composer + Bubble + QuickReplyChips**
  - Mount in `app/layout.tsx` (or `ConditionalShell`).
  - Guest variant → "Đăng nhập để chat" link.
  - SSE consumer with buffer accumulator.
  - Sessions list sidebar (load via `GET /api/chat/sessions`).
  - Empty state 3 chips.
  - react-markdown for assistant bubble.
  - Toast on error + "Thử lại" button.

### Wave 4 — Admin button + E2E + docs (after Wave 2 + Wave 3)
- **22-06-PLAN.md — Admin order detail "AI suggest reply" button**
  - Add button to `app/admin/orders/[id]/page.tsx`.
  - Modal showing generated text, "Sao chép" button, manual edit.
- **22-07-PLAN.md — Playwright E2E suite + manual UAT**
  - 3 spec files per Validation Architecture Wave 0 list.
  - SSE mock helper `tests/utils/mockChatStream.ts`.
  - 22-VERIFICATION.md sections: smoke (curl), DB inspection, rate-limit drill, key-leak grep, manual VN review.

**Dependency graph:**
```
22-01 (foundations) → 22-02 (stream) → 22-05 (customer UI)
                   ↘ 22-03 (sessions)  ↗
                   ↘ 22-04 (admin route) → 22-06 (admin button)
                                        → 22-07 (E2E + verify)
```

**Estimated wall-clock (single dev, sequential within wave):** ~6-10 hours total. Wave 1 = 1.5h, Wave 2 = 2-3h, Wave 3 = 2-3h, Wave 4 = 1-2h.

---

## Metadata

**Confidence breakdown:**
- Standard stack: **HIGH** — all versions verified npm, locked decisions inherited
- Architecture: **HIGH** — patterns verified from official docs + codebase inspection
- Pitfalls: **HIGH** — concrete failure modes verified from SDK docs + Next.js docs + project codebase
- Cost estimate: **MEDIUM** — Haiku 4.5 prices ASSUMED, must verify
- Discrepancies (R1 user_id type): **HIGH** confidence flagged; needs user/planner decision

**Research date:** 2026-05-02
**Valid until:** 2026-06-02 (Anthropic SDK + Next.js evolve rapidly; revalidate if phase delayed)

---

## RESEARCH COMPLETE
