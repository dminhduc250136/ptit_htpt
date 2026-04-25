---
phase: 04-frontend-contract-alignment-e2e-validation
plan: 05
subsystem: backend
status: complete
requirements:
  - FE-01 (backend half — order-service command DTO + X-User-Id derivation)
completed: 2026-04-25
tags:
  - backend
  - order-service
  - dto
  - command
  - jwt
  - openapi
  - spring-boot
  - gap-closure

dependency_graph:
  requires:
    - 04-01 (typed HTTP tier + openapi-typescript codegen pipeline)
    - 04-02 (error-recovery dispatcher; ApiError contract)
    - 04-03 (UAT walkthrough that surfaced FE-01 runtime gap — order DTO mismatch)
    - 04-04 (sibling plan — product-service half of FE-01; independent file path, no coupling)
  provides:
    - order-service `POST /orders` accepts FE's `CreateOrderCommand` body shape `{items[], shippingAddress, paymentMethod, note}`
    - `X-User-Id` header derivation at the controller; service throws standardized `BAD_REQUEST` envelope when the header is missing
    - `status = "PENDING"` defaulted server-side; FE no longer needs to know its own user id or set status
    - `totalAmount` computed server-side from `items[].quantity * items[].unitPrice` (FE supplies cart price snapshot)
    - existing `OrderUpsertRequest` admin path on `OrderController.PUT /{id}` + `AdminOrderController` (`/admin/orders` POST/PUT/PATCH/DELETE) untouched — Phase 02 CRUD smoke not regressed
    - regenerated OpenAPI emit (`sources/frontend/src/types/api/orders.generated.ts` +23/-2) carrying `CreateOrderCommand`, `OrderItemRequest`, `ShippingAddressRequest` schemas + `X-User-Id` header on `createOrder` op
    - `OrderControllerCreateOrderCommandTest` (3 tests, all green) covering 201 happy-path + 400 missing-header + 400 empty-items validation
  affects:
    - Plan 04-06 (FE hardening + UAT re-run): can re-run UAT row A5 + B1 against real backend without stubs; commits codegen drift on `orders.generated.ts`
    - Phase 5 candidates: replace X-User-Id header trust with JWT-claim derivation at gateway; wire `createOrderFromCommand` → inventory-service.reserve to produce real Q3 STOCK_SHORTAGE shape; integrate payment-service into checkout chain (real Q4 PAYMENT_FAILED shape); persist per-item rows + shippingAddress + paymentMethod on OrderEntity (currently aggregate-only)

tech-stack:
  added:
    - "(no new dependencies — `spring-boot-starter-test` already present in `pom.xml` at scope=test)"
  patterns:
    - "Domain command DTO (CreateOrderCommand) replaces raw entity DTO (OrderUpsertRequest) on the public POST path; admin/CRUD path keeps the raw entity DTO so Phase 02 smoke remains stable"
    - "userId derived server-side from X-User-Id header (`@RequestHeader(value = \"X-User-Id\", required = false)`); missing header → `ResponseStatusException(BAD_REQUEST, \"Missing X-User-Id session header\")` → standardized envelope via existing `handleResponseStatus` branch (no new handler code)"
    - "totalAmount computed at the service layer from items[].quantity * items[].unitPrice; status defaulted to \"PENDING\" at the service layer — FE never sends either field"
    - "Nested validation via `@Valid` on `List<@Valid OrderItemRequest>` + `@Valid ShippingAddressRequest` produces fieldErrors entries that flow through the existing `handleValidation` (Phase 03 D-01 sanitization preserved)"

key-files:
  created:
    - "sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/web/OrderControllerCreateOrderCommandTest.java"
  modified:
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java"
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/OrderController.java"
    - "sources/frontend/src/types/api/orders.generated.ts" (regenerated; left uncommitted — plan 04-06 commits the drift)

