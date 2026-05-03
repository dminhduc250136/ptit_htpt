# Phase 22: AI Chatbot Claude API MVP - Context

**Gathered:** 2026-05-02
**Status:** Ready for planning
**Mode:** `--auto --chain` (Claude auto-selected recommended options per gray area; user reviews here before plan/execute)

<domain>
## Phase Boundary

Phase 22 giao một chatbot AI MVP cho khách hàng đã đăng nhập (FAQ + Q&A sản phẩm + recommendation tiếng Việt, streaming token-by-token, lịch sử chat persist DB) cộng thêm nút "AI suggest reply" trong admin order detail (manual review trước khi gửi). API key Anthropic KHÔNG bao giờ rò rỉ ra browser — toàn bộ trafic Claude API đi qua Next.js API route proxy.

**Trong phạm vi:** AI-01..AI-05 (xem REQUIREMENTS.md).
**Ngoài phạm vi (defer):** agentic tool-use, function calling, RAG embedding, multimodal, voice input, guest chat, multilingual ngoài Vietnamese, helpful-vote/feedback chat, auto-confirm order từ chatbot.

</domain>

<decisions>
## Implementation Decisions

Các decisions dưới đây kế thừa từ `STATE.md > v1.3 locks` (đã lock từ research + user answers ngày 2026-05-02). Phần auto-selected của discuss-phase này chỉ bổ sung HOW chưa locked.

### Inherited Locks (từ STATE.md, không re-discuss)
- **D-01:** Provider = Anthropic Claude API; SDK `@anthropic-ai/sdk@0.92.0`; model `claude-haiku-4-5`. Lý do: chi phí thấp + đủ chất lượng cho FAQ + product Q&A tiếng Việt.
- **D-02:** Streaming UI = native `ReadableStream` (Web Streams API) + `fetch` body reader trong React component — KHÔNG dùng Vercel AI SDK. Lý do: tránh dependency lớn cho MVP, kiểm soát hoàn toàn wire format.
- **D-03:** Persistence = schema riêng `chat_svc` trên Postgres dùng chung instance, init schema **qua Next.js API route raw `pg` driver**, KHÔNG tạo Spring Boot microservice mới, KHÔNG dùng Flyway cho schema này.
- **D-04:** Prompt caching bắt buộc từ ngày 1 — `cache_control: { type: 'ephemeral' }` trên system prompt + product context block. Lý do: giảm chi phí + latency ngay khi rollout.
- **D-05:** Login required cho cả customer chatbot lẫn admin suggest-reply. Guest thấy nút "Đăng nhập để chat".
- **D-06:** Sliding window = 10 turns (20 messages user+assistant gần nhất) gửi vào API mỗi lần.
- **D-07:** Admin "AI suggest reply" KHÔNG auto-confirm order — chỉ generate text gợi ý, admin paste/edit trước khi gửi customer.
- **D-08:** KHÔNG agentic tool-use / function-calling / RAG embedding trong MVP.

### Chat UI Placement & States (auto-selected)
- **D-09:** FloatingChatButton render từ `app/layout.tsx` qua client wrapper, hiển thị mọi route (cả khi chưa login → button đổi thành "Đăng nhập để chat" với link `/login?next=…`). Lý do: 1 chỗ mount, không cần thêm provider, theo pattern existing layout.
- **D-10:** Modal mở từ button dùng lại `components/ui/Modal` (đã có), full-height phía bottom-right desktop, full-screen mobile (≤640px). State open/close giữ trong React `useState` của wrapper, KHÔNG persist URL/query.
- **D-11:** Empty state (chưa có message): hiển thị 3 quick-reply chips ("Tư vấn laptop tầm 20 triệu?", "iPhone 15 Pro còn không?", "So sánh chuột Logitech vs Razer"). Click chip = autofill input và submit.
- **D-12:** Loading: trong khi đang stream, input bị disable, hiện indicator "Đang trả lời…" + assistant bubble với cursor nhấp nháy ở token cuối.
- **D-13:** Error: nếu stream fail (network/5xx/timeout), hiển thị toast lỗi tiếng Việt + assistant bubble có nút "Thử lại" gửi lại lần cuối.

