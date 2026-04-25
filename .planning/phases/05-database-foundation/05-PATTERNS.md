# Phase 5: Database Foundation — Pattern Map

**Mapped:** 2026-04-26
**Files analyzed:** ~36 files (5 services × ~6 files + 2 root)
**Analogs found:** 34 / 36 (2 NEW files không có analog: `db/init/01-schemas.sql`, `db/seed-dev/V2__seed_dev_data.sql` — dùng RESEARCH.md SQL outline)

> Lưu ý: Toàn bộ 5 services (`user/product/order/payment/inventory`) đã đồng dạng về layout, pom.xml, application.yml, và repository pattern. Vì vậy mỗi pattern dưới đây chỉ liệt kê **1 analog canonical** (lấy từ `product-service` nơi có 2 entity = trường hợp "khó nhất"); 4 services còn lại copy mẫu, đổi tên schema/entity/table. Notification-service KHÔNG động (D-09).

---

## File Classification

### Per-service refactor (5 services × cùng pattern)

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `sources/backend/<svc>/pom.xml` | config (Maven) | build-deps | `sources/backend/product-service/pom.xml` | exact (same parent + identical structure) |
| `sources/backend/<svc>/src/main/resources/application.yml` | config (Spring) | request-response | `sources/backend/product-service/src/main/resources/application.yml` | exact |
| `sources/backend/<svc>/src/main/java/.../domain/XxxEntity.java` (NEW class) | model (JPA entity) | CRUD | `sources/backend/product-service/.../domain/ProductEntity.java` (record) | role-match (record→class refactor) |
| `sources/backend/<svc>/src/main/java/.../domain/XxxDto.java` (NEW record) | model (DTO) | request-response | `sources/backend/product-service/.../domain/ProductEntity.java` (record cũ — copy nguyên + đổi tên) | exact (rename/clone) |
| `sources/backend/<svc>/src/main/java/.../domain/XxxMapper.java` (NEW) | utility (Entity↔DTO) | transform | (no analog — pattern mới) | none |
| `sources/backend/<svc>/src/main/java/.../repository/XxxRepository.java` (NEW interface) | repository (JPA) | CRUD | `sources/backend/product-service/.../repository/InMemoryProductRepository.java` (replace) | role-match (interface vs class) |
| `sources/backend/<svc>/src/main/java/.../repository/InMemoryXxxRepository.java` | DELETE | — | self | — |
| `sources/backend/<svc>/src/main/java/.../service/XxxCrudService.java` | service | CRUD | `sources/backend/product-service/.../service/ProductCrudService.java` | exact (cùng file, minor edits) |
| `sources/backend/<svc>/src/main/resources/db/migration/V1__init_schema.sql` (NEW) | migration (DDL) | schema | (no analog — first migration) | use RESEARCH.md §Migration SQL Outline |
| `sources/backend/<svc>/src/main/resources/db/seed-dev/V2__seed_dev_data.sql` (NEW) | migration (DML seed) | seed | (no analog) | use RESEARCH.md §V2__seed_dev_data |

### Root-level

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `docker-compose.yml` (modify) | config (compose) | infra | self (existing block format) | exact |
| `db/init/01-schemas.sql` (NEW) | migration (init) | schema | (no analog) | use RESEARCH.md §Decision #3 |

### Frontend cleanup (cuối phase)

| File/Folder | Role | Action |
|-------------|------|--------|
| `sources/frontend/src/mock-data/` | data fixtures | DELETE folder + grep imports |

---

## Pattern Assignments

### A. `pom.xml` per service (config, build-deps)

**Analog:** `sources/backend/product-service/pom.xml` (lines 1–58 — toàn bộ file)

**Imports / parent block** (lines 7–12) — KHÔNG đổi:
```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.3.2</version>
  <relativePath/>
</parent>
```

**Properties block** (lines 20–23) — thêm property version cho deps mới (optional nếu không có aggregator pom):
```xml
<properties>
  <java.version>17</java.version>
  <springdoc.version>2.6.0</springdoc.version>
  <!-- Phase 5 thêm: version managed bởi Spring Boot 3.3.2 BOM, KHÔNG hardcode -->
</properties>
```