key-decisions:
  - "Branch A applied: AdminOrderController.java đã tồn tại trên repo → admin path (`/admin/orders`) tự động được giữ nguyên. OrderController POST /orders đổi sang CreateOrderCommand mà không cần migrate admin endpoint."
  - "OrderController PUT /orders/{id} vẫn bind OrderUpsertRequest — đó là admin-style update endpoint còn dùng cho Phase 02 CRUD smoke. Chỉ POST /orders đổi DTO; existing `OrderUpsertRequest` import giữ lại."
  - "Service `createOrderFromCommand` không persist per-item rows / shippingAddress / paymentMethod vào OrderEntity (entity record không có những field đó). MVP: chấp nhận persist aggregate-only (id, userId, totalAmount, status, note); FE chỉ cần response envelope confirm 201 + status PENDING. Phase 5 candidate: enrich OrderEntity sau khi datastore thật ship."
  - "Q3 (real STOCK_SHORTAGE shape) + Q4 (real PAYMENT_FAILED shape) deferred to Phase 5 per 04-VERIFICATION.md. Plan 04-06 sẽ keep Playwright stubs cho B2/B3 và document deferral."
  - "Test totalAmount assertion relaxed thành substring `\"198000\"` thay vì literal `\"\\\"totalAmount\\\":198000\"` để tránh phụ thuộc Jackson BigDecimal serialization variants (198000 vs 198000.0 vs \"198000\"). Live smoke đã chứng minh real backend emit `\"totalAmount\":198000` trên integer wholesum case."

requirements-completed:
  - FE-01 (backend half — order-service)

# Metrics
duration: ~6m
completed: 2026-04-25
---

# Phase 04 Plan 05: Backend half of FE-01 — order-service CreateOrderCommand + X-User-Id Summary

**order-service hiện accept FE's command-shape body `{items[], shippingAddress, paymentMethod, note}` trên POST `/api/orders/orders`, derive `userId` server-side từ `X-User-Id` header, default `status = "PENDING"`, compute `totalAmount` từ items, và emit response envelope chuẩn 201 PENDING. Existing admin path (`/admin/orders` + PUT `/orders/{id}`) trên `OrderUpsertRequest` không đổi — Phase 02 CRUD smoke không regress. Đóng phần backend còn lại của FE-01 runtime gap được surfaced từ 04-VERIFICATION (gap 2).**

## Performance

- **Duration:** ~6 phút (plan ngắn, 3 task tuần tự)
- **Started:** 2026-04-25T11:38:11Z
- **Completed:** 2026-04-25T11:43:30Z
- **Tasks:** 3
- **Files modified:** 2 source + 1 test created + 1 codegen drift (uncommitted)

## Accomplishments

- order-service `POST /api/orders/orders` nay accept đúng shape FE gửi từ `services/orders.createOrder` + `app/checkout/page.tsx handleSubmit`: `{items: [{productId, quantity, unitPrice}], shippingAddress: {street, ward, district, city, zipCode?}, paymentMethod, note?}`.
- Header `X-User-Id` được map qua `@RequestHeader(value="X-User-Id", required=false) String userId`; service `createOrderFromCommand(userId, command)` validate non-blank trước khi xử lý, miss → throw `ResponseStatusException(BAD_REQUEST, "Missing X-User-Id session header")` → envelope chuẩn `{code:"BAD_REQUEST", status:400, traceId, …}` qua `handleResponseStatus` đã có sẵn từ Phase 3.
- `totalAmount` compute server-side từ `items.stream().map(i -> i.unitPrice() * BigDecimal.valueOf(i.quantity())).reduce(BigDecimal.ZERO, BigDecimal::add)`. Live smoke: `[{quantity:2, unitPrice:99000}]` → `totalAmount: 198000`.
- `status` default `"PENDING"` ở service layer — FE không cần biết.
- `OrderControllerCreateOrderCommandTest` 3/3 PASS (`@SpringBootTest(WebEnvironment.RANDOM_PORT)` + `TestRestTemplate`); `GlobalExceptionHandlerTest` 6/6 vẫn green (Phase 03 smoke không regress); tổng `mvn clean test` → `Tests run: 9, Failures: 0, Errors: 0`.
- Live curl smoke 3/3 PASS qua API gateway: 201 happy-path → 400 missing-header → 400 VALIDATION_ERROR (items must not be empty).
- Admin endpoint regression check: `POST /api/orders/admin/orders` với `{userId, totalAmount, status, note}` raw shape vẫn trả 201 → Phase 02 CRUD admin smoke không bị break.
- FE codegen drift `+23/-2` trên `sources/frontend/src/types/api/orders.generated.ts` carrying `CreateOrderCommand` + `OrderItemRequest` + `ShippingAddressRequest` schemas + `X-User-Id` optional header trên op `createOrder`.

## Task Commits

**Important:** Theo MEMORY.md user (`feedback_no_autocommit.md`), agent KHÔNG được tự `git commit` / `git add`. User stage + commit thủ công sau khi review. Vì vậy phần này không có hash.

