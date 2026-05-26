# Phase 23: Message Queue Integration (RabbitMQ) — Research

**Researched:** 2026-05-20
**Domain:** Asynchronous messaging cho Spring Boot microservices (RabbitMQ + Spring AMQP)
**Confidence:** HIGH (CONTEXT.md đã lock 18 quyết định kỹ thuật; research chỉ verify + lấp khoảng trống)
**Ngôn ngữ:** Toàn bộ docs tiếng Việt — identifier + commit prefix giữ tiếng Anh.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions (D-01 → D-18)

Sao chép verbatim từ `23-CONTEXT.md`. Planner KHÔNG được trái với các quyết định này — chỉ được fill detail bên trong.

**Topology**
- **D-01** Publish duy nhất event `OrderPlaced`, routing key `order.placed`. Các event khác defer.
- **D-02** Topic exchange `order.events` (type=topic, durable=true). 2 queue durable: `inventory.order-events`, `notification.order-events`, bind với binding key `order.#`.
- **D-15** JSON envelope: `{ eventId, eventType, occurredAt, traceId, payload: { orderId, userId, items: [{productId, quantity, priceAtPurchase}], totalAmount, currency } }`. Dùng `Jackson2JsonMessageConverter`.

**Publish (Producer)**
- **D-03** Publish SAU DB commit qua `TransactionSynchronizationManager.registerSynchronization` + `afterCommit()` — KHÔNG publish trong transaction.
- **D-04** Publisher Confirms bật: `spring.rabbitmq.publisher-confirm-type=correlated` + `publisher-returns=true`. Timeout confirm 5s → log warning + alert.
- **D-05** Publish thất bại (nack / timeout / return) → log error + alert + vẫn trả success cho user. Order đã có trong DB không rollback. Admin replay defer.

**Reliability (Consumer)**
- **D-06** Idempotency qua bảng `processed_events(event_id VARCHAR(36) PK, event_type VARCHAR(64), processed_at TIMESTAMPTZ NOT NULL DEFAULT now())`. Mỗi consumer có bảng riêng trong schema của mình (`inventory_svc.processed_events`, `notification_svc.processed_events`). INSERT trong cùng transaction với business logic; conflict → ACK & bỏ qua.
- **D-07** Retry: 3 lần, exponential backoff 1s→2s→4s. Config qua `spring.rabbitmq.listener.simple.retry.*` (`enabled=true`, `max-attempts=3`, `initial-interval=1000`, `multiplier=2.0`, `max-interval=10000`).
- **D-08** 2 custom exception trong `*.messaging.exception`:
  - `TransientMessageException` → retry
  - `PermanentMessageException` → reject NGAY → DLQ (không retry)
- **D-09** Dead Letter Exchange `order.dlx` (type=direct) + queue `order-events.dlq` (durable). Queue chính khai báo `x-dead-letter-exchange=order.dlx`.

**Consumers**
- **D-10** inventory-service: tìm `InventoryEntity` theo productId (không có → `PermanentMessageException`), giảm `quantity`, ghi `inventory_svc.stock_ledger(id, event_id, order_id, product_id, quantity_change, reason='order.placed', created_at)`, insert `processed_events`, ACK.
- **D-11** Stock validate đồng bộ trong `OrderCrudService.validateStock()` GIỮ NGUYÊN — vẫn trả 409 STOCK_SHORTAGE tại API.
- **D-12** Stock deduct REST trong `OrderCrudService.deductStock()` + `buildPatchBody()` XÓA — thay bằng publish event.
- **D-13** Flyway `V102__seed_initial_inventory.sql` (seed-dev) copy `product_svc.products.stock` → `inventory_svc.inventory_items.quantity`, idempotent (`ON CONFLICT DO NOTHING`).
- **D-14** notification-service consumer: render template "order-confirmation", INSERT `notification_svc.dispatch_log(id, event_id, recipient_user_id, channel='email', subject, body, status='SENT', sent_at)`, insert `processed_events`, ACK. KHÔNG gửi SMTP thật.

**Tracing & Logging**
- **D-16** Trace propagation: producer set header `X-Trace-Id` từ MDC; consumer interceptor đọc header → set MDC trước khi xử lý.
- **D-17** Logging contract:
  - Producer: `[MQ-PUB] event=OrderPlaced routingKey=order.placed eventId=... traceId=...`
  - Consumer: `[MQ-CONSUME] queue=... eventId=... status=received|done|failed|skipped-duplicate`
  - Retry: `[MQ-RETRY] eventId=... attempt=N/3 error=...`
  - DLQ: `[MQ-DLQ] eventId=... reason=... payload=...`

**Testing**
- **D-18** Integration test Testcontainers `rabbitmq:3-management`, 4 scenario tối thiểu: happy path, idempotency, DLQ (PermanentMessageException), retry-then-success.

### Claude's Discretion

- Tên `@Bean` cụ thể (`RabbitMQConfig`, `OrderEventPublisher`, `OrderEventConsumer`, etc.)
- SQL chi tiết của Flyway migration cho `processed_events`, `stock_ledger`, seed inventory
- Prefetch count (đề xuất 10 — verify ở Common Pitfalls)
- Tách package `messaging/` thành module riêng hay không (đề xuất: package thường, không Maven module riêng)
- `@RabbitListener` annotation vs programmatic listener container (đề xuất: `@RabbitListener` — đơn giản, đủ cho scope)

### Deferred Ideas (OUT OF SCOPE)

1. Saga + compensating action (`OrderCancelled`)
2. Event `PaymentSucceeded` / `PaymentFailed`
3. SMTP integration thật
4. Admin replay tool cho DLQ
5. Transactional Outbox pattern
6. Distributed tracing UI (Zipkin/Jaeger)
7. Tách DB hạ tầng (Phase 24)
8. Sửa lỗ hổng X-User-Id (Phase 25)
</user_constraints>

<phase_requirements>
## Phase Requirements

**Lưu ý:** REQUIREMENTS.md hiện tại (v1.3 milestone) KHÔNG có entry MQ-01..MQ-05 — chúng chỉ xuất hiện trong ROADMAP.md §214 và §277-281. Đây là gap trong REQUIREMENTS.md cần planner backfill (đề xuất task riêng cập nhật `.planning/REQUIREMENTS.md` thêm section "MQ — Message Queue").