**Pattern thêm 4 deps** (insert sau dòng 47, trước `</dependencies>`):
```xml
<!-- Phase 5: persistence -->
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
<!-- Test infra (chỉ thêm nếu planner chọn Testcontainers — RESEARCH §Decision #7) -->
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

**Apply to:** 5 services (`user-service`, `product-service`, `order-service`, `payment-service`, `inventory-service`). KHÔNG apply notification-service / api-gateway.

---

### B. `application.yml` per service (config, request-response)

**Analog:** `sources/backend/product-service/src/main/resources/application.yml` (lines 1–19)

**Pattern hiện tại** (full file):
```yaml
spring:
  application:
    name: product-service

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health,info

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
```

**Pattern mở rộng Phase 5** (insert vào block `spring:`, ngay dưới `application.name`):
```yaml
spring:
  application:
    name: product-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:tmdt}?currentSchema=product_svc
    username: ${DB_USER:tmdt}
    password: ${DB_PASSWORD:tmdt}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        default_schema: product_svc
        format_sql: true
    open-in-view: false
  flyway:
    enabled: true
    schemas: product_svc
    default-schema: product_svc
    baseline-on-migrate: false
    locations: classpath:db/migration

# Profile dev: thêm seed-dev location
---
spring:
  config:
    activate:
      on-profile: dev
  flyway:
    locations: classpath:db/migration,classpath:db/seed-dev
```

**Per-service substitution table:**

| Service | `currentSchema=` / `default-schema` / `flyway.schemas` |
|---------|-------------------------------------------------------|
| user-service | `user_svc` |
| product-service | `product_svc` |
| order-service | `order_svc` |
| payment-service | `payment_svc` |
| inventory-service | `inventory_svc` |

---

### C. `XxxEntity.java` — record→@Entity class refactor (model, CRUD)

**Analog:** `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/domain/ProductEntity.java` (lines 1–40)

**Hiện tại (record pattern — sẽ bị thay):**
```java
public record ProductEntity(
    String id,
    String name,
    String slug,
    String categoryId,
    BigDecimal price,
    String status,
    boolean deleted,
    Instant createdAt,
    Instant updatedAt
) {
  public static ProductEntity create(String name, String slug, String categoryId, BigDecimal price, String status) {
    Instant now = Instant.now();
    return new ProductEntity(UUID.randomUUID().toString(), name, slug, categoryId, price, status, false, now, now);
  }
  public ProductEntity update(...) { ... }
  public ProductEntity setStatus(String status) { ... }
  public ProductEntity softDelete() { ... }
}
```

**Pattern mới (mutable JPA @Entity class)** — copy từ RESEARCH §Decision #1 + #5:
```java
package com.ptit.htpt.productservice.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "products", schema = "product_svc")
@SQLRestriction("deleted = false")
@SQLDelete(sql = "UPDATE product_svc.products SET deleted = true, updated_at = NOW() WHERE id = ?")
public class ProductEntity {

  @Id
  @Column(length = 36, nullable = false, updatable = false)
  private String id;

  @Column(nullable = false, length = 300)
  private String name;

  @Column(nullable = false, length = 320, unique = true)
  private String slug;

  @Column(name = "category_id", nullable = false, length = 36)
  private String categoryId;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal price;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(nullable = false)
  private boolean deleted = false;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  // JPA proxy: protected no-arg constructor (Pitfall 1, RESEARCH §Risks)
  protected ProductEntity() {}

  // All-args constructor for the static factory
  protected ProductEntity(String id, String name, String slug, String categoryId,
                          BigDecimal price, String status, boolean deleted,
                          Instant createdAt, Instant updatedAt) {
    this.id = id; this.name = name; this.slug = slug; this.categoryId = categoryId;
    this.price = price; this.status = status; this.deleted = deleted;
    this.createdAt = createdAt; this.updatedAt = updatedAt;
  }

  public static ProductEntity create(String name, String slug, String categoryId,
                                     BigDecimal price, String status) {
    Instant now = Instant.now();
    return new ProductEntity(UUID.randomUUID().toString(), name, slug, categoryId,
        price, status, false, now, now);
  }

