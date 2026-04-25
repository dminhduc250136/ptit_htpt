# Phase 4: Frontend Contract Alignment + E2E Validation - Research

**Researched:** 2026-04-23
**Domain:** Next.js 16 App Router client + OpenAPI-driven typed HTTP client + manual E2E walkthrough
**Confidence:** HIGH

## Summary

This phase wires the existing mock-only Next.js frontend (`sources/frontend/`) to the Phase 1–3 backend contract: typed DTOs auto-generated from each service's `/v3/api-docs`, a native-fetch wrapper that auto-unwraps the `ApiResponse` envelope and throws a typed `ApiError`, standardized UX for the six error codes (`VALIDATION_ERROR`, `UNAUTHORIZED`, `FORBIDDEN`, `CONFLICT`, `NOT_FOUND`, `INTERNAL_ERROR`), route protection for `/checkout`, `/profile`, `/admin/*`, and a committed manual E2E checklist.

All implementation approach choices are already locked in `04-CONTEXT.md` (D-01..D-14). This research focuses on the concrete mechanics the planner needs to write grep-verifiable tasks:

- `openapi-typescript@7.13.0` (verified latest, published Feb 2026) invoked per-service against `http://localhost:808{1..6}/v3/api-docs` with output committed to `src/types/api/{service}.generated.ts`.
- Native `fetch` wrapper in `services/http.ts` that reads `accessToken` from `localStorage` at call time, attaches `Authorization: Bearer <token>`, parses the standard envelope, and throws `ApiError` with `{code, message, fieldErrors, status, traceId}`.
- **Critical resolved conflict:** D-11 (localStorage token) and D-12 (middleware protection) cannot both function as originally described — `localStorage` is a browser-only API and Next.js server-side proxy/middleware runs outside the browser. **Resolution:** set a presence-only cookie (`auth_present=1`, non-httpOnly) alongside `localStorage` at login; `middleware.ts` reads the cookie. The access token itself stays in `localStorage` as D-11 dictates.
- **Next.js 16 naming note (non-blocking):** `middleware.ts` is deprecated in Next.js 16 and renamed to `proxy.ts`. `middleware.ts` still works with a console warning through Next.js 16.x, so D-12's literal filename is honored; this RESEARCH flags the naming as a Phase-5+ follow-up.
- Client-only cart in `localStorage['cart']` with the existing `CartItem[]` shape, transformed to `CreateOrderRequest` at checkout submit.

**Primary recommendation:** Split Phase 4 into **three plans**: (P1) typed HTTP tier + env + codegen + `AuthProvider`, (P2) page rewires + error-recovery UI components + cart/checkout flow, (P3) route protection + E2E UAT checklist + cleanup. Details in § Plan Skeleton Recommendation.

## User Constraints (from CONTEXT.md)

### Locked Decisions (must research THESE, not alternatives)

**API client + fetch wrapper (Area 1)**
- **D-01:** Native `fetch` + custom wrapper. No TanStack Query, no SWR. Keep function-based abstraction like current `services/api.ts`; swap mock internals for real HTTP.
- **D-02:** Wrapper auto-unwraps envelope on success (returns `data` directly) and throws typed `ApiError` (fields: `code`, `message`, `fieldErrors`, `status`, `traceId`) on failure. Callers use `try/catch`.
- **D-03:** `sources/frontend/src/services/http.ts` core module (baseURL, auth header, envelope parsing, error throwing) + per-domain modules `services/products.ts`, `services/auth.ts`, `services/orders.ts`, `services/cart.ts`, `services/payments.ts`, `services/inventory.ts`, `services/notifications.ts`.

**DTO alignment with OpenAPI (Area 2)**
- **D-04:** Auto-generate FE TypeScript types from backend OpenAPI. Manual sync banned for backend-derived DTOs.
- **D-05:** `openapi-typescript` (pure type output). NOT `orval`, `@hey-api/openapi-ts`, or `swagger-typescript-api`.
- **D-06:** Spec source is per-service `/v3/api-docs`. One types file per service (e.g., `types/api/products.generated.ts`). No gateway-aggregated spec for codegen.

**Error code → UX mapping (Area 3)**
- **D-07:** `VALIDATION_ERROR` (400 + `fieldErrors[]`): inline error under each `<Input>` + single top banner `"Vui lòng kiểm tra các trường bị lỗi"`.
- **D-08:** `UNAUTHORIZED` (401): `http.ts` clears tokens and silently redirects to `/login?returnTo=<current-path>`. No toast.
- **D-09:** `CONFLICT` (409) including stock shortage and payment mock failure: modal with recovery actions. Stock: "Cập nhật số lượng" / "Xóa khỏi giỏ". Payment: "Thử lại" / "Đổi phương thức thanh toán".
- **D-10:** `INTERNAL_ERROR` (5xx) + network: toast `"Đã có lỗi, vui lòng thử lại"` + inline retry button. Wrapper must NOT auto-retry mutating requests (POST/PUT/DELETE).

**Auth + E2E validation (Area 4)**
- **D-11:** `accessToken` and `refreshToken` in `localStorage`. `http.ts` attaches `Authorization: Bearer <token>`. XSS tradeoff accepted.
- **D-12:** Protect `/checkout`, `/profile`, `/admin/*` via root-level `sources/frontend/middleware.ts` (presence check only). Missing → `/login?returnTo=<path>`.
- **D-13:** Committed `04-UAT.md` checklist with happy path + 3 mandatory failure cases (validation blank field, stock conflict, payment mock failure). No Playwright.
- **D-14:** Cart in `localStorage['cart']`. On "Đặt hàng", POST full `CreateOrderRequest` to `/api/orders`. No intermediate cart endpoint.

### Claude's Discretion

- Exact shape of `ApiError` (class vs discriminated union), as long as D-02 contract holds.
- `npm run gen:api` script implementation (single loop vs per-service scripts).
- `FORBIDDEN` (403) default: toast `"Bạn không có quyền"` + stay on page, unless the route itself is admin (redirect to `/`).
- `NOT_FOUND` (404): context-dependent — empty list for collection endpoints, 404 page for direct resource navigation.
- Token refresh flow (silent refresh vs re-login on 401): planner may choose based on backend `refreshToken` endpoint availability; D-08 still applies if refresh also fails.
- Internal file/package structure within `services/` as long as D-03 module split preserved.
- Delete or keep `mock-data/`: decide during plan after checking remaining references.

### Deferred Ideas (OUT OF SCOPE)

- Server-side cart via order-service cart endpoint.
- Automated E2E via Playwright/Cypress.
- httpOnly cookie auth + CSRF.
- CI check that generated types are fresh.
- Token refresh with silent rotation (planner-optional only).
- Admin area rewiring (`/admin/*`) — pages exist but are NOT the UAT walkthrough target.
- Search UX overhaul.
- Rollback of `mock-data/` — deferred to plan phase.

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FE-01 | Frontend API client aligns to documented contracts (URLs, DTOs, status codes, error format). | § Standard Stack (openapi-typescript@7.13.0), § Architecture Patterns (http.ts + per-domain split, envelope parser), § Code Examples (codegen script, http wrapper skeleton). |
| FE-02 | Checkout and cart flows handle error cases gracefully (validation, stock, payment, auth). | § Code Examples (ApiError → UX dispatcher), § Architecture Patterns (modal/banner components from UI-SPEC), § Pitfalls (do-not-auto-retry mutations, race on cart localStorage, stock-shortage discriminator). |

## Phase Goal Alignment

Phase 4 success criteria (from ROADMAP):
1. FE uses documented endpoints/DTOs and handles standardized errors without breaking UX. → **Addressed by** codegen pipeline (D-04..D-06) + typed wrapper (D-01..D-03) + error dispatcher (D-07..D-10).
2. Shopping flow validated E2E: browse → cart → checkout → payment (mock) → confirmation. → **Addressed by** `services/products.ts`, `services/orders.ts`, client-only cart (D-14), and committed UAT checklist (D-13).
3. Failures during checkout (validation, stock, payment) surfaced clearly and recoverably. → **Addressed by** Validation Banner + Error Recovery Modal (UI-SPEC surfaces 1 & 2) + FE-02 verification in UAT.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Envelope parse + typed error throw | Browser / Client (SPA) | — | All calls originate from `'use client'` pages today; `http.ts` is a browser module using `fetch`. |
| Auth token storage + Authorization header | Browser / Client (SPA) | — | `localStorage` is browser-only per D-11. |
| Route protection (pre-render) | Frontend Server (Next.js proxy/middleware) | — | `middleware.ts` runs on the Next.js server before rendering; must use cookies not localStorage. |
| OpenAPI → TS types generation | Build-time (Node.js dev script) | — | Runs via `npm run gen:api` locally; output committed. Not a runtime concern. |
| Cart persistence | Browser / Client (SPA) | — | D-14 locks client-only `localStorage`. |
| Order submission | Browser → API Gateway → order-service | — | Single POST `/api/orders` per D-14; no intermediate FE-side cart endpoint. |
| Contract smoke / OpenAPI spec hosting | API / Backend (springdoc) | — | Each service exposes `/v3/api-docs` via springdoc-openapi 2.6.0 (Phase 1 baseline). |
| Error format compatibility | API Gateway | API / Backend | Pass-through when envelope matches, normalize otherwise (Phase 3 D-05..D-07). |

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `openapi-typescript` | **7.13.0** | Generate TS types from OpenAPI 3.0 specs. Pure type output, no runtime. | [VERIFIED: `npm view openapi-typescript version` → 7.13.0, published 2026-02-11]. Matches D-05 constraint (pure types, no hooks). Supports remote URL input natively. |
| Native `fetch` | Built-in | HTTP client. | [CITED: MDN/Next.js 16]. D-01 locks native fetch; no client lib. |

