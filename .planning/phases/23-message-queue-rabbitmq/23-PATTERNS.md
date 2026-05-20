# Phase 23: Message Queue Integration (RabbitMQ) — Pattern Map

**Mapped:** 2026-05-20
**Files analyzed:** ~25 new/modified files
**Analogs found:** 22 / 25 (3 không có analog — Spring AMQP messaging chưa từng xuất hiện trong repo)
**Project conventions:** Vietnamese cho commit body/docs; English cho identifier + commit prefix.

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `docker-compose.yml` (modify) | Config (infra) | service-orchestration | Existing `postgres` service block (lines 2-17) | exact |
| `sources/backend/order-service/pom.xml` (modify) | Config (build) | dependency-declaration | `sources/backend/inventory-service/pom.xml` | exact |
| `sources/backend/inventory-service/pom.xml` (modify) | Config (build) | dependency-declaration | Self (add amqp + spring-rabbit-test + testcontainers:rabbitmq) | exact |
| `sources/backend/notification-service/pom.xml` (modify) | Config (build) | dependency-declaration | `sources/backend/inventory-service/pom.xml` (full clone — bootstrap JPA stack) | exact |
| `sources/backend/notification-service/src/main/resources/application.yml` (new) | Config | property | `sources/backend/inventory-service/src/main/resources/application.yml` | exact |
| `sources/backend/{order,inventory,notification}-service/.../messaging/config/RabbitMQConfig.java` (new) | Config (Spring `@Configuration`) | bean-declaration | `sources/backend/order-service/.../AppConfig.java` (ObjectMapper/RestTemplate bean pattern) | role-match |
| `sources/backend/{order,inventory,notification}-service/.../messaging/event/OrderEventEnvelope.java` (new) | DTO (record) | data-transfer | `sources/backend/order-service/.../domain/OrderDto.java` | exact |
| `sources/backend/{order,inventory,notification}-service/.../messaging/exception/TransientMessageException.java` (new) | Exception | error-classification | `sources/backend/order-service/.../exception/CouponException.java` + `StockShortageException.java` | exact |
| `sources/backend/{order,inventory,notification}-service/.../messaging/exception/PermanentMessageException.java` (new) | Exception | error-classification | (same as above) | exact |
| `sources/backend/order-service/.../messaging/publisher/OrderEventPublisher.java` (new) | Producer (Spring `@Component`) | event-driven (publish-after-commit) | `OrderCrudService.deductStockAfterPersist()` (lines 381-411) — post-commit side-effect hiện tại; structurally REST-driven nhưng same call-site | partial (cùng call-site, khác wire) |
| `sources/backend/inventory-service/.../messaging/consumer/OrderPlacedListener.java` (new) | Consumer (`@RabbitListener`) | event-driven (consume) | None — first listener trong repo | no-analog (use RESEARCH skeleton) |
| `sources/backend/notification-service/.../messaging/consumer/OrderPlacedNotifyListener.java` (new) | Consumer (`@RabbitListener`) | event-driven (consume) | (same as above) | no-analog |
| `sources/backend/{order,inventory,notification}-service/.../messaging/tracing/TraceIdMessagePostProcessor.java` (new) | Middleware (producer) | header-injection | `sources/backend/order-service/.../web/TraceIdFilter.java` | role-match (MDC bridge) |
| `sources/backend/{inventory,notification}-service/.../messaging/tracing/TraceIdConsumerInterceptor.java` (new) | Middleware (consumer) | header-extraction | `TraceIdFilter.java` (reverse direction) | role-match |
| `sources/backend/order-service/.../service/OrderCrudService.java` (modify — D-12 XÓA + publish) | Service (existing) | CRUD + side-effect | Self (lines 122-184 createOrderFromCommand) | exact |
| `sources/backend/inventory-service/.../domain/ProcessedEventEntity.java` (new) | Entity (JPA) | persistence | `sources/backend/inventory-service/.../domain/InventoryEntity.java` | exact |
| `sources/backend/notification-service/.../domain/ProcessedEventEntity.java` (new) | Entity (JPA) | persistence | (same — copy với schema=notification_svc) | exact |
| `sources/backend/inventory-service/.../domain/StockLedgerEntity.java` (new) | Entity (JPA) | persistence | `InventoryEntity.java` | exact |
| `sources/backend/notification-service/.../domain/DispatchLogEntity.java` (new) | Entity (JPA) | persistence | `InventoryEntity.java` | role-match |
| `sources/backend/inventory-service/.../repository/ProcessedEventRepository.java` (new) | Repository (JPA) | data-access | `InventoryRepository.java` | exact |
| `sources/backend/inventory-service/src/main/resources/db/migration/V2__add_messaging_tables.sql` (new) | Migration (Flyway) | schema-DDL | `sources/backend/order-service/src/main/resources/db/migration/V5__add_coupons.sql` | exact |
| `sources/backend/notification-service/src/main/resources/db/migration/V1__init_schema.sql` (new) | Migration (Flyway bootstrap) | schema-DDL | `sources/backend/inventory-service/.../db/migration/V1__init_schema.sql` | exact |
| `sources/backend/inventory-service/src/main/resources/db/seed-dev/V102__seed_initial_inventory.sql` (new) | Migration (seed-dev) | data-seed | `sources/backend/inventory-service/.../db/seed-dev/V2__seed_dev_data.sql` | role-match |
| `db/init/01-schemas.sql` (modify) | Config (infra DDL) | schema-creation | Self (add `notification_svc` line) | exact |
| `sources/backend/{order,inventory,notification}-service/src/test/java/.../OrderEventIT.java` (new) | Test (Testcontainers IT) | integration | `sources/backend/order-service/.../service/OrderCouponRaceConditionIT.java` | role-match |