| ID | Description (suy ra từ ROADMAP §215-222 + đề chủ đề 4 mục 3.3) | Research Support |
|----|------------------------------------------------------|------------------|
| MQ-01 | RabbitMQ container chạy trong docker-compose.yml với Management UI tại :15672 | Standard Stack §Docker; Architecture Patterns §Topology |
| MQ-02 | order-service publish `OrderPlaced` SAU DB commit với Publisher Confirms vào topic exchange `order.events` | Architecture Patterns §Producer pattern; Code Examples §Publisher |
| MQ-03 | inventory-service consume từ `inventory.order-events`, trừ kho atomic + ledger entry, idempotent qua `processed_events` | Architecture Patterns §Consumer + Idempotency; Code Examples §Inventory consumer |
| MQ-04 | notification-service consume từ `notification.order-events`, ghi `dispatch_log`, idempotent | Architecture Patterns §Consumer; Code Examples §Notification consumer; ⚠️ notification-service hiện CHƯA có JPA/Flyway/Postgres deps (in-memory) — cần bootstrap |
| MQ-05 | Retry 3 lần exponential backoff → DLQ `order-events.dlq` verify được trong Management UI; logs có `traceId` xuyên 3 service | Architecture Patterns §Retry+DLQ; Common Pitfalls §Trace propagation; Validation Architecture |
</phase_requirements>

## Summary

Phase 23 tích hợp RabbitMQ làm message broker bất đồng bộ cho 3 microservice (order publisher; inventory + notification consumers). CONTEXT.md đã lock 18 quyết định kỹ thuật cốt lõi (topology, reliability, idempotency, retry, DLQ, logging, testing) → vai trò research là (a) verify rằng các pattern Spring AMQP chuẩn match với D-01..D-18, (b) phát hiện gap không lường trước, và (c) cung cấp code skeleton + validation evidence cho planner.

**Gap critical phát hiện:**
1. **notification-service chưa có persistence** — pom.xml hiện chỉ có `web` + `validation` + `actuator` + `springdoc`. KHÔNG có `data-jpa`, `postgresql`, `flyway-core`, `flyway-database-postgresql`, `testcontainers`. Để D-14 (`dispatch_log` table trong schema `notification_svc`) hoạt động, planner phải bootstrap toàn bộ stack persistence cho service này. Đây có thể là 1 task riêng trong wave đầu.
2. **REQUIREMENTS.md thiếu MQ-01..MQ-05** — chỉ ROADMAP có, REQUIREMENTS.md không có section MQ. Cần task cập nhật REQUIREMENTS.md.
3. **docker-compose.yml chưa có RabbitMQ service** — cần thêm với healthcheck + Management UI port + 3 service `depends_on: rabbitmq: condition: service_healthy`.
4. **notification-service trong docker-compose thiếu env DB** — section `notification-service` (lines 106-109) không có `SPRING_PROFILES_ACTIVE` + `DB_*` env như các service khác. Phải thêm khi bootstrap persistence.

**Primary recommendation:** Sử dụng `@RabbitListener` annotation pattern + Spring Boot starter `spring-boot-starter-amqp` (v3.3.2 align với parent), Testcontainers `RabbitMQContainer` v1.20.x với `@ServiceConnection` (Spring Boot 3.1+). Đặt `afterCommit()` publisher trong service-layer method, KHÔNG trong controller. Idempotency check qua `INSERT ... ON CONFLICT DO NOTHING RETURNING` (Postgres-specific) trong cùng `@Transactional` với business logic.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Publish `OrderPlaced` event | order-service (Producer) | — | Owner của order domain — phát event khi entity state thay đổi |
| Stock decrement (async) | inventory-service (Consumer) | — | Owner của inventory domain — D-12 chuyển từ sync REST sang async event |
| Notification dispatch log | notification-service (Consumer) | — | Owner của dispatch_log — tách khỏi order flow, không block tạo đơn |
| Stock validate (sync) | order-service → product-service REST | — | D-11 lock: UX cần feedback đồng bộ — KHÔNG chuyển sang async |
| Idempotency check | Mỗi consumer service (DB-level) | — | Phải ở consumer side — broker không guarantee exactly-once |
| Retry + DLQ routing | RabbitMQ broker (infra) | Spring AMQP listener | Broker giữ DLX binding; Spring quyết định throw/reject |
| Trace propagation | order-service (producer set header) | inventory + notification (consumer interceptor) | MDC contextual; cần manual bridge qua AMQP header |

## Standard Stack

### Core (Verified — pom.xml dùng Spring Boot 3.3.2 parent)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `spring-boot-starter-amqp` | quản lý qua BOM Spring Boot 3.3.2 (Spring AMQP 3.1.x) | Producer + Consumer + connection factory + listener container | Starter chính thức của Spring cho RabbitMQ — auto-config từ `spring.rabbitmq.*` properties |
| `rabbitmq:3-management` (Docker image) | tag `3-management` (RabbitMQ 3.13.x tại thời điểm research, tag là rolling) | Broker + Management UI tại :15672 | Image chính thức; `-management` plugin enabled built-in. D-18 specify tag này. |
| `org.testcontainers:rabbitmq` | 1.20.x (matched với version test-containers postgres đang dùng — verify trong root pom/parent BOM) | Spin RabbitMQ container per test class | Module chuẩn của Testcontainers cho RabbitMQ. |

[VERIFIED: pom.xml hiện tại Spring Boot 3.3.2 — `spring-boot-starter-amqp` cùng version sẽ pull Spring AMQP transitive từ BOM]
[CITED: testcontainers.com/modules/rabbitmq — module name `rabbitmq`]
[ASSUMED: testcontainers 1.20.x — verify bằng `mvn dependency:tree | grep testcontainers` trên service hiện có]

### Supporting (cho notification-service bootstrap)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `spring-boot-starter-data-jpa` | BOM | ORM cho `dispatch_log` + `processed_events` | notification-service mới — copy từ inventory pom |
| `org.postgresql:postgresql` | BOM | JDBC driver | runtime scope |
| `org.flywaydb:flyway-core` + `flyway-database-postgresql` | BOM | Schema migration | Spring Boot 3.x require cả 2 cho Postgres |
| `org.testcontainers:postgresql` + `junit-jupiter` | match version với inventory | Integration test | test scope |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `@RabbitListener` annotation | Programmatic `SimpleMessageListenerContainer` bean | Programmatic cho fine-grained control multi-queue + dynamic binding — không cần cho scope Phase 23 |
| Topic exchange | Direct exchange | D-02 đã lock topic + binding `order.#` — cho phép mở rộng sau (`order.cancelled`, `order.shipped`) không cần đụng topology |
| `ON CONFLICT DO NOTHING` (Postgres) | `SELECT FOR UPDATE` + INSERT | ON CONFLICT atomic 1 round-trip; SELECT-then-INSERT có race |
| Testcontainers `@ServiceConnection` (Spring Boot 3.1+) | Manual `@DynamicPropertySource` | `@ServiceConnection` đơn giản hơn — Spring tự inject `spring.rabbitmq.host/port/username/password` |