**Installation:**
```bash
npm install -D openapi-typescript@7.13.0
```

(dev dependency only — codegen runs locally, output is committed JS/TS. No runtime package.)

**Version verification performed 2026-04-23:**
- `openapi-typescript@7.13.0` — latest stable, 156 total versions, peer `typescript: ^5.x` (project has `typescript: ^5` ✓).
- Backend `springdoc-openapi-starter-webmvc-ui@2.6.0` (already installed, Phase 1) emits OpenAPI 3.0.1 JSON at `/v3/api-docs` — compatible with `openapi-typescript` input.

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| None additional | — | — | Phase scope is intentionally tight: no axios, no query lib, no form lib. D-01 locks this. |

### Alternatives Considered (and ruled out by CONTEXT.md)

| Instead of | Alternative | Why Rejected |
|------------|-------------|--------------|
| native `fetch` | `axios` | D-01: wrapper pattern must stay on native fetch. |
| `openapi-typescript` | `orval` | D-05: generates React Query hooks → conflicts with D-01. |
| `openapi-typescript` | `@hey-api/openapi-ts` | D-05: heavier than needed for MVP. |
| `openapi-typescript` | `swagger-typescript-api` | D-05: generates runtime client → conflicts with D-01 wrapper pattern. |
| per-service codegen | gateway-aggregated spec | D-06: Spring Cloud Gateway does not aggregate springdoc output natively. The `springdoc.swagger-ui.urls` list in `api-gateway/application.yml` is only a Swagger-UI multi-spec picker, not a merged spec. |
| `localStorage` token | httpOnly cookie auth | D-11 + Deferred Ideas: locked for MVP; httpOnly is a future security phase. |
| Playwright E2E | — | D-13: manual UAT checklist only. |

## Architecture Patterns

### System Architecture Diagram

```
                   ┌───────────────────────────────────────────────┐
                   │        User Browser (Next.js 16 SPA)          │
                   │                                               │
  User click ───▶  │  Page Component (use client)                  │
                   │       │                                       │
                   │       ▼                                       │
                   │  services/{domain}.ts  (typed function)       │
                   │       │                                       │
                   │       ▼                                       │
                   │  services/http.ts  — the wrapper              │
                   │    1. read  localStorage.accessToken          │
                   │    2. set   Authorization: Bearer <token>     │
                   │    3. fetch NEXT_PUBLIC_API_BASE_URL + path   │
                   │    4. parse envelope:                         │
                   │         ok → return data  (unwrap)            │
                   │         !ok → throw ApiError                  │
                   │                                               │
                   │  [ApiError caught by caller]                  │
                   │       │                                       │
                   │       ▼                                       │
                   │  Error Dispatcher (code → UX)                 │
                   │    VALIDATION_ERROR → inline + Banner         │
                   │    UNAUTHORIZED     → clear tokens + redirect │
                   │    FORBIDDEN        → toast (+ maybe redirect)│
                   │    CONFLICT         → Modal (stock|payment)   │
                   │    NOT_FOUND        → context-dependent       │
                   │    INTERNAL_ERROR   → toast + Retry section   │
                   └───────────────────────────┬───────────────────┘
                                               │
                           (HTTP over network)│
                                               │
                                               ▼
  Pre-render protection path                   │
  ┌────────────────────────────────────────────┴─────┐
  │  Next.js Server (middleware.ts)                  │
  │  matcher: /checkout, /profile, /admin/:path*     │
  │  read cookie("auth_present") → if missing:       │
  │    NextResponse.redirect("/login?returnTo=...")  │
  └──────────────────────────────────────────────────┘
                                               │
                                               ▼
                   ┌───────────────────────────────────────────────┐
                   │  API Gateway  (localhost:8080)                │
                   │  CORS: allowedOrigins [http://localhost:3000] │
                   │  Routes:                                      │
                   │    /api/users/**       → user-service:8080    │
                   │    /api/products/**    → product-service:8080 │
                   │    /api/orders/**      → order-service:8080   │
                   │    /api/payments/**    → payment-service:8080 │
                   │    /api/inventory/**   → inventory-service    │
                   │    /api/notifications  → notification-service │
                   │  Error handler: pass-through compliant bodies │
                   │                 / normalize otherwise         │
                   └───────────────────────────────────────────────┘
                                               │
                                               ▼
                   ┌───────────────────────────────────────────────┐
                   │  Each Spring Boot service (springdoc 2.6.0)   │
                   │    /v3/api-docs        ← codegen input        │
                   │    GlobalExceptionHandler → ApiErrorResponse  │
                   │    ApiResponseAdvice → ApiResponse<T> envelope│
                   │    (skips /v3/api-docs/* and swagger paths)   │
                   └───────────────────────────────────────────────┘


Build-time (not runtime) — runs on dev workstation:

  npm run gen:api
     │
     ▼
  For each service in [users, products, orders, payments, inventory, notifications]:
     openapi-typescript http://localhost:808X/v3/api-docs \
       -o src/types/api/{service}.generated.ts
     │
     ▼
  git add src/types/api/*.generated.ts && git commit
```

### Recommended Project Structure

```
sources/frontend/
├── middleware.ts                     # D-12: root-level, NOT under src/
├── next.config.ts                    # (existing)
├── package.json                      # + "gen:api" script + "openapi-typescript" devDep
├── .env.local                        # NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
├── .env.example                      # commit documented default
├── scripts/
│   └── gen-api.mjs                   # loops over services, calls openapi-typescript
└── src/
    ├── services/
    │   ├── http.ts                   # NEW — core wrapper (D-01..D-03)
    │   ├── errors.ts                 # NEW — ApiError class + isApiError()
    │   ├── auth.ts                   # NEW — login/register/refresh wrappers
    │   ├── products.ts               # NEW — per-domain, imports ../types/api/products.generated
    │   ├── orders.ts                 # NEW
    │   ├── cart.ts                   # NEW — client-only, reads/writes localStorage
    │   ├── payments.ts               # NEW
    │   ├── inventory.ts              # NEW
    │   ├── notifications.ts          # NEW
    │   ├── token.ts                  # NEW — get/set/clear tokens + auth_present cookie
    │   ├── api.ts                    # REFACTOR — keeps formatPrice/formatPriceShort;
    │   │                             #   re-exports named fns from new modules as shims
    │   │                             #   during transition; remove after all pages migrated
    │   └── index.ts                  # NEW (optional) — barrel
    ├── types/
    │   ├── index.ts                  # EDIT — keep UI-shape types (ProductFilter, etc.);
    │   │                             #   remove backend-derived DTOs once mapped
    │   └── api/
    │       ├── users.generated.ts         # GENERATED
    │       ├── products.generated.ts      # GENERATED
    │       ├── orders.generated.ts        # GENERATED
    │       ├── payments.generated.ts      # GENERATED
    │       ├── inventory.generated.ts     # GENERATED
    │       └── notifications.generated.ts # GENERATED
    ├── providers/
    │   └── AuthProvider.tsx          # NEW — React context for current user + auth_present cookie sync
    ├── components/ui/
    │   ├── Banner/Banner.tsx         # NEW (UI-SPEC surface 1)
    │   ├── Modal/Modal.tsx           # NEW (UI-SPEC surface 2 shell)
    │   ├── RetrySection/RetrySection.tsx # NEW (UI-SPEC surface 3)
    │   ├── Input/…                   # (existing, no change)
    │   ├── Toast/…                   # (existing, + aria-label per UI-SPEC)
    │   ├── Button/…                  # (existing, no change)
    │   └── …
    └── app/
        ├── layout.tsx                # EDIT — wrap with <ToastProvider> and <AuthProvider>
        ├── cart/page.tsx             # REWRITE — localStorage cart per D-14
        ├── checkout/page.tsx         # REWRITE — real POST /api/orders + error recovery
        ├── login/page.tsx            # EDIT — wire to services/auth.ts + set token + cookie
        ├── register/page.tsx         # EDIT — same as login
        ├── profile/page.tsx          # EDIT — wire to services/orders.ts order history
        ├── products/page.tsx         # EDIT — wire to services/products.ts
        ├── products/[slug]/page.tsx  # EDIT — wire to services/products.ts
        └── search/page.tsx           # EDIT — wire to services/products.ts
```

### Pattern 1: Native-fetch wrapper + envelope unwrap + typed error (D-01/D-02)

**What:** Single `http.ts` module exposes `httpGet/httpPost/httpPut/httpPatch/httpDelete`. Each reads the token at call time, attaches headers, parses the `ApiResponse<T>` envelope on success, and throws `ApiError` on failure.

**When to use:** Every outgoing HTTP call in the frontend. Per-domain modules (`services/products.ts` etc.) are thin type-wrappers over `http.ts`.