---

## Pattern Assignments

### `docker-compose.yml` (modify — thêm RabbitMQ service + cập nhật 3 backend service)

**Analog:** `docker-compose.yml` postgres block (lines 2-17) + user-service block (lines 31-44).

**Postgres healthcheck pattern** (lines 2-17):
```yaml
postgres:
  image: postgres:16-alpine
  environment:
    POSTGRES_DB: tmdt
    POSTGRES_USER: tmdt
    POSTGRES_PASSWORD: tmdt
  volumes:
    - tmdt-pgdata:/var/lib/postgresql/data
    - ./db/init:/docker-entrypoint-initdb.d:ro
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U tmdt -d tmdt"]
    interval: 5s
    timeout: 5s
    retries: 10
  ports:
    - "5432:5432"
```

**Backend service env block pattern** (lines 31-44, user-service):
```yaml
user-service:
  build: ./sources/backend/user-service
  depends_on:
    postgres:
      condition: service_healthy
  environment:
    SPRING_PROFILES_ACTIVE: dev
    DB_HOST: postgres
    DB_PORT: 5432
    DB_NAME: tmdt
    DB_USER: tmdt
    DB_PASSWORD: tmdt
  ports:
    - "8081:8080"
```

**Apply to Phase 23:**
- Add `rabbitmq` service block (mirror postgres structure: image, healthcheck, ports, volume).
- Add `SPRING_RABBITMQ_HOST/USER/PASS` to order/inventory/notification env.
- Add `rabbitmq: condition: service_healthy` to `depends_on` of 3 services.
- Notification-service block (lines 106-109) hiện thiếu env DB+profile → bổ sung đầy đủ như user-service.
- Volume: thêm `tmdt-rabbitmqdata:` to top-level `volumes:` (line 128).

---

### `sources/backend/notification-service/pom.xml` (modify — bootstrap JPA + thêm AMQP)

**Analog:** `sources/backend/inventory-service/pom.xml` (full file — clone toàn bộ dependencies block).

**Persistence stack pattern** (inventory pom lines 43-75):
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
  <groupId>org.postgresql</groupId>
  <artifactId>postgresql</artifactId>
  <scope>runtime</scope>
</dependency>
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-database-postgresql</artifactId>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>postgresql</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>junit-jupiter</artifactId>
  <scope>test</scope>