1. **Task 1: CreateOrderCommand + OrderItemRequest + ShippingAddressRequest sub-DTOs + createOrderFromCommand service method** — file: `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java`. Thêm imports `jakarta.validation.Valid`, `NotEmpty`, `NotNull`; thêm `createOrderFromCommand(String userId, CreateOrderCommand command)` method (~20 lines, validate userId, compute totalAmount, default status=PENDING, persist via existing `OrderEntity.create(...)`); thêm 3 record DTOs ở cuối file. Existing `OrderUpsertRequest` + `createOrder(OrderUpsertRequest)` UNTOUCHED. Self-verify: 8/8 grep acceptance OK; `mvn -DskipTests clean package` exit 0 (lần đầu báo "Nothing to compile" do timestamp cache, force `clean` để re-compile thật).
2. **Task 2: OrderController POST /orders rewired to CreateOrderCommand + X-User-Id header** — file: `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/OrderController.java`. Thêm imports `OrderCrudService.CreateOrderCommand` + `RequestHeader`; rewrite `createOrder` method: `@Valid @RequestBody CreateOrderCommand command, @RequestHeader(value="X-User-Id", required=false) String userId` → `orderCrudService.createOrderFromCommand(userId, command)`. PUT `/{id}` + existing `OrderUpsertRequest` import giữ nguyên (admin update path). Self-verify: 5/5 grep acceptance OK; `mvn -DskipTests clean package` exit 0.
3. **Task 3: OrderControllerCreateOrderCommandTest + live smoke + FE codegen drift check** — file CREATED: `sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/web/OrderControllerCreateOrderCommandTest.java` (104 lines). 3 tests: happy-path 201 PENDING + missing-header 400 BAD_REQUEST + empty-items 400 VALIDATION_ERROR. Self-verify: 4/4 grep acceptance OK; `mvn clean test` → `Tests run: 9, Failures: 0, Errors: 0` (3 mới + 6 GlobalExceptionHandler); `docker compose up -d --no-deps --build order-service` warm-up ~12s → live curl 3/3 PASS; `npm run gen:api` regenerate `orders.generated.ts` +23/-2.

**Plan metadata:** SUMMARY + STATE + ROADMAP updates đều ghi trực tiếp xuống disk; user sẽ tự stage + commit khi review.

## Files Created/Modified

- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java` — Task 1: imports `Valid`, `NotEmpty`, `NotNull`; new `createOrderFromCommand` method; new records `CreateOrderCommand`, `OrderItemRequest`, `ShippingAddressRequest`. (Modified)
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/OrderController.java` — Task 2: import `CreateOrderCommand` + `RequestHeader`; POST `/orders` rewired. (Modified)
- `sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/web/OrderControllerCreateOrderCommandTest.java` — Task 3: 3 integration tests. (NEW)
- `sources/frontend/src/types/api/orders.generated.ts` — REGENERATED, drift +23/-2. Để uncommitted để plan 04-06 commit cùng các FE-side change. (Modified)

## Live Smoke Evidence

Container rebuild + restart:
```
$ docker compose up -d --no-deps --build order-service
... Container tmdt-use-gsd-order-service-1 Started
$ curl -s http://localhost:8080/api/orders/actuator/health
{"status":"UP"} (HTTP 200)
```

**Happy path (command + X-User-Id) → 201 PENDING:**
```bash
curl -X POST http://localhost:8080/api/orders/orders \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: user-uat-1' \
  -d '{"items":[{"productId":"prod-uat-1","quantity":2,"unitPrice":99000}],
       "shippingAddress":{"street":"1 Cau Giay","ward":"Dich Vong","district":"Cau Giay","city":"Ha Noi"},
       "paymentMethod":"COD","note":"UAT smoke"}'
```
Response (HTTP 201):
```json
{
  "timestamp": "2026-04-25T11:42:47.258546911Z",
  "status": 201,
  "message": "Order created",
  "data": {
    "id": "c042c84f-fd8a-49d7-a1fe-9e0eac6dbb95",
    "userId": "user-uat-1",
    "totalAmount": 198000,
    "status": "PENDING",
    "note": "UAT smoke",
    "deleted": false,
    "createdAt": "2026-04-25T11:42:47.258210593Z",
    "updatedAt": "2026-04-25T11:42:47.258210593Z"
  }
}
```

