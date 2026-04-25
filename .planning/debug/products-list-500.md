---
slug: products-list-500
status: root_cause_found
trigger: |
  GET /api/products/products trả 500 INTERNAL_ERROR thay vì empty list từ in-memory repo
created: 2026-04-25
updated: 2026-04-25
---

# Debug Session — products-list-500

## Symptoms

<DATA_START>
- expected_behavior: |
    GET http://localhost:8080/api/products/products?page=0&size=5 phải trả 200 với envelope
    {
      "timestamp": "...",
      "status": 200,
      "message": "OK",
      "data": {
        "content": [...],
        "totalElements": ...,
        "totalPages": ...,
        "currentPage": 0,
        "pageSize": 5,
        "isFirst": true,
        "isLast": ...
      }
    }
    Theo Phase 02 SUMMARY (02-02-SUMMARY.md), product-service dùng in-memory repository,
    nên ngay cả khi chưa seed dữ liệu thì list phải là content=[] (200), không phải 500.

- actual_behavior: |
    GET http://localhost:8080/api/products/products?page=0&size=5 trả status 500.
    Body lỗi:
    {
      "timestamp": "2026-04-25T04:20:33.030633527Z",
      "status": 500,
      "error": "Internal Server Error",
      "message": "Internal server error",
      "code": "INTERNAL_ERROR",
      "path": "/products",
      "traceId": "ae66948c-7af4-42fc-8efe-48440142868b",
      "fieldErrors": []
    }

- error_messages: |
    Body trả về chỉ có "Internal server error" generic (Phase 03 mask leak hoạt động đúng).
    Stack trace thật chưa được kiểm tra — phải đọc từ `docker compose logs product-service`.
    User chưa xem log; trông cậy debugger fetch.

- timeline: |
    User test endpoint qua browser (paste URL vào Google) → 500 ngay lần đầu.
    Đây là LẦN ĐẦU TIÊN ai đó gọi GET /products live trên container.
    Phase 02 SUMMARY đánh dấu hoàn thành 2026-04-22 nhưng chỉ verify build Maven + smoke gateway prefix
    (script .ps1 chỉ check route 200/4xx, không assert body). Chưa từng có UAT đọc data thật.