</dependency>
```

**Add for Phase 23 (cả 3 service):**
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
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

---

### `sources/backend/notification-service/src/main/resources/application.yml` (new)

**Analog:** `sources/backend/inventory-service/src/main/resources/application.yml` (full file).

**Datasource + flyway pattern** (lines 1-22):
```yaml
spring:
  application:
    name: notification-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:tmdt}?currentSchema=notification_svc
    username: ${DB_USER:tmdt}
    password: ${DB_PASSWORD:tmdt}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        default_schema: notification_svc
        format_sql: true
    open-in-view: false
  flyway:
    enabled: true
    schemas: notification_svc
    default-schema: notification_svc
    locations: classpath:db/migration
```

**Add for Phase 23 (cả 3 service application.yml):**
```yaml
  rabbitmq:
    host: ${SPRING_RABBITMQ_HOST:localhost}
    port: 5672
    username: ${SPRING_RABBITMQ_USER:guest}
    password: ${SPRING_RABBITMQ_PASS:guest}
    publisher-confirm-type: correlated
    publisher-returns: true
    listener:
      simple:
        acknowledge-mode: auto
        prefetch: 10
        default-requeue-rejected: false
        retry:
          enabled: true
          max-attempts: 3
          initial-interval: 1000
          multiplier: 2.0
          max-interval: 10000
```

**Dev profile pattern** (inventory yml lines 39-46) — copy nguyên để notification cũng load seed-dev khi `profile=dev`.

---

### `messaging/event/OrderEventEnvelope.java` (new — 3 service cùng copy)

**Analog:** `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderDto.java` (record pattern + factory `create`).

**Record DTO pattern** (Notification: `notification-service/.../domain/NotificationDispatch.java` lines 6-18):
```java
public record NotificationDispatch(
    String id,
    String templateId,
    String recipient,
    String status,
    boolean deleted,
    Instant createdAt,
    Instant updatedAt
) {
  public static NotificationDispatch create(String templateId, String recipient, String status) {
    Instant now = Instant.now();
    return new NotificationDispatch(UUID.randomUUID().toString(), templateId, recipient, status, false, now, now);
  }
}
```

**Apply to Phase 23:** Tạo record `OrderEventEnvelope(String eventId, String eventType, String occurredAt, String traceId, OrderPlacedPayload payload)` + nested record `OrderPlacedPayload(orderId, userId, items[], totalAmount, currency)` + `Item(productId, quantity, priceAtPurchase)`. Factory `create(...)` set eventId=UUID, occurredAt=Instant.now().

---

### `messaging/exception/{Transient,Permanent}MessageException.java` (new)

**Analog:** `sources/backend/order-service/.../exception/StockShortageException.java` (custom domain exception class).

**Custom exception pattern** (`StockShortageException` — nested record cho field details):
```java
package com.ptit.htpt.orderservice.exception;
// ... extends RuntimeException, nested record StockShortageItem, constructor accept list ...
```

**Apply:**
- `TransientMessageException extends RuntimeException` — DB timeout / network blip → retry.
- `PermanentMessageException extends RuntimeException` — bad payload / business invariant → reject NOW.
- Constructor pattern giống `CouponException(CouponErrorCode code, String msg)` — accept message + cause.

---

### `OrderEventPublisher.java` (new — order-service producer)

**Analog:** `OrderCrudService.deductStockAfterPersist()` lines 381-411 — same call-site (post-persist side-effect) nhưng REST. Phase 23 thay bằng AMQP publish trong `afterCommit()`.

**Existing post-persist side-effect call** (line 178-181):
```java
OrderEntity saved = orderRepository.save(order);

// D-05: Deduct stock sau persist — best-effort, không rollback order nếu fail
deductStockAfterPersist(command.items());
```

**Apply to Phase 23:**
- Inject `OrderEventPublisher publisher` thay cho `deductStockAfterPersist` private method.
- Replace với `publisher.publishOrderPlaced(buildPayload(saved, command.items()))`.
- Bên trong `publishOrderPlaced`: capture `MDC.get("traceId")` NGAY, sau đó `TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { afterCommit(){...} })` để defer publish.
- XÓA: methods `deductStockAfterPersist`, `buildStockUpdateBody` (lines 376-445). Lưu file path + line range vào commit SUMMARY.

**Logging contract** (D-17):
```java
log.info("[MQ-PUB] event=OrderPlaced routingKey={} eventId={} traceId={}",
    ROUTING_KEY, eventId, traceId);