**Missing header → 400 BAD_REQUEST:**
```bash
curl -X POST http://localhost:8080/api/orders/orders \
  -H 'Content-Type: application/json' \
  -d '{"items":[{"productId":"prod-uat-1","quantity":1,"unitPrice":50000}],
       "shippingAddress":{"street":"1","ward":"1","district":"1","city":"1"},
       "paymentMethod":"COD"}'
```
Response (HTTP 400):
```json
{
  "timestamp": "2026-04-25T11:42:53.237892413Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Missing X-User-Id session header",
  "code": "BAD_REQUEST",
  "path": "/orders",
  "traceId": "3ebc4bcc-cf2f-4164-bdff-3137ed87ca9d",
  "fieldErrors": []
}
```

**Empty items → 400 VALIDATION_ERROR:**
```bash
curl -X POST http://localhost:8080/api/orders/orders \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: user-uat-1' \
  -d '{"items":[],"shippingAddress":{"street":"1","ward":"1","district":"1","city":"1"},"paymentMethod":"COD"}'
```
Response (HTTP 400):
```json
{
  "timestamp": "2026-04-25T11:42:59.974950825Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "code": "VALIDATION_ERROR",
  "path": "/orders",
  "traceId": "51b64c06-f51a-46c8-819c-1ba943f47f13",
  "fieldErrors": [{"field": "items", "rejectedValue": "[]", "message": "must not be empty"}]
}
```

**Admin path regression check (`POST /api/orders/admin/orders` với `OrderUpsertRequest` raw shape):**
```bash
curl -X POST http://localhost:8080/api/orders/admin/orders \
  -H 'Content-Type: application/json' \
  -d '{"userId":"admin-1","totalAmount":50000,"status":"PENDING","note":"admin smoke"}'
```
Response (HTTP 201): `{"status":201,"message":"Admin order created","data":{"id":"23afa0b9-…","userId":"admin-1","totalAmount":50000,"status":"PENDING","note":"admin smoke",…}}` → admin path **không regress**.

**FE codegen drift on `orders.generated.ts`:** `+23/-2`. New schemas added:
```ts
CreateOrderCommand: {
    items: components["schemas"]["OrderItemRequest"][];
    shippingAddress: components["schemas"]["ShippingAddressRequest"];
    paymentMethod: string;
    note?: string;
};
OrderItemRequest: {
    productId: string;
    /** Format: int32 */
    quantity?: number;
    unitPrice: number;
};
ShippingAddressRequest: {
    street: string;
    ward: string;
    district: string;
    city: string;
    zipCode?: string;
};
```
Operation `createOrder` updated:
```ts
header?: {
    "X-User-Id"?: string;
};
requestBody: {
    content: {
        "application/json": components["schemas"]["CreateOrderCommand"];
    };
};
```
Drift left uncommitted. Plan 04-06 sẽ commit cùng FE-side changes.

## Plan-Level Output Spec Coverage

Per plan `<output>` section:
- ✅ **Exact response body emitted by the command-shape POST**: paste của `data` envelope ở Live Smoke section trên.
- ✅ **Branch A vs Branch B from Task 2**: **Branch A áp dụng** — `AdminOrderController.java` đã tồn tại trên repo (`@RequestMapping("/admin/orders")` với POST/PUT/PATCH/DELETE handlers), nên admin path tự động được giữ nguyên trên file riêng. Chỉ `OrderController.POST /orders` đổi sang `CreateOrderCommand`.
- ✅ **Phase 02/03 tests in order-service trong `mvn test`**: chỉ một existing test class — `com.ptit.htpt.orderservice.api.GlobalExceptionHandlerTest` (6 tests, từ Phase 03 hardening). Tất cả 6/6 PASS sau plan này. Không có existing controller test trong order-service.
- ✅ **Drift line count**: `git diff --stat sources/frontend/src/types/api/orders.generated.ts` → `+23/-2` (1 file changed).
- ✅ **Phase 5 follow-up entries** — đã list trong section "Decisions Made" + "Next Phase Readiness" dưới đây.
- ✅ **Deviation từ locked plan** — đã document trong section "Deviations from Plan" dưới đây.

## Decisions Made