**Example:**
```typescript
// Source: CONTEXT.md D-01..D-03, ApiErrorResponse.java (service-origin),
//         gateway/ApiErrorResponse.java (gateway-origin); both have identical keys.

// services/errors.ts
export interface FieldError { field: string; rejectedValue?: unknown; message: string; }
export class ApiError extends Error {
  constructor(
    public readonly code: string,             // 'VALIDATION_ERROR' | 'UNAUTHORIZED' | ...
    public readonly status: number,           // HTTP status
    message: string,
    public readonly fieldErrors: FieldError[] = [],
    public readonly traceId?: string,
    public readonly path?: string,
    public readonly details?: Record<string, unknown>, // for CONFLICT 'domainCode', 'items'
  ) { super(message); this.name = 'ApiError'; }
}
export function isApiError(e: unknown): e is ApiError { return e instanceof ApiError; }

// services/token.ts
const ACCESS_KEY = 'accessToken';
const REFRESH_KEY = 'refreshToken';
const PRESENCE_COOKIE = 'auth_present';

export function getAccessToken(): string | null {
  if (typeof window === 'undefined') return null;
  return window.localStorage.getItem(ACCESS_KEY);
}
export function setTokens(access: string, refresh: string): void {
  window.localStorage.setItem(ACCESS_KEY, access);
  window.localStorage.setItem(REFRESH_KEY, refresh);
  // Non-httpOnly presence cookie so middleware.ts can see the user is logged in.
  // Value is intentionally minimal ('1') — no PII, no JWT. Backend never reads this cookie.
  document.cookie = `${PRESENCE_COOKIE}=1; Path=/; SameSite=Lax; Max-Age=2592000`;
}
export function clearTokens(): void {
  window.localStorage.removeItem(ACCESS_KEY);
  window.localStorage.removeItem(REFRESH_KEY);
  document.cookie = `${PRESENCE_COOKIE}=; Path=/; SameSite=Lax; Max-Age=0`;
}

// services/http.ts
import { ApiError, type FieldError } from './errors';
import { getAccessToken, clearTokens } from './token';

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';

interface ApiEnvelope<T> {
  timestamp?: string;
  status?: number;
  message?: string;
  data: T;
}
interface ApiErrorBody {
  status: number;
  error?: string;
  message: string;
  code: string;
  path?: string;
  traceId?: string;
  fieldErrors?: FieldError[];
  details?: Record<string, unknown>;
}

async function request<T>(
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE',
  path: string,
  body?: unknown,
): Promise<T> {
  const token = getAccessToken();
  const headers: Record<string, string> = {
    'Accept': 'application/json',
  };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers['Authorization'] = `Bearer ${token}`;

  let res: Response;
  try {
    res = await fetch(`${BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'omit',       // token is in header, not cookie
    });
  } catch (networkErr) {
    // Classify as INTERNAL_ERROR for the dispatcher; no retry for mutations (D-10).
    throw new ApiError('INTERNAL_ERROR', 0, 'Network error', [], undefined, path);
  }

  const text = await res.text();
  const parsed = text ? JSON.parse(text) : null;

  if (res.ok) {
    // Success envelope: { timestamp, status, message, data }
    // If data is undefined (e.g., 204), return undefined as T.
    return (parsed as ApiEnvelope<T> | null)?.data as T;
  }

  // Failure envelope (identical keys on service-origin and gateway-origin per Phase 3 D-05..D-07)
  const err = (parsed ?? {}) as Partial<ApiErrorBody>;

  // Silent 401: clear tokens and redirect. Throw anyway so calling pages don't continue.
  if (res.status === 401) {
    clearTokens();
    if (typeof window !== 'undefined') {
      const returnTo = encodeURIComponent(window.location.pathname);
      window.location.href = `/login?returnTo=${returnTo}`;
    }
  }

  throw new ApiError(
    err.code ?? 'INTERNAL_ERROR',
    err.status ?? res.status,
    err.message ?? `Request failed (${res.status})`,
    err.fieldErrors ?? [],
    err.traceId,
    err.path,
    err.details,
  );
}

export const httpGet    = <T>(path: string) => request<T>('GET', path);
export const httpPost   = <T>(path: string, body?: unknown) => request<T>('POST', path, body);
export const httpPut    = <T>(path: string, body?: unknown) => request<T>('PUT', path, body);
export const httpPatch  = <T>(path: string, body?: unknown) => request<T>('PATCH', path, body);
export const httpDelete = <T>(path: string) => request<T>('DELETE', path);
```

**Pitfall intentionally avoided:** no automatic retry on any method. D-10 forbids auto-retry on POST/PUT/DELETE (duplicate order risk). We chose to also skip auto-retry on GET to keep the wrapper single-responsibility; manual "Thử lại" buttons (UI-SPEC surface 3) handle retries at the UI layer.

### Pattern 2: Per-domain module using generated types (D-03 + D-05)

**What:** Each `services/{domain}.ts` re-exports type-narrowed functions over `httpGet`/`httpPost`/etc., typed with `openapi-typescript`-generated `paths['...'].get['responses']['200']['content']['application/json']`.

**Example:**
```typescript
// services/products.ts
// Source: openapi-typescript README — "Type helpers for paths/operations"
import type { paths } from '@/types/api/products.generated';
import { httpGet } from './http';

// The envelope wraps ApiResponse<T>, but the generated schema describes T directly
// if the controller returns T. If controllers return ApiResponse<T> in their schema,
// extract the data field here. CHECK in plan phase — see Open Questions Q1.

type ProductListResponse =
  paths['/products']['get']['responses']['200']['content']['application/json'];