```
(format reuse pattern hiện có: `log.info("[D-05] Stock deducted for productId={}: {} → {}", ...)` line 404.)

---

### `RabbitMQConfig.java` (new — 3 service)

**Analog:** Không có analog config class cho messaging. Bean pattern dùng `AppConfig.java` (order-service) cho ObjectMapper/RestTemplate. Sử dụng skeleton từ RESEARCH.md §Pattern 2 (lines 299-335).

**Spring `@Configuration` declare pattern** (analog: `sources/backend/order-service/.../AppConfig.java`):
- `@Configuration` class với multiple `@Bean` methods.
- Constants `public static final String EXCHANGE = "order.events"` ngay đầu class (giống convention `HEADER_NAME` trong `TraceIdFilter`).

**Decision (Open Q #3):** Mỗi service declare cùng topology toàn bộ — Spring `AmqpAdmin` idempotent với matching args. Beans cần: `TopicExchange`, `DirectExchange dlx()`, `Queue dlq()`, `Queue {service}Queue()` với `x-dead-letter-exchange=order.dlx`, `Binding`, `Jackson2JsonMessageConverter`, `RabbitTemplate (setMandatory=true)`.

---

### `OrderPlacedListener.java` (new — inventory + notification consumers)

**Analog:** **NO ANALOG** — đây là `@RabbitListener` đầu tiên trong repo. Dùng skeleton từ RESEARCH.md §Pattern 3 (lines 341-385).

**Reference patterns từ existing code:**
- `InventoryRepository.findByProductId(String)` → tái dùng cho lookup (lines 1-9 InventoryRepository.java).
- `InventoryEntity.adjustQuantity(int)` → có sẵn — nhưng cần thêm method `decrement(int amount)` tương tự (lines 65-68 InventoryEntity.java pattern).
- Throw style cùng `OrderCrudService.validateStockOrThrow` — throw exception từ inside loop.

**Apply:**
- `@Component @RequiredArgsConstructor` (chưa thấy lombok dùng project — verify pom; fallback constructor injection theo `OrderCrudService` lines 49-59).
- `@RabbitListener(queues = "inventory.order-events")` + `@Transactional`.
- Idempotency INSERT đầu tiên qua `ProcessedEventRepository.insertIfAbsent` (native query `ON CONFLICT DO NOTHING`).
- Throw `AmqpRejectAndDontRequeueException` để skip retry (RESEARCH §Pitfall 4).

---

### `TraceIdMessagePostProcessor.java` + `TraceIdConsumerInterceptor.java` (new)

**Analog:** `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/TraceIdFilter.java` (full file).

**MDC bridge pattern** (TraceIdFilter lines 24-36):
```java
String traceId = request.getHeader(HEADER_NAME);
if (traceId == null || traceId.isBlank()) {
  traceId = UUID.randomUUID().toString().replace("-", "");
}
MDC.put(ATTR_NAME, traceId);
try {
  filterChain.doFilter(request, response);
} finally {
  MDC.remove(ATTR_NAME);
}
```

**Apply to Phase 23:**
- **Producer (MessagePostProcessor):** capture `MDC.get("traceId")` (using `TraceIdFilter.ATTR_NAME`), inject vào `messageProperties.setHeader("X-Trace-Id", traceId)`.
- **Consumer (interceptor):** đọc `@Header("X-Trace-Id")` → `MDC.put(...)` trước business; `MDC.remove(...)` trong `finally` (same try-finally pattern as TraceIdFilter).

---

### `ProcessedEventEntity.java` + `StockLedgerEntity.java` + `DispatchLogEntity.java` (new JPA entities)

**Analog:** `sources/backend/inventory-service/.../domain/InventoryEntity.java` (full file lines 1-89).

**JPA Entity pattern** (InventoryEntity lines 18-57):
```java
@Entity
@Table(name = "inventory_items", schema = "inventory_svc")
public class InventoryEntity {

