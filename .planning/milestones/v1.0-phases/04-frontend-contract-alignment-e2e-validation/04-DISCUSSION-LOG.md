# Phase 4: Frontend Contract Alignment + E2E Validation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in `04-CONTEXT.md` — this log preserves the alternatives considered.

**Date:** 2026-04-24
**Phase:** 04-frontend-contract-alignment-e2e-validation
**Areas discussed:** API client + fetch wrapper, DTO alignment with OpenAPI, Error code → UX mapping, Auth + E2E validation

---

## Gray area selection

| Option | Description | Selected |
|--------|-------------|----------|
| Tầng API client + fetch wrapper | Cách `services/api.ts` chuyển từ mock sang HTTP thật. | ✓ |
| Đồng bộ DTO với OpenAPI | Sửa tay vs auto-gen từ Swagger. | ✓ |
| Mapping lỗi chuẩn → UX | Cách map code → UX (inline, toast, modal, redirect). | ✓ |
| Phạm vi E2E validation + auth cho checkout | Token storage + cách xác nhận "validated E2E". | ✓ |

**User's choice:** All four areas — they selected the full recommended set.

---

## Area 1: API client + fetch wrapper

### Q1.1 — Chọn công nghệ nền cho tầng gọi API?

| Option | Description | Selected |
|--------|-------------|----------|
| fetch + wrapper tự viết (Khuyến nghị) | Zero dep, `services/http.ts` parse envelope/error, swap mock → fetch. | ✓ |
| SWR + fetch | Thêm `swr` (~4KB) cho cache + revalidation. | |
| TanStack Query + fetch | Mạnh nhất, overhead lớn cho MVP. | |

**User's choice:** fetch + wrapper tự viết (recommended).

### Q1.2 — Wrapper xử lý response envelope kiểu nào?

| Option | Description | Selected |
|--------|-------------|----------|
| Auto-unwrap + throw typed error (Khuyến nghị) | Success trả `data`, fail throw `ApiError` (code, message, fieldErrors, status). | ✓ |
| Trả raw envelope, caller tự unwrap | Minh bạch hơn nhưng boilerplate. | |
| Hybrid: helper fn từng endpoint | Không wrapper chung, dễ lệch chuẩn. | |

**User's choice:** Auto-unwrap + throw typed error (recommended).

### Q1.3 — File/mô-đun tầng API nên tổ chức ra sao?

| Option | Description | Selected |
|--------|-------------|----------|
| http.ts + services/{domain}.ts (Khuyến nghị) | 1 core + per-backend-service module. | ✓ |
| Giữ nguyên `services/api.ts` khối to | Không cấu trúc lại, file phình. | |
| Inline fetch trong page/component | Nhanh nhưng khó test, lặp logic. | |

**User's choice:** http.ts + services/{domain}.ts (recommended).

---

## Area 2: DTO alignment with OpenAPI

### Q2.1 — Làm sao giữ `types/index.ts` khớp với DTO backend?

| Option | Description | Selected |
|--------|-------------|----------|
| Auto-gen từ OpenAPI (Khuyến nghị) | Script sinh types từ `/v3/api-docs`, loại drift. | ✓ |
| Sửa tay `types/index.ts` mỗi khi BE đổi | Dễ quên, lệch chuẩn. | |
| Hybrid: auto-gen core + tự viết view-model | Cân bằng đồng bộ + linh hoạt FE. | |

**User's choice:** Auto-gen từ OpenAPI (recommended).

### Q2.2 — Nếu auto-gen, chọn công cụ nào?

| Option | Description | Selected |
|--------|-------------|----------|
| openapi-typescript (Khuyến nghị) | Chỉ sinh type TS, nhẹ, hợp wrapper tự viết ở Area 1. | ✓ |
| orval / @hey-api/openapi-ts | Sinh cả client + React Query hooks; mâu thuẫn Area 1. | |
| swagger-typescript-api | Cũ hơn, ít maintenance. | |

**User's choice:** openapi-typescript (recommended).

### Q2.3 — Nguồn OpenAPI spec là ở đâu?

| Option | Description | Selected |
|--------|-------------|----------|
| Per-service /v3/api-docs (Khuyến nghị) | Fetch từng service, sinh file type riêng per domain. | ✓ |
| Gateway aggregated spec | Spring Cloud Gateway không aggregate tự động, rủi ro verify. | |
| Checked-in static spec files | Ổn định nhưng lại drift ngầm. | |

**User's choice:** Per-service /v3/api-docs (recommended).

---

## Area 3: Error code → UX mapping

### Q3.1 — `VALIDATION_ERROR` (fieldErrors[]) hiển thị thế nào?

| Option | Description | Selected |
|--------|-------------|----------|
| Inline dưới field + banner tổng (Khuyến nghị) | Error dưới `<Input>` + banner top "Vui lòng kiểm tra các trường bị lỗi". | ✓ |
| Chỉ inline dưới field | Gọn nhưng dễ bỏ sót khi form dài. | |
| Toast + inline | Toast bị giây chạy, không lý tưởng form dài. | |

**User's choice:** Inline + banner (recommended).

### Q3.2 — `UNAUTHORIZED` (401) xử lý ra sao?