**Installation (thêm vào pom.xml của 3 service):**

```xml
<!-- Phase 23: messaging -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>

<!-- Test scope -->
<dependency>
  <groupId>org.springframework.amqp</groupId>
  <artifactId>spring-rabbit-test</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>rabbitmq</artifactId>
  <scope>test</scope>
</dependency>
```

**Version verification gợi ý (chạy 1 lần trước khi commit pom):**
```bash
cd sources/backend/order-service && mvn dependency:tree -Dincludes=org.springframework.amqp
```

## Architecture Patterns

### System Architecture Diagram

```
[FE Checkout] --POST /orders--> [order-service]
                                      |
                                      | 1. @Transactional begin
                                      | 2. validateStock (REST → product-service) [D-11 GIỮ]
                                      | 3. INSERT orders + order_items + coupon_redemption
                                      | 4. @Transactional commit
                                      | 5. afterCommit() callback:
                                      |    publish(OrderPlaced, eventId, traceId)
                                      v
                          [RabbitMQ topic exchange: order.events]
                                      |
                       routing key: order.placed
                       binding: order.#
                                /          \
                               /            \
                              v              v
              [inventory.order-events]  [notification.order-events]
                  | (prefetch=10)            | (prefetch=10)
                  | @RabbitListener          | @RabbitListener
                  | retry 3x (1s,2s,4s)      | retry 3x
                  | 1. Set MDC traceId       | 1. Set MDC traceId
                  | 2. Check processed_events| 2. Check processed_events
                  | 3. UPDATE inventory_items| 3. INSERT dispatch_log
                  | 4. INSERT stock_ledger   | 4. INSERT processed_events
                  | 5. INSERT processed_events| 5. ACK
                  | 6. ACK                   |
                  v                          v
            (PermanentMsgException)    (PermanentMsgException)
                  \                          /
                   \                        /
                    v                      v
              [order.dlx (direct) → order-events.dlq]
                            |
                  (verify via :15672 UI / CLI)
```

### Recommended Project Structure (per service)

```
{service}/src/main/java/com/ptit/htpt/{service}/
├── messaging/
│   ├── config/
│   │   └── RabbitMQConfig.java          # Exchange, Queue, Binding @Bean
│   ├── exception/
│   │   ├── TransientMessageException.java
│   │   └── PermanentMessageException.java
│   ├── event/
│   │   └── OrderPlacedEvent.java        # POJO/record cho payload
│   ├── tracing/
│   │   └── TraceIdMessagePostProcessor.java  # producer set header
│   │   └── TraceIdConsumerInterceptor.java   # consumer read header → MDC
│   ├── publisher/                       # CHỈ order-service
│   │   └── OrderEventPublisher.java
│   └── consumer/                        # CHỈ inventory + notification
│       └── OrderPlacedListener.java
{service}/src/main/resources/
├── application.yml                      # spring.rabbitmq.* + listener config
└── db/migration/Vxx__add_messaging_tables.sql
```

### Pattern 1: Publisher với afterCommit (D-03, D-04)

**What:** Publish event sau khi DB transaction commit thành công — đảm bảo không publish phantom event nếu transaction rollback.
**When to use:** Mọi event publish liên quan đến state change trong DB.

```java
// File: order-service/messaging/publisher/OrderEventPublisher.java
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {
  private final RabbitTemplate rabbitTemplate;
  private static final String EXCHANGE = "order.events";
  private static final String ROUTING_KEY = "order.placed";

  /** Gọi từ OrderCrudService.createOrderFromCommand SAU khi save() — Spring sẽ defer đến afterCommit */
  public void publishOrderPlaced(OrderPlacedPayload payload) {
    String eventId = UUID.randomUUID().toString();
    String traceId = MDC.get("traceId"); // capture NGAY (callback chạy thread khác)
    OrderEventEnvelope envelope = new OrderEventEnvelope(
        eventId, "OrderPlaced", Instant.now().toString(), traceId, payload);

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override public void afterCommit() {
            doPublish(envelope, eventId, traceId);
          }
        });
    } else {
      doPublish(envelope, eventId, traceId); // fallback nếu gọi ngoài tx
    }
  }

  private void doPublish(OrderEventEnvelope envelope, String eventId, String traceId) {
    CorrelationData correlation = new CorrelationData(eventId);
    rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, envelope, msg -> {
      msg.getMessageProperties().setHeader("X-Trace-Id", traceId);
      msg.getMessageProperties().setMessageId(eventId);
      return msg;
    }, correlation);

    // Async confirm callback đã register trong RabbitTemplate config
    try {
      CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
      if (!confirm.isAck()) {
        log.error("[MQ-PUB-NACK] eventId={} reason={}", eventId, confirm.getReason());
      } else {
        log.info("[MQ-PUB] event=OrderPlaced routingKey={} eventId={} traceId={}",
            ROUTING_KEY, eventId, traceId);
      }
    } catch (TimeoutException e) {
      log.error("[MQ-PUB-TIMEOUT] eventId={} traceId={}", eventId, traceId);
    } catch (Exception e) {
      log.error("[MQ-PUB-ERR] eventId={} err={}", eventId, e.getMessage(), e);
    }
  }
}
```

[CITED: docs.spring.io/spring-amqp/api .../CorrelationData.html — `getFuture()` returns CompletableFuture<Confirm>]
[CITED: developers.ascendcorp.com — reliable publishing pattern với CorrelationData per-message]

### Pattern 2: Topology Config (D-02, D-09)

```java
// RabbitMQConfig.java — declare cùng ở cả 3 service để Spring auto-declare khi connect
@Configuration
public class RabbitMQConfig {
  public static final String EXCHANGE = "order.events";
  public static final String DLX = "order.dlx";
  public static final String DLQ = "order-events.dlq";

  @Bean TopicExchange orderEvents() { return new TopicExchange(EXCHANGE, true, false); }
  @Bean DirectExchange dlx() { return new DirectExchange(DLX, true, false); }

  @Bean Queue dlq() { return QueueBuilder.durable(DLQ).build(); }
  @Bean Binding dlqBinding() { return BindingBuilder.bind(dlq()).to(dlx()).with("order-events"); }

  /** inventory-service chỉ declare queue của nó; notification tương tự — tách thành 2 config riêng theo service */
  @Bean Queue inventoryQueue() {
    return QueueBuilder.durable("inventory.order-events")
        .withArgument("x-dead-letter-exchange", DLX)
        .withArgument("x-dead-letter-routing-key", "order-events")
        .build();
  }
  @Bean Binding inventoryBinding() {
    return BindingBuilder.bind(inventoryQueue()).to(orderEvents()).with("order.#");
  }

  @Bean Jackson2JsonMessageConverter messageConverter(ObjectMapper om) {
    return new Jackson2JsonMessageConverter(om);
  }

  @Bean RabbitTemplate rabbitTemplate(ConnectionFactory cf, Jackson2JsonMessageConverter conv) {
    RabbitTemplate t = new RabbitTemplate(cf);
    t.setMessageConverter(conv);
    t.setMandatory(true); // cho publisher-returns
    return t;
  }
}
```