- **Branch A áp dụng (Task 2):** verified `test -f sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/AdminOrderController.java` → exists. AdminOrderController bind `/admin/orders` với POST/PUT/PATCH/DELETE qua `OrderUpsertRequest`. Plan này không cần migrate admin endpoint; chỉ rewire `OrderController.POST /orders`.
- **OrderController PUT `/{id}` giữ OrderUpsertRequest:** đó là admin-style update endpoint, không phải FE checkout flow. Existing `OrderUpsertRequest` import + PUT method binding không đổi → grep `OrderUpsertRequest` trên file vẫn return 1 match (line 6 import + line 56 method param).
- **Aggregate-only persist:** OrderEntity record chỉ có `(id, userId, totalAmount, status, note, deleted, createdAt, updatedAt)`. `createOrderFromCommand` không persist per-item rows / shippingAddress / paymentMethod vì entity không có field tương ứng. Đây là plan-locked decision (Task 1 PLAN line 222-223). Phase 5 candidate: enrich OrderEntity sau khi datastore thật ship.
- **Test assertion relaxed:** plan recipe gốc dùng `assertThat(body).contains("\"totalAmount\":198000")`. Để tránh fragile coupling với Jackson BigDecimal serialization (198000 vs 198000.0 vs "198000"), test dùng substring `assertThat(body).contains("198000")`. Live smoke chứng minh integer-wholesum trường hợp emit raw `198000` không có decimal — assertion vẫn cover ý ban đầu.
- **No new `spring-boot-starter-test` add (Task 3):** order-service `pom.xml` đã có sẵn dependency này (line 43-47). Plan PLAN line 536 note đúng: **Branch A** — pom skipped (sibling product-service plan 04-04 đã thêm cho service đó vì product-service trước plan 04-04 chưa có test starter).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Test totalAmount assertion fragile vs Jackson BigDecimal serialization**
- **Found during:** Task 3 (review test recipe trước khi run; chưa thực sự fail vì `mvn test` PASS với fix sẵn)
- **Issue:** Plan recipe (PLAN line 421) hard-code `assertThat(body).contains("\"totalAmount\":198000")`. Jackson default behavior cho `BigDecimal(198000)` là `198000` không có decimal khi value là integer-whole, **nhưng** nếu Jackson config khác (e.g. `WRITE_BIGDECIMAL_AS_PLAIN` + scale ≥ 1) emit `198000.0` hoặc `"198000"`, assertion sẽ fail. Plan note ở line 463-464 nói rõ "If your Jackson config emits 198000.00 or '198000', relax the assertion to `assertThat(body).contains(\"198000\")`."
- **Fix:** Áp dụng đúng plan-allowed relaxation từ đầu — dùng substring assertion `contains("198000")` thay vì literal `contains("\"totalAmount\":198000")`. Vẫn cover ý "totalAmount đúng giá trị".
- **Files modified:** `sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/web/OrderControllerCreateOrderCommandTest.java` (line 67-69 + comment).
- **Verification:** `mvn clean test` → `Tests run: 9, Failures: 0, Errors: 0`; live smoke chứng minh real backend emit `"totalAmount":198000` trên integer-whole case (nên cả strict assertion lẫn relaxed assertion đều PASS — chọn relaxed cho defensive).
- **Committed in:** N/A (no auto-commit per user MEMORY.md; user sẽ stage thủ công).

---

**Total deviations:** 1 auto-fixed (1 plan-allowed relaxation, đã được PLAN.md ghi rõ ở line 463-464).
**Impact on plan:** Không có scope creep. Test contract (totalAmount đúng) vẫn được giữ nguyên; live smoke independently chứng minh real backend body chứa exact `"totalAmount":198000`.

## Issues Encountered

- **`mvn` không có trên Windows host PATH** → dùng Docker `maven:3.9-eclipse-temurin-17` image với volume mount để chạy build/test (`MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd -W):/workspace" -v "$HOME/.m2:/root/.m2" -w //workspace/sources/backend/order-service maven:3.9-eclipse-temurin-17 mvn …`). `~/.m2` shared để cache dependencies.
- **`mvn package` (no clean) báo "Nothing to compile"** lần đầu sau Edit do timestamp cache (cùng issue documented trong 04-04-SUMMARY). Phải `mvn clean package` để force re-compile và verify Java thật sự pass type-check trên các record DTO mới.
- **In-memory repo container fresh start là rỗng** — không phải bug, là thiết kế của `InMemoryOrderRepository` Phase 02. Live smoke happy-path tự seed order qua POST.
- **CRLF/LF line ending warning** trên `orders.generated.ts` — `git diff` cảnh báo "LF will be replaced by CRLF the next time Git touches it". Warning vô hại trên Windows host; user có thể ignore khi commit. (Không có `.gitattributes` config force LF cho generated files.)

## User Setup Required

None — không có external service config. User chỉ cần:
1. Stage các file ở section "Files Created/Modified" (trừ `orders.generated.ts` — file đó để plan 04-06 commit cùng FE-side changes).
2. Commit theo từng Task:
   - Task 1 commit: `service/OrderCrudService.java`.
   - Task 2 commit: `web/OrderController.java`.
   - Task 3 commit: `src/test/.../OrderControllerCreateOrderCommandTest.java`.
   - Plan metadata commit: `04-05-SUMMARY.md` + `STATE.md` + `ROADMAP.md`.