  @Id
  @Column(length = 36, nullable = false, updatable = false)
  private String id;

  @Column(name = "product_id", length = 36, nullable = false, unique = true)
  private String productId;

  // ... timestamps ...
  protected InventoryEntity() {}

  public static InventoryEntity create(String productId, int quantity, int reserved) {
    Instant now = Instant.now();
    return new InventoryEntity(UUID.randomUUID().toString(), productId, quantity, reserved, now, now);
  }

  // Getters: record-style accessor names — `productId()` not `getProductId()`
  public String productId() { return productId; }
}
```

**Apply:**
- `ProcessedEventEntity` — `@Id String eventId`, `String eventType`, `Instant processedAt`. Schema = `inventory_svc` / `notification_svc`.
- `StockLedgerEntity` — `@Id Long id` (BIGSERIAL — dùng `@GeneratedValue(strategy=IDENTITY)`), `String eventId`, `String orderId`, `String productId`, `int quantityChange`, `String reason`, `Instant createdAt`.
- `DispatchLogEntity` — replace existing `NotificationDispatch` record. `@Id String id`, `String eventId`, `String recipientUserId`, `String channel`, `String subject`, `String body`, `String status`, `Instant sentAt`.

---

### `ProcessedEventRepository.java` (new)

**Analog:** `sources/backend/inventory-service/.../repository/InventoryRepository.java` (lines 1-9).

**Repository pattern:**
```java
public interface InventoryRepository extends JpaRepository<InventoryEntity, String> {
  Optional<InventoryEntity> findByProductId(String productId);
}
```

**Apply:**
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

---

### `V2__add_messaging_tables.sql` (new — inventory-service)

**Analog:** `sources/backend/order-service/src/main/resources/db/migration/V5__add_coupons.sql` (full file).

**Flyway DDL pattern** (V5 lines 4-16):
```sql
CREATE TABLE IF NOT EXISTS order_svc.coupons (
  id                 VARCHAR(36)   PRIMARY KEY,
  code               VARCHAR(64)   NOT NULL UNIQUE,
  -- ...
  created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_redemptions_coupon ON order_svc.coupon_redemptions(coupon_id);
```

**Apply (inventory V2):**
```sql
CREATE TABLE IF NOT EXISTS inventory_svc.processed_events (
  event_id VARCHAR(36) PRIMARY KEY,
  event_type VARCHAR(64) NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS inventory_svc.stock_ledger (
  id BIGSERIAL PRIMARY KEY,
  event_id VARCHAR(36) NOT NULL,
  order_id VARCHAR(36) NOT NULL,
  product_id VARCHAR(36) NOT NULL,
  quantity_change INT NOT NULL,
  reason VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_stock_ledger_order ON inventory_svc.stock_ledger(order_id);
```

**Version note:** inventory hiện stop tại V1 (init). V2 reasonable. Notification: V1 = init schema + bảng `dispatch_log` + `processed_events` (bootstrap).

---

### `V102__seed_initial_inventory.sql` (new — seed-dev)

**Analog:** `sources/backend/inventory-service/src/main/resources/db/seed-dev/V2__seed_dev_data.sql` (full file) + `sources/backend/product-service/src/main/resources/db/seed-dev/V101__seed_catalog_realistic.sql`.

**Seed pattern (V2 inventory):**
```sql
INSERT INTO inventory_svc.inventory_items (id, product_id, quantity, reserved, created_at, updated_at) VALUES
  ('inv-001', 'prod-001', 25, 0, NOW(), NOW()),
  ...
```

**Apply (V102 seed_initial_inventory — D-13):**
```sql
-- Copy stock from product_svc.products → inventory_svc.inventory_items
-- Idempotent: ON CONFLICT DO NOTHING
INSERT INTO inventory_svc.inventory_items (id, product_id, quantity, reserved, created_at, updated_at)
SELECT gen_random_uuid()::text, p.id, COALESCE(p.stock, 0), 0, NOW(), NOW()
FROM product_svc.products p
WHERE NOT EXISTS (
  SELECT 1 FROM inventory_svc.inventory_items i WHERE i.product_id = p.id
)
ON CONFLICT (product_id) DO NOTHING;
```

**Note:** V-number 102 vì 101 đang chuẩn (seed-dev product/user dùng V100/V101/V102 — phải check inventory existing V2 = V2; có thể đổi thành V101 hoặc V102 — planner xác nhận theo Flyway version đã có).

---

### `db/init/01-schemas.sql` (modify)

**Analog:** Self (lines 1-5).

**Current content:**
```sql
CREATE SCHEMA IF NOT EXISTS user_svc;
CREATE SCHEMA IF NOT EXISTS product_svc;
CREATE SCHEMA IF NOT EXISTS order_svc;
CREATE SCHEMA IF NOT EXISTS payment_svc;
CREATE SCHEMA IF NOT EXISTS inventory_svc;
```

**Add:**
```sql
CREATE SCHEMA IF NOT EXISTS notification_svc;
```

**Note:** docker-entrypoint-initdb.d chỉ chạy khi volume RỖNG. Nếu dev đã chạy `docker compose up` rồi → phải `docker volume rm tmdt-pgdata` hoặc chạy manual `CREATE SCHEMA` qua psql. Document trong VERIFICATION.md.

---

### `OrderEventIT.java` (new — Testcontainers IT)

**Analog:** `sources/backend/order-service/src/test/java/.../service/OrderCouponRaceConditionIT.java` (lines 1-76).

**Testcontainers + Spring Boot Test pattern** (lines 47-69):
```java
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrderCouponRaceConditionIT {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
      .withDatabaseName("tmdt")
      .withUsername("tmdt")
      .withPassword("tmdt")
      .withInitScript("test-init/01-schemas.sql");

  @DynamicPropertySource
  static void datasourceProps(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url",
        () -> postgres.getJdbcUrl() + "?currentSchema=order_svc");
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @BeforeEach
  void setUp() {
    redemptionRepo.deleteAll();
    couponRepo.deleteAll();
    orderRepo.deleteAll();
  }
```

**Apply to Phase 23:**
- Thêm `@Container static RabbitMQContainer mq = new RabbitMQContainer("rabbitmq:3-management");`
- Dùng `@ServiceConnection` (Spring Boot 3.1+ — đã có 3.3.2) cho cả Postgres và RabbitMQ → remove `@DynamicPropertySource` boilerplate.
- Test class trong inventory-service (`OrderPlacedListenerIT`) + notification-service + order-service (`OrderEventPublisherIT`).
- Test scenarios theo D-18: happyPath, idempotent, permanentToDLQ, retryThenSuccess.
- Verify DLQ count qua HTTP API hoặc Spring `RabbitAdmin.getQueueProperties("order-events.dlq").getMessageCount()`.

**Init script:** Cần verify `src/test/resources/test-init/01-schemas.sql` có schema `notification_svc` không (hiện chỉ có 5 schema). Bổ sung nếu thiếu.

---

## Shared Patterns

### Logging Format (D-17)
**Source:** `OrderCrudService.java` line 404 (`log.info("[D-05] Stock deducted for productId={}: {} → {}", ...)`)
**Convention:** `[TAG] key1={} key2={} ...` — bracketed tag prefix.
**Apply to all messaging code:**
```java
log.info("[MQ-PUB] event=OrderPlaced routingKey={} eventId={} traceId={}", ROUTING_KEY, eventId, traceId);
log.info("[MQ-CONSUME] queue={} eventId={} status={}", queueName, eventId, status);
log.warn("[MQ-RETRY] eventId={} attempt={}/{} error={}", eventId, attempt, max, err);
log.error("[MQ-DLQ] eventId={} reason={} payload={}", eventId, reason, payloadJson);
log.error("[MQ-PUB-NACK] eventId={} reason={}", eventId, reason);
log.error("[MQ-PUB-TIMEOUT] eventId={} traceId={}", eventId, traceId);
```

### Trace Propagation (D-16)
**Source:** `TraceIdFilter.java` lines 13-38.
**Apply to:** All producer + consumer messaging files. Producer = MessagePostProcessor (set header from MDC). Consumer = `@Header("X-Trace-Id")` param + put-MDC-with-finally-remove.

### Constructor Injection (no Lombok)
**Source:** `OrderCrudService.java` lines 49-59 — explicit constructor with `this.x = x` assignments.
**Apply to:** `OrderEventPublisher`, `OrderPlacedListener`, etc. — explicit constructor (KHÔNG dùng `@RequiredArgsConstructor` — Lombok chưa thấy trong pom).

### Schema-Bound JPA Entity
**Source:** `InventoryEntity.java` lines 18-19 (`@Table(name="...", schema="inventory_svc")`).
**Apply to:** All new entities — set `schema = "{service}_svc"` explicitly + `default_schema` trong application.yml jpa.properties.

### Custom Exception (no @ControllerAdvice needed)
**Source:** `StockShortageException.java` + `CouponException.java`.
**Apply to:** `TransientMessageException`, `PermanentMessageException` — plain `extends RuntimeException` với constructor `(String, Throwable)`. Consumer wrap → `AmqpRejectAndDontRequeueException(msg, cause)` cho permanent.

### Service Layer Patterns (`@Transactional`, defensive validation)
**Source:** `OrderCrudService.createOrderFromCommand` lines 122-184.
**Apply to:** `OrderPlacedListener.onOrderPlaced` — `@Transactional` bao toàn bộ idempotency check + business logic; throw exception sẽ rollback.

### Flyway Version Numbering
**Source:** Existing migrations.
- `inventory-service/db/migration`: V1 only → Phase 23 add **V2**.
- `inventory-service/db/seed-dev`: V2 (legacy naming) → Phase 23 add **V102** (align với product V101/V102 / user V101/V102 — V100 series cho seed-dev sau Phase 16).
- `notification-service/db/migration`: empty → Phase 23 add **V1** (bootstrap).

### Vietnamese Comments + English Identifiers
**Source:** All `*Service.java`, `*Entity.java` comments (e.g. OrderCrudService line 61: "Bug fix (orders-api-500): dùng findAllWithItems() để LEFT JOIN FETCH items").
**Apply to:** All new Java/SQL files — javadoc + inline comments tiếng Việt; class/method/var English.

---

## No Analog Found

| File | Role | Data Flow | Reason | Fallback |
|------|------|-----------|--------|----------|
| `OrderEventPublisher.java` | Producer | event-driven (afterCommit) | First AMQP publisher trong repo | RESEARCH.md §Pattern 1 (lines 240-291) skeleton |
| `OrderPlacedListener.java` (inventory + notification) | Consumer | event-driven (@RabbitListener) | First AMQP consumer trong repo | RESEARCH.md §Pattern 3 (lines 341-385) skeleton |
| `RabbitMQConfig.java` | Config | bean-declaration | First Spring AMQP config | RESEARCH.md §Pattern 2 (lines 299-335) skeleton |

Planner **must** reference RESEARCH.md skeleton for these 3 files; pattern verification trong dev test sau khi implement.

---

## Metadata

**Analog search scope:**
- `sources/backend/order-service/**` (service + repo + entity + IT + filter)
- `sources/backend/inventory-service/**` (entity + repo + service + yml + pom + migration)
- `sources/backend/notification-service/**` (current in-memory shape)
- `sources/backend/*/pom.xml` (all 7 modules)
- `sources/backend/*/src/main/resources/db/migration/` + `db/seed-dev/`
- `sources/backend/*/src/test/java/**/*IT.java`
- `db/init/`, `docker-compose.yml`

**Files scanned:** ~30
**Pattern extraction date:** 2026-05-20
**Project conventions verified:** No Lombok (constructor injection), record-style accessors (`x()` not `getX()`), `[TAG] key={}` log format, Vietnamese comments + English identifiers, Flyway dev-profile seed split, Testcontainers postgres init via `test-init/01-schemas.sql`, schema-per-service.