[CITED: rabbitmq.com/docs/dlx — `x-dead-letter-exchange` + `x-dead-letter-routing-key` arguments]

### Pattern 3: Consumer + Idempotency (D-06, D-08, D-10)

```java
// inventory-service/messaging/consumer/OrderPlacedListener.java
@Component
@RequiredArgsConstructor
public class OrderPlacedListener {
  private final ProcessedEventRepository processedEventRepo;
  private final InventoryRepository inventoryRepo;
  private final StockLedgerRepository ledgerRepo;

  @RabbitListener(queues = "inventory.order-events")
  @Transactional
  public void onOrderPlaced(@Payload OrderEventEnvelope envelope,
                            @Header(name = "X-Trace-Id", required = false) String traceId) {
    try {
      MDC.put("traceId", traceId != null ? traceId : "no-trace");
      log.info("[MQ-CONSUME] queue=inventory.order-events eventId={} status=received", envelope.eventId());

      // Idempotency check — atomic insert; conflict = đã xử lý
      boolean inserted = processedEventRepo.insertIfAbsent(envelope.eventId(), envelope.eventType());
      if (!inserted) {
        log.info("[MQ-CONSUME] queue=inventory.order-events eventId={} status=skipped-duplicate", envelope.eventId());
        return; // ACK auto
      }

      // Business logic — phân loại exception
      for (Item item : envelope.payload().items()) {
        InventoryEntity inv = inventoryRepo.findByProductId(item.productId())
            .orElseThrow(() -> new PermanentMessageException(
                "No inventory record for productId=" + item.productId()));
        inv.decrement(item.quantity()); // log warning nếu < 0
        ledgerRepo.save(StockLedgerEntry.of(envelope.eventId(), envelope.payload().orderId(),
            item.productId(), -item.quantity(), "order.placed"));
      }
      log.info("[MQ-CONSUME] queue=inventory.order-events eventId={} status=done", envelope.eventId());
    } catch (PermanentMessageException e) {
      log.error("[MQ-DLQ] eventId={} reason={} payload={}", envelope.eventId(), e.getMessage(), envelope);
      throw new AmqpRejectAndDontRequeueException(e.getMessage(), e); // → DLQ ngay
    } catch (DataAccessResourceFailureException | TransientDataAccessException e) {
      throw new TransientMessageException("DB transient", e); // retry
    } finally {
      MDC.remove("traceId");
    }
  }
}
```

[CITED: baeldung.com/spring-amqp-error-handling — `AmqpRejectAndDontRequeueException` send reject với requeue=false]

### Pattern 4: Idempotency Repository

```java
public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, String> {
  @Modifying
  @Query(value = "INSERT INTO processed_events(event_id, event_type) " +
                 "VALUES (:id, :type) ON CONFLICT (event_id) DO NOTHING",
         nativeQuery = true)
  int insertNative(@Param("id") String eventId, @Param("type") String eventType);

  default boolean insertIfAbsent(String eventId, String eventType) {
    return insertNative(eventId, eventType) == 1;
  }
}
```

### Pattern 5: application.yml (cho 3 service)

```yaml
spring:
  rabbitmq:
    host: ${SPRING_RABBITMQ_HOST:localhost}
    port: 5672
    username: ${SPRING_RABBITMQ_USER:guest}
    password: ${SPRING_RABBITMQ_PASS:guest}
    publisher-confirm-type: correlated     # D-04 (producer only — but harmless ở consumer)
    publisher-returns: true                # D-04
    listener:
      simple:
        acknowledge-mode: auto             # Spring quản lý ACK theo exception
        prefetch: 10                       # Claude's Discretion
        default-requeue-rejected: false    # message reject → DLQ chứ không requeue infinite
        retry:
          enabled: true                    # D-07
          max-attempts: 3
          initial-interval: 1000
          multiplier: 2.0
          max-interval: 10000
```

[CITED: docs.spring.io/spring-boot Spring Boot reference — `spring.rabbitmq.listener.simple.retry.*`]

### Anti-Patterns to Avoid

- **Publish trong `@Transactional` trước commit:** Nếu transaction rollback nhưng broker đã nhận message → phantom event. D-03 đã giải quyết bằng `afterCommit()`.
- **Idempotency check trước transaction (SELECT-then-INSERT):** Race window — 2 message cùng eventId có thể cùng SELECT thấy "chưa có" rồi cùng INSERT business logic. Dùng `INSERT ... ON CONFLICT DO NOTHING` atomic.
- **`acknowledge-mode: manual` mà không track ACK:** Spring `auto` mode đã ACK/NACK theo exception → đơn giản hơn nhiều cho scope này. Manual chỉ cần khi muốn batch ACK hay custom timing.
- **Throw `RuntimeException` chung không phân loại:** Spring retry tất cả → message đúng "permanent" cũng bị retry 3 lần lãng phí. Luôn throw `AmqpRejectAndDontRequeueException` cho lỗi vĩnh viễn.
- **Capture `MDC.get("traceId")` trong `afterCommit()` callback:** Callback chạy ở thread khác → MDC có thể empty. Capture traceId NGAY trong method gốc rồi pass vào envelope (đã thể hiện ở Pattern 1).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| AMQP wire protocol | Custom socket client | `spring-boot-starter-amqp` | Connection pool, heartbeat, reconnect, channel mgmt built-in |
| JSON ↔ Message body | Manual `ObjectMapper.writeValueAsBytes` mỗi publish | `Jackson2JsonMessageConverter` bean | Auto detect `__TypeId__` header, Spring inject |
| Retry với backoff | `Thread.sleep` trong catch block | `spring.rabbitmq.listener.simple.retry.*` | Spring `RetryInterceptor` handle threading + counters |
| DLQ routing | Custom topic + republish in catch | `x-dead-letter-exchange` queue arg + `AmqpRejectAndDontRequeueException` | Broker-level, không miss khi consumer crash |
| Idempotency cache | In-memory `Set<eventId>` | `processed_events` table với UNIQUE PK + `ON CONFLICT` | Persist qua restart, atomic |
| Test broker | Embedded broker giả lập | Testcontainers `RabbitMQContainer` | Image thật `rabbitmq:3-management` — phát hiện bug topology thật |
| Trace propagation | Truyền traceId qua payload field | AMQP header `X-Trace-Id` | Tách concern: payload = business; header = infra metadata |

