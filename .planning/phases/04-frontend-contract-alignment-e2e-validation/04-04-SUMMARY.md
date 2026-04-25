---
phase: 04-frontend-contract-alignment-e2e-validation
plan: 04
subsystem: backend
status: complete
requirements:
  - FE-01 (backend half — rich Product DTO + slug 200/404 + observability)
completed: 2026-04-25
tags:
  - backend
  - product-service
  - dto
  - slug
  - observability
  - spring-boot
  - openapi
  - gap-closure

dependency_graph:
  requires:
    - 04-01 (typed HTTP tier + openapi-typescript codegen pipeline)
    - 04-02 (error-recovery dispatcher; ApiError contract)
    - 04-03 (UAT walkthrough that surfaced FE-01 runtime gap + slug 500 + observability gap)
    - .planning/debug/products-list-500.md (Option A patch recipe — verbatim)
  provides:
    - product-service emits rich `ProductResponse` shape on `GET /products` (list) and `GET /products/{id}` (detail)
    - new `GET /products/slug/{slug}` endpoint returning 200 with rich shape on hit, 404 NOT_FOUND envelope on miss
    - `GlobalExceptionHandler.handleFallback` now logs unhandled Throwable with traceId at ERROR before returning the masked body (Phase 3 D-01 leak-mask rule preserved)
    - regenerated OpenAPI emit (`sources/frontend/src/types/api/products.generated.ts` +78/-1) carrying `ProductResponse` + `CategoryRef` schemas with `thumbnailUrl`, `rating`, `reviewCount`, `tags`, `category{name,slug}`
    - `ProductControllerSlugTest` (2 tests, both green) covering slug-200 happy path + slug-404 missing
  affects:
    - Plan 04-06 (FE hardening + UAT re-run): có thể tiêu thụ rich shape từ backend mà không cần adapter; commit drift của `products.generated.ts`
    - Phase 5 candidate: replicate the Option A observability fix to user/order/payment/inventory/notification GlobalExceptionHandler (5 sibling files); persist `description`/`shortDescription`/`thumbnailUrl`/`tags` on real Product entity once a real datastore lands

tech-stack:
  added:
    - spring-boot-starter-test (test scope) — added to product-service `pom.xml`; required for `@SpringBootTest` + `TestRestTemplate` + AssertJ in `ProductControllerSlugTest`
  patterns:
    - "Read endpoint returns rich `ProductResponse`; admin upsert paths (POST/PUT) preserve raw `ProductEntity` to keep Phase 02 CRUD smoke contract stable"
    - "`toResponse(ProductEntity)` mapper + `CategoryRef` lookup with placeholder fallback (`—` + raw categoryId) when category is deleted/missing → FE never crashes on missing join"
    - "Slug lookup throws `ResponseStatusException(NOT_FOUND)` so the existing `handleResponseStatus` branch produces a standardized `{code:NOT_FOUND, status:404, …}` envelope — no new handler code path"
    - "Logger declared as `private static final Logger log = LoggerFactory.getLogger(...)`, parameterized SLF4J formatting (`{} placeholders`) inside `handleFallback` to prevent log-injection from `request.getRequestURI()`"

key-files:
  created:
    - "sources/backend/product-service/src/test/java/com/ptit/htpt/productservice/web/ProductControllerSlugTest.java"
  modified:
    - "sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/api/GlobalExceptionHandler.java"
    - "sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductCrudService.java"
    - "sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/ProductController.java"
    - "sources/backend/product-service/pom.xml"
    - "sources/frontend/src/types/api/products.generated.ts" (regenerated; left uncommitted — plan 04-06 commits the drift)

key-decisions:
  - "CategoryEntity does NOT carry slug() accessor → mapper uses lowercase-hyphenated slugify fallback on category.name(); rồi sẽ thay bằng c.slug() khi Phase 5 thêm cột slug thật."
  - "POST/PUT vẫn trả về raw ProductEntity — không break Phase 02 admin CRUD smoke. Chỉ READ paths (list, getById, getBySlug) emit ProductResponse."
  - "Slug 404 đi qua ResponseStatusException → handleResponseStatus → envelope NOT_FOUND chuẩn; không cần thêm handler riêng."
  - "spring-boot-starter-test chưa có trên pom.xml → thêm scope=test; sibling services có thể đã có sẵn (ngoài scope plan này)."
  - "Sibling-service GlobalExceptionHandler observability rollout (user/order/payment/inventory/notification) deferred to Phase 5 to keep plan scoped vào FE-01 closure (đã ghi rõ trong Task 1 plan note)."

requirements-completed:
  - FE-01

# Metrics
duration: ~25m (after Task 1 already committed in earlier session)
completed: 2026-04-25
---