export function listProducts(params?: { page?: number; size?: number; sort?: string; categoryId?: string }) {
  const qs = new URLSearchParams();
  if (params?.page !== undefined) qs.set('page', String(params.page));
  if (params?.size !== undefined) qs.set('size', String(params.size));
  if (params?.sort)               qs.set('sort', params.sort);
  if (params?.categoryId)         qs.set('categoryId', params.categoryId);
  const suffix = qs.toString() ? `?${qs}` : '';
  return httpGet<ProductListResponse>(`/api/products/products${suffix}`);
}
```

**Note on path prefix:** Gateway route is `/api/products/**` and rewrites the prefix off before forwarding to the service. The service itself exposes controllers at whatever path Phase 2 wired (likely `/products` or root). The planner must verify each service's actual controller paths via `/v3/api-docs` before finalizing URLs. See § Open Questions Q1.

### Pattern 3: `openapi-typescript` codegen script (D-04/D-05/D-06)

**What:** A Node.js script that, for each of 6 services, fetches `/v3/api-docs` and writes the generated `.d.ts` / `.ts` file. Ships as `npm run gen:api`.

**Example:**
```javascript
// scripts/gen-api.mjs
// Source: openapi-typescript CLI docs — https://openapi-ts.dev/cli
// Usage: npm run gen:api (requires `docker compose up` so each service port is reachable)

import { execFileSync } from 'node:child_process';
import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';

// Direct per-service ports (bypass gateway so we get raw OpenAPI without gateway wrapping).
// Gateway rewrites /api/{service}/v3/api-docs → /v3/api-docs on the downstream service,
// so BOTH paths work and yield identical specs. Per-service direct is more resilient to
// gateway config drift during Phase 4.
const SERVICES = [
  { name: 'users',         url: 'http://localhost:8081/v3/api-docs' },
  { name: 'products',      url: 'http://localhost:8082/v3/api-docs' },
  { name: 'orders',        url: 'http://localhost:8083/v3/api-docs' },
  { name: 'payments',      url: 'http://localhost:8084/v3/api-docs' },
  { name: 'inventory',     url: 'http://localhost:8085/v3/api-docs' },
  { name: 'notifications', url: 'http://localhost:8086/v3/api-docs' },
];

const outDir = resolve(process.cwd(), 'src/types/api');
mkdirSync(outDir, { recursive: true });

for (const s of SERVICES) {
  const outFile = resolve(outDir, `${s.name}.generated.ts`);
  console.log(`→ ${s.name}  ${s.url}  →  ${outFile}`);
  execFileSync(
    'npx',
    ['--yes', 'openapi-typescript@7.13.0', s.url, '-o', outFile],
    { stdio: 'inherit', shell: process.platform === 'win32' },
  );
}
console.log('✓ All services regenerated. Commit src/types/api/*.generated.ts');
```

**Package.json entry:**
```json
{
  "scripts": {
    "dev": "next dev",
    "build": "next build",
    "start": "next start",
    "lint": "eslint",
    "gen:api": "node scripts/gen-api.mjs"
  },
  "devDependencies": {
    "openapi-typescript": "7.13.0"
  }
}
```

**Prerequisite:** All 6 backend services must be running (`docker compose up` → ports 8081–8086 exposed per `docker-compose.yml`). Windows/WSL note: the `shell: true` flag on `execFileSync` handles `npx.cmd` resolution on native Windows shells.

### Pattern 4: Route protection via middleware + presence cookie (D-11 + D-12 resolved)

**What:** `middleware.ts` (root) reads a non-httpOnly cookie `auth_present=1` that is set alongside the `localStorage` token at login and cleared on logout. Middleware never reads/validates the JWT — that's the backend's job. Presence-only cookie solves the edge-runtime / localStorage conflict without changing the auth contract.

**Why this resolution:**
- **Option (a) reject** — pure-client guards in `layout.tsx` — flashes protected content for ~100ms before redirect; fails the "recoverably" UX bar.
- **Option (b) reject** — move tokens to httpOnly cookie — violates D-11 and triggers CORS/CSRF work outside Phase 4.
- **Option (c) chosen** — presence-only non-httpOnly cookie + token still in localStorage:
  - Satisfies D-11 literally: accessToken/refreshToken in localStorage.
  - Satisfies D-12 literally: `middleware.ts` protects routes with a presence check (not JWT validity).
  - XSS tradeoff is already accepted (D-11). A presence cookie readable by JS is no worse than a token that is JS-readable.
  - No backend work needed: backend still reads `Authorization: Bearer` header only.
  - Middleware behavior is deterministic and server-side — no pre-render flash.

**Example:**
```typescript
// sources/frontend/middleware.ts  (NOT under src/)
// Source: Next.js official proxy/middleware docs — https://nextjs.org/docs/app/api-reference/file-conventions/proxy
//
// NOTE: In Next.js 16 this convention is renamed to proxy.ts with a deprecation warning on
// middleware.ts. Both still function in Next.js 16.2.3. D-12 locks the filename as middleware.ts
// for this phase; migration to proxy.ts is a Phase-5+ follow-up (see Open Questions Q6).

import { NextRequest, NextResponse } from 'next/server';

export function middleware(req: NextRequest) {
  const authPresent = req.cookies.get('auth_present')?.value;
  if (!authPresent) {
    const returnTo = encodeURIComponent(req.nextUrl.pathname + req.nextUrl.search);
    const loginUrl = new URL(`/login?returnTo=${returnTo}`, req.url);
    return NextResponse.redirect(loginUrl);
  }
  return NextResponse.next();
}

export const config = {
  matcher: ['/checkout/:path*', '/profile/:path*', '/admin/:path*'],
};
```

**At login (`services/auth.ts`):**
```typescript
import { httpPost } from './http';
import { setTokens } from './token';
import type { paths } from '@/types/api/users.generated';

type LoginBody = paths['/auth/login']['post']['requestBody']['content']['application/json'];
type AuthResp  = paths['/auth/login']['post']['responses']['200']['content']['application/json'];

export async function login(body: LoginBody): Promise<AuthResp> {
  const data = await httpPost<AuthResp>('/api/users/auth/login', body);
  setTokens(data.accessToken, data.refreshToken);   // writes localStorage + sets auth_present cookie
  return data;
}
```

### Pattern 5: Client-only cart in localStorage (D-14)

**What:** Cart is a `CartItem[]` (existing shape from `types/index.ts`) stored under key `cart`. Write on add/update/remove; read on cart/checkout pages. Mount on mount via `useEffect` to avoid SSR hydration mismatch.

**Example:**
```typescript
// services/cart.ts
import type { Product } from '@/types';

const CART_KEY = 'cart';

export interface CartItem { productId: string; name: string; thumbnailUrl: string; price: number; quantity: number; }

export function readCart(): CartItem[] {
  if (typeof window === 'undefined') return [];
  const raw = window.localStorage.getItem(CART_KEY);
  if (!raw) return [];
  try { return JSON.parse(raw) as CartItem[]; } catch { return []; }
}

export function writeCart(items: CartItem[]): void {
  window.localStorage.setItem(CART_KEY, JSON.stringify(items));
  window.dispatchEvent(new CustomEvent('cart:change'));   // lets header cart badge update live
}

export function addToCart(product: Pick<Product, 'id'|'name'|'thumbnailUrl'|'price'>, qty = 1): void {
  const items = readCart();
  const existing = items.find(i => i.productId === product.id);
  if (existing) existing.quantity += qty;
  else items.push({ productId: product.id, name: product.name, thumbnailUrl: product.thumbnailUrl, price: product.price, quantity: qty });
  writeCart(items);
}
export function removeFromCart(productId: string): void { writeCart(readCart().filter(i => i.productId !== productId)); }
export function clearCart(): void { window.localStorage.removeItem(CART_KEY); window.dispatchEvent(new CustomEvent('cart:change')); }
```

**At checkout submit (`app/checkout/page.tsx`):**
```typescript
import { readCart, clearCart } from '@/services/cart';
import { createOrder } from '@/services/orders';
import { ApiError, isApiError } from '@/services/errors';

async function handleSubmit() {
  const items = readCart();
  if (items.length === 0) return;

  const body = {
    items: items.map(i => ({ productId: i.productId, quantity: i.quantity })),
    shippingAddress: { street, ward, district, city },
    paymentMethod,     // 'COD' | 'BANK_TRANSFER' | 'E_WALLET'
    note: note || undefined,
  };

  try {
    const order = await createOrder(body);
    clearCart();
    setShowSuccess({ orderCode: order.orderCode });
  } catch (e) {
    if (!isApiError(e)) throw e;
    // Dispatcher — see Pattern 6
    dispatchErrorToUi(e);
  }
}
```

### Pattern 6: Error → UX dispatcher (D-07..D-10)

**What:** A single dispatcher per page (or a shared hook) maps `ApiError.code` to the correct UI surface. Keeps page components declarative; keeps UI-SPEC surfaces coherent.

**Example (checkout page dispatcher):**
```typescript
type DispatcherHandlers = {
  setFieldErrors: (errs: Record<string, string>) => void;
  setBannerVisible: (v: boolean) => void;
  openStockModal:   (items: unknown) => void;
  openPaymentModal: () => void;
  toast:            (msg: string, kind?: 'error'|'success'|'info') => void;
};

function dispatchErrorToUi(e: ApiError, h: DispatcherHandlers) {
  switch (e.code) {
    case 'VALIDATION_ERROR': {
      const byField: Record<string, string> = {};
      for (const f of e.fieldErrors) byField[f.field] = f.message;
      h.setFieldErrors(byField);
      h.setBannerVisible(true);
      return;
    }
    case 'UNAUTHORIZED':
      // http.ts already triggered the redirect; nothing else to do.
      return;
    case 'FORBIDDEN':
      h.toast('Bạn không có quyền', 'error');
      return;
    case 'CONFLICT': {
      const domainCode = (e.details as { domainCode?: string } | undefined)?.domainCode;
      if (domainCode === 'STOCK_SHORTAGE' || Array.isArray((e.details as any)?.items)) {
        h.openStockModal((e.details as any).items);
      } else {
        h.openPaymentModal();
      }
      return;
    }
    case 'NOT_FOUND':
      h.toast(e.message || 'Không tìm thấy', 'error');
      return;
    case 'INTERNAL_ERROR':
    default:
      h.toast('Đã có lỗi, vui lòng thử lại', 'error');
      return;
  }
}
```

### Anti-Patterns to Avoid

- **Hand-maintaining DTO types** that are owned by the backend — banned by D-04.
- **Calling `fetch` directly from page components** — bypasses envelope unwrap + error typing. All calls go through `http.ts`.
- **Reading `localStorage` during SSR** — `localStorage` is undefined on the server; always guard with `typeof window !== 'undefined'` OR call only inside `useEffect` / event handlers.
- **Auto-retrying mutations on 5xx** — duplicate orders/payments. D-10 forbids.
- **Showing a toast on 401** — UI-SPEC surface 4 explicitly forbids this; silent redirect only.
- **Relying on `middleware.ts` for authorization** — it only checks *presence*. Backend must still validate the JWT on every call.
- **Committing `.env.local`** — commit `.env.example` only; `.env.local` holds per-dev secrets (we have none for Phase 4, but document the pattern).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| TS types from OpenAPI | Hand-typed `interface` mirroring backend DTOs | `openapi-typescript@7.13.0` | D-04/D-05. Manual sync drifts; codegen is verified equivalent and ~3s/service to run. |
| HTTP client features (interceptors, retries, cancel) | Custom `fetchWithTimeout` / retry loop | Native `fetch` + `AbortController` where needed | D-01. Phase 4 scope does not need cancel/timeout beyond defaults. |
| Form validation rules | Custom regex validators | Backend's `fieldErrors[]` from `VALIDATION_ERROR` | The backend is the source of truth. Client pre-submit validation is *fallback only* (UI-SPEC copy list). |
| Route guards | Custom client-side redirect component | Next.js `middleware.ts` (D-12) | Runs before render, no flash. Presence cookie pattern solves edge/localStorage conflict. |
| Modal / Banner / RetrySection | Ad-hoc JSX inline | Components under `components/ui/` per UI-SPEC | UI-SPEC surfaces 1–3 require reusable components with exact visual contract. |
| Toasts | `alert()` or `window.confirm()` | Existing `ToastProvider` + `useToast()` hook | Already built and styled; UI-SPEC locks toast copy. **Note:** `ToastProvider` is NOT currently mounted in `app/layout.tsx` — plan must add it. |

**Key insight:** Every hand-roll candidate in this phase is already solved by either (a) a published tool (openapi-typescript) or (b) a Next.js convention (middleware) or (c) an existing in-repo component (Input/Toast/Button). The FE-01/FE-02 scope is wiring, not inventing.

## Runtime State Inventory

Not applicable — Phase 4 is a net-new integration layer, not a rename/migration. No existing runtime state (OS-registered tasks, live service config, secrets by name, build artifacts) is being renamed or moved. `localStorage['cart']` is a new key introduced in this phase, not a renamed key.

**Category answers (explicit):**
- **Stored data:** None — no existing DB/datastore contains frontend-owned keys. The server-side DBs are untouched.
- **Live service config:** None — gateway routes, CORS, springdoc paths are all stable from Phase 1–3.
- **OS-registered state:** None — no tasks, services, or scheduled jobs reference frontend paths.
- **Secrets/env vars:** One net-new: `NEXT_PUBLIC_API_BASE_URL` (default `http://localhost:8080`). No rename of existing vars.
- **Build artifacts:** None existing. Net-new: `src/types/api/*.generated.ts` committed files; deleting them and re-running `npm run gen:api` is the recovery path.

## Common Pitfalls

### Pitfall 1: Running `gen:api` with services down
**What goes wrong:** `openapi-typescript` prints `ECONNREFUSED` for whichever service is not up; script exits partway through and types are out of sync across services.
**Why it happens:** Codegen is a runtime HTTP fetch against live services per D-06.
**How to avoid:** `scripts/gen-api.mjs` must precheck each URL with a HEAD/GET before invoking CLI and fail-fast with a human message. README step: `docker compose up -d && wait-for-healthy && npm run gen:api`.
**Warning signs:** Committed `.generated.ts` file missing a whole service, or TypeScript `paths[...]` access erroring with `Property '/X' does not exist`.

### Pitfall 2: `localStorage` accessed during SSR/pre-render
**What goes wrong:** Build fails or first render throws `ReferenceError: localStorage is not defined`.
**Why it happens:** `'use client'` components still run once on the server for initial HTML.
**How to avoid:** Always guard: `if (typeof window === 'undefined') return ...`. Or only touch `localStorage` inside `useEffect`, event handlers, or `onClick` callbacks. Page components should initialize cart state to `[]` and hydrate in `useEffect(() => { setCart(readCart()); }, [])`.
**Warning signs:** Next.js build output mentions "hydration mismatch" near cart count badge; runtime error `window is not defined`.

### Pitfall 3: `NEXT_PUBLIC_` env var not prefixed correctly
**What goes wrong:** Runtime `process.env.API_BASE_URL` is `undefined` in the browser bundle — Next.js silently strips non-`NEXT_PUBLIC_` vars from client builds.
**Why it happens:** Next.js only inlines env vars prefixed `NEXT_PUBLIC_` into the client bundle.
**How to avoid:** Use `NEXT_PUBLIC_API_BASE_URL` (already the CONTEXT.md convention). Document in `.env.example` with a default.
**Warning signs:** `fetch()` calls go to `undefinedhttp://...` or to the same origin unexpectedly.

### Pitfall 4: Stock-shortage CONFLICT discriminator unknown
**What goes wrong:** The error-recovery modal can't tell stock conflict from payment conflict; wrong variant opens.
**Why it happens:** Phase 3 standardized `CONFLICT` as a common code but left the `details.domainCode` value as per-service discretion. The exact value emitted by order-service on stock shortage is not frozen in contract.
**How to avoid:** **Plan must verify** by grepping order-service + inventory-service for how they signal stock shortage, or by triggering the error once during local testing. Candidates: `details.domainCode = 'STOCK_SHORTAGE'` OR `details.items[]` presence. If neither, fall back to a status-code + caller-context heuristic (if the caller is `createOrder`, it's stock; if `createPayment`, it's payment). Document the discriminator in the plan's verification steps.
**Warning signs:** Wrong modal opens during UAT step "Stock conflict".

### Pitfall 5: Gateway CORS blocks Authorization header
**What goes wrong:** Browser preflight fails; `fetch` rejects with "CORS error".
**Why it happens:** CORS `allowedHeaders` must explicitly include `Authorization` (or `*`).
**How to avoid:** Current `application.yml` has `allowedHeaders: ["*"]` ✓ **[VERIFIED]**. No change needed. If the gateway CORS config is tightened in a future phase, `Authorization`, `Content-Type`, and `X-Request-Id` must be whitelisted.
**Warning signs:** Only authenticated endpoints fail with CORS errors; unauthenticated GETs succeed.

### Pitfall 6: Next.js 16 deprecation warning on `middleware.ts`
**What goes wrong:** Dev console shows `"You are using the middleware file convention, which is deprecated and has been renamed to proxy"`. Build succeeds; runtime works.
**Why it happens:** Next.js 16 renamed `middleware.ts` → `proxy.ts` and moved the default runtime to Node.js. `middleware.ts` is retained for backward compat with a deprecation warning [CITED: nextjs.org/docs/app/api-reference/file-conventions/proxy]. The project uses Next.js `16.2.3` → deprecated but functional.
**How to avoid:** Keep `middleware.ts` per D-12 for this phase. The warning is non-blocking. Add a phase-exit TODO: "Migrate to `proxy.ts` via `npx @next/codemod@canary middleware-to-proxy .`".
**Warning signs:** Deprecation warning in `npm run dev` console; PR reviewers asking about it.

### Pitfall 7: Generated types mismatch `paths[...]['content']['application/json']` because backend doesn't declare content-type
**What goes wrong:** `paths['/foo']['get']['responses']['200']` has no `content` key → generated type is `never`.
**Why it happens:** springdoc 2.6.0 only emits `content: application/json` if the controller has a visible return type and Jackson is on the classpath. For endpoints that return `ResponseEntity<Void>` or rely on `ApiResponseAdvice` wrapping, springdoc sometimes fails to infer the response body schema.
**How to avoid:** When generated types are `never`, the plan must either (a) add `@Operation(responses = @ApiResponse(content = @Content(schema = @Schema(implementation = Foo.class))))` annotations on the backend controller (small backend edit, acceptable Phase-4-scope task), OR (b) hand-narrow at call site: `httpGet<ExpectedShape>(...)`.
**Warning signs:** `.generated.ts` has type aliases ending in `: never` or `: unknown`.

### Pitfall 8: ApiResponseAdvice double-wraps the `data` field
**What goes wrong:** FE expects `{ timestamp, status, message, data: { content: [...], totalElements: ... } }` but OpenAPI schema describes the controller's return type directly (`PaginatedResponse<Product>`) because `ApiResponseAdvice` is invisible to springdoc.
**Why it happens:** `ApiResponseAdvice` wraps at serialization time, after springdoc has already inferred the schema from the controller method signature. The declared schema and the real wire body differ by one level of wrapping.
**How to avoid:** `http.ts` **always** unwraps one level (`parsed.data`). The generated types describe the inner shape (`PaginatedResponse<Product>`), which is what callers receive. **Plan must assert this in a verification test**: a smoke call through `httpGet` returns the unwrapped shape.
**Warning signs:** Type-level confusion in `services/{domain}.ts`; runtime `undefined` when accessing `.content` on a list response.

## Code Examples

### Common Operation 1: Add Product to Cart
```typescript
// Source: CONTEXT.md D-14; cart.ts pattern above
'use client';
import { addToCart } from '@/services/cart';
import { useToast } from '@/components/ui/Toast/Toast';

function AddToCartButton({ product }: { product: Product }) {
  const { showToast } = useToast();
  return (
    <Button onClick={() => {
      addToCart(product, 1);
      showToast('Đã thêm vào giỏ hàng', 'success');
    }}>
      Thêm vào giỏ
    </Button>
  );
}
```

### Common Operation 2: Login + Return-to Redirect
```typescript
// Source: CONTEXT.md D-08, D-11, D-12 resolution; services/auth.ts pattern above
'use client';
import { useRouter, useSearchParams } from 'next/navigation';
import { login } from '@/services/auth';
import { isApiError } from '@/services/errors';

function LoginForm() {
  const router = useRouter();
  const returnTo = useSearchParams().get('returnTo') || '/';
  const [email, setEmail]       = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors]     = useState<Record<string,string>>({});

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    try {
      await login({ email, password });           // sets tokens + auth_present cookie
      router.replace(returnTo);                    // middleware will now allow through
    } catch (err) {
      if (isApiError(err)) {
        if (err.code === 'VALIDATION_ERROR') {
          const byField: Record<string,string> = {};
          for (const f of err.fieldErrors) byField[f.field] = f.message;
          setErrors(byField);
        } else if (err.code === 'UNAUTHORIZED') {
          setErrors({ email: 'Sai email hoặc mật khẩu' });
        }
      }
    }
  }
  /* … JSX with <Input error={errors.email}> etc. … */
}
```

### Common Operation 3: Checkout Submit with Full Error Recovery
```typescript
// Source: CONTEXT.md D-09, D-14; Pattern 5 + Pattern 6 above
'use client';
import { readCart, clearCart } from '@/services/cart';
import { createOrder } from '@/services/orders';
import { isApiError } from '@/services/errors';
import { useToast } from '@/components/ui/Toast/Toast';

