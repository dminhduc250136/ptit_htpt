# Phase 4: Frontend Contract Alignment + E2E Validation - Context

**Gathered:** 2026-04-24
**Status:** Ready for planning

<domain>
## Phase Boundary

Align the Next.js frontend at `sources/frontend/` with the backend API contract (Swagger/OpenAPI, DTO shapes, error codes) already standardized in Phases 1-3, replace mock-only data flow with real HTTP calls through the gateway, and validate the shopping flow end-to-end locally (browse → cart → checkout → payment mock → confirmation) with recoverable error handling on checkout failures.

In scope:
- `services/api.ts` migration from mock to real HTTP with a shared parser.
- DTO alignment between `types/index.ts` and backend `/v3/api-docs`.
- Standardized error → UX mapping for `VALIDATION_ERROR`, `UNAUTHORIZED`, `FORBIDDEN`, `CONFLICT`, `NOT_FOUND`, `INTERNAL_ERROR`.
- Auth token plumbing required for the authenticated portion of the checkout flow.
- A documented manual E2E checklist proving the full flow + the three mandatory failure cases.

Out of scope (see Deferred Ideas):
- New business capabilities (search UX overhaul, recommendations, reviews feature flow).
- Automated E2E frameworks (Playwright/Cypress) — noted for a future testing milestone.
- Production-grade auth (httpOnly cookie + CSRF) — Phase 3 locked header-based auth contract.
- Real payment gateway integration (mock only, per PROJECT.md).

</domain>

<decisions>
## Implementation Decisions

### API client + fetch wrapper (Area 1)
- **D-01:** Use native `fetch` + a custom wrapper instead of adding TanStack Query or SWR. Keep the function-based abstraction pattern that `sources/frontend/src/services/api.ts` already uses; swap the mock internals for real HTTP.
- **D-02:** The wrapper auto-unwraps the response envelope on success (returns `data` directly) and throws a typed `ApiError` (fields: `code`, `message`, `fieldErrors`, `status`, `traceId`) on failure. Callers use `try/catch`, not branching on `success` flags at every call site.
- **D-03:** Organize the tier as a single `sources/frontend/src/services/http.ts` core module (baseURL, auth header, envelope parsing, error throwing) + per-domain modules: `services/products.ts`, `services/auth.ts`, `services/orders.ts`, `services/cart.ts`, `services/payments.ts`, `services/inventory.ts`, `services/notifications.ts`. Each domain module mirrors one backend service.

### DTO alignment with OpenAPI (Area 2)
- **D-04:** Auto-generate FE TypeScript types from backend OpenAPI. Manual sync of `types/index.ts` is banned for backend-derived DTOs.
- **D-05:** Use `openapi-typescript` (pure type output, no runtime client generation). Do not use `orval`, `@hey-api/openapi-ts`, or `swagger-typescript-api` — they either generate React Query hooks (conflicts with D-01) or add more code than needed for the MVP scope.
- **D-06:** Spec source is per-service `/v3/api-docs` across all six backend services (users, products, orders, payments, inventory, notifications). Generate one types file per service (e.g., `types/api/products.generated.ts`) so each domain service module in D-03 imports its matching generated types. Do **not** rely on a gateway-aggregated spec for this phase — Spring Cloud Gateway does not aggregate springdoc output natively and this would add unnecessary risk.

### Error code → UX mapping (Area 3)
- **D-07:** `VALIDATION_ERROR` (HTTP 400 with `fieldErrors[]`): render inline error under each matching `<Input>` field AND a single top banner `"Vui lòng kiểm tra các trường bị lỗi"` to catch off-viewport errors on long forms (register, checkout).
- **D-08:** `UNAUTHORIZED` (HTTP 401): the `http.ts` wrapper clears stored tokens and silently redirects the client to `/login?returnTo=<current-path>`. No toast, no intermediate screen.
- **D-09:** `CONFLICT` (HTTP 409) — includes stock shortage on checkout and payment mock failure: present a modal with a specific recovery action. Stock shortage modal: lists affected items with current available quantity + two buttons ("Cập nhật số lượng" / "Xóa khỏi giỏ"). Payment failure modal: offers "Thử lại" or "Đổi phương thức thanh toán". This satisfies success criterion #3 ("recoverably").
- **D-10:** `INTERNAL_ERROR` (HTTP 5xx) and network errors: toast `"Đã có lỗi, vui lòng thử lại"` plus an inline retry button on the affected section (e.g., empty product list with retry CTA). The wrapper must **not** auto-retry mutating requests (`POST/PUT/DELETE`) to avoid duplicate orders/payments.