# Phase 04 Plan 04: Backend half of FE-01 gap closure — rich Product DTO + slug endpoint + observability fallback Summary

**product-service hiện emit rich `ProductResponse` (category{name,slug}, thumbnailUrl, rating, reviewCount, tags, …) trên list/detail/slug endpoints, có endpoint mới `GET /products/slug/{slug}` trả 200/404 thay vì 500, và `GlobalExceptionHandler.handleFallback` log Throwable với traceId trước khi trả masked body — đóng phần backend của FE-01 runtime gap được surfaced từ 04-VERIFICATION.**

## Performance

- **Duration:** ~25 phút (Task 1 đã thực hiện ở session trước; phiên này hoàn thành Task 2 + Task 3 + verification)
- **Started:** 2026-04-25T11:25:00Z (resume)
- **Completed:** 2026-04-25T11:35:00Z
- **Tasks:** 3 (Task 1 carried over)
- **Files modified:** 4 source + 1 test created + 1 codegen drift (uncommitted)

## Accomplishments

- product-service `GET /api/products/products?size=N` trả về `content[]` với đầy đủ field FE cần (`category{name,slug}`, `thumbnailUrl`, `rating`, `reviewCount`, `tags`, `description`, `shortDescription`, `images`, `stock`, `originalPrice`, `discount`, `brand`, `status`, timestamps).
- `GET /api/products/products/slug/{slug}` mới — 200 cho slug hit (rich shape), 404 envelope chuẩn `{code:NOT_FOUND, status:404, traceId}` cho slug miss; trước plan này endpoint không tồn tại nên Spring rớt sang catchall handler trả 500.
- `GlobalExceptionHandler.handleFallback` thêm `log.error(...)` với SLF4J parameterized format (method, URI, traceId, ex) trước khi trả masked body — Phase 3 D-01 leak-mask rule **không** đổi, body shape giữ nguyên.
- `ProductControllerSlugTest` (2 tests, cả hai green) chạy với `@SpringBootTest(WebEnvironment.RANDOM_PORT)` + `TestRestTemplate` + `InMemoryProductRepository` seed.
- FE codegen `npm run gen:api` regenerate `products.generated.ts` (+78/-1), `ProductResponse` + `CategoryRef` schemas surface ra OpenAPI emit.

## Task Commits

**Important:** Theo MEMORY.md của user (`feedback_no_autocommit.md`), agent KHÔNG được tự `git commit` / `git add`. Mọi staging và commit do user thực hiện thủ công. Vì vậy phần này không có hash — user sẽ stage + commit theo từng task khi muốn.

1. **Task 1: SLF4J logger trong product-service GlobalExceptionHandler.handleFallback** — đã thực hiện ở session trước, build clean. Files: `api/GlobalExceptionHandler.java` (+5 imports/lines, +1 logger field, +2 lines log.error trong handleFallback). Self-verify: 5/5 grep acceptance OK; `mvn -DskipTests package` exit 0.
2. **Task 2: ProductResponse + CategoryRef + toResponse + getProductBySlug + controller wiring** — files: `service/ProductCrudService.java` (+ records + mapper + slug lookup + listProducts content map), `web/ProductController.java` (+ ProductResponse import, getProduct rewired, getProductBySlug @GetMapping("/slug/{slug}")). Self-verify: 10/10 grep acceptance OK; `mvn -DskipTests clean package` exit 0 (14 source files compiled).
3. **Task 3: ProductControllerSlugTest + spring-boot-starter-test dep** — files: `pom.xml` (+1 dependency block), `src/test/java/.../ProductControllerSlugTest.java` (created, 78 lines). Self-verify: `mvn test` → `Tests run: 2, Failures: 0, Errors: 0`; 4/4 grep acceptance OK.

**Plan metadata:** SUMMARY + STATE + ROADMAP updates đều ghi trực tiếp xuống disk theo yêu cầu user; user sẽ tự stage + commit khi review.

## Files Created/Modified

- `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/api/GlobalExceptionHandler.java` — Task 1: logger field + SLF4J imports + log.error trong handleFallback (body shape unchanged).
- `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductCrudService.java` — Task 2: thêm `ProductResponse` record + `CategoryRef` record + `toResponse(ProductEntity)` mapper + `categorySlugFor(CategoryEntity)` slugify fallback + `getProductBySlug(String)` lookup; `listProducts` rewrap content list bằng mapper.
- `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/ProductController.java` — Task 2: import `ProductResponse`; `GET /{id}` chuyển sang `toResponse(getProduct(...))`; `GET /slug/{slug}` mới.
- `sources/backend/product-service/pom.xml` — Task 3: thêm `spring-boot-starter-test` (scope=test).
- `sources/backend/product-service/src/test/java/com/ptit/htpt/productservice/web/ProductControllerSlugTest.java` — Task 3 (NEW): 2 tests cho slug-200 happy + slug-404 missing.
- `sources/frontend/src/types/api/products.generated.ts` — REGENERATED, drift +78/-1, để uncommitted để plan 04-06 commit cùng các FE-side change.