function CheckoutPage() {
  const { showToast } = useToast();
  const [fieldErrors, setFieldErrors]     = useState<Record<string,string>>({});
  const [bannerVisible, setBannerVisible] = useState(false);
  const [stockModal,   setStockModal]     = useState<StockConflictItem[] | null>(null);
  const [paymentModal, setPaymentModal]   = useState(false);
  const [successOrder, setSuccessOrder]   = useState<{ orderCode: string } | null>(null);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    try {
      const order = await createOrder({
        items: readCart().map(i => ({ productId: i.productId, quantity: i.quantity })),
        shippingAddress: { /* from form */ } as any,
        paymentMethod: form.paymentMethod,
        note: form.note || undefined,
      });
      clearCart();
      setSuccessOrder({ orderCode: order.orderCode });
    } catch (err) {
      if (!isApiError(err)) { showToast('Đã có lỗi, vui lòng thử lại','error'); return; }
      switch (err.code) {
        case 'VALIDATION_ERROR': {
          const byField: Record<string,string> = {};
          for (const f of err.fieldErrors) byField[f.field] = f.message;
          setFieldErrors(byField); setBannerVisible(true); break;
        }
        case 'CONFLICT': {
          const d = err.details as any;
          if (d?.domainCode === 'STOCK_SHORTAGE' || Array.isArray(d?.items)) setStockModal(d.items);
          else setPaymentModal(true);
          break;
        }
        case 'UNAUTHORIZED': /* http.ts already redirected */ break;
        case 'FORBIDDEN':    showToast('Bạn không có quyền', 'error'); break;
        default:             showToast('Đã có lỗi, vui lòng thử lại', 'error');
      }
    }
  }
  /* … JSX with Banner, Modal, Input fields, success state … */
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Hand-written TS interfaces mirroring backend DTOs | `openapi-typescript` codegen | ~2022 (`openapi-typescript` v5+) | Zero-drift types; backend changes surface as TS errors. |
| `middleware.ts` on edge runtime | `proxy.ts` on Node.js runtime | Next.js 16 (Oct 2025) | This phase keeps `middleware.ts` per D-12; migration deferred. |
| Token in `localStorage` | httpOnly cookie + CSRF token | industry baseline ~2020+ | Phase 4 accepts the XSS tradeoff for MVP scope per D-11; future security milestone. |
| `fetch` everywhere ad-hoc | Single wrapper + per-domain modules | N/A — evergreen pattern | Concentrates auth header, envelope parse, error typing in one place. |
| TanStack Query / SWR for data caching | None (native fetch only) | — | D-01 locks out query libs. Acceptable for MVP because pages are simple GETs with no real caching need. |