### Auth + E2E validation (Area 4)
- **D-11:** Store `accessToken` and `refreshToken` in `localStorage`. The `http.ts` wrapper reads the access token and attaches `Authorization: Bearer <token>` to every outgoing request. XSS tradeoff is accepted for the MVP assignment scope; Phase 3 already locked header-based auth.
- **D-12:** Protect `/checkout`, `/profile`, `/admin/*` via a root-level `sources/frontend/middleware.ts` that checks token presence only (not JWT validity — that stays on the backend). Missing token → redirect to `/login?returnTo=<path>`.
- **D-13:** "Validated E2E shopping flow" means a committed `04-UAT.md` checklist with explicit steps for the happy path (register/login → browse → add to cart → checkout form → payment mock → confirmation) **plus** three mandatory failure cases: (a) validation failure (blank required field), (b) stock conflict on checkout (force via admin-reduced stock or backend direct edit), (c) payment mock failure. Each step records the expected observable behavior. No automated test framework in this phase.
- **D-14:** Cart state lives in `localStorage` (`cart` key with items + quantity). On "Thanh toán" click, the checkout page POSTs a full `CreateOrderRequest` (already typed in `types/index.ts`) to `/api/orders`. No intermediate server-side cart endpoint. This keeps the scope to one plan and avoids dependency on an order-service cart endpoint that may or may not exist.

### Claude's Discretion
- Exact shape of `ApiError` class vs discriminated union — as long as D-02 contract holds.
- `npm run gen:api` script implementation (single script looping over services vs per-service scripts) — as long as generated types are committed and the team can regenerate in one command.
- `FORBIDDEN` (403) default handling: toast `"Bạn không có quyền"` + stay on page, unless the route itself is forbidden (admin area) in which case redirect to `/`.
- `NOT_FOUND` (404) handling: context-dependent — empty list UI for collection endpoints, 404 page for direct resource navigation (e.g., `/products/[slug]` on unknown slug).
- Token refresh flow (silent refresh vs re-login on 401): planner may choose based on backend `refreshToken` endpoint availability; D-08 still applies if refresh also fails.
- Internal file/package structure within `services/` as long as D-03 module split is preserved.
- Whether to delete `mock-data/` once pages are wired to real APIs, or keep as dev fixtures — decide during plan after checking what pages still reference it.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase scope, requirements, and success criteria
- `.planning/ROADMAP.md` — Phase 4 goal, dependencies, requirements (FE-01, FE-02), and success criteria #1-3.
- `.planning/REQUIREMENTS.md` — FE-01 (contract alignment) and FE-02 (checkout/cart error recovery) definitions.
- `.planning/PROJECT.md` — milestone intent, out-of-scope boundaries (no real payment, no mobile, no real-time).

### Carried-forward contract decisions
- `.planning/phases/01/01-01-SUMMARY.md` — standard API response + error schema shape (envelope, error fields).
- `.planning/phases/01/01-02-SUMMARY.md` — per-service Swagger/OpenAPI availability.
- `.planning/phases/01/01-03-SUMMARY.md` — gateway API surface conventions.
- `.planning/phases/02-crud-completeness-across-services/02-CONTEXT.md` — CRUD route conventions, gateway prefixes (D-08), list/pagination shape (D-03..D-05).
- `.planning/phases/03-validation-error-handling-hardening/03-CONTEXT.md` — standardized error codes, `fieldErrors[]` shape, gateway pass-through behavior, auth error contract (D-08..D-10 of Phase 3).