  public void update(String name, String slug, String categoryId, BigDecimal price, String status) {
    this.name = name; this.slug = slug; this.categoryId = categoryId;
    this.price = price; this.status = status; this.updatedAt = Instant.now();
  }

  public void setStatus(String status) { this.status = status; this.updatedAt = Instant.now(); }
  public void softDelete()              { this.deleted = true; this.updatedAt = Instant.now(); }

  // Getters
  public String id() { return id; }
  public String name() { return name; }
  public String slug() { return slug; }
  public String categoryId() { return categoryId; }
  public BigDecimal price() { return price; }
  public String status() { return status; }
  public boolean deleted() { return deleted; }
  public Instant createdAt() { return createdAt; }
  public Instant updatedAt() { return updatedAt; }

  // equals/hashCode by id only (Pitfall 2, RESEARCH §Risks)
  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ProductEntity that)) return false;
    return Objects.equals(id, that.id);
  }
  @Override public int hashCode() { return Objects.hash(id); }
}
```

**Why getters use `name()` not `getName()`:** Giữ tên accessor giống record cũ → service layer (`product.name()`, `product.deleted()`...) KHÔNG phải đổi gọi.

**Apply to (5 entity tổng cộng — 1 entity chính per service trong scope V1):**

| Service | Entity (new class) | Table | Schema | Phase 5 fields (V1) |
|---------|-------------------|-------|--------|---------------------|
| user-service | `UserEntity` (renamed from UserProfile) | `users` | `user_svc` | id, username, email, password_hash, roles, deleted, createdAt, updatedAt |
| product-service | `ProductEntity` | `products` | `product_svc` | id, name, slug, category_id, price, status, deleted, createdAt, updatedAt |
| product-service | `CategoryEntity` | `categories` | `product_svc` | id, name, slug, deleted, createdAt, updatedAt |
| order-service | `OrderEntity` | `orders` | `order_svc` | id, user_id, total_amount, status, deleted, createdAt, updatedAt (note column drop hoặc giữ — planner quyết) |
| payment-service | `PaymentTransactionEntity` | `payments` | `payment_svc` | id, session_id (or order_id), amount, method, status, createdAt, updatedAt |
| inventory-service | `InventoryItem` | `inventory_items` | `inventory_svc` | id, product_id, quantity, reserved, createdAt, updatedAt |

> Phase 5 scope-cut: bỏ qua `UserAddress`, `CartEntity`, `PaymentSessionEntity`, `InventoryReservation`, `NotificationDispatch`, `NotificationTemplate` (defer Phase 8 hoặc giữ in-memory side). Planner xác nhận với REQ DB-04 trước khi cắt.

---

### D. `XxxDto.java` — DTO record (model, request-response)

**Analog:** `sources/backend/product-service/.../domain/ProductEntity.java` lines 7–17 (record block) — copy nguyên định nghĩa, đổi tên class thành `ProductDto`.

**Pattern:**
```java
package com.ptit.htpt.productservice.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** Wire format — dùng cho controller response. KHÔNG có field `deleted` để tránh leak qua Jackson. */
public record ProductDto(
    String id,
    String name,
    String slug,
    String categoryId,
    BigDecimal price,
    String status,
    Instant createdAt,
    Instant updatedAt
) {}
```

**Critical:** `deleted` field BỊ LOẠI khỏi DTO (Pitfall 3 + Decision #8 RESEARCH).

---

### E. `XxxMapper.java` — Entity↔DTO transform (utility, transform)

**Analog:** Không có analog cùng vai trò trong codebase (pattern mới). Tham chiếu RESEARCH §Decision #8.

**Pattern:**
```java
package com.ptit.htpt.productservice.domain;

public final class ProductMapper {
  private ProductMapper() {}

  public static ProductDto toDto(ProductEntity e) {
    return new ProductDto(
        e.id(), e.name(), e.slug(), e.categoryId(),
        e.price(), e.status(), e.createdAt(), e.updatedAt()
    );
  }
}
```

**Apply to:** 1 mapper per entity (5–6 mapper classes tổng).

---

### F. `XxxRepository.java` — JpaRepository interface (repository, CRUD)

**Analog (replace target):** `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/repository/InMemoryProductRepository.java` (lines 1–41)

**Hiện tại (in-memory):**
```java
@Repository
public class InMemoryProductRepository {
  private final Map<String, ProductEntity> products = new LinkedHashMap<>();
  // findAllProducts(), findProductById(String), saveProduct(ProductEntity)
  // findAllCategories(), findCategoryById(String), saveCategory(CategoryEntity)
}
```

**Pattern mới (split per entity, JpaRepository interface):**
```java
package com.ptit.htpt.productservice.repository;

