# Phase 26: Tích Hợp Thanh Toán VNPay Sandbox - Pattern Map

**Mapped:** 2026-05-22
**Files analyzed:** 19 (new + modified)
**Analogs found:** 17 / 19

> Mọi file mới PHẢI copy pattern từ analog đã verified bên dưới. Hạ tầng messaging Phase 23 + REST client Phase 19 đã ổn định — KHÔNG phát minh pattern mới (RESEARCH §Don't Hand-Roll).

---

## File Classification

### payment-service (BE)

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `payment-service/.../vnpay/VNPayConfig.java` | config | — | order-service `RabbitMQConfig` (`@Configuration` + constants) | role-match |
| `payment-service/.../vnpay/VNPaySignature.java` | utility | transform | — (HMAC SHA512 — RESEARCH §Code Examples) | no analog |
| `payment-service/.../vnpay/VNPayService.java` | service | request-response | order-service `OrderCrudService` (service + REST + tx) | role-match |
| `payment-service/.../vnpay/VNPayController.java` | controller | request-response | payment-service `PaymentController` | exact |
| `payment-service/.../messaging/config/RabbitMQConfig.java` | config | event-driven | order-service `messaging/config/RabbitMQConfig.java` | exact |
| `payment-service/.../messaging/event/PaymentEventEnvelope.java` | model | event-driven | order-service `messaging/event/OrderEventEnvelope.java` | exact |
| `payment-service/.../messaging/publisher/PaymentEventPublisher.java` | service | pub-sub | order-service `messaging/publisher/OrderEventPublisher.java` | exact |
| `payment-service/.../service/PaymentCrudService.java` (MODIFY) | service | CRUD | self — mở rộng `createSession` cho provider VNPAY | exact |
| `payment-service/src/main/resources/db/migration/V2__*.sql` (optional processed_events) | migration | — | order-service `V5__add_coupons.sql` + inventory `processed_events` schema | role-match |
| `payment-service/pom.xml` (MODIFY) | config | — | order-service `pom.xml` (đã có `spring-boot-starter-amqp`) | exact |

### order-service (BE)

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `order-service/.../service/PaymentSessionClient.java` (NEW) | service | request-response | order-service `service/ProductBatchClient.java` | exact |
| `order-service/.../messaging/consumer/PaymentEventListener.java` (NEW) | consumer | event-driven | inventory-service `messaging/consumer/OrderPlacedListener.java` | exact |
| `order-service/.../messaging/config/RabbitMQConfig.java` (MODIFY) | config | event-driven | self — thêm exchange `payment.events` + queue `order.payment-events` | exact |
| `order-service/.../service/OrderCrudService.java` (MODIFY) | service | CRUD | self — nhánh VNPAY trong `createOrderFromCommand` | exact |
| `order-service/.../domain/OrderEntity.java` (MODIFY) | model | — | self — thêm field `paymentStatus` + `vnpTransactionNo` (pattern cột `couponCode`) | exact |
| `order-service/.../domain/OrderDto.java` + `OrderMapper.java` (MODIFY) | model | — | self — expose `paymentStatus`, `vnpTransactionNo`, `paymentUrl` (nullable) | exact |
| `order-service/src/main/resources/db/migration/V6__add_payment_status.sql` (NEW) | migration | — | order-service `V5__add_coupons.sql` (ALTER TABLE orders ADD COLUMN) | exact |

### api-gateway (BE)

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `api-gateway/src/main/resources/application.yml` (MODIFY) | config | — | self — thêm 2 entry vào `app.auth.public-endpoints` | exact |

### frontend (FE)

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `frontend/src/app/checkout/page.tsx` (MODIFY) | component | request-response | self — thêm 1 entry vào mảng payment options + redirect | exact |
| `frontend/src/app/checkout/result/page.tsx` (NEW route) | component | request-response | `frontend/src/app/profile/orders/[id]/page.tsx` (load + poll + states) | role-match |
| `frontend/src/services/payments.ts` hoặc `orders.ts` (MODIFY) | service | request-response | `frontend/src/services/orders.ts` (httpGet/httpPost helpers) | exact |
| `frontend/src/lib/orderLabels.ts` (MODIFY) | utility | — | self — thêm `VNPAY` vào `paymentMethodMap` (đã có `paymentStatusMap`) | exact |

---

## Pattern Assignments

### `payment-service/.../messaging/config/RabbitMQConfig.java` (config, event-driven)

**Analog:** `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/config/RabbitMQConfig.java`

Copy nguyên cấu trúc. Đổi exchange/queue/DLX sang namespace `payment.*`. RESEARCH Anti-Pattern: KHÔNG reuse `order.events` (bind `order.#` → inventory + notification consume nhầm).

**Constants + topic exchange** (analog lines 35-47):
```java
public static final String EXCHANGE = "payment.events";          // exchange RIÊNG (RESEARCH §Pattern 3)
public static final String DLX = "payment.dlx";
public static final String DLQ = "payment-events.dlq";
public static final String ROUTING_KEY_SUCCEEDED = "payment.succeeded";
public static final String ROUTING_KEY_FAILED = "payment.failed";

@Bean
public TopicExchange paymentEventsExchange() {
  return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
}
```

**RabbitTemplate + Jackson converter** (analog lines 90-102) — copy nguyên `setMandatory(true)` để bật publisher-returns.

> Lưu ý topology: order-service `RabbitMQConfig` (consumer side) khai báo queue `order.payment-events` bind `payment.#` tới exchange `payment.events`. AmqpAdmin idempotent khi args identical — cả hai service declare cùng exchange OK (analog javadoc lines 18-23).

---

### `payment-service/.../messaging/event/PaymentEventEnvelope.java` (model, event-driven)

**Analog:** `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/event/OrderEventEnvelope.java`

Copy `record` + factory-method pattern (analog lines 15-44). Cùng 5 field top-level `eventId/eventType/occurredAt/traceId/payload` để order-service consumer deserialize qua cùng `Jackson2JsonMessageConverter`.

```java
public record PaymentEventEnvelope(
    String eventId, String eventType, String occurredAt, String traceId, PaymentPayload payload) {

  public static PaymentEventEnvelope of(String eventType, String traceId, PaymentPayload payload) {
    return new PaymentEventEnvelope(
        UUID.randomUUID().toString(), eventType, Instant.now().toString(), traceId, payload);
  }

  // payload: orderId (← từ session), paymentSessionId, vnpTransactionNo, amount, currency
  public record PaymentPayload(
      String orderId, String paymentSessionId, String vnpTransactionNo, BigDecimal amount, String currency) {}
}
```
`eventType` = `"PaymentSucceeded"` | `"PaymentFailed"`.

---

### `payment-service/.../messaging/publisher/PaymentEventPublisher.java` (service, pub-sub)

**Analog:** `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/publisher/OrderEventPublisher.java`

Copy NGUYÊN file (afterCommit defer + Publisher Confirms 5s + capture MDC traceId — analog lines 52-106). Đây là pattern critical: publish SAU commit transaction, capture `MDC.get("traceId")` NGAY tại caller thread (Pitfall 1).

**afterCommit defer** (analog lines 52-69):
```java
public void publishPaymentEvent(String eventType, PaymentEventEnvelope.PaymentPayload payload) {
  String traceId = MDC.get("traceId");
  final String safeTraceId = traceId != null ? traceId : "no-trace";
  final PaymentEventEnvelope envelope = PaymentEventEnvelope.of(eventType, safeTraceId, payload);
  if (TransactionSynchronizationManager.isSynchronizationActive()) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override public void afterCommit() { doPublish(envelope, safeTraceId); }
    });
  } else {
    doPublish(envelope, safeTraceId);
  }
}
```

**doPublish + Confirms** (analog lines 71-106) — copy nguyên; routing key chọn theo `eventType` (`payment.succeeded` / `payment.failed`). Giữ log contract `[MQ-PUB]` / `[MQ-PUB-NACK]` / `[MQ-PUB-TIMEOUT]`.

> Cần copy thêm `TraceIdMessagePostProcessor` (analog `messaging/tracing/`) vào payment-service.

---

### `payment-service/.../vnpay/VNPayController.java` (controller, request-response)

**Analog:** `sources/backend/payment-service/src/main/java/com/ptit/htpt/paymentservice/web/PaymentController.java`

**Imports + class shape** (analog lines 1-28):
```java
@RestController
@RequestMapping("/payments")
public class VNPayController {
  private final VNPayService vnPayService;
  public VNPayController(VNPayService vnPayService) { this.vnPayService = vnPayService; }
```

**CRITICAL — bypass ApiResponseAdvice envelope.** RESEARCH §IPN handler + Assumption A2: `ApiResponseAdvice` (`@RestControllerAdvice`, `ResponseBodyAdvice`) wrap MỌI JSON response không phải `ApiResponse`/`ApiErrorResponse`/`CharSequence`. VNPay yêu cầu JSON THUẦN `{"RspCode","Message"}`. Hai cách (planner chốt):
- Trả `String` (JSON thủ công) — `ApiResponseAdvice.supports()` đã loại `CharSequence` (verified `ApiResponseAdvice.java` line: `return !CharSequence.class.isAssignableFrom(paramType)`), HOẶC
- Thêm path prefix `/payments/vnpay` vào `SKIP_PREFIXES` của `ApiResponseAdvice` (verified set tại đầu file).

**IPN endpoint** (RESEARCH §Code Examples — IPN handler):
```java
@GetMapping("/vnpay/ipn")
public Map<String, String> ipn(@RequestParam Map<String, String> params) {
  // verify chữ ký → so khớp amount/txnRef → idempotent check → publish event
  // return Map.of("RspCode","00","Message","Confirm Success")  // 02 nếu đã xử lý
}
```

**return endpoint** — `GET /payments/vnpay/return`: CHỈ verify chữ ký để FE hiển thị, KHÔNG update DB, KHÔNG publish event (D-06, Pitfall 5). Có thể trả redirect 302 sang FE result page với query string, hoặc trả JSON cho FE đọc.

---

### `payment-service/.../vnpay/VNPaySignature.java` (utility, transform) — NO ANALOG

Không có analog HMAC trong codebase. Dùng `javax.crypto.Mac.getInstance("HmacSHA512")` (JDK built-in). Copy `buildPaymentUrl` + `verifySignature` + `hmacSHA512` verbatim từ RESEARCH §Code Examples (lines 282-351). Điểm chí mạng: dùng CÙNG `URLEncoder.encode(v, StandardCharsets.US_ASCII)` cho cả ký lẫn verify; loại `vnp_SecureHash` + `vnp_SecureHashType` trước khi verify; `vnp_Amount` × 100.

---

### `payment-service/.../vnpay/VNPayConfig.java` (config) — `@ConfigurationProperties`

Đọc env var (RESEARCH §Runtime State): `VNP_TMN_CODE`, `VNP_HASH_SECRET`, `VNP_PAY_URL`, `VNP_RETURN_URL`, `VNP_IPN_URL`. KHÔNG hardcode, KHÔNG log secret. Pattern `@ConfigurationProperties` record + `@EnableConfigurationProperties`. payment-service `application.yml` đã dùng `${ENV:default}` placeholder (verified `datasource.username: ${DB_USER:payment_svc}`) — theo cùng style.

---

### `order-service/.../service/PaymentSessionClient.java` (service, request-response) — NEW

**Analog:** `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/ProductBatchClient.java`

Copy NGUYÊN cấu trúc `@Component` + `RestTemplate` injected. RESEARCH §Pattern 2 + A5 khuyến nghị gọi QUA gateway (`http://api-gateway:8080/api/payments/sessions`) + forward Bearer JWT — IDENTICAL `ProductBatchClient`.

**Imports + class + URL constant** (analog lines 1-37):
```java
@Component
public class PaymentSessionClient {
  private static final String URL = "http://api-gateway:8080/api/payments/sessions";
  private final RestTemplate restTemplate;
  public PaymentSessionClient(RestTemplate restTemplate) { this.restTemplate = restTemplate; }
```

**POST + forward Bearer JWT + unwrap envelope** (analog lines 39-64):
```java
public String createVNPaySession(String orderId, long amountVnd, String orderInfo, String authHeader) {
  HttpHeaders headers = new HttpHeaders();
  headers.setContentType(MediaType.APPLICATION_JSON);
  if (authHeader != null) headers.set(HttpHeaders.AUTHORIZATION, authHeader);  // analog lines 46-49
  HttpEntity<Map<String,Object>> entity = new HttpEntity<>(
      Map.of("provider","VNPAY","orderId",orderId,"amount",amountVnd,"orderInfo",orderInfo), headers);
  ResponseEntity<ApiResponse<...>> resp = restTemplate.exchange(
      URL, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
  // unwrap ApiResponse envelope → trả paymentUrl
}
```
> KHÁC `ProductBatchClient` ở error handling: `ProductBatchClient` best-effort fallback empty map. `PaymentSessionClient` fail → đơn VNPAY không có paymentUrl là lỗi cứng → throw `ResponseStatusException` (pattern `OrderCrudService` lines 87, 98). Planner chốt.

---

### `order-service/.../messaging/consumer/PaymentEventListener.java` (consumer, event-driven) — NEW

**Analog:** `sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/messaging/consumer/OrderPlacedListener.java`

Copy NGUYÊN file. order-service hiện CHƯA có consumer nào — đây là consumer đầu tiên của order-service.

**`@RabbitListener` + `@Transactional` + idempotency-first** (analog lines 45-65):
```java
@RabbitListener(queues = "order.payment-events")
@Transactional
public void onPaymentEvent(@Payload PaymentEventEnvelope envelope,
                           @Header(name="X-Trace-Id", required=false) String traceIdHeader) {
  String eventId = envelope.eventId();
  // Pitfall 8: INSERT processed_events ĐẦU TIÊN trong cùng transaction
  boolean inserted = processedEventRepository.insertIfAbsent(eventId, envelope.eventType());
  if (!inserted) { /* skipped-duplicate */ return; }
  // business: orderCrudService.applyPaymentResult(orderId, eventType)
  //   PaymentSucceeded → payment_status=PAID + publish OrderPlaced (D-09)
  //   PaymentFailed    → payment_status=FAILED
}
```

**Error classification** (analog lines 66-80) — copy nguyên: `PermanentMessageException` → DLQ; `TransientDataAccessException` → `TransientMessageException` retry; default `RuntimeException` → `AmqpRejectAndDontRequeueException`.

> **DEPENDENCY:** order-service CHƯA có `ProcessedEventRepository` / `ProcessedEventEntity` / `processed_events` table. Phải port từ inventory-service (`ProcessedEventRepository.java` + `ProcessedEventEntity.java` đã đọc — copy verbatim, đổi package) + thêm migration tạo bảng `processed_events` (gộp vào V6 hoặc V7). order-service cũng cần copy `TraceIdConsumerInterceptor` từ inventory.

---

### `order-service/.../service/OrderCrudService.java` MODIFY — nhánh VNPAY

**Analog:** chính file này — `createOrderFromCommand` (lines 123-198).

Hiện tại line 195 `orderEventPublisher.publishOrderPlaced(payload)` chạy VÔ ĐIỀU KIỆN. D-09/D-10: rẽ nhánh theo `command.paymentMethod()`:
- `VNPAY` → set `order.paymentStatus="PENDING"`; gọi `paymentSessionClient.createVNPaySession(...)` lấy `paymentUrl`; **KHÔNG** gọi `publishOrderPlaced`; trả `OrderDto` có field `paymentUrl`.
- non-VNPAY (COD...) → giữ nguyên hành vi: `publishOrderPlaced` ngay (line 195).

`OrderEventPublisher` được order-service gọi để publish `OrderPlaced` lúc consume `PaymentSucceeded` — tách ra method dùng chung (`applyPaymentResult`). `vnp_Amount` = `saved.total()` (đã trừ discount — line 162) × 100.

---

### `order-service/.../domain/OrderEntity.java` MODIFY — thêm cột

**Analog:** chính file này — pattern cột `couponCode` (lines 60-61, 125-127).

```java
@Column(name = "payment_status", nullable = false, length = 20)
private String paymentStatus = "PENDING";          // default — pattern discountAmount line 57-58

@Column(name = "vnp_transaction_no", length = 50)
private String vnpTransactionNo;                    // nullable — pattern couponCode line 60-61
```
Thêm getter style record (`paymentStatus()`, `vnpTransactionNo()`) + setter (pattern lines 124-127).

---

### `order-service/src/main/resources/db/migration/V6__add_payment_status.sql` (migration) — NEW

**Analog:** `sources/backend/order-service/src/main/resources/db/migration/V5__add_coupons.sql` (lines 29-30: `ALTER TABLE orders ADD COLUMN IF NOT EXISTS`).

Verified: order-svc migrations hiện có `V1, V2, V4, V5` → migration mới = **V6** (A1 confirmed — V5 là cao nhất). Nếu gộp `processed_events` table → có thể V6 = cột + V7 = bảng, hoặc gộp 1 file.
```sql
-- order-svc V6 / PAY-02 (D-02): payment_status + vnp_transaction_no cho VNPay
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS vnp_transaction_no VARCHAR(50);

-- processed_events cho PaymentEventListener idempotency (pattern inventory-service)
CREATE TABLE IF NOT EXISTS processed_events (
  event_id     VARCHAR(36) PRIMARY KEY,
  event_type   VARCHAR(64) NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

### `api-gateway/src/main/resources/application.yml` MODIFY — whitelist IPN/return

**Analog:** chính file này — `app.auth.public-endpoints` (verified lines 220-226).

Thêm 2 entry (RESEARCH §Pitfall 4 + D-15) — theo đúng format `{ method, pattern }` hiện có:
```yaml
public-endpoints:
  # ... entry hiện có (login, register, products, ...) ...
  - { method: GET, pattern: "/api/payments/vnpay/ipn" }
  - { method: GET, pattern: "/api/payments/vnpay/return" }
```
> Route `payment-service` (gateway lines 175-180: `Path=/api/payments/**` → `RewritePath` strip `/api/payments`) đã forward đúng — không cần route mới. Bảo mật dựa verify `vnp_SecureHash` (D-15).

---

### `frontend/src/app/checkout/page.tsx` MODIFY — selector VNPay

**Analog:** chính file này — mảng payment options (lines 332-350) + `submitOrder` (lines 183-274).

**Thêm 1 entry vào mảng** (lines 333-337) + mở rộng type union line 76:
```tsx
{ value: 'VNPAY', label: 'Thanh toán qua VNPay', icon: '💳' },   // UI-SPEC §Copywriting
// type: paymentMethod: 'COD' | 'BANK_TRANSFER' | 'E_WALLET' | 'VNPAY'
```

**Redirect sau `createOrder`** — trong `submitOrder` (lines 188-220), sau khi nhận `order`: nếu `form.paymentMethod === 'VNPAY'` và `order.paymentUrl` có giá trị → `showToast('Đang chuyển tới cổng thanh toán VNPay...', 'success')` rồi `window.location.assign(order.paymentUrl)` THAY VÌ `router.push('/profile/orders/'+order.id)` (line 220). Giữ cart cleanup logic (lines 209-219) như cũ.

---

### `frontend/src/app/checkout/result/page.tsx` (component, request-response) — NEW

**Analog:** `sources/frontend/src/app/profile/orders/[id]/page.tsx` — pattern load + state machine (loading/failed/empty/data) + `RetrySection`.

Copy structure: `'use client'` + `useSearchParams` (đọc `vnp_ResponseCode`, `vnp_TxnRef`) + `useState` cho order + `useCallback` load. Phải bọc `<Suspense>` vì dùng `useSearchParams` (pattern checkout `page.tsx` lines 530-537).

**Polling** (UI-SPEC §Interaction Contract — KHÔNG có analog poll trực tiếp, nhưng base = `load` callback analog lines 29-47): poll `getOrderById(txnRef→orderId)` mỗi **3s, tối đa 5 lần**, dừng sớm khi `payment_status !== 'PENDING'`. Dùng `setInterval` + cleanup trong `useEffect`.

**States + components** (UI-SPEC §Component reuse): `Banner`, `Button` (`variant="primary"/"secondary"`), `RetrySection` (lỗi mạng poll — analog lines 68-76). Copy tiếng Việt verbatim từ UI-SPEC §Copywriting Contract (4 trạng thái: đang xác nhận / thành công / thất bại / huỷ / poll hết hạn). Render sơ bộ từ `vnp_ResponseCode`: `00`→chờ IPN, `24`→huỷ, khác→thất bại.

---

### `frontend/src/services/orders.ts` MODIFY (hoặc `payments.ts`)

**Analog:** chính file này — `getOrderById` (lines 69-71), `httpGet`/`httpPost` helpers.

`getOrderById` đã đủ cho polling `payment_status`. Chỉ cần đảm bảo type `Order` (`@/types`) có thêm field `paymentStatus`, `vnpTransactionNo`, `paymentUrl` (nullable). KHÔNG cần endpoint FE mới — `createOrder` (line 51-53) trả `Order` đã chứa `paymentUrl` (A3 — field nullable trên `OrderDto`).

---

### `frontend/src/lib/orderLabels.ts` MODIFY

**Analog:** chính file này.

`paymentStatusMap` ĐÃ có `PENDING/PAID/FAILED/REFUNDED` — không đổi. Chỉ thêm 1 entry vào `paymentMethodMap`:
```ts
export const paymentMethodMap: Record<string, string> = {
  COD: 'Thanh toán khi nhận hàng',
  BANK_TRANSFER: 'Chuyển khoản ngân hàng',
  E_WALLET: 'Ví điện tử',
  VNPAY: 'Thanh toán qua VNPay',   // UI-SPEC §Order display
};
```
Order display tại `profile/orders/[id]/page.tsx` (lines 234-240) ĐÃ render `paymentMethodMap` + `paymentStatusMap` — chỉ cần thêm dòng `vnp_transaction_no` (ẩn nếu rỗng — UI-SPEC). `admin/orders/[id]` tương tự.

---

## Shared Patterns

### Idempotency (event consumer)
**Source:** `inventory-service/.../repository/ProcessedEventRepository.java` + `domain/ProcessedEventEntity.java`
**Apply to:** `order-service` PaymentEventListener. payment-service IPN dùng `PaymentTransactionEntity.status` terminal làm idempotency key chính (RESEARCH §Pattern 4 / A6 — không bắt buộc bảng riêng).
```java
@Query(value = "INSERT INTO processed_events(event_id, event_type) "
    + "VALUES (:id, :type) ON CONFLICT (event_id) DO NOTHING", nativeQuery = true)
int insertNative(@Param("id") String eventId, @Param("type") String eventType);
default boolean insertIfAbsent(String eventId, String eventType) { return insertNative(eventId, eventType) == 1; }
```

### Publish-after-commit
**Source:** `order-service/.../messaging/publisher/OrderEventPublisher.java`
**Apply to:** payment-service `PaymentEventPublisher`. `TransactionSynchronizationManager.afterCommit` + capture MDC traceId tại caller thread + Publisher Confirms 5s timeout.

### Cross-service REST (qua gateway + forward JWT)
**Source:** `order-service/.../service/ProductBatchClient.java`
**Apply to:** `order-service` PaymentSessionClient. URL `http://api-gateway:8080/api/...`, forward `HttpHeaders.AUTHORIZATION`, `RestTemplate.exchange` + `ParameterizedTypeReference`.

### RabbitMQ listener retry (yml)
**Source:** `inventory-service/src/main/resources/application.yml` (lines 24-41)
**Apply to:** payment-service `application.yml` (consumer side không cần — payment chỉ publish; nhưng nếu thêm) + order-service đã có. Copy block `spring.rabbitmq` (`publisher-confirm-type: correlated`, `publisher-returns: true`, `listener.simple.retry` exp backoff 3 lần).

### ApiResponse envelope bypass
**Source:** `payment-service/.../api/ApiResponseAdvice.java` (`SKIP_PREFIXES` set + `supports()` loại `CharSequence`)
**Apply to:** `VNPayController` IPN/return — trả `String` JSON HOẶC thêm prefix vào `SKIP_PREFIXES`. VNPay yêu cầu JSON thuần `{"RspCode","Message"}`.

### Vietnamese label maps
**Source:** `frontend/src/lib/orderLabels.ts`
**Apply to:** mọi FE surface hiển thị payment method/status — lookup map, KHÔNG hardcode chuỗi.

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `payment-service/.../vnpay/VNPaySignature.java` | utility | transform | Không có HMAC SHA512 trong codebase — dùng `javax.crypto.Mac` (JDK built-in) + copy RESEARCH §Code Examples lines 282-351 |
| Polling logic trong `checkout/result/page.tsx` | component | request-response | Không có FE page nào poll định kỳ — base = `load` callback của `profile/orders/[id]`; thêm `setInterval` 3s × 5 (UI-SPEC §Interaction Contract) |

---

## Metadata

**Analog search scope:** `sources/backend/{order,inventory,payment,api-gateway}-service`, `sources/frontend/src/{app,services,lib}`
**Files scanned:** ~25 (RabbitMQConfig, OrderEventEnvelope, OrderEventPublisher, OrderPlacedListener, ProductBatchClient, PaymentController, ProcessedEventRepository/Entity, OrderCrudService, OrderEntity, V5 migration, checkout/page.tsx, orders.ts, profile/orders/[id]/page.tsx, orderLabels.ts, ApiResponseAdvice, gateway+inventory+payment application.yml)
**Pattern extraction date:** 2026-05-22
**Key cross-phase dependency:** order-service phải port `ProcessedEventRepository` + `ProcessedEventEntity` + `TraceIdConsumerInterceptor` từ inventory-service (order-service chưa có consumer infra). payment-service phải thêm `spring-boot-starter-amqp` + copy toàn bộ messaging module từ order-service.
```