| Option | Description | Selected |
|--------|-------------|----------|
| Silent redirect /login?returnTo=... (Khuyến nghị) | Clear token + redirect, preserve return path. | ✓ |
| Toast cảnh báo + redirect | Rõ nhưng chậm. | |
| Giữ nguyên trang + banner inline | User thấy trang trống, UX kém. | |

**User's choice:** Silent redirect (recommended).

### Q3.3 — `CONFLICT` (stock shortage, payment fail) khôi phục ra sao?

| Option | Description | Selected |
|--------|-------------|----------|
| Modal recovery với hành động cụ thể (Khuyến nghị) | Modal liệt kê item hết hàng + nút "Cập nhật số lượng" / "Xóa khỏi giỏ"; payment fail có "Thử lại" / "Đổi phương thức". | ✓ |
| Banner inline trên trang checkout | Nhẹ hơn modal nhưng dễ khuất. | |
| Toast + navigate về /cart | Mất ngữ cảnh checkout. | |

**User's choice:** Modal recovery (recommended).

### Q3.4 — `INTERNAL_ERROR` (5xx) / network fail?

| Option | Description | Selected |
|--------|-------------|----------|
| Toast + retry button (Khuyến nghị) | Toast chung + nút retry inline. | ✓ |
| Full-page error boundary | Quá nặng cho 1 lỗi network nhỏ. | |
| Toast + auto retry 1 lần | Không an toàn cho POST/mutation. | |

**User's choice:** Toast + retry button (recommended).

---

## Area 4: Auth + E2E validation

### Q4.1 — Token lưu ở đâu trên FE?

| Option | Description | Selected |
|--------|-------------|----------|
| localStorage (Khuyến nghị cho MVP) | Đơn giản, wrapper đính `Authorization: Bearer`. XSS tradeoff chấp nhận cho assignment. | ✓ |
| httpOnly cookie từ backend | An toàn hơn XSS nhưng yêu cầu BE/gateway thay đổi lớn. | |
| sessionStorage | Mất khi đóng tab, UX xấu. | |

**User's choice:** localStorage (recommended).

### Q4.2 — Cách chặn route đòi đăng nhập?

| Option | Description | Selected |
|--------|-------------|----------|
| Next.js `middleware.ts` check token presence (Khuyến nghị) | Middleware root redirect khi thiếu token; không verify JWT ở FE. | ✓ |
| Client guard component từng page | `<AuthGuard>` wrapper trong `useEffect`, flash content trước khi redirect. | |
| Không guard ở FE — dựa vào 401 từ BE | User thấy trang rỗng trước khi biết cần login. | |

**User's choice:** Next.js middleware.ts (recommended).

### Q4.3 — "Validated E2E shopping flow" định nghĩa ra sao?

| Option | Description | Selected |
|--------|-------------|----------|
| Manual walkthrough + checklist có kiểm chứng (Khuyến nghị) | `04-UAT.md` liệt kê từng bước + 3 case lỗi (validation, stock conflict, payment fail). | ✓ |
| Playwright smoke test + checklist | Thêm test framework, overhead cho 1-plan phase. | |
| Chỉ screenshot flow đưa vào report | Không reproducible. | |

**User's choice:** Manual walkthrough + checklist (recommended).

### Q4.4 — Cart state được lưu ở đâu?

| Option | Description | Selected |
|--------|-------------|----------|
| Client localStorage → gửi full cart khi POST /orders (Khuyến nghị) | Cart ở `localStorage`, POST `CreateOrderRequest` tới `/api/orders`. | ✓ |
| Server cart qua order-service | Cần verify endpoint BE, phụ thuộc. | |
| Hybrid: localStorage trước login, sync BE sau | Phức tạp cho scope 1 plan. | |

**User's choice:** Client localStorage + POST /orders (recommended).

---

## Closing question

### Q5 — Tiếp theo làm gì?

| Option | Description | Selected |
|--------|-------------|----------|
| Tạo CONTEXT.md ngay | Ghi quyết định, chuẩn bị cho `/gsd-plan-phase 4`. | ✓ |
| Khám phá thêm gray area | Mock rollback, refresh flow, Docker env, admin scope. | |
| Chỉnh lại 1 area đã chốt | Revisit Area 1/2/3/4. | |

**User's choice:** Tạo CONTEXT.md ngay.

---

## Claude's Discretion

- Exact `ApiError` class shape (class vs discriminated union).
- `npm run gen:api` script layout (one loop vs per-service).
- `FORBIDDEN` (403) UI: toast + stay unless admin route → redirect `/`.
- `NOT_FOUND` (404) UI: context-dependent (empty list vs 404 page).
- Token refresh flow (silent rotation vs fallback to D-08 redirect).
- Whether to delete `sources/frontend/src/mock-data/` — decide during plan after dependency audit.
- Internal package structure inside `services/`.

## Deferred Ideas

- Server-side cart via order-service cart endpoint.
- Automated E2E (Playwright/Cypress) — future TEST-01 milestone.
- httpOnly cookie auth + CSRF — future security milestone.
- CI check for generated-types freshness.
- Silent token refresh with rotation.
- Admin area E2E walkthrough (focus here is customer shopping flow).
- Search UX overhaul.
- `mock-data/` rollback timing — decide in plan phase.