**Key insight:** Spring AMQP là framework battle-tested >10 năm. Mọi thử custom code trong list trên gần như chắc chắn miss edge case (connection recovery, channel close, prefetch ack ordering, retry counter persistence khi crash).

## Runtime State Inventory

> Phase 23 là greenfield messaging (không rename). Vẫn liệt kê vì có thay đổi runtime config + remove code legacy.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | `inventory_svc.inventory_items` có thể rỗng/thiếu records cho 100 sản phẩm Phase 16 seed | Data migration: `V102__seed_initial_inventory.sql` (seed-dev) — D-13 |
| Live service config | docker-compose.yml CHƯA có RabbitMQ service; notification-service section thiếu DB env | Cập nhật `docker-compose.yml`: thêm `rabbitmq` service + healthcheck + sửa `notification-service` thêm `SPRING_PROFILES_ACTIVE`, `DB_*` env, `depends_on: postgres + rabbitmq` |
| OS-registered state | Không có | None — verified bằng đọc docker-compose.yml |
| Secrets/env vars | `SPRING_RABBITMQ_HOST`, `SPRING_RABBITMQ_USER`, `SPRING_RABBITMQ_PASS` (mới) | Thêm vào docker-compose.yml env section của 3 service. Dev dùng `guest:guest`. |
| Build artifacts | order-service `deductStock()` + `buildPatchBody()` method (D-12) — code legacy | XÓA trong cùng commit thay bằng publisher call. Lưu SUMMARY commit hash để dễ rollback. |

**Đặc biệt:** notification-service hiện chưa có db schema riêng — `db/init/*.sql` ở root (xem volume mount `./db/init`) cần verify có tạo schema `notification_svc` chưa. Nếu chưa, planner thêm bootstrap migration.

## Common Pitfalls

### Pitfall 1: MDC trace lost trong afterCommit callback
**What goes wrong:** `MDC.get("traceId")` trong callback return null.
**Why:** `afterCommit` chạy ở thread khác hoặc sau khi servlet filter cleanup MDC (`finally { MDC.remove }` trong `TraceIdFilter`).
**How to avoid:** Capture traceId NGAY trong service method (trước `registerSynchronization`), pass qua closure vào envelope.
**Warning signs:** Log dòng `[MQ-PUB] traceId=null`.

### Pitfall 2: Publisher Confirms timeout với `CachingConnectionFactory` shared channel
**What goes wrong:** Random `TIMEOUT WAITING FOR ACK` sau hàng ngàn message publish.
**Why:** Channel cache reuse — confirm callbacks race nhau khi nhiều thread publish cùng channel.
**How to avoid:** Hoặc set `connection-factory.publisher-confirm-type=correlated` + dùng `CorrelationData` riêng từng message (đã làm); hoặc `cache-mode: connection` thay vì `channel` cho high-throughput. Scope đồ án: prefetch + throughput thấp → mặc định OK.
**Warning signs:** GitHub issue spring-amqp#2907.
[CITED: github.com/spring-projects/spring-amqp/issues/2907]

### Pitfall 3: Queue declaration mismatch giữa 3 service
**What goes wrong:** Service A declare queue với arg `x-dead-letter-exchange=order.dlx`; service B start trước declare KHÔNG có arg → broker reject service A với `PRECONDITION_FAILED`.
**Why:** RabbitMQ queue arguments immutable sau declare.
**How to avoid:** Hoặc CHỈ 1 service declare topology (đề xuất: inventory-service declare cả 2 queue + DLX; order-service chỉ declare exchange). Hoặc dùng RabbitMQ `definitions.json` import lúc start broker. Đề xuất scope: tách `MessagingTopologyConfig` chung; copy file/code vào cả 3 service nhưng `@ConditionalOnProperty` để chỉ 1 service active declare.
**Warning signs:** Service log error `channel error; protocol method: ... reply-text=PRECONDITION_FAILED`.

### Pitfall 4: Retry chạy CẢ với PermanentException
**What goes wrong:** Spring retry interceptor default retry tất cả `Exception`. `PermanentMessageException` cũng bị retry 3 lần → log noise, message delay vào DLQ 7 giây.
**Why:** `RetryInterceptorBuilder` mặc định `SimpleRetryPolicy` ko exclude classes.
**How to avoid:** Throw `AmqpRejectAndDontRequeueException` (Spring có built-in fatal-exception classifier skip retry). Hoặc custom `RetryPolicy` exclude `PermanentMessageException`. Đề xuất: wrap `throw new AmqpRejectAndDontRequeueException("permanent", e)` trong catch block của consumer (đã thể hiện ở Pattern 3).
**Warning signs:** DLQ message có 3 `x-death` entry thay vì 0.

### Pitfall 5: `prefetch=10` gây ordering issue
**What goes wrong:** 10 message in-flight song song; consumer xử lý không theo thứ tự eventId.
**Why:** RabbitMQ deliver prefetch concurrent.
**How to avoid:** Cho Phase 23, ordering KHÔNG quan trọng (mỗi event là 1 order khác nhau, không có dependency giữa order). Idempotency cover replay. Nếu cần strict ordering trong tương lai → `concurrency=1` + `prefetch=1`.
**Warning signs:** Test idempotency pass nhưng log thấy `eventId=B done` trước `eventId=A done` dù A publish trước.

### Pitfall 6: Testcontainer RabbitMQ port random — config sai
**What goes wrong:** `application-test.yml` hardcode `port: 5672` → test fail connect.
**Why:** Testcontainers expose random host port; container internal vẫn 5672.
**How to avoid:** Dùng `@ServiceConnection` (Spring Boot 3.1+) — Spring tự inject `getAmqpPort()`. Hoặc `@DynamicPropertySource` set `spring.rabbitmq.port` từ `container.getAmqpPort()`.
[CITED: github.com/testcontainers/testcontainers-java discussions/7721]

