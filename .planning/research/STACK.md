# Technology Stack — v1.3 Additions

**Project:** tmdt-use-gsd (e-commerce Spring Boot + Next.js)
**Researched:** 2026-05-02
**Scope:** Stack ADDITIONS cho v1.3 — chỉ liệt kê thư viện MỚI, không re-research stack đã lock

---

## Existing Stack (DO NOT re-research)

| Layer | Technology | Version | Status |
|-------|------------|---------|--------|
| Frontend framework | Next.js | 16.2.3 | LOCKED |
| UI state / forms | react-hook-form + zod | 7.74 / 4.3.6 | LOCKED |
| Backend | Spring Boot microservices | Java 17 | LOCKED |
| Database | Postgres 16 + JPA + Flyway | V5 schemas | LOCKED |
| Auth | JWT HS256 24h + BCrypt | — | LOCKED |
| E2E tests | Playwright | 1.59.1 | LOCKED |
| API codegen | openapi-typescript | 7.13.0 | LOCKED |
| Dev infra | Docker Compose | — | LOCKED |

Frontend hiện tại KHÔNG có: charting lib, AI SDK, streaming utilities — đây là 3 additions cần chọn.

---

## Stack Additions Cần Cho v1.3

### 1. Charting Library — Admin Analytics Dashboard

**Chọn: Recharts v3.8.1**

| Criterion | Recharts | Chart.js (react-chartjs-2) | Nivo |
|-----------|----------|--------------------------|------|
| Bundle size | ~290KB | ~213KB | ~186KB |
| React-native API | Yes — JSX components | No — imperative Canvas | Yes — JSX components |
| TypeScript support | Built-in (v2.5+) | Via DefinitelyTyped | Built-in |
| Next.js App Router | `use client` + works | `use client` + works | `use client` + works |
| SSR compatibility | SVG-based, no issue | Canvas, no SSR | SVG-based, no issue |
| shadcn/ui ecosystem | Natively integrated | Not integrated | Not integrated |
| Maintenance 2026 | Active (v3.8.1 May 2026) | Active | Active |
| Vietnamese locale | Không dùng locale formatter | Dùng locale → risk | Không dùng locale formatter |
| Learning curve | Low — composable props | Medium — config objects | Medium — prop-heavy |

**Rationale chọn Recharts:**
- Frontend đã dùng React 19 + Next.js 16 App Router — Recharts là composable SVG-based, tích hợp tự nhiên nhất
- 4 chart types cần (area/revenue-over-time, bar/top-products, pie/order-status, line/signups + low-stock) đều có sẵn: `AreaChart`, `BarChart`, `PieChart`, `LineChart`
- `ResponsiveContainer` wrapper giải quyết responsive không cần config thêm
- Không dùng `Intl.NumberFormat` hay en-US locale formatter (Chart.js tooltip mặc định dùng locale) — an toàn cho Vietnamese UI
- Bundle size 290KB chấp nhận được cho admin-only routes (không ảnh hưởng public pages)
- Version 3.8.1 là latest stable tính đến May 2026 (verified qua npm registry)

**Không chọn Chart.js:** Canvas-based, tooltip formatter mặc định dùng browser locale có thể gây en-US format cho số, không React-native.  
**Không chọn Nivo:** Bundle nặng hơn tổng, thừa accessibility features cho internal admin, config phức tạp hơn Recharts với cùng chart types.

**Installation:**
```bash
npm install recharts@3.8.1
```

**Integration point:** `'use client'` components trong `app/admin/` — tất cả chart components phải là client components. Pattern:

```tsx
// app/admin/components/RevenueChart.tsx
'use client'
import { AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts'

export function RevenueChart({ data }: { data: { date: string; revenue: number }[] }) {
  return (
    <ResponsiveContainer width="100%" height={300}>
      <AreaChart data={data}>
        <XAxis dataKey="date" />
        <YAxis tickFormatter={(v) => `${(v/1000).toFixed(0)}k`} />
        <Tooltip formatter={(v: number) => [`${v.toLocaleString('vi-VN')}₫`, 'Doanh thu']} />
        <Area type="monotone" dataKey="revenue" stroke="#6750A4" fill="#EAD5F9" />
      </AreaChart>
    </ResponsiveContainer>
  )
}
```