import com.ptit.htpt.productservice.domain.ProductEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<ProductEntity, String> {
  Optional<ProductEntity> findBySlug(String slug);
}
```

```java
public interface CategoryRepository extends JpaRepository<CategoryEntity, String> {
  Optional<CategoryEntity> findBySlug(String slug);
}
```

**Method-name mapping (cho service layer migration — đổi gọi tối thiểu):**

| InMemoryXxxRepository (cũ) | JpaRepository (mới — built-in) |
|----------------------------|--------------------------------|
| `findAllProducts()` returns `Collection<ProductEntity>` | `findAll()` returns `List<ProductEntity>` |
| `findProductById(String)` returns `Optional<ProductEntity>` | `findById(String)` returns `Optional<ProductEntity>` |
| `saveProduct(ProductEntity)` returns `ProductEntity` | `save(ProductEntity)` returns `ProductEntity` |
| (delete: never called — soft via `softDelete()` mutator) | `delete(entity)` triggers `@SQLDelete` UPDATE |

**Apply to:** 1 interface per entity. Delete the `InMemoryXxxRepository.java` after refactor.

---

### G. `XxxCrudService.java` — service layer adjustment (service, CRUD)

**Analog:** `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductCrudService.java` (lines 1–259)

**Constructor injection pattern hiện tại** (lines 22–27):
```java
@Service
public class ProductCrudService {
  private final InMemoryProductRepository repository;
  public ProductCrudService(InMemoryProductRepository repository) {
    this.repository = repository;
  }
}
```

**Pattern mới — minimal change (inject 2 JPA repos):**
```java
@Service
public class ProductCrudService {
  private final ProductRepository productRepo;
  private final CategoryRepository categoryRepo;

  public ProductCrudService(ProductRepository productRepo, CategoryRepository categoryRepo) {
    this.productRepo = productRepo;
    this.categoryRepo = categoryRepo;
  }
  // ...
}
```

**Per-method change pattern** (apply throughout):

| Cũ | Mới |
|----|-----|
| `repository.findAllProducts()` | `productRepo.findAll()` |
| `repository.findProductById(id)` | `productRepo.findById(id)` |
| `repository.saveProduct(p)` | `productRepo.save(p)` |
| `repository.findCategoryById(id)` | `categoryRepo.findById(id)` |
| `repository.findAllCategories()` | `categoryRepo.findAll()` |
| `repository.saveCategory(c)` | `categoryRepo.save(c)` |
| `current.softDelete()` returns new entity | `current.softDelete(); productRepo.save(current);` (mutate in-place) |
| `current.update(...)` returns new entity | `current.update(...); productRepo.save(current);` |

**`getProductBySlug` simplification** (lines 51–59):
```java
// Cũ: stream filter findFirst trên collection
// Mới: dùng custom finder
public ProductEntity getProductBySlug(String slug) {
  return productRepo.findBySlug(slug)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
}
```

**`toResponse` mapper** (lines 134–160) — KHÔNG đổi shape, nhưng category lookup dùng `categoryRepo.findById(...)` thay `repository.findCategoryById(...)`.

**Critical:** Controller signatures, ProductResponse record, validation records (lines 215–258) GIỮ NGUYÊN → OpenAPI zero-diff (Decision #8).

---

### H. `docker-compose.yml` (config, infra)

**Analog (modify target):** `docker-compose.yml` lines 1–48 (entire file — current shape)

**Hiện tại (sample, line 19–22 product-service):**
```yaml
product-service:
  build: ./sources/backend/product-service
  ports:
    - "8082:8080"
```

**Pattern thêm** — insert `postgres` service ở đầu `services:` block (trước `api-gateway`), copy nguyên từ RESEARCH §Decision #9:
```yaml
services:
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