### Pitfall 7: notification-service chưa từng có DB → khi thêm JPA, missing config
**What goes wrong:** Thêm `data-jpa` xong start service crash `Failed to determine a suitable driver class`.
**Why:** Application.yml hiện tại không có `spring.datasource.*`. Cũng không có schema `notification_svc` trong db/init.
**How to avoid:** Sao chép pattern từ inventory-service: `application.yml` datasource block + `spring.flyway.schemas=notification_svc` + bootstrap migration `V1__init_schema.sql` tạo schema + bảng `dispatch_log` + `processed_events`. Cập nhật docker-compose.yml notification-service section với env DB_*.

### Pitfall 8: `processed_events` insert SAU business logic
**What goes wrong:** Business logic chạy → INSERT processed_events fail (vd. constraint vi phạm) → toàn transaction rollback → message retry → business logic chạy lần 2 → race với duplicate.
**Why:** Order of operations sai.
**How to avoid:** INSERT processed_events ĐẦU TIÊN trong cùng transaction; nếu conflict → return ngay (skip-duplicate); nếu success → tiếp tục business logic. Pattern này đã thể hiện ở Pattern 3.

## Code Examples

### Idempotency check pattern (Postgres-specific)

```sql
-- Flyway migration: V<n>__add_messaging_tables.sql (inventory-service)
CREATE TABLE IF NOT EXISTS inventory_svc.processed_events (
  event_id VARCHAR(36) PRIMARY KEY,
  event_type VARCHAR(64) NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_processed_events_type ON inventory_svc.processed_events(event_type);

CREATE TABLE IF NOT EXISTS inventory_svc.stock_ledger (
  id BIGSERIAL PRIMARY KEY,
  event_id VARCHAR(36) NOT NULL,
  order_id VARCHAR(36) NOT NULL,
  product_id VARCHAR(64) NOT NULL,
  quantity_change INT NOT NULL,
  reason VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_stock_ledger_order ON inventory_svc.stock_ledger(order_id);
```

### docker-compose RabbitMQ service

```yaml
  rabbitmq:
    image: rabbitmq:3-management
    container_name: tmdt-rabbitmq
    ports:
      - "5672:5672"
      - "15672:15672"
    environment:
      RABBITMQ_DEFAULT_USER: guest
      RABBITMQ_DEFAULT_PASS: guest
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "ping"]
      interval: 10s
      timeout: 5s
      retries: 10
    volumes:
      - tmdt-rabbitmqdata:/var/lib/rabbitmq
```

Thêm vào `volumes:` block: `tmdt-rabbitmqdata:`.
Sửa `order-service` / `inventory-service` / `notification-service` section thêm:
```yaml
    depends_on:
      postgres:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
    environment:
      # ...existing...
      SPRING_RABBITMQ_HOST: rabbitmq
      SPRING_RABBITMQ_USER: guest
      SPRING_RABBITMQ_PASS: guest
```

### Integration test skeleton (D-18)

```java
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class OrderEventIntegrationIT {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @Container @ServiceConnection
  static RabbitMQContainer mq = new RabbitMQContainer("rabbitmq:3-management");

  @Autowired RabbitTemplate template;
  @Autowired InventoryRepository inventoryRepo;
  @Autowired ProcessedEventRepository processedRepo;

  @Test void happyPath_publishOrderPlaced_inventoryDecremented() {
    // given seed inventory product=P1 qty=10
    // when publish OrderPlaced(eventId=E1, items=[P1, qty=3])
    // then Awaitility await ≤5s: inventory.qty == 7 AND processed_events HAS E1
  }

  @Test void idempotent_samePayloadTwice_decrementOnce() {
    // publish 2x cùng eventId → assert inventory.qty đã trừ đúng 1 lần
  }

  @Test void permanentException_routedToDLQ() {
    // publish event với productId không tồn tại → assert message trong DLQ
    // verify qua RabbitAdmin.getQueueProperties("order-events.dlq").messageCount == 1
  }

  @Test void transientThenSuccess_retryThenAck() {
    // mock InventoryRepo throw 2 lần TransientException rồi success
    // assert: 3 attempt log + final inventory.qty đúng 1 lần trừ
  }
}
```

[CITED: java.testcontainers.org/modules/rabbitmq — `RabbitMQContainer` + `@ServiceConnection`]

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual `@DynamicPropertySource` cho Testcontainers | `@ServiceConnection` annotation | Spring Boot 3.1 (2023) | Cleaner test setup, không phải hardcode property names |
| `@RabbitListener` với manual `Channel basicAck` | `acknowledge-mode: auto` + throw exception | Spring AMQP 2.x+ | Đơn giản hơn, Spring manage ack |
| Publish trong cùng transaction | `afterCommit` callback | Best practice từ ~2018 | Tránh phantom event |
| Embedded broker test (Qpid) | Testcontainers `rabbitmq:3-management` | ~2020 | Test với image production-realistic |

**Deprecated/outdated:**
- `RabbitTemplate.setConfirmCallback` global callback đơn lẻ — thay bằng `CorrelationData.getFuture()` per-message (Spring AMQP 2.1+). [CITED: docs.spring.io/spring-integration/.../alternative-confirms-returns.html]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Testcontainers version 1.20.x match với version đang dùng cho postgres | Standard Stack | Mismatch BOM gây ClassNotFound — verify `mvn dependency:tree` trước khi viết test |
| A2 | Prefetch=10 là default reasonable cho scope đồ án | Architecture Patterns | Throughput thấp, ordering không strict → 10 OK. Nếu test thấy contention thì hạ về 1. |
| A3 | RabbitMQ user/pass `guest:guest` chạy được trong docker-compose (broker mặc định không cho `guest` remote, nhưng intra-docker-network = `localhost` từ góc nhìn broker → có thể OK) | Code Examples | Nếu broker reject `guest` từ container khác (qua link), tạo user mới qua `definitions.json` mount. **Verify ngay sau khi `docker compose up` lần đầu.** |
| A4 | docker-compose `db/init/*.sql` (volume mount) chưa tạo schema `notification_svc` | Runtime State Inventory | Nếu schema chưa có → Flyway connect fail. Cần đọc `./db/init/` directory để verify. Đề xuất planner thêm task "verify + create schema notification_svc" trước migration V1. |
| A5 | `concurrency` listener default = 1 (single thread per @RabbitListener) → ordering không issue trong scope hiện tại | Common Pitfalls | Nếu set `concurrency` > 1 và test idempotency fail → ordering matter. Default Spring = 1. |

## Open Questions

1. **REQUIREMENTS.md backfill MQ-01..MQ-05?**
   - What we know: ROADMAP có references; REQUIREMENTS.md không.
   - What's unclear: Có cần task riêng update REQUIREMENTS.md trong cùng phase không? Hay defer.
   - Recommendation: Planner thêm 1 task ngắn "update REQUIREMENTS.md thêm section MQ" — không tốn nhiều effort, traceability tốt hơn.