### Backend contract anchors (shape FE must match)
- `sources/backend/api-gateway/src/main/resources/application.yml` — gateway routing, per-service prefixes (`/api/users`, `/api/products`, `/api/orders`, `/api/payments`, `/api/inventory`, `/api/notifications`).
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/api/ApiErrorResponse.java` — service error payload shape (baseline; identical patterns apply in the other five services).
- `sources/backend/api-gateway/src/main/java/com/ptit/htpt/apigateway/gateway/ApiErrorResponse.java` — gateway error payload shape.
- `sources/backend/api-gateway/src/main/java/com/ptit/htpt/apigateway/gateway/GlobalGatewayErrorHandler.java` — gateway error propagation behavior (informs when FE sees a gateway-origin vs service-origin error).

### Frontend surface to modify
- `sources/frontend/src/services/api.ts` — current mock-based API client; to be split into `http.ts` + per-domain modules per D-03.
- `sources/frontend/src/types/index.ts` — current hand-written DTOs; generated types under `types/api/*.generated.ts` replace the backend-derived portions per D-04..D-06.
- `sources/frontend/src/app/cart/page.tsx` — currently hardcoded `mockProducts[]` cart; rewire to `localStorage` cart per D-14.
- `sources/frontend/src/app/checkout/page.tsx` — currently uses hardcoded cart + fake `setTimeout` submit; rewire to real `POST /api/orders` + error recovery modals per D-09.
- `sources/frontend/src/app/login/page.tsx` and `sources/frontend/src/app/register/page.tsx` — wire to `services/auth.ts` and persist token per D-11.
- `sources/frontend/src/components/ui/Input/Input.tsx` — hosts inline field errors per D-07 (verify current API supports `error` prop; adjust in plan if needed).
- `sources/frontend/src/components/ui/Toast/Toast.tsx` — delivery surface for D-08/D-09/D-10 user-facing messages.

### Codebase maps
- `.planning/codebase/STRUCTURE.md` — directory layout, where new `services/`, `types/api/`, `middleware.ts` live.
- `.planning/codebase/CONVENTIONS.md` — naming/style conventions to respect.
- `.planning/codebase/INTEGRATIONS.md` — existing integration points (gateway, service discovery) relevant to base URL config.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `sources/frontend/src/components/ui/Input/Input.tsx` and `Toast/Toast.tsx` — already exist, should be the anchor for D-07 (inline validation) and D-10 (toasts); plan should verify these components expose the props needed or extend them minimally.
- `sources/frontend/src/types/index.ts` `PaginatedResponse<T>` — already matches Phase 2's D-05 shape (`content`, `totalElements`, `totalPages`, `currentPage`, `pageSize`, `isFirst`, `isLast`), so list rendering today is already contract-compatible.
- `CreateOrderRequest` in `types/index.ts` — already aligned with the expected `POST /api/orders` payload (items + shippingAddress + paymentMethod + note). D-14 depends on this staying stable after auto-gen; verify against order-service DTO.
- Function-based `api.ts` pattern — minimal refactor cost since consumers already import named functions; swap internals without touching page components.

### Established Patterns
- Next.js App Router (`src/app/**/page.tsx`) — protected routes go through `middleware.ts` at the `sources/frontend/` root per D-12.
- Client components with `'use client'` directive (cart, checkout, login pages) — fit localStorage access per D-11 and D-14.
- Existing service abstraction in `services/api.ts` — imported from pages via `@/services/api`; preserve this import surface during D-03 split or provide a barrel re-export during transition.
- Vietnamese UI copy throughout pages (`"Giỏ hàng"`, `"Đặt hàng thành công!"`) — error messages from D-07/D-08/D-10 must also be in Vietnamese.

### Integration Points
- `http.ts` baseURL: must read from `process.env.NEXT_PUBLIC_API_BASE_URL` (default `http://localhost:8080`) so dev, docker-compose, and demo environments can swap the gateway host.
- `middleware.ts` sits at `sources/frontend/middleware.ts` (NOT inside `src/`) per Next.js convention; must match `config.matcher` against `/checkout`, `/profile`, `/admin/:path*`.
- The `openapi-typescript` codegen script needs all six backend services reachable during generation; document the prerequisite `docker compose up` step in README/plan.
- Error contract compatibility: the `ApiError` class in `http.ts` must parse whatever shape both service-origin and gateway-origin errors produce (per Phase 3 D-05..D-07). If gateway normalizes, FE only sees one shape; if pass-through, FE might see slight variations — test both paths during E2E.

</code_context>

<specifics>
## Specific Ideas

- All four discussion areas resolved with the recommended option (user trusted the agent's MVP-scoped defaults). No conflicts with prior-phase decisions; the error-code set and gateway prefix lock-in from Phase 3 is carried forward verbatim.
- Priority is correctness of contract alignment (FE-01) over UX polish — animation, exact spacing, skeleton variants can be iterated later as long as the standardized contract handling is in place.
- E2E validation is treated as a scripted manual checklist, not automated tests. Tests can still accompany component-level logic (parser, wrapper) — but the phase's success criterion is the documented walkthrough in `04-UAT.md`.
- Vietnamese user copy is the project default — D-07 banner, D-08 redirect experience, D-09 modal buttons, and D-10 toast all use Vietnamese strings.

</specifics>

<deferred>
## Deferred Ideas

- **Server-side cart via order-service cart endpoint** — could enable multi-device cart sync. Deferred because MVP scope has single-device assumption and the backend cart endpoint has not been verified as part of Phase 2 CRUD. Add to backlog as potential post-v1.0 enhancement.
- **Automated E2E via Playwright/Cypress** — would make regressions cheaper to detect. Aligns with future requirement TEST-01 (Broad integration test suite). Deferred to a dedicated testing milestone.
- **httpOnly cookie auth + CSRF** — more XSS-resilient. Deferred because Phase 3 locked header-based auth and switching midway triggers gateway/backend CORS/cookie work outside this phase's scope. Candidate for a future security hardening milestone.
- **CI check that generated types are fresh** (fail build if `gen:api` drift detected) — nice-to-have. Deferred until a CI/CD phase exists; manual regeneration + committed output is enough for the assignment.
- **Token refresh with silent rotation** — the plan phase may or may not implement it depending on backend support. D-08 explicitly states the fallback (redirect on 401) so even without silent refresh the contract is honored.
- **Admin area rewiring (`/admin/*`)** — the admin pages exist (`sources/frontend/src/app/admin/`) and will benefit from the same service tier + middleware guard, but the E2E focus (success criterion #2) is the customer shopping flow. Admin pages should consume the shared `services/*` modules but are not the UAT walkthrough target; dedicated admin UAT can come later.
- **Search UX overhaul** — the `/search` page exists but is not in this phase's E2E path. Treat as read-only consumer of `services/products.ts` during this phase.
- **Rollback of `mock-data/`** — leave the decision for the plan phase after auditing which pages still depend on it.

</deferred>

---

*Phase: 04-frontend-contract-alignment-e2e-validation*
*Context gathered: 2026-04-24*