---

### 2. Claude API Integration — AI Chatbot

#### 2a. Approach: Next.js API Route Proxy (không thêm Spring Boot service)

**Chọn: Next.js API Route (`app/api/chat/route.ts`) gọi trực tiếp `@anthropic-ai/sdk`**

| Criterion | Next.js API Route Proxy | Spring Boot Chat Service mới |
|-----------|------------------------|------------------------------|
| Độ phức tạp | Low — 1 file route handler | High — new microservice + Docker |
| Thời gian implement | ~2 giờ | ~1-2 ngày |
| Latency overhead | Minimal — same process | +network hop gateway→chat-svc |
| Secret management | `ANTHROPIC_API_KEY` trong Next.js .env | Cần propagate qua gateway |
| Prompt caching | Trivially thêm vào route handler | Cần Spring AI config |
| Chat history persist | Gọi Order/User svc qua gateway | Cần design DB schema riêng |
| Phù hợp v1.3 scope | Yes — 1 phase chatbot MVP | No — overengineered cho MVP |
| Vendor lock-in risk | Low — Anthropic SDK, swap dễ | Low nhưng thêm Spring AI dep |

**Rationale:** v1.3 scope là "Claude API MVP — NO agentic tool-use". Next.js route proxy là standard pattern cho chatbot đơn giản. Spring Boot service chỉ hợp lý khi cần: auth riêng, rate-limit backend, hoặc multiple FE apps consume cùng 1 chat API. Với 1 FE app và MVP scope, proxy trong Next.js đủ và nhanh hơn 5x về implementation time.

Chat history persist DB sẽ gọi qua existing User/Order service API — không cần service mới.

#### 2b. Package: `@anthropic-ai/sdk`

**Chọn: `@anthropic-ai/sdk@0.92.0`** (latest stable, verified npm registry 2026-05-02)