2. **db/init/ contents cho notification_svc schema?**
   - What we know: docker-compose mount `./db/init` → Postgres init script chỉ chạy 1 lần khi volume rỗng.
   - What's unclear: Có script tạo schema `notification_svc` chưa? Cần đọc thư mục đó.
   - Recommendation: Planner Task wave 0: kiểm tra `ls db/init/` + đảm bảo có `CREATE SCHEMA IF NOT EXISTS notification_svc`. Nếu chưa, thêm vào.

3. **Topology declaration ownership?**
   - What we know: 3 service đều có thể declare exchange + queue → conflict nếu arguments khác nhau.
   - What's unclear: Phân chia ai declare cái gì?
   - Recommendation: Đặt `MessagingTopologyConfig` ở mỗi service nhưng:
     - order-service: chỉ declare exchange `order.events` + `order.dlx` + DLQ
     - inventory-service: declare queue `inventory.order-events` + binding
     - notification-service: declare queue `notification.order-events` + binding
   - Hoặc đơn giản hơn: TẤT CẢ cùng declare giống nhau (Spring `AmqpAdmin` declare idempotent nếu arguments match) — đề xuất cách này.

4. **Producer fallback khi RabbitMQ down lúc start order-service?**
   - What we know: D-05 nói "log error + trả success cho user".
   - What's unclear: Spring AMQP behavior khi broker down lúc startup — Spring có retry connect không?
   - Recommendation: Default Spring có connection retry. Set `spring.rabbitmq.connection-timeout=5000` để fail fast nếu cần.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker Desktop / Docker Engine | docker-compose + Testcontainers | ✓ (giả định — project đã chạy docker-compose) | — | Không có |
| Maven | Build 3 service | ✓ | — | Không có |
| JDK 17 | Spring Boot 3.3.2 | ✓ (pom.xml `java.version=17`) | 17 | Không có |
| `rabbitmq:3-management` image | docker-compose + Testcontainers | Sẽ pull tự động | rolling tag | Không có |
| Postgres 16 (đã chạy) | inventory/notification migrate `processed_events` | ✓ docker-compose | 16-alpine | Không có |

**Missing dependencies with no fallback:** None — stack đã có sẵn.

**Missing dependencies with fallback:** None.

## Validation Architecture

> Phase config: `workflow.nyquist_validation = true` → section bắt buộc.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + Spring Boot Test + Testcontainers 1.20.x + Spring AMQP test |
| Config file | Mỗi service `src/test/resources/application-test.yml` (cần tạo cho notification-service) |
| Quick run command | `cd sources/backend/order-service && mvn test -Dtest=OrderEventPublisherTest` |
| Full suite command | `cd sources/backend && mvn -pl order-service,inventory-service,notification-service test -am` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| MQ-01 | RabbitMQ container start + Management UI :15672 reachable | smoke | `curl -u guest:guest -fsS http://localhost:15672/api/overview` (sau `docker compose up`) | ❌ Wave 0 — viết shell smoke script |
| MQ-02 | order-service publish `OrderPlaced` sau commit với publisher confirms | integration | `mvn -pl order-service test -Dtest=OrderEventPublisherIT` | ❌ Wave 0 |
| MQ-02 | `afterCommit` thực sự defer — rollback transaction → NOT publish | unit | `mvn -pl order-service test -Dtest=OrderEventPublisherAfterCommitTest` | ❌ Wave 0 |
| MQ-03 | inventory consumer trừ kho khi nhận event | integration | `mvn -pl inventory-service test -Dtest=OrderPlacedListenerIT#happyPath` | ❌ Wave 0 |
| MQ-03 | Idempotency: 2 message cùng eventId → trừ 1 lần | integration | `mvn -pl inventory-service test -Dtest=OrderPlacedListenerIT#idempotent` | ❌ Wave 0 |
| MQ-04 | notification consumer ghi dispatch_log + processed_events | integration | `mvn -pl notification-service test -Dtest=OrderPlacedNotifyListenerIT` | ❌ Wave 0 |
| MQ-05 | Retry 3 lần exponential backoff | integration | `mvn -pl inventory-service test -Dtest=OrderPlacedListenerIT#retryThenSuccess` | ❌ Wave 0 |
| MQ-05 | PermanentException → DLQ ngay (0 retry) | integration | `mvn -pl inventory-service test -Dtest=OrderPlacedListenerIT#permanentToDLQ` | ❌ Wave 0 |
| MQ-05 | DLQ message verify được qua RabbitMQ HTTP API | integration | Trong test trên, `RestTemplate` query `/api/queues/%2F/order-events.dlq` assert `messages == 1` | ❌ Wave 0 |
| MQ-05 | TraceId xuyên 3 service trong logs | manual | `docker compose logs order-service inventory-service notification-service \| grep <traceId>` sau khi tạo 1 đơn | manual-only (justified: log-level assertion phức tạp; demo trực quan trong VERIFICATION) |

### Evidence Map cho 7 Success Criteria (ROADMAP §215-222)

| # | Success Criterion | Evidence to Produce | How to Verify |
|---|-------------------|---------------------|---------------|
| 1 | RabbitMQ container chạy + Management UI :15672 | Screenshot UI sau `docker compose up -d rabbitmq` | `curl http://localhost:15672/api/overview` returns 200 |
| 2 | order-service publish `OrderPlaced` sau commit với Publisher Confirms | Log `[MQ-PUB] event=OrderPlaced eventId=...` + Postgres `orders` table có row tương ứng + Management UI exchange `order.events` rate >0 sau test order | Integration test `OrderEventPublisherIT` + grep log + RabbitMQ HTTP API `/api/exchanges/%2F/order.events` |
| 3 | inventory-service consume + trừ kho atomic + ledger + idempotent | Postgres `inventory_svc.inventory_items.quantity` giảm; `stock_ledger` có row mới với event_id; `processed_events` có row mới; 2 lần cùng eventId → chỉ 1 row ledger | Integration test `OrderPlacedListenerIT#happyPath` + `#idempotent` |
| 4 | notification-service consume + dispatch_log + idempotent | Postgres `notification_svc.dispatch_log` có row với status=SENT; `processed_events` có row | Integration test `OrderPlacedNotifyListenerIT` |
| 5 | Retry 3 lần exponential backoff → DLQ verify trong UI | RabbitMQ HTTP API `/api/queues/%2F/order-events.dlq` `messages == 1` sau retry exhaust; logs `[MQ-RETRY] attempt=1/3 ... 2/3 ... 3/3` + `[MQ-DLQ]` | Integration test `OrderPlacedListenerIT#permanentToDLQ` + `#retryThenSuccess` + manual UI screenshot |
| 6 | TraceId xuyên 3 service | `docker compose logs ... \| grep <traceId>` cho ra ≥3 dòng từ 3 service container khác nhau cho 1 order | Manual demo trong VERIFICATION.md + log grep script |
| 7 | Integration test end-to-end | `mvn test` pass với 4 scenario D-18 | CI green |