## Next Phase Readiness

- **Plan 04-06 (FE hardening + Playwright re-run):** đã unblock backend-side. 04-06 cần:
  - Plumb `unitPrice` từ cart vào checkout body (FE side; cart đã có `price` trên `CartItem`, một-line wire trong `app/checkout/page.tsx handleSubmit`).
  - Set `X-User-Id` header trên `services/orders.createOrder` (FE — derive từ stored token / AuthProvider; consistent với plan T-04-01).
  - Commit codegen drift: `sources/frontend/src/types/api/products.generated.ts` (+78/-1 từ plan 04-04) + `sources/frontend/src/types/api/orders.generated.ts` (+23/-2 từ plan 04-05).
  - Re-run Playwright UAT — A5 + B1 sẽ pass real backend; B2/B3 vẫn dùng stubs (Q3/Q4 deferred).
- **Phase 5 candidates (deferred từ plan này):**
  - Replace `X-User-Id` header trust với JWT-claim derivation tại API gateway (gateway verify token → forward verified-userId header). Hiện service trust header không kiểm tra origin.
  - Wire `OrderCrudService.createOrderFromCommand` → `inventory-service.reserve` để produce real Q3 STOCK_SHORTAGE shape khi stock không đủ.
  - Wire `payment-service` vào checkout chain (sau khi inventory.reserve OK) để produce real Q4 PAYMENT_FAILED shape khi payment mock fail.
  - Persist per-item rows (OrderItemEntity?) + `shippingAddress` (ShippingAddressEntity?) + `paymentMethod` trên OrderEntity. Hiện chỉ persist aggregate.
  - Add `@Size(max=100)` (hoặc tương tự) trên `CreateOrderCommand.items` để mitigate T-04-05-04 DoS threat (unbounded list size).
  - Add product-service price re-fetch trong `createOrderFromCommand` (mitigate T-04-05-02 — client-supplied unitPrice tampering). Hiện accept client-supplied price snapshot.

---

## Self-Check: PASSED

**Created/Modified files exist on disk:**
- FOUND: `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java`
- FOUND: `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/OrderController.java`
- FOUND: `sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/web/OrderControllerCreateOrderCommandTest.java`
- FOUND: `sources/frontend/src/types/api/orders.generated.ts` (drift uncommitted)

**Acceptance criteria (Task 1):** 8/8 grep OK (`CreateOrderCommand`, `OrderItemRequest`, `ShippingAddressRequest`, `createOrderFromCommand`, `"PENDING"`, `X-User-Id`, `OrderUpsertRequest` count=1, `createOrder(OrderUpsertRequest request)` count=1); `mvn -DskipTests clean package` exit 0.

**Acceptance criteria (Task 2):** 5/5 grep OK (`import …CreateOrderCommand;`, `@RequestHeader(value = "X-User-Id"`, `@Valid @RequestBody CreateOrderCommand command`, `orderCrudService.createOrderFromCommand(userId, command)`, `OrderUpsertRequest` import retained); `mvn -DskipTests clean package` exit 0.

**Acceptance criteria (Task 3):** test file exists; 4/4 grep OK (`X-User-Id`, `isEqualTo(HttpStatus.CREATED)`, `"PENDING"`, `VALIDATION_ERROR`); `mvn clean test` → `Tests run: 9, Failures: 0, Errors: 0`; live curl 3/3 PASS; admin path regression check 201; FE codegen drift +23/-2 confirmed.

**Verification gates (per plan `<verification>`):**
1. ✅ Compile gate — exit 0
2. ✅ Test gate — `Tests run: 9, Failures: 0, Errors: 0`
3. ✅ Live happy path — 201 PENDING with correct userId + totalAmount
4. ✅ Live missing-header — 400 BAD_REQUEST + "Missing X-User-Id"
5. ✅ Live validation — 400 VALIDATION_ERROR + fieldErrors[items]
6. ✅ FE codegen drift — +23/-2 on `orders.generated.ts`

**No commits made** — theo user MEMORY.md "Claude must not run git commit without explicit per-commit ask"; user sẽ stage + commit thủ công.

---
*Phase: 04-frontend-contract-alignment-e2e-validation*
*Plan: 04-05*
*Completed: 2026-04-25*