### Streaming Wire Format (auto-selected)
- **D-14:** Wire format = **SSE-style chunks** (`text/event-stream`) — Anthropic SDK `client.messages.stream()` → forward từng `text_delta` về client qua `Response` với `ReadableStream` controller. Mỗi event là JSON `{ "type": "delta" | "done" | "error", "text"?: string, "error"?: string }`. Lý do: dễ debug DevTools, dễ extend metadata (tokens used) mà không phải parse raw text.
- **D-15:** Backpressure: chỉ flush controller khi nhận chunk từ SDK — không buffer trên server.

### Context Injection — Top-N Products (auto-selected)
- **D-16:** Ranking đơn giản: ILIKE keyword match trên `product.name + product.brand + category.name` từ user message (sau khi lowercase + trim VN diacritics). Lấy top 5, fallback top 5 sản phẩm bán chạy nếu match < 3.
- **D-17:** Inject vào user message, KHÔNG vào system prompt (giữ system cacheable). Format:
  ```
  <product_context>
    <product id="..." name="..." price="..." brand="..." stock="..."/>
    ...
  </product_context>

  <user_question>{nội dung khách}</user_question>
  ```
- **D-18:** Query products qua **product-service REST** (`GET /api/products?search=…&limit=5`) trong Next.js route handler, KHÔNG truy vấn trực tiếp `product_svc` schema từ chat route. Lý do: giữ ranh giới service.

### DB Schema — `chat_svc` (auto-selected)
- **D-19:** Tables (init qua raw pg trong route handler, idempotent `CREATE TABLE IF NOT EXISTS` chạy lần đầu app khởi động hoặc qua admin trigger):
  - `chat_svc.chat_sessions` — `id BIGSERIAL PK`, `user_id VARCHAR(36) NOT NULL` (logical FK đến `user_svc.users.id`, type khớp UUID hiện tại — verified bằng `V1__init_schema.sql`; KHÔNG hard FK cross-schema), `title VARCHAR(200)`, `created_at TIMESTAMPTZ DEFAULT now()`, `updated_at TIMESTAMPTZ DEFAULT now()`. Index `(user_id, updated_at DESC)`.
  - `chat_svc.chat_messages` — `id BIGSERIAL PK`, `session_id BIGINT NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE`, `role VARCHAR(16) NOT NULL CHECK (role IN ('user','assistant'))`, `content TEXT NOT NULL`, `created_at TIMESTAMPTZ DEFAULT now()`. Index `(session_id, created_at)`.
- **D-20:** Title auto-generate = 50 ký tự đầu của user message đầu tiên (trim + ellipsis); admin sau có thể đổi (out of scope MVP — chỉ auto).
- **D-21:** Idempotent init: 1 lần kiểm tra `to_regclass('chat_svc.chat_sessions')` mỗi process boot — nếu null thì chạy init script. KHÔNG chạy mỗi request.

### API Route Shape (auto-selected)
- **D-22:** Routes mới dưới `app/api/chat/`:
  - `POST /api/chat/stream` — body `{ sessionId?: number, message: string }`. Auth qua cookie/Bearer JWT hiện tại (verify giống `services/http.ts`). Tạo session mới nếu sessionId null. Persist user message NGAY khi nhận, persist assistant message SAU khi stream done. Trả `text/event-stream`.
  - `GET /api/chat/sessions` — list sessions của user (paginate `?limit=20&before=…`).
  - `GET /api/chat/sessions/[id]/messages` — list messages của 1 session (auth check: chỉ owner).
  - `POST /api/admin/orders/[id]/suggest-reply` — admin-only (kiểm tra role qua existing admin guard). Body trống. Trả 1-shot JSON `{ text: string }`, KHÔNG stream.