**Deprecated/outdated:**
- Next.js `middleware.ts` filename: deprecated in Next.js 16, warned but functional. Retained for D-12.
- `orval`, `swagger-typescript-api`, `@hey-api/openapi-ts` for this project: ruled out by D-05.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Backend uses `details.domainCode = 'STOCK_SHORTAGE'` to signal stock conflict on CONFLICT 409. | Pitfall 4, Pattern 6 | [ASSUMED] Medium — wrong modal variant opens. Plan must verify by grep/smoke test. |
| A2 | Order service endpoint for order creation is `POST /api/orders/orders` (controller at `/orders` after gateway prefix rewrite). | Pattern 2 note, Pattern 5 | [ASSUMED] Medium — wrong URL → 404. Plan verifies by hitting `/v3/api-docs`. |
| A3 | User service endpoint for login is `POST /api/users/auth/login`. | Pattern 4 example | [ASSUMED] Medium — wrong URL → 404. Plan verifies. |
| A4 | `ApiResponseAdvice` wrapping is invisible to springdoc, so generated schemas describe the INNER payload. | Pitfall 8, Pattern 1 | [ASSUMED — verified partially by grep showing advice exists but not by running codegen] Medium. First `npm run gen:api` run confirms. |
| A5 | Payment mock failure maps to HTTP 409 CONFLICT (not 400 or 402). | D-09 interpretation, Pattern 6 | [ASSUMED from CONTEXT.md D-09 phrasing] Medium — if payment mock returns 400 or 500 instead, the payment modal doesn't open. Plan must verify by grep in `payment-service` for how failure is signaled. |
| A6 | Backend has a `refreshToken` endpoint (mentioned in `types/index.ts` `AuthResponse`). | Claude's Discretion on silent refresh | [ASSUMED] Low — if missing, planner falls back to D-08 behavior per CONTEXT. |
| A7 | Gateway CORS `allowedHeaders: ["*"]` permits `Authorization` and `Content-Type` without preflight issues on browser. | Pitfall 5 | [VERIFIED in `api-gateway/application.yml`] Low. |
| A8 | `openapi-typescript@7.13.0` correctly parses springdoc 2.6.0 output (OpenAPI 3.0.1). | Standard Stack | [ASSUMED — widely used combo, but not run in this repo yet] Low. First `gen:api` run confirms. |

**User confirmation needed on:** A1 + A5 (the two CONFLICT discriminators) — the plan phase must either verify via codebase grep or trigger once in local testing and document. A2, A3 are auto-resolved the moment `gen:api` runs successfully (generated `paths[...]` keys make the correct URLs self-evident).

## Open Questions

1. **Q1: Exact per-service controller path after gateway rewrite.**
   - What we know: Gateway rewrites `/api/users/**` → `/**` on the downstream service. Each service exposes `/v3/api-docs`. Phase 2 added CRUD endpoints but the exact paths (e.g., `/products` vs `/api/v1/products`) are not documented in CONTEXT.md.
   - What's unclear: Whether each service mounts controllers at root or under a sub-prefix.
   - Recommendation: The first task in P1 is to run `gen:api` and inspect one generated file to confirm the path prefix. Then all `services/{domain}.ts` modules use consistent URLs.

2. **Q2: Does backend expose a `refreshToken` endpoint?**
   - What we know: FE already has `refreshToken` in `types/index.ts AuthResponse`.
   - What's unclear: Whether there's a `POST /api/users/auth/refresh` endpoint in Phase 2's CRUD work.
   - Recommendation: Check `users.generated.ts` after codegen; if present, implement silent refresh in `http.ts`. If absent, Claude's Discretion says skip (D-08 fallback covers it).

3. **Q3: How does stock-shortage appear on the wire?**
   - What we know: Phase 3 D-03 mentions `details.domainCode`; Phase 4 CONTEXT.md mentions `details.items[]`.
   - What's unclear: Exact field name and shape used by order-service.
   - Recommendation: Plan's first UAT step triggers a stock conflict via backend direct edit and records the exact response body. Modal discriminator then keys off the observed shape.

4. **Q4: Payment mock failure signal.**
   - What we know: D-09 bundles payment failure under CONFLICT.
   - What's unclear: Whether payment-service emits 409 or a different code.
   - Recommendation: Grep `payment-service/src/main/java/**/*Controller.java` for `HttpStatus.CONFLICT` or force a failure during UAT and document.

5. **Q5: Does `ToastProvider` need to be mounted in `app/layout.tsx`?**
   - What we know: `ToastProvider` component exists (`components/ui/Toast/Toast.tsx`); `app/layout.tsx` does NOT currently import it. **[VERIFIED by Read]**
   - Recommendation: P2 task must wrap `<main>` with `<ToastProvider>`. Without this, `useToast()` calls no-op.

6. **Q6: Next.js 16 `proxy.ts` migration — defer or do now?**
   - What we know: `middleware.ts` is deprecated; `proxy.ts` is the new name. Project is on Next.js 16.2.3.
   - What's unclear: Whether the team wants the deprecation warning silenced in this phase.
   - Recommendation: **Defer.** D-12 locks `middleware.ts`. Add a one-line Phase-5 ticket: "Run `npx @next/codemod@canary middleware-to-proxy .`". No functional impact.

7. **Q7: Should `mock-data/` be deleted at end of phase?**
   - What we know: 10 files reference `mockProducts`, `mockOrders`, `mockUsers`. Most are under `admin/*` (not the UAT walkthrough target).
   - Recommendation: Per CONTEXT.md Claude's Discretion — keep `mock-data/` during Phase 4 (admin pages still depend on it). Add a one-line Phase-5 ticket to delete once admin pages migrate.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Node.js ≥ 18.17 | `openapi-typescript@7.13.0`, Next.js 16 | [ASSUMED ✓ — Next.js 16 already building] ≥ 18.17 | — |
| Docker Desktop + docker-compose v2 | Running 6 services locally for codegen + UAT | [ASSUMED ✓ — docker-compose.yml exists] — | Run services with `mvn spring-boot:run` per-service |
| npm / npx | Installing dev deps + running codegen CLI | [ASSUMED ✓ — package.json exists] — | — |
| TypeScript 5.x | `openapi-typescript` peer dep + project strict mode | ✓ VERIFIED in package.json | `^5` | — |
| Network access from dev laptop to `localhost:8081-8086` | Codegen fetches live `/v3/api-docs` | [ASSUMED ✓ — ports mapped in docker-compose.yml] — | — |
| Backend services compile + start | Codegen fails without live endpoints | ✓ VERIFIED — Phase 1–3 all green | — | — |

**Missing dependencies with no fallback:** None identified.