## Live Smoke Evidence

Sau `docker compose up -d --no-deps --build product-service`, container restart green (health=200).

**Seeded data through gateway:**
- POST `/api/products/products/categories` → category id `64570f35-97e1-46cd-80e3-f1de44211f3b` (name "UAT Live Smoke").
- POST `/api/products/products` → product id `e8d6dcc4-02ef-445c-b43e-a98b1029f26d` (slug `uat-smoke-prod`).

**Rich shape on GET /api/products/products?size=5:**
```json
{
  "id": "e8d6dcc4-02ef-445c-b43e-a98b1029f26d",
  "name": "UAT Smoke Prod",
  "slug": "uat-smoke-prod",
  "description": "",
  "shortDescription": "",
  "price": 99000,
  "originalPrice": null,
  "discount": null,
  "images": [],
  "thumbnailUrl": "",
  "category": {
    "id": "64570f35-97e1-46cd-80e3-f1de44211f3b",
    "name": "UAT Live Smoke",
    "slug": "uat-live-smoke"
  },
  "brand": null,
  "rating": 0,
  "reviewCount": 0,
  "stock": 0,
  "status": "ACTIVE",
  "tags": [],
  "createdAt": "2026-04-25T11:31:15.805486978Z",
  "updatedAt": "2026-04-25T11:31:15.805486978Z"
}
```

**Slug 200 (hit):** `curl http://localhost:8080/api/products/products/slug/uat-smoke-prod` → `200` với rich shape (cùng các field như list[0] ở trên).

**Slug 404 (miss):** `curl http://localhost:8080/api/products/products/slug/no-such-slug-exists` → `404` với envelope:
```json
{
  "timestamp": "2026-04-25T11:31:33.365180317Z",
  "status": 404,
  "error": "Not Found",
  "message": "Product not found",
  "code": "NOT_FOUND",
  "path": "/products/slug/no-such-slug-exists",
  "traceId": "1f619dbd-187d-4fd4-bae8-239ab4b0b326",
  "fieldErrors": []
}
```

**FE codegen drift:** `git diff --stat sources/frontend/src/types/api/products.generated.ts` → `+78/-1`. Schemas mới `ApiResponseProductResponse`, `CategoryRef`, `ProductResponse` (carrying `thumbnailUrl?`, `category?`, `reviewCount?`, …) surface trong file generated. Drift để uncommitted, plan 04-06 commit cùng FE-side change.

## Decisions Made

- **CategoryEntity slug fallback:** record `CategoryEntity` chưa có `slug()` accessor → mapper dùng `categorySlugFor(c)` private method với regex `[^a-z0-9]+` → `-` + trim. Khi Phase 5 thêm cột slug thật, đổi thành `return c.slug();` và xóa fallback. Live smoke chứng minh: category "UAT Live Smoke" → slug "uat-live-smoke".
- **Admin contract preserved:** POST/PUT vẫn trả raw `ProductEntity` (không phải `ProductResponse`) để Phase 02 admin CRUD smoke không bị break. Chỉ READ paths (list, getById, getBySlug) emit rich shape — đây là quyết định trong PLAN.md (Step 5 explicit) và đã verify qua live smoke (POST trả `{id, name, slug, categoryId, price, status, deleted, createdAt, updatedAt}` raw shape).
- **Slug 404 reuse existing handler:** `getProductBySlug` throw `ResponseStatusException(HttpStatus.NOT_FOUND)` → đi qua `handleResponseStatus` đã có sẵn → envelope `{code:"NOT_FOUND", status:404, traceId, …}` chuẩn. Không thêm handler mới, giữ contract Phase 3.
- **spring-boot-starter-test added:** product-service `pom.xml` chỉ có `spring-boot-starter-web/validation/actuator` + springdoc, không có test starter. Thêm scope=test để compile + run được `@SpringBootTest`. Đây là planned-deviation note trong Task 3 PLAN.md (line 536).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Test assertion incorrect — success ApiResponse envelope không có `code` field**
- **Found during:** Task 3 (chạy `mvn test` lần đầu)
- **Issue:** Plan recipe ở line 510 cho assertion `body.contains("\"code\"").doesNotContain("\"INTERNAL_ERROR\"")` cho test happy-path 200. Nhưng `ApiResponse` envelope cho success chỉ có `{timestamp, status, message, data}` — không có `code` (chỉ error envelope `ApiErrorResponse` mới có `code`). Test fail với message "Expecting actual ... to contain '\"code\"'".
- **Fix:** Thay assertion thành `contains("\"message\":\"Product loaded\"")` + `doesNotContain("\"INTERNAL_ERROR\"")` + `doesNotContain("\"NOT_FOUND\"")`. Vẫn cover ý "envelope đúng và không phải error path".
- **Files modified:** `sources/backend/product-service/src/test/java/com/ptit/htpt/productservice/web/ProductControllerSlugTest.java` (lines 56-59).
- **Verification:** `mvn test` re-run → `Tests run: 2, Failures: 0, Errors: 0`.
- **Committed in:** N/A (no auto-commit per user MEMORY.md; user sẽ stage thủ công).