- **D-23:** Auth pattern: dùng lại helper từ `lib/auth/*` (nếu có) hoặc decode JWT trực tiếp trong route handler — clarify trong research.

### Cost & Safety Guards (auto-selected)
- **D-24:** Per-user rate limit: 20 messages / 5 phút, in-memory `Map<userId, timestamps[]>` trong Next.js process (đủ cho MVP single instance). Vượt = HTTP 429 + toast "Bạn nhắn quá nhanh, thử lại sau ít phút".
- **D-25:** Max input length = 2000 ký tự client-side + server-side validation. Max output `max_tokens = 1024` trong API call.
- **D-26:** Env var `ANTHROPIC_API_KEY` chỉ đọc server-side. Verify KHÔNG có biến `NEXT_PUBLIC_ANTHROPIC_*`.

### Claude's Discretion
- Tên cụ thể của FloatingChatButton component, cấu trúc folder (`components/chat/*` vs `app/_chat/*`) — planner quyết khi đọc convention thực tế.
- Thư viện markdown render cho assistant bubble (`react-markdown` vs `marked` + `dompurify`) — research/planner pick, nhưng MUST sanitize.
- Cấu trúc system prompt cụ thể (wording tiếng Việt) — researcher draft, user duyệt khi review plan.
- Cách ký test cho streaming (Playwright network mock vs jest unit) — planner quyết.

### Folded Todos
*(Không có pending todos liên quan Phase 22 — bỏ qua.)*

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Roadmap & Requirements
- `.planning/ROADMAP.md` §"Phase 22: AI Chatbot Claude API MVP" — Goal, success criteria, REQ mapping, plans hint.
- `.planning/REQUIREMENTS.md` AI-01..AI-05 — Acceptance criteria gốc.
- `.planning/STATE.md` §"Decisions (active v1.3 locks) > Chatbot AI" — toàn bộ locks đã chốt từ trước.

### Codebase Maps
- `.planning/codebase/STACK.md` — Next.js 16.2.3 + React 19.2.4, Spring Boot 3.3.2 microservices.
- `.planning/codebase/CONVENTIONS.md` — patterns FE service layer, error handling.
- `.planning/codebase/STRUCTURE.md` — vị trí `sources/frontend/src/{app,components,services,lib}`.
- `.planning/codebase/INTEGRATIONS.md` — gateway routing, cross-service contracts.

### Existing FE Assets (reuse)
- `sources/frontend/src/components/ui/Modal/` — modal hiện hữu, dùng cho chat panel.
- `sources/frontend/src/components/ui/Button/` + `Toast` — chuẩn UI primitives.
- `sources/frontend/src/services/http.ts` — fetch wrapper với auth/error envelope.
- `sources/frontend/src/services/auth.ts` — auth helpers (login/refresh).
- `sources/frontend/src/app/layout.tsx` — chỗ mount FloatingChatButton.

### External Specs (researcher đọc khi cần)
- Anthropic Messages API streaming docs (researcher fetch qua `mcp__context7__*` hoặc WebFetch khi tới phase research).
- Anthropic prompt-caching guide.
- `@anthropic-ai/sdk@0.92.0` README/changelog.

### Prior Phase Context (đã đọc)
- Không có CONTEXT.md cho phase trước (project chạy v1.3 từ phase 16 nhưng không phải phase nào cũng có discuss). Decisions tham chiếu trực tiếp từ STATE.md/PROJECT.md.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`components/ui/Modal`** — dùng làm panel chat (full-screen mobile, anchored bottom-right desktop bằng custom variant).
- **`components/ui/Button`, `Toast`, `Input`** — UI primitives đủ cho chat composer + error toast.
- **`services/http.ts`** — fetch wrapper có sẵn auth header + ApiErrorResponse parsing → dùng cho REST calls (`/api/chat/sessions`, suggest-reply). Stream call cần fetch raw vì SSE.
- **`services/auth.ts`** — JWT/Bearer pattern; chat client-side gọi cùng auth header.
- **Pattern admin guard** — admin order detail (`app/admin/orders/[id]`) đã có pattern check role qua middleware/page; tái dùng cho suggest-reply button visibility.