**Pattern modify per-service block** (5 services: user/product/order/payment/inventory) — thay block cũ:
```yaml
product-service:
  build: ./sources/backend/product-service
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
    - "8082:8080"
```

**Pattern thêm volumes block** (cuối file):
```yaml
volumes:
  tmdt-pgdata:
```

**Notification-service + api-gateway + frontend:** KHÔNG động (D-09 + ngoài scope).

---

### I. `db/init/01-schemas.sql` (NEW, no analog)

**Source:** RESEARCH §Decision #3.

**Content (full file):**
```sql
CREATE SCHEMA IF NOT EXISTS user_svc;
CREATE SCHEMA IF NOT EXISTS product_svc;
CREATE SCHEMA IF NOT EXISTS order_svc;
CREATE SCHEMA IF NOT EXISTS payment_svc;
CREATE SCHEMA IF NOT EXISTS inventory_svc;
```

Mount qua docker-compose volume `./db/init:/docker-entrypoint-initdb.d:ro` — chạy 1 lần khi volume `tmdt-pgdata` lần đầu init.

---

### J. `V1__init_schema.sql` per service (NEW, no analog)

**Source:** RESEARCH §Migration SQL Outline §V1__init_schema.sql per service.

Copy nguyên các DDL block từ RESEARCH (đã verified consistent với entity field schema):
- `product_svc.products` + `product_svc.categories` (RESEARCH lines 317–342)
- `user_svc.users` (RESEARCH lines 345–358)
- `order_svc.orders` (RESEARCH lines 361–372)
- `payment_svc.payments` (RESEARCH lines 375–386)
- `inventory_svc.inventory_items` (RESEARCH lines 389–399)

---

### K. `V2__seed_dev_data.sql` per service (NEW, no analog)

**Source:** RESEARCH §V2__seed_dev_data.sql per service (lines 405–453).

Copy nguyên:
- user-service V2: 2 users (admin BCrypt + demo_user)
- product-service V2: 5 categories + 10 products
- order-service V2: 2 demo orders
- inventory-service V2: 10 inventory rows
- payment-service V2: skip (Phase 8 mới có data thật)

---

## Shared Patterns

### Soft-delete via Hibernate 6 annotations
**Source:** RESEARCH §Decision #5 (Hibernate 6 user guide)
**Apply to:** Tất cả `XxxEntity` classes (5–6 entities).
```java
@SQLRestriction("deleted = false")
@SQLDelete(sql = "UPDATE <schema>.<table> SET deleted = true, updated_at = NOW() WHERE id = ?")
```

> Sau khi áp dụng, `findAll()` / `findById()` tự động loại soft-deleted records — service layer logic `if (product.deleted()) throw NOT_FOUND` (ProductCrudService line 45) trở thành **dead code** nhưng GIỮ LẠI cho safety (entity vẫn có cờ `deleted` hiển thị, hữu ích cho admin queries sau này).

### UUID String ID
**Source:** CONTEXT D-04 + RESEARCH §Decision #1
**Apply to:** Tất cả entities + tất cả V1 DDL.
```java
@Id @Column(length = 36, nullable = false, updatable = false)
private String id;
// Factory: UUID.randomUUID().toString()
```
```sql
id VARCHAR(36) PRIMARY KEY
```

### Entity↔DTO boundary tại service layer
**Source:** RESEARCH §Decision #8 (Pitfall 3)
**Apply to:** Tất cả service methods trả ra controller layer.
```java
public ProductDto findById(String id) {
  ProductEntity entity = productRepo.findById(id).orElseThrow(...);
  return ProductMapper.toDto(entity);   // explicit boundary, KHÔNG return entity
}
```

> **Gotcha:** ProductCrudService hiện trả `ProductEntity` (record) trực tiếp cho controller — RESEARCH §Decision #8 yêu cầu insert mapper. Planner cần audit từng method signature, decide: (a) đổi return type của service sang Dto (clean), hoặc (b) giữ Entity trả về và để controller map (faster refactor). Recommend (a) cho consistency.