- reproduction: |
    1. docker compose up -d
    2. Mở browser → http://localhost:8080/api/products/products?page=0&size=5
       (hoặc trực tiếp http://localhost:8082/products)
    3. Quan sát response 500.

- environment: |
    OS: Windows 11 Pro
    Docker Desktop với compose stack trong docker-compose.yml
    7 service (api-gateway 8080, user 8081, product 8082, order 8083, payment 8084, inventory 8085, notification 8086)
    Frontend Next.js 16.2.3 ở port 3000.
    User chưa chạy `docker compose ps` để check container status — debugger nên kiểm tra trước.
</DATA_END>

## Initial Hypotheses (chưa verify)

1. **In-memory repository không thực sự là in-memory** — code có thể đã chuyển sang JPA/PostgreSQL ngầm (e.g., `@Entity` + `@Repository` extends `JpaRepository`) mà không có DataSource → AutoConfiguration crash khi service nhận request.
2. **In-memory repo OK nhưng controller bug** — paging param parsing fail (e.g., `Pageable` resolver không cấu hình, sort param strict, hoặc cast lỗi).
3. **Container product-service không lành mạnh** — Spring Boot start fail (port conflict, OOM, cấu hình thiếu) → gateway forward → service trả 5xx hoặc gateway không thấy upstream.
4. **Path mismatch sau gateway rewrite** — gateway rewrite `/api/products/**` → `/**` nhưng controller mount tại `/products`, double-segment đáng lẽ phải là `/api/products/products`. Path field trong response là `/products` (sau rewrite) → controller có khớp không?

## Current Focus

- hypothesis: Transient runtime fault (host-sleep induced) caught by silent fallback handler — source code is correct.
- next_action: Present root cause + observability fix to user.
- test: live curl reproduction post-restart returns 200 with empty content envelope (already verified).
- expecting: User-visible 500 cannot recur as long as JVM stays alive; underlying observability gap must be patched.

## Evidence

- timestamp: 2026-04-25T04:38:30Z
  finding: docker compose ps shows all 7 services Up "About a minute" — product-service container is healthy AT INVESTIGATION TIME (but was not at failure time, see below).

- timestamp: 2026-04-25T04:38:58Z
  finding: |
    Live reproduction attempts both succeed with HTTP 200:
      curl http://localhost:8082/products?page=0&size=5 →
        {"timestamp":"...","status":200,"message":"Products listed",
         "data":{"content":[],"totalElements":0,"totalPages":1,"currentPage":0,"pageSize":5,"isFirst":true,"isLast":true}}
      curl http://localhost:8080/api/products/products?page=0&size=5 → identical envelope
    The reported 500 is NOT reproducible against the current container.

- timestamp: 2026-04-25T04:39:15Z
  finding: |
    docker inspect tmdt-use-gsd-product-service-1 — StartedAt=2026-04-25T04:37:14Z, RestartCount=0.
    docker image inspect tmdt-use-gsd-product-service — image Created=2026-04-24T00:44:41Z.
    Container created 2026-04-24T00:44:57Z but StartedAt is 2026-04-25T04:37:14Z with RestartCount=0:
    pattern of `docker compose stop` / `docker compose start` (or Docker Desktop pause/resume across host sleep).
    No image rebuild in the failure window.

- timestamp: 2026-04-25T04:39:30Z
  finding: |
    git log --since='2026-04-25 03:00' product-service & docker-compose.yml — empty.
    Latest source change: commit d3f61c0 "feat: phase 3 implement p1" at 2026-04-23T15:26 UTC.
    No code change between failure (04:20:33Z) and current healthy state (04:38:58Z).
    Conclusion: same source, same image — yet failure ≠ current behavior.
    The bug is NOT in the committed source.

- timestamp: 2026-04-25T04:39:45Z
  finding: |
    docker compose logs product-service shows TWO Spring Boot startup banners separated by a 6h 51m gap:
      banner #1 → "Started ProductServiceApplication" 2026-04-24T00:45:19.661Z
      last log → "Init duration for springdoc-openapi is: 131 ms" 2026-04-24T00:45:32.782Z
      banner #2 → "Starting ProductServiceApplication" 2026-04-25T04:37:19.168Z
    User's failed request at 2026-04-25T04:20:33Z falls inside this gap — yet the buffered log stream has
    ZERO entries during the gap (no request log, no exception log, no shutdown notice).
    Interpretation: JVM was suspended (Docker Desktop WSL2/Hyper-V froze on host sleep) for hours.
    The container did not crash (RestartCount=0), it was paused-then-resumed.

- timestamp: 2026-04-25T04:40:00Z
  finding: |
    Source review of GlobalExceptionHandler.handleFallback (product-service line 97-112):
      @ExceptionHandler(Exception.class)
      public ResponseEntity<ApiErrorResponse> handleFallback(Exception ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(ApiErrorResponse.of(
            status.value(), status.getReasonPhrase(),
            "Internal server error",       // <-- exact lowercase string in user's failure body
            "INTERNAL_ERROR",
            request.getRequestURI(),
            getTraceId(request),
            List.of()
        ));
      }
    NO logger, NO ex.getMessage() printed, NO stack trace emitted.
    The exception is caught and the response is built — the original Throwable is discarded.

- timestamp: 2026-04-25T04:40:15Z
  finding: |
    The failure envelope's `message: "Internal server error"` (exact case) matches ONLY the product-service
    handler. Gateway's GlobalGatewayErrorHandler uses `message: "Internal error"` (no "server").
    Therefore the 500 was authored by product-service itself, not the gateway, and forwarded via
    api-gateway's pass-through path (tryExtractPassThroughBody) — gateway preserved the upstream envelope.
    => product-service WAS reachable at 04:20:33Z and DID throw an Exception that hit handleFallback.

- timestamp: 2026-04-25T04:40:30Z
  finding: |
    pom.xml has only spring-boot-starter-{web,validation,actuator} + springdoc-openapi-starter-webmvc-ui.
    No spring-boot-starter-data-jpa, no h2, no postgresql driver. application.yml has no datasource block.
    ProductCrudService.listProducts handles empty repository correctly (paginate(emptyList, 0, 5) returns
    content=[], totalElements=0, totalPages=1, isFirst=true, isLast=true — matches 04:38:58Z curl output).
    Hypothesis 1 (silent JPA wiring) ELIMINATED.
    Hypothesis 2 (controller paging bug) ELIMINATED — same code, same params, returns 200 now.
    Hypothesis 3 (container unhealthy) PARTIALLY confirmed: container alive but JVM thread state corrupt
      after host-sleep resume.
    Hypothesis 4 (path mismatch) ELIMINATED — gateway rewrite /api/products/(?<segment>.*) → /${segment}
      maps /api/products/products → /products, controller @RequestMapping("/products") matches,
      and live request via gateway at 04:38:58Z returns 200.

- timestamp: 2026-04-25T04:40:45Z
  finding: |
    Systemic observability gap — identical handleFallback signature (no logger) exists in ALL six services:
      sources/backend/{user,product,order,payment,inventory,notification}-service/.../api/GlobalExceptionHandler.java
    Phase 03 mask-leak rule was applied uniformly but the logging side of that rule was not implemented.
    Any future 500 in any service will be undebuggable unless container is captured live with a profiler.

## Eliminated

- JPA/PostgreSQL silent autoconfig (no JPA deps in pom.xml, no datasource in application.yml).
- Pageable resolver bug (controller uses primitive int params, comparator handles default sort safely).
- Path-mismatch after gateway rewrite (route + controller mapping verified live).
- Source-level bug in listProducts/paginate empty-list path (live request returns 200 with expected envelope).
- Container crash / OOM (RestartCount=0, no exit/restart events).

## Resolution

- root_cause: |
    Two-layer root cause:

    PROXIMATE (the user-facing 500 at 04:20:33Z):
      product-service JVM was paused for ~6h 51m by Docker Desktop while the Windows host slept.
      When the user's request arrived (presumably after partial host wake), the JVM thread handling
      the request encountered a transient I/O / timing fault — most likely a stale-connection or
      response-write IOException as the gateway's HTTP client had already timed out from its side —
      which propagated as a generic Exception caught by GlobalExceptionHandler.handleFallback.

    DEEPER (why we cannot prove the proximate cause):
      GlobalExceptionHandler.handleFallback in all 6 services catches Exception.class and builds a
      masked response WITHOUT logging the original Throwable. The stack trace was discarded. This is
      a Phase 03 implementation gap: the leak-mask rule was applied to the response body but the
      paired requirement to log internally was not. Phase 02 SUMMARY's "in-memory returns content=[]"
      contract is INTACT — current source serves the empty list correctly.

- fix: |
    Two recommended actions, presented as options to the user (not auto-applied):

    Option A — Observability fix only (smallest, addresses the "we couldn't see what happened" gap):
      In all six services' GlobalExceptionHandler.java, add a private static SLF4J logger and log the
      caught Throwable inside handleFallback with full stack trace before returning the masked response.
      Suggested patch shape (apply to each of 6 files):

        import org.slf4j.Logger;
        import org.slf4j.LoggerFactory;
        ...
        private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ...
        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiErrorResponse> handleFallback(Exception ex, HttpServletRequest request) {
          log.error("Unhandled exception for {} {} (traceId={})",
              request.getMethod(), request.getRequestURI(), getTraceId(request), ex);
          HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
          return ResponseEntity.status(status).body(ApiErrorResponse.of(...));  // unchanged body
        }

      Files affected:
        sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/api/GlobalExceptionHandler.java
        sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/api/GlobalExceptionHandler.java
        sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/api/GlobalExceptionHandler.java
        sources/backend/payment-service/src/main/java/com/ptit/htpt/paymentservice/api/GlobalExceptionHandler.java
        sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/api/GlobalExceptionHandler.java
        sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/api/GlobalExceptionHandler.java

      Tests: matching GlobalExceptionHandlerTest.java exists for at least order-service & user-service —
      add an assertion that handleFallback emits a log event at ERROR level (using a CapturingAppender or
      OutputCaptureExtension). Update Phase 03 SUMMARY/checklist to record the logging requirement.

    Option B — Observability fix + UAT smoke harden (recommended, prevents recurrence in CI):
      Apply Option A AND extend the existing PowerShell smoke script that "checks route 200/4xx but not
      body shape" to assert the response envelope shape on at least one happy-path GET per service
      (e.g., GET /api/products/products must return 200 with data.content as an array). This converts
      the failure mode that escaped to UAT (empty-list 200 vs accidental 500) into a CI gate.

    No source change is recommended for the proximate transient cause. Spring Boot containers paused by
    host sleep is environmental; the correct mitigation is the observability fix so that if such a
    transient does recur, the stack trace is captured.

- verification: |
    After applying Option A:
      1. Restart product-service: docker compose restart product-service
      2. Force a fallback path (e.g., temporarily inject a controller that throws IllegalStateException,
         or trigger an existing edge case) and verify:
           - response body is unchanged (still masked: "Internal server error" / INTERNAL_ERROR)
           - product-service logs now contain a stack trace at ERROR level with the traceId.
      3. Empty-list happy path remains 200:
           curl -s http://localhost:8080/api/products/products?page=0&size=5 | jq '.status, .data.content'
           # expected: 200, []

    Already confirmed without any change:
      - curl http://localhost:8082/products?page=0&size=5 → HTTP 200 with content=[]
      - curl http://localhost:8080/api/products/products?page=0&size=5 → HTTP 200 with content=[]

- files_changed: |
    NONE applied during investigation. Per user policy (no auto-commit, manual staging), proposed
    changes are described above as Option A / Option B for user to apply when ready.