### Established Patterns
- **API-first FE** (memory `feedback_priority.md` visible-first; v1.3 cart→DB lock): mọi user-data đi qua REST/Next.js route, KHÔNG localStorage. Chat sessions/messages tuân thủ.
- **`ApiErrorResponse + traceId envelope`** (PROJECT.md lock): chat REST endpoints (`/sessions`, `/messages`, `/suggest-reply`) trả envelope chuẩn. Stream endpoint dùng SSE event `{type:'error'}` thay vì envelope (technical exception).
- **Vietnamese-first UI copy** (memory `feedback_language.md`): toàn bộ chat UI + system prompt + error messages tiếng Việt.

### Integration Points
- `app/layout.tsx` — mount `<FloatingChatButton />` global.
- `app/admin/orders/[id]/page.tsx` — thêm button "AI suggest reply" + handler.
- Postgres instance hiện tại (đang chứa `user_svc`, `product_svc`, `order_svc`) — thêm schema `chat_svc`. Connection string lấy từ env (cùng host/port các svc khác — research xác nhận).
- Env: thêm `ANTHROPIC_API_KEY` vào FE `.env.local` (server-only — không có `NEXT_PUBLIC_` prefix). `docker-compose.yml` cần inject biến này khi build/run.

</code_context>

<specifics>
## Specific Ideas

- 3 quick-reply chips đề xuất ban đầu (chỉnh theo catalog thực):
  1. "Tư vấn laptop tầm 20 triệu cho sinh viên?"
  2. "iPhone 15 Pro Max còn hàng không?"
  3. "So sánh chuột Logitech G Pro X và Razer DeathAdder V3"
- System prompt persona: "Bạn là **Trợ lý mua sắm tmdt-use-gsd**, chuyên về điện thoại / laptop / chuột / bàn phím / tai nghe. Trả lời ngắn gọn, thân thiện, tiếng Việt. Khi được hỏi về sản phẩm, ƯU TIÊN dùng dữ liệu trong `<product_context>` (giá, tên, brand, stock chính xác). KHÔNG bịa sản phẩm. Nếu sản phẩm không có trong context, nói rõ 'mình chưa thấy trong catalog hiện tại'."
- Admin suggest-reply prompt: nhận order JSON (id, items, total, status, customer name) → trả gợi ý reply 3-5 câu tiếng Việt, tone chuyên nghiệp, KHÔNG hứa hẹn ngoài thông tin order.

</specifics>

<deferred>
## Deferred Ideas

| Idea | Note |
|------|------|
| Agentic tool-use (gọi DB/API trực tiếp từ Claude) | Defer v1.4+ — MVP dùng context injection thay thế. |
| Function-calling + structured output | Defer v1.4+ |
| RAG embedding (semantic search Vietnamese) | Defer v1.4+ — keyword match đủ cho MVP. |
| Helpful-vote / 👍👎 trên message assistant | Defer v1.4+ |
| Multilingual (English) | Defer v1.4+ — MVP Vietnamese-only. |
| Guest chat (không login) | Out of scope (D-05 lock). |
| Auto-confirm order từ chatbot | Out of scope (D-07 lock). |
| Voice input / TTS output | Out of scope MVP. |
| Image upload (multimodal) | Out of scope MVP. |
| Cost dashboard (admin theo dõi token usage) | Defer v1.4+ — MVP chỉ log. |

</deferred>

---

*Phase: 22-ai-chatbot-claude-api-mvp*
*Context gathered: 2026-05-02*
*Mode: --auto --chain (recommendations auto-locked, user reviews here before plan)*