**Missing dependencies with fallback:**
- If `docker compose` is unavailable on the dev machine, `mvn spring-boot:run` per service is a documented fallback (each service already has its own `pom.xml`). Slower but functional.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | None configured for frontend [VERIFIED: `package.json` has no `jest`, `vitest`, or `@testing-library/*`]. Phase's primary validation is the manual UAT checklist (D-13). TypeScript `tsc --noEmit` via `next build` serves as a compile-time contract check. |
| Config file | `tsconfig.json` (compile check only); `04-UAT.md` (manual) |
| Quick run command | `npm run build` (runs `next build` → type-checks all code incl. `services/*` against generated types) |
| Full suite command | `npm run build && npm run lint` + manual UAT walkthrough per `04-UAT.md` |
| Phase gate | `next build` green + UAT checklist completed with observations logged |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| FE-01 | Generated types compile against actual backend schemas | compile | `npm run build` | ❌ Wave 0: requires `gen:api` to run first |
| FE-01 | `services/http.ts` unwraps envelope and returns data only | manual | inspect one call's return shape in UAT | ❌ Wave 0: documented in `04-UAT.md` step "A2" |
| FE-01 | `ApiError` has all 6 fields populated from error body | manual | trigger VALIDATION_ERROR via blank checkout; inspect thrown error in devtools | ❌ Wave 0: documented in `04-UAT.md` step "B1" |
| FE-02 | Validation banner appears on form submit with fieldErrors | manual | `04-UAT.md` step "B1" | ❌ Wave 0 |
| FE-02 | Stock-shortage modal opens with correct items | manual | `04-UAT.md` step "B2" | ❌ Wave 0 |
| FE-02 | Payment-failure modal opens and recovery buttons work | manual | `04-UAT.md` step "B3" | ❌ Wave 0 |
| FE-02 | 401 silently redirects to `/login?returnTo=` | manual | `04-UAT.md` step "B4" (manually delete token from localStorage) | ❌ Wave 0 |
| FE-02 | 5xx shows toast + inline retry; no auto-retry on mutations | manual | `04-UAT.md` step "B5" (stop a service mid-checkout) | ❌ Wave 0 |
| FE-01/02 | Full happy path: register → browse → add → checkout → order success | manual | `04-UAT.md` step "A1..A6" | ❌ Wave 0 |

**Rationale for manual-heavy validation:** D-13 explicitly excludes Playwright/Cypress for this phase. The FE has no existing test infrastructure, and setting one up exceeds phase scope. `next build` enforces TypeScript compile-time contract checks (high-value auto-test), and the committed UAT checklist is the phase's primary deliverable.

### Sampling Rate
- **Per task commit:** `npm run build` (≤30s on a warm cache; surfaces type contract drift from `.generated.ts`).
- **Per wave merge:** `npm run build && npm run lint`.
- **Phase gate:** Full UAT walkthrough recorded in `04-UAT.md` with per-step observations; `next build` green.

### Wave 0 Gaps
- [ ] `scripts/gen-api.mjs` — required before any codegen can happen (P1 first task).
- [ ] `.env.local` (git-ignored) and `.env.example` (committed) — required for `NEXT_PUBLIC_API_BASE_URL` to resolve.
- [ ] `src/types/api/` directory — must exist (or be created by gen-api.mjs on first run).
- [ ] `src/providers/AuthProvider.tsx` — not strictly required if per-page `useEffect` reads token, but recommended for cart badge + header "Xin chào, {name}".
- [ ] `04-UAT.md` template — written as part of P3; ships as the phase deliverable.
- [ ] `ToastProvider` mounted in `app/layout.tsx` — required for any `showToast` call to work.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Backend validates JWT on Authorization header (already in place). FE only stores/forwards; no client-side validation. |
| V3 Session Management | yes | Token in `localStorage` (D-11, XSS tradeoff accepted). Clear on 401 (D-08). Presence-only cookie is non-httpOnly by design (not a session token). |
| V4 Access Control | yes | Route protection via `middleware.ts` (presence check only, not authorization). Real authorization stays on backend per Phase 3 D-08/D-09/D-10. |
| V5 Input Validation | yes | Delegated to backend `VALIDATION_ERROR` + `fieldErrors[]` (D-07). FE pre-submit validation is fallback-only. |
| V6 Cryptography | no | FE never handles crypto material; JWTs are opaque strings from backend. |

### Known Threat Patterns for {Next.js 16 SPA + Bearer token}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| XSS exfiltrating localStorage token | Information Disclosure | Phase 4 accepts the risk per D-11 (MVP scope). Mitigate: CSP headers in `next.config.ts` as a Phase-5+ follow-up. **No new XSS sinks introduced by this phase** (no `dangerouslySetInnerHTML`, no eval, no raw HTML insertion from API). |
| CSRF via Authorization header (irrelevant — header auth is immune to CSRF) | Tampering | No CSRF risk for this token scheme. Non-httpOnly `auth_present` cookie is not an auth credential — it's a presence hint. |
| Open redirect via `returnTo` query param | Tampering | `returnTo` is URL-decoded and navigated via `router.replace()`. **Mitigation required:** validate `returnTo` starts with `/` (relative path only) before redirect; reject absolute URLs. Plan must include this check in both `login/page.tsx` and `http.ts` 401 handler. |
| Generated types import secrets from backend | Information Disclosure | `openapi-typescript` output contains schema shapes only, not secrets. Low risk. |
| Stale token persists after logout | Information Disclosure | `clearTokens()` clears both `localStorage` keys AND the presence cookie. Verified in Pattern 1 code. |
| Stock conflict reveals internal inventory state | Information Disclosure | Backend-controlled; CONFLICT response contains exactly what the user needs to recover (current available qty). No further FE filtering needed. |

## Sources

### Primary (HIGH confidence)

- **Next.js 16 official docs — `proxy.js` file convention** — https://nextjs.org/docs/app/api-reference/file-conventions/proxy — Authoritative for middleware→proxy rename, Node.js runtime, cookie access, matcher syntax. Fetched 2026-04-23.
- **`openapi-typescript` CLI docs** — https://openapi-ts.dev/cli — Remote URL codegen syntax, peer deps, multi-schema pattern. Referenced in WebSearch 2026-04-23.
- **`openapi-typescript` GitHub** — https://github.com/openapi-ts/openapi-typescript — Package metadata, 156 versions, latest 7.13.0.
- **Project CONTEXT.md** — `.planning/phases/04-frontend-contract-alignment-e2e-validation/04-CONTEXT.md` — D-01..D-14 locked decisions.
- **Project UI-SPEC.md** — `.planning/phases/04-frontend-contract-alignment-e2e-validation/04-UI-SPEC.md` — Visual contract for Banner, Modal, RetrySection, Toast copy.
- **Phase 3 summaries** — `.planning/phases/03-validation-error-handling-hardening/03-01-SUMMARY.md` + `03-02-SUMMARY.md` — Locked error envelope shape (identical keys on service + gateway), common code taxonomy.
- **api-gateway/application.yml** — verified per-service route prefixes, CORS config, springdoc multi-spec URL list.
- **user-service/application.yml + pom.xml** — verified springdoc 2.6.0 exposes `/v3/api-docs` at root on each service.
- **`package.json` + `tsconfig.json`** — verified TS 5.x, React 19.2.4, Next.js 16.2.3, strict mode, `@/` alias.
- **`sources/frontend/src/components/ui/Input/Input.tsx`** — verified `error: string` prop already exists (matches UI-SPEC verification matrix).
- **`sources/frontend/src/components/ui/Toast/Toast.tsx`** — verified `useToast()` exists but `ToastProvider` is NOT yet mounted in `app/layout.tsx` (Pitfall Q5).

### Secondary (MEDIUM confidence)

- **Next.js 16 blog** — https://nextjs.org/blog/next-16 — Confirms middleware deprecation timeline (via WebSearch summary 2026-04-23).
- **`Authgear` Next.js JWT guide** — https://www.authgear.com/post/nextjs-jwt-authentication — Confirms Edge runtime cannot read localStorage; cookie pattern is standard. Cross-verified with official docs.

### Tertiary (LOW confidence)

- None marked LOW in this research — all critical claims cross-verified against Next.js official docs, in-repo files, or `npm view` registry calls.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — `openapi-typescript@7.13.0` verified on registry; native fetch + Next.js 16 both official.
- Architecture: HIGH — every file path, module split, and API URL traced to either CONTEXT.md or official docs.
- Middleware/localStorage conflict resolution: HIGH — official Next.js 16 docs on cookie access in middleware; pattern is widely used.
- Stock-shortage CONFLICT discriminator: MEDIUM (A1 ASSUMED) — plan must verify via grep or live trigger.
- Payment failure → CONFLICT mapping: MEDIUM (A5 ASSUMED) — plan must verify.
- Per-service controller paths after gateway rewrite: MEDIUM — auto-resolved the moment `gen:api` runs.
- Validation architecture: HIGH — no test framework in place is an observation, not a gap (matches D-13).

**Research date:** 2026-04-23
**Valid until:** 2026-05-23 (30 days — stable stack; `openapi-typescript` may minor-bump but API is stable; Next.js 16.x deprecation timeline is announced).

---

## File Layout Proposal

Concrete file operations the planner will sequence into tasks. Grouped by concern for easy 2–4 plan split.

### Group A — Env / Config / Codegen scaffolding

| Path | Op | Notes |
|------|----|-------|
| `sources/frontend/package.json` | EDIT | Add `"gen:api"` script; add `openapi-typescript@7.13.0` to `devDependencies`. |
| `sources/frontend/scripts/gen-api.mjs` | CREATE | Codegen loop (Pattern 3). |
| `sources/frontend/.env.example` | CREATE | Commit `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080` as documented default. |
| `sources/frontend/.env.local` | CREATE (git-ignored) | Local override for devs. Add `.env.local` to `.gitignore` if not already. |
| `sources/frontend/src/types/api/users.generated.ts` | CREATE (generated) | Commit generated output. |
| `sources/frontend/src/types/api/products.generated.ts` | CREATE (generated) | " |
| `sources/frontend/src/types/api/orders.generated.ts` | CREATE (generated) | " |
| `sources/frontend/src/types/api/payments.generated.ts` | CREATE (generated) | " |
| `sources/frontend/src/types/api/inventory.generated.ts` | CREATE (generated) | " |
| `sources/frontend/src/types/api/notifications.generated.ts` | CREATE (generated) | " |