Không dùng Vercel AI SDK (`ai@6.0.174`) vì:
- AI SDK 6.0 thay đổi architecture lớn (Server Actions thay REST route) — breaking change cho existing pattern
- AI SDK 6 có bug documented: dynamic chat instance streaming breaks permanently (issue #10926)
- `@anthropic-ai/sdk` direct cho phép control tốt hơn prompt caching `cache_control` syntax
- Frontend v1.3 không cần provider-agnostic abstraction (chỉ dùng Claude)
- Dependency nhẹ hơn — `@anthropic-ai/sdk` vs `ai` + `@ai-sdk/anthropic` + types

**Installation:**
```bash
# Frontend only (Next.js API route)
npm install @anthropic-ai/sdk@0.92.0
```

**Environment variable thêm vào `.env.local`:**
```
ANTHROPIC_API_KEY=sk-ant-...
```

#### 2c. Route Handler Pattern (với Prompt Caching từ ngày 1)

```typescript
// app/api/chat/route.ts
import Anthropic from '@anthropic-ai/sdk'
import { NextRequest } from 'next/server'

const anthropic = new Anthropic({ apiKey: process.env.ANTHROPIC_API_KEY })

const SYSTEM_PROMPT = `Bạn là trợ lý tư vấn mua sắm cho cửa hàng điện tử tmdt.
Hỗ trợ khách hàng: hỏi về sản phẩm, so sánh laptop/điện thoại/phụ kiện, 
gợi ý theo budget, theo dõi đơn hàng. Trả lời bằng tiếng Việt.
[...static product catalog context goes here — cache this block...]`

export const runtime = 'nodejs' // Edge runtime không hỗ trợ full Node streams

export async function POST(req: NextRequest) {
  const { messages } = await req.json()

  const stream = await anthropic.messages.stream({
    model: 'claude-haiku-4-5',  // Haiku: cost-optimal cho chatbot FAQ
    max_tokens: 1024,
    system: [
      {
        type: 'text',
        text: SYSTEM_PROMPT,
        cache_control: { type: 'ephemeral' }  // Cache system prompt + catalog
      }
    ],
    messages,
  })

  const encoder = new TextEncoder()
  const readable = new ReadableStream({
    async start(controller) {
      stream.on('text', (text) => {
        controller.enqueue(encoder.encode(`data: ${JSON.stringify({ type: 'text', text })}\n\n`))
      })
      stream.on('message', (msg) => {
        controller.enqueue(encoder.encode(`data: ${JSON.stringify({ type: 'done', usage: msg.usage })}\n\n`))
      })
      stream.on('error', (err) => {
        controller.enqueue(encoder.encode(`data: ${JSON.stringify({ type: 'error', error: err.message })}\n\n`))
        controller.close()
      })
      await stream.finalMessage()
      controller.close()
    },
  })

  return new Response(readable, {
    headers: {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Connection': 'keep-alive',
    },
  })
}
```

**Prompt caching strategy:**
- System prompt (FAQ + catalog context) → `cache_control: { type: 'ephemeral' }` (5-min TTL, auto-refresh)
- Minimum 1024 tokens để cache activate — system prompt phải đủ dài (thêm product descriptions)
- Cache saves ~90% cost trên cached tokens sau lần đầu
- Monitor via `response.usage.cache_read_input_tokens` / `cache_creation_input_tokens`

**Model chọn cho chatbot:** `claude-haiku-4-5` — cost-optimal, fast response (<1s TTFT), đủ cho FAQ + recommendation. Chỉ escalate lên `claude-sonnet-4-5` nếu cần reasoning phức tạp hơn.

#### 2d. Streaming UI — EventSource / fetch + ReadableStream

**Không cần thêm thư viện streaming UI.** Dùng native `fetch` + `ReadableStream` reader trực tiếp:

```tsx
// components/ChatWidget.tsx (client component)
'use client'
const [streamText, setStreamText] = useState('')

async function sendMessage(userMsg: string) {
  const res = await fetch('/api/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ messages: [...history, { role: 'user', content: userMsg }] })
  })
  
  const reader = res.body!.getReader()
  const decoder = new TextDecoder()
  
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    const chunk = decoder.decode(value)
    // Parse SSE data: lines starting with "data: "
    for (const line of chunk.split('\n')) {
      if (!line.startsWith('data: ')) continue
      const payload = JSON.parse(line.slice(6))
      if (payload.type === 'text') setStreamText(prev => prev + payload.text)
    }
  }
}
```

Không dùng `EventSource` API vì EventSource chỉ hỗ trợ GET requests — chatbot cần POST với body messages. Native fetch + ReadableStream reader là đúng approach.

---

### 3. Storage Audit Tooling

**Không cần thêm thư viện.** Dùng ripgrep (rg) đã có trong Windows dev environment:

```bash
# Tìm tất cả localStorage/sessionStorage usage trong FE source
rg "localStorage|sessionStorage" sources/frontend/app/ sources/frontend/components/ --type ts --type tsx -n
```

Pattern classify:
- `localStorage.setItem('cart*')` → migrate sang DB (cart confirmed leak)
- `localStorage.setItem('token'|'user')` → giữ hoặc move sang httpOnly cookie (security)
- `sessionStorage.*` → classify per use-case

Không cần install gì thêm.

---

### 4. Coupon System

**Không cần thêm thư viện.** Implement thuần:
- Backend: Flyway migration V6 (coupon table) + Spring Boot CouponService với validation logic
- Frontend: Input field trong checkout flow, gọi `POST /api/orders/coupons/validate`
- Validation logic: % off / fixed amount, min order, expiry timestamp, max-usage-per-user (counter trên DB)

---

### 5. Order Detail Items Fix

**Không cần thêm thư viện.** Debug và fix DTO mapping + FE render:
- Backend: kiểm tra `OrderItemEntity` mapping trong `OrderResponse` DTO — likely thiếu `@JsonProperty` hoặc lazy-load issue
- Frontend: kiểm tra `openapi-typescript` generated type cho `orderItems` array — có thể undefined check bị thiếu

---

## Full Installation Commands

```bash
# Trong thư mục sources/frontend/
npm install recharts@3.8.1
npm install @anthropic-ai/sdk@0.92.0
```

Tổng 2 packages mới. Không thêm gì khác.

---

## What NOT To Add

| Package | Lý do KHÔNG dùng |
|---------|-----------------|
| `ai` (Vercel AI SDK) | SDK 6.0 breaking changes (Server Actions), streaming bug documented, overkill cho single-provider MVP |
| `@ai-sdk/anthropic` | Phụ thuộc vào Vercel AI SDK — tránh cả cụm |
| `react-chartjs-2` + `chart.js` | Canvas-based, locale formatter issue cho VN numbers, không React-native |
| `@nivo/*` | Bundle nặng, overkill cho 4 chart types, complexity cao hơn cần thiết |
| `visx` | Low-level D3 wrapper, cần nhiều boilerplate, không phù hợp timeline |
| `EventSource` polyfill | Native EventSource không hỗ trợ POST body — dùng fetch+ReadableStream thay |
| `swr` / `react-query` | Data fetching đã dùng native fetch pattern — không cần thêm layer |
| Spring AI (Java) | Chat-svc riêng overkill cho MVP — Next.js API route đủ |

---

## Environment Variables Mới

```bash
# .env.local (frontend)
ANTHROPIC_API_KEY=sk-ant-api03-...   # Required cho chatbot

# KHÔNG cần thêm gì vào Spring Boot services
```

---

## Confidence Assessment

| Area | Level | Source |
|------|-------|--------|
| Recharts version (3.8.1) | HIGH | npm registry verified 2026-05-02 |
| Recharts chart types coverage | HIGH | Official docs + Context7 |
| `@anthropic-ai/sdk` version (0.92.0) | HIGH | npm registry verified 2026-05-02 |
| Prompt caching `cache_control` syntax | HIGH | Official Anthropic docs fetched |
| Next.js route proxy vs Spring Boot | HIGH | Architecture rationale + community patterns |
| Streaming pattern (fetch + ReadableStream) | HIGH | Multiple 2026 sources, official SDK docs |
| Vercel AI SDK 6.0 breaking changes | MEDIUM | GitHub issue + official blog (verified) |
| Storage audit = no new lib needed | HIGH | ripgrep standard tool |

---

## Sources

- [Recharts npm package (v3.8.1 latest)](https://www.npmjs.com/package/recharts)
- [Recharts vs chart.js vs nivo comparison 2026 — pkgpulse](https://www.pkgpulse.com/guides/recharts-vs-chartjs-vs-nivo-vs-visx-react-charting-2026)
- [Best React chart libraries 2025 — LogRocket](https://blog.logrocket.com/best-react-chart-libraries-2025/)
- [@anthropic-ai/sdk npm](https://www.npmjs.com/package/@anthropic-ai/sdk)
- [Anthropic Claude prompt caching official docs](https://platform.claude.com/docs/en/build-with-claude/prompt-caching)
- [Building production Claude streaming with Next.js Edge — DEV Community](https://dev.to/bydaewon/building-a-production-ready-claude-streaming-api-with-nextjs-edge-runtime-3e7)
- [How to stream Claude API responses in Next.js](https://vadimall.com/posts/Stream-claude-api-responses-in-nextjs)
- [Vercel AI SDK 6 release notes](https://vercel.com/blog/ai-sdk-6)
- [AI SDK 6.0 migration guide](https://ai-sdk.dev/docs/migration-guides/migration-guide-6-0)
- [Anthropic TypeScript SDK GitHub](https://github.com/anthropics/anthropic-sdk-typescript)