---

**Total deviations:** 1 auto-fixed (1 bug — test recipe assertion vs actual envelope shape).
**Impact on plan:** Không có scope creep. Bug ở test assertion, không phải source code; rich shape live smoke đã chứng minh contract đúng. Mục đích của test (200 với rich shape, 404 với NOT_FOUND code) vẫn được giữ nguyên.

## Issues Encountered

- **`mvn` không có trên Windows host PATH** → dùng Docker `maven:3.9-eclipse-temurin-17` image với volume mount để chạy build/test. `~/.m2` shared để cache dependencies. Lần đầu chạy `mvn package` (no clean) báo "Nothing to compile - all classes are up to date" do timestamp cache; phải `mvn clean package` để force re-compile và verify thật sự.
- **Container chạy old image** → `docker compose up -d --no-deps --build product-service` rebuild + recreate, mất ~10s warm-up. Health probe `/actuator/health` → 200 sau khoảng 12s.
- **In-memory repo của container fresh start là rỗng** → phải seed category + product qua POST gateway trước khi smoke list/slug. Không phải bug — `InMemoryProductRepository` không persist disk theo design Phase 02.

## User Setup Required

None — không có external service config. User chỉ cần:
1. Stage các file ở section "Files Created/Modified" (trừ `products.generated.ts` — file đó để plan 04-06 commit).
2. Commit theo từng Task:
   - Task 1 commit (đã ready từ phiên trước): `api/GlobalExceptionHandler.java`.
   - Task 2 commit: `service/ProductCrudService.java` + `web/ProductController.java`.
   - Task 3 commit: `pom.xml` + `src/test/.../ProductControllerSlugTest.java`.
   - Plan metadata commit: `04-04-SUMMARY.md` + `STATE.md` + `ROADMAP.md`.

## Next Phase Readiness

- **Plan 04-05 (order-service):** đã unblock — chạy parallel hoặc sequential đều OK; không có file dependency.
- **Plan 04-06 (FE hardening + Playwright re-run):** cần chờ 04-04 + 04-05 land. Plan 04-06 sẽ commit `products.generated.ts` drift (+78/-1) cùng các change khác phía FE.
- **Phase 5 candidates (deferred từ plan này):**
  - Apply Option A observability fix to `user/order/payment/inventory/notification` `GlobalExceptionHandler.handleFallback` (5 sibling services).
  - Persist real `description`, `shortDescription`, `thumbnailUrl`, `tags`, `images`, `stock` (from inventory-service) trên `ProductEntity` khi datastore thật ship — hiện default empty/zero/null.
  - Persist `slug` column trên `CategoryEntity` để xóa slugify fallback ở `categorySlugFor`.

---

## Self-Check: PASSED

**Created/Modified files exist on disk:**
- FOUND: `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/api/GlobalExceptionHandler.java`
- FOUND: `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductCrudService.java`
- FOUND: `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/ProductController.java`
- FOUND: `sources/backend/product-service/pom.xml`
- FOUND: `sources/backend/product-service/src/test/java/com/ptit/htpt/productservice/web/ProductControllerSlugTest.java`
- FOUND: `sources/frontend/src/types/api/products.generated.ts` (drift uncommitted)

**Acceptance criteria:**
- Task 1: 5/5 grep OK; mvn package exit 0; `"Internal server error"` count = 1.
- Task 2: 10/10 grep OK; mvn package exit 0 (14 source files compiled); live curl chứng minh rich shape on list + getById.
- Task 3: 4/4 grep OK; mvn test → 2/2 PASS; live slug-200 → 200; live slug-404 → 404 với `code:NOT_FOUND`.
- FE codegen: `products.generated.ts` drift +78/-1; `ProductResponse` + `CategoryRef` + `thumbnailUrl` + `reviewCount` đều surface trong generated TS.

**No commits made** — theo user MEMORY.md "Claude must not run git commit without explicit per-commit ask"; user sẽ stage + commit thủ công.

---
*Phase: 04-frontend-contract-alignment-e2e-validation*
*Plan: 04-04*
*Completed: 2026-04-25*