### Profile-gated Flyway seed
**Source:** RESEARCH §Decision #2 + CONTEXT D-06
**Apply to:** Tất cả 5 application.yml.
```yaml
spring:
  flyway:
    locations: classpath:db/migration   # default — V1 only

---
spring:
  config:
    activate:
      on-profile: dev
  flyway:
    locations: classpath:db/migration,classpath:db/seed-dev   # dev: V1 + V2
```

### Existing patterns preserved (KHÔNG đổi)
- **ApiErrorResponse + GlobalExceptionHandler** envelope (CONTEXT §Reusable Assets) — JPA exceptions auto-map. Nếu cần handler riêng cho `DataIntegrityViolationException` / `EntityNotFoundException`, thêm vào handler hiện có, không tạo file mới.
- **TraceIdFilter** per-service — không động.
- **Springdoc OpenAPI** path `/v3/api-docs` — không động.
- **Validation records** (`ProductUpsertRequest`, etc. trong `ProductCrudService`) — không động.

---

## No Analog Found

Các file sau KHÔNG có analog trong codebase. Planner dùng RESEARCH.md trực tiếp:

| File | Role | Reason | Source |
|------|------|--------|--------|
| `db/init/01-schemas.sql` | migration init | First init script | RESEARCH §Decision #3 |
| `<svc>/db/migration/V1__init_schema.sql` | migration DDL | First Flyway migration | RESEARCH §Migration SQL Outline |
| `<svc>/db/seed-dev/V2__seed_dev_data.sql` | migration seed | First seed | RESEARCH §V2__seed_dev_data |
| `XxxMapper.java` | utility transform | First explicit boundary | RESEARCH §Decision #8 |
| Testcontainers `@DataJpaTest` setup | test infra | Codebase chưa có integration test | RESEARCH §Decision #7 |

---

## Important Cross-Cutting Notes for Planner

1. **Service entity name asymmetry:** `user-service` có class hiện tại là `UserProfile` (không phải `UserProfileEntity`). Khi refactor, planner quyết:
   - (a) Rename `UserProfile` → `UserEntity` + tạo `UserDto` mới (cleaner), HOẶC
   - (b) Giữ `UserProfile` và tạo `UserProfileEntity` riêng (nhiều file hơn).
   Recommend (a). Tương tự `InventoryItem` → có thể rename `InventoryEntity` cho symmetry.

2. **Service entity gấp đôi:** `product-service` có 2 entity (Product + Category) → phải tạo 2 Repository, 2 Mapper, 2 Dto. Tương tự `order-service` (Cart + Order — nhưng Cart in scope phase 5 hay defer?). Planner cắt scope theo CONTEXT decision (orderEntity primary, cart defer).

3. **`note` field trong OrderEntity:** record cũ có `note` (line 13 OrderEntity.java) nhưng RESEARCH V1 DDL không include. Planner phải pick: drop `note` (cắt) hoặc thêm column `note VARCHAR(500)` vào V1 DDL. Recommend giữ (low cost, FE có thể xài).

4. **`PaymentTransactionEntity` field mismatch:** record có `sessionId, reference, message` nhưng RESEARCH `payments` table dùng `order_id, amount, method`. Đây là schema rename rộng — planner cần entity refactor lớn hơn rest, hoặc đề xuất giữ field cũ trong V1 (`session_id, reference, message`). Recommend giữ field cũ V1, rename Phase 8.

5. **InMemoryNotificationRepository:** KHÔNG động (D-09).

6. **Aggregator pom.xml:** Không tồn tại `sources/backend/pom.xml` (verified via Glob — RESEARCH Assumption A2 confirmed). 5 services add deps độc lập, version managed bởi Spring Boot 3.3.2 BOM (không cần property). Defer DRY parent setup.

---

## Metadata

**Analog search scope:**
- `sources/backend/*/src/main/java/.../domain/` (12 entity files)
- `sources/backend/*/src/main/java/.../repository/` (6 in-memory repos)
- `sources/backend/*/pom.xml` (7 poms — verified identical structure)
- `sources/backend/*/src/main/resources/application.yml` (7 yamls — verified identical structure)
- `docker-compose.yml` (root)

**Files scanned:** ~36
**Pattern extraction date:** 2026-04-26

---

## PATTERN MAPPING COMPLETE