### Sampling Rate
- **Per task commit:** `mvn -pl <service> test` (single service quick) — < 60s với reuse containers
- **Per wave merge:** `mvn -pl order-service,inventory-service,notification-service test -am` — < 5 min
- **Phase gate:** Full suite green + manual demo (1 order → 3 service logs cùng traceId + Management UI screenshot exchange/queue rate)

### Wave 0 Gaps

- [ ] `sources/backend/notification-service/pom.xml` — thêm JPA + Postgres + Flyway + Testcontainers
- [ ] `sources/backend/notification-service/src/main/resources/application.yml` — datasource + flyway.schemas + rabbitmq config
- [ ] `sources/backend/notification-service/src/test/resources/application-test.yml` — Testcontainers profile
- [ ] `sources/backend/notification-service/src/main/resources/db/migration/V1__init_schema.sql` — tạo schema + bảng dispatch_log
- [ ] `sources/backend/{order,inventory,notification}-service/src/main/java/.../messaging/config/RabbitMQConfig.java` — topology @Bean
- [ ] `sources/backend/{inventory,notification}-service/src/main/java/.../messaging/consumer/OrderPlacedListener.java` — @RabbitListener
- [ ] `sources/backend/order-service/src/main/java/.../messaging/publisher/OrderEventPublisher.java` — afterCommit + CorrelationData
- [ ] `sources/backend/{inventory,notification}-service/src/main/resources/db/migration/V<n>__add_messaging_tables.sql` — processed_events + (inventory) stock_ledger
- [ ] `sources/backend/order-service/src/main/resources/db/seed-dev/V<n>__seed_initial_inventory.sql` — D-13 copy stock
- [ ] `docker-compose.yml` — add `rabbitmq` service + update 3 service env + depends_on
- [ ] Shell script `scripts/verify-mq.sh` (gợi ý) — smoke test Management UI + 1 publish end-to-end

## Security Domain

> `security_enforcement` không set explicit trong config.json → enabled by default.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes (broker auth) | RabbitMQ user/pass trong env var (dev: guest:guest; prod defer — đề bài project chỉ dev) |
| V3 Session Management | no | Stateless message bus |
| V4 Access Control | partial | Broker `guest` user mặc định CHỈ cho localhost — verify intra-docker-network ok hoặc tạo user riêng |
| V5 Input Validation | yes | Message payload deserialize qua Jackson → validate `eventId` UUID format, `payload.items` non-empty, `quantity > 0` ở consumer before business logic |
| V6 Cryptography | no | Intra-docker-network không cần TLS cho dev. Prod TLS defer. |

### Known Threat Patterns cho stack RabbitMQ + Spring AMQP

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Message tampering (network MITM) | Tampering | TLS (defer — intra-docker chấp nhận risk) |
| Replay attack (cùng message gửi lại) | — | `processed_events` idempotency table — D-06 |
| Poison message (payload sai format) | DoS | `PermanentMessageException` → DLQ tránh infinite retry |
| Information disclosure qua DLQ log | Disclosure | Log `[MQ-DLQ]` chỉ log eventId + reason + payloadJson — payload không chứa PII nhạy cảm (userId là internal ID không phải email) |
| Deserialize untrusted JSON | Tampering | `Jackson2JsonMessageConverter` mặc định KHÔNG enable polymorphic typing → an toàn. KHÔNG set `enableDefaultTyping`. |
| Broker credential leak | Spoofing | Dev: guest/guest acceptable; tài liệu khi prod tách credential riêng |

## Sources

### Primary (HIGH confidence)
- pom.xml các service (read trực tiếp) — verified Spring Boot 3.3.2, Java 17
- 23-CONTEXT.md — D-01..D-18 locked decisions
- ROADMAP.md §210-228 — success criteria
- [docs.spring.io/spring-amqp/api .../CorrelationData.html](https://docs.spring.io/spring-amqp/api/org/springframework/amqp/rabbit/connection/CorrelationData.html) — CorrelationData.getFuture API
- [rabbitmq.com/docs/dlx](https://www.rabbitmq.com/docs/dlx) — Dead Letter Exchanges arguments
- [java.testcontainers.org/modules/rabbitmq](https://java.testcontainers.org/modules/rabbitmq) — RabbitMQContainer + @ServiceConnection

### Secondary (MEDIUM confidence — WebSearch + đối chiếu docs)
- [baeldung.com/spring-amqp-error-handling](https://www.baeldung.com/spring-amqp-error-handling) — AmqpRejectAndDontRequeueException pattern
- [developers.ascendcorp.com — Reliable publishing với Spring AMQP](https://developers.ascendcorp.com/reliable-publishing-to-rabbitmq-with-spring-amqp-d2f3e81275e7) — per-message CorrelationData
- [docs.spring.io/spring-integration .../alternative-confirms-returns.html](https://docs.spring.io/spring-integration/reference/amqp/alternative-confirms-returns.html) — alternative confirm mechanism
- [github.com spring-amqp issues/2907](https://github.com/spring-projects/spring-amqp/issues/2907) — confirm timeout edge case
- [github.com testcontainers-java discussions/7721](https://github.com/testcontainers/testcontainers-java/discussions/7721) — random port handling

### Tertiary (LOW — single source, flag for validation)
- A1 testcontainers version 1.20.x — cần `mvn dependency:tree` để verify

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — pom.xml verified, Spring AMQP là standard Spring starter
- Architecture: HIGH — CONTEXT.md đã lock 18 quyết định; research chỉ supplement code skeleton
- Pitfalls: MEDIUM-HIGH — patterns dựa trên Baeldung + GitHub issues + Spring docs
- Notification-service bootstrap gap: HIGH — đã verify trực tiếp pom.xml + filesystem
- Testcontainer compat: MEDIUM — chưa verify version exact, cần `mvn dependency:tree`

**Research date:** 2026-05-20
**Valid until:** 2026-06-20 (Spring AMQP API ổn định >5 năm; RabbitMQ image rolling tag không ảnh hưởng)