### Group B — HTTP tier

| Path | Op | Notes |
|------|----|-------|
| `sources/frontend/src/services/errors.ts` | CREATE | `ApiError`, `FieldError`, `isApiError`. |
| `sources/frontend/src/services/token.ts` | CREATE | localStorage helpers + `auth_present` cookie. |
| `sources/frontend/src/services/http.ts` | CREATE | Core wrapper (Pattern 1). |
| `sources/frontend/src/services/auth.ts` | CREATE | login/register/logout/me. |
| `sources/frontend/src/services/products.ts` | CREATE | listProducts, getProductBySlug, etc. |
| `sources/frontend/src/services/orders.ts` | CREATE | createOrder, listMyOrders, getOrder. |
| `sources/frontend/src/services/cart.ts` | CREATE | Client-only localStorage cart (Pattern 5). |
| `sources/frontend/src/services/payments.ts` | CREATE | Thin wrapper (likely called from orders or checkout). |
| `sources/frontend/src/services/inventory.ts` | CREATE | Read-only if needed for stock display. |
| `sources/frontend/src/services/notifications.ts` | CREATE | Read-only if in UAT path. |
| `sources/frontend/src/services/api.ts` | EDIT | Keep `formatPrice`, `formatPriceShort`. Re-export named mock fns as shims during transition, then remove. |
| `sources/frontend/src/types/index.ts` | EDIT | Keep UI shapes (`ProductFilter`, `CartItem`, `PaginatedResponse`); remove/trim backend DTO duplicates once pages migrated. |

### Group C — Route protection + Auth provider

| Path | Op | Notes |
|------|----|-------|
| `sources/frontend/middleware.ts` | CREATE | Pattern 4. Root-level (sibling of `src/`, `next.config.ts`). |
| `sources/frontend/src/providers/AuthProvider.tsx` | CREATE | Optional but recommended — reads initial user from localStorage + cart count. |
| `sources/frontend/src/app/layout.tsx` | EDIT | Wrap children with `<ToastProvider>` and `<AuthProvider>`. |

### Group D — Error-recovery UI components (per UI-SPEC)

| Path | Op | Notes |
|------|----|-------|
| `sources/frontend/src/components/ui/Banner/Banner.tsx` | CREATE | UI-SPEC surface 1. |
| `sources/frontend/src/components/ui/Banner/Banner.module.css` | CREATE | Uses `--error-container`, `--space-3`, etc. |
| `sources/frontend/src/components/ui/Modal/Modal.tsx` | CREATE | Generic modal shell (UI-SPEC surface 2). |
| `sources/frontend/src/components/ui/Modal/Modal.module.css` | CREATE | " |
| `sources/frontend/src/components/ui/RetrySection/RetrySection.tsx` | CREATE | UI-SPEC surface 3. |
| `sources/frontend/src/components/ui/RetrySection/RetrySection.module.css` | CREATE | " |
| `sources/frontend/src/components/ui/Toast/Toast.tsx` | EDIT | Add `aria-label="Đóng thông báo"` on close button per UI-SPEC verification matrix. |

### Group E — Page rewires

| Path | Op | Notes |
|------|----|-------|
| `sources/frontend/src/app/login/page.tsx` | EDIT | Wire to `services/auth.login`; handle `returnTo`; validate `returnTo` starts with `/`. |
| `sources/frontend/src/app/register/page.tsx` | EDIT | Wire to `services/auth.register`; redirect to `/login` or auto-login per planner discretion. |
| `sources/frontend/src/app/cart/page.tsx` | REWRITE | Read from `services/cart.readCart()`; remove `mockProducts` import. |
| `sources/frontend/src/app/checkout/page.tsx` | REWRITE | Real POST via `services/orders.createOrder`; full error dispatcher (Pattern 6). |
| `sources/frontend/src/app/products/page.tsx` | EDIT | Swap mock import for `services/products.listProducts`. RetrySection on 5xx. |
| `sources/frontend/src/app/products/[slug]/page.tsx` | EDIT | Swap for `services/products.getProductBySlug`. 404 page on NOT_FOUND. |
| `sources/frontend/src/app/profile/page.tsx` | EDIT | Order history via `services/orders.listMyOrders`. |
| `sources/frontend/src/app/search/page.tsx` | EDIT | `services/products.listProducts` with keyword. |
| `sources/frontend/src/app/page.tsx` | EDIT | Featured / new products from real API. |

### Group F — Phase deliverables

| Path | Op | Notes |
|------|----|-------|
| `.planning/phases/04-frontend-contract-alignment-e2e-validation/04-UAT.md` | CREATE | Manual checklist: A1–A6 happy path, B1–B5 failure cases, observation column. |
| `sources/frontend/README.md` | EDIT | Document `npm run gen:api` prerequisite (`docker compose up`); document `.env.local`; document route protection. |

## Plan Skeleton Recommendation

Recommend **3 plans**. Dependency is strict left-to-right.

### Plan 04-01 — "Typed HTTP tier + codegen + route protection foundation"
**Goal:** Everything a page component needs to make its first real API call, plus the protection + provider wiring. No page rewires yet.

**Covers:**
- Group A (env, package.json, `scripts/gen-api.mjs`, 6× `.generated.ts`) — includes running codegen once to produce the files.
- Group B (errors, token, http, auth, products, orders, cart, payments, inventory, notifications) — **all modules created; bodies can be stubs for any module not used in P2**.
- Group C (middleware, AuthProvider, layout wrap with providers).
- `ToastProvider` mounted (Pitfall Q5).

**Dependency:** None within Phase 4. Depends on Phase 3 complete (✓).

**Verification:**
- `npm run gen:api` completes for all 6 services; files committed.
- `npm run build` green (no new TS errors).
- Navigating to `/checkout` without token redirects to `/login?returnTo=%2Fcheckout` (middleware smoke test).
- Login page → login call → cookie set → navigation unblocks. **UAT step A1–A2 passes as a side-effect.**

### Plan 04-02 — "Page rewires + error-recovery UI + checkout flow"
**Goal:** Replace mocks with real API calls on the UAT-critical path. Ship Banner/Modal/RetrySection and the checkout error dispatcher.

**Covers:**
- Group D (Banner, Modal, RetrySection, Toast aria-label).
- Group E but scoped to **UAT-path pages only**: `login`, `register`, `cart`, `checkout`, `products`, `products/[slug]`, `profile`, `page.tsx` home. Defer `admin/*` per CONTEXT.md "Deferred Ideas". Defer `search` if time-boxed (read-only consumer, lower risk).

**Dependency:** 04-01 must be complete.

**Verification:**
- `npm run build` green.
- `npm run lint` green.
- Smoke-click the happy path in local dev. UAT steps A3–A6 + B1 + B2 + B3 pass during implementation.

### Plan 04-03 — "UAT checklist + cleanup + phase deliverable"
**Goal:** Commit the manual UAT doc; run it end-to-end; record observations; clean up residual `mock-data/` references on UAT-path pages.

**Covers:**
- Group F (`04-UAT.md`, README edits).
- Cleanup: remove `mock-data/` imports from UAT-path pages; confirm admin pages still work untouched (they still use mocks — explicitly OK per CONTEXT.md).
- UAT walkthrough recording: fill in each step's "Observed" column.

**Dependency:** 04-02 must be complete.

**Verification:**
- All 11 UAT rows (A1–A6 + B1–B5) have observations.
- `npm run build` green.
- All three FE-02 failure recovery modes verified end-to-end by human.

**Why 3 plans, not 2 or 4:**
- **Not 2** because mixing "wire HTTP + rewrite all pages" in one plan creates a >15-file PR with mixed concerns, which fails the plan-check tier-cohesion dimension.
- **Not 4** because splitting codegen from HTTP wrapper forces P2 to partially-use generated types (fragile). Splitting middleware+auth into its own plan creates a plan that's almost pure boilerplate (<5 files), trivial enough to stay in P1.
- **3 aligns** with the three distinct phases of the work: *infrastructure → features → delivery*. Each plan has a clean verification gate.

## Pitfalls & Open Questions (summary for planner)

See § Common Pitfalls (8 items) and § Open Questions (7 items) above. The top-3 pitfalls the plan phase must encode as explicit verification steps are:

1. **Pitfall 4 / Q3 + Q4** — The CONFLICT discriminator for stock vs payment is not frozen by Phase 3 contract. Planner must add a "trigger and record" step before the dispatcher logic is locked.
2. **Pitfall 8** — `ApiResponseAdvice` + springdoc interaction means generated types describe the **inner** payload. `http.ts` unwraps exactly one level. First real call through `http.ts` must verify this assumption.
3. **Q5** — `ToastProvider` is not currently mounted. Forgetting this makes every `showToast` call a silent no-op; the D-10 contract (toast on 5xx) would break unobservably.

Additional Windows-specific note: `scripts/gen-api.mjs` uses `execFileSync('npx', …)` — on native Windows PowerShell (not WSL), `npx` resolves to `npx.cmd` and Node.js `execFileSync` needs `shell: true`. The example script above handles this.

Docker compose note: current `docker-compose.yml` exposes ports 8081–8086 on the host for each service — OK for codegen. If the team later switches to an internal-only network, the codegen script must shell-exec inside a container instead.
