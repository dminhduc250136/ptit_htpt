# Phase 5: Database Foundation — Research

**Researched:** 2026-04-26
**Domain:** Spring Boot 3.3.2 + Hibernate 6 + Flyway 10 + Postgres 16 (multi-schema) + Java 17 records→entities refactor
**Confidence:** HIGH (stack đã locked v1.0; CONTEXT.md đã chốt 9 quyết định; chỉ còn micro-decisions implementation-level)

---

## Phase Summary

Phase 5 đặt nền tảng database thật cho v1.1: thêm 1 Postgres 16 container vào docker-compose (volume `tmdt-pgdata`, healthcheck `pg_isready`), gắn Spring Data JPA + Flyway 10 + flyway-database-postgresql vào 5 services có entity (user/product/order/payment/inventory — notification giữ in-memory theo D-09), refactor `XxxEntity` records → mutable `@Entity` classes (giữ `String` UUID ID theo D-04), repository in-memory → `interface JpaRepository`, mỗi service quản lý 1 schema riêng (`user_svc`, `product_svc`, `order_svc`, `payment_svc`, `inventory_svc`) qua Flyway `V1__init_schema.sql` + V2 seed dev profile-gated (~10 products, 5 categories, 1 admin BCrypt `admin123`, 2-3 demo orders), verify gateway round-trip GET /api/products xanh end-to-end qua FE thật, rồi xóa `sources/frontend/src/mock-data/`. Toàn bộ tuân thủ visible-first: tối thiểu test refactor, defer hardening.

**Primary recommendation:** Dùng `VARCHAR(36)` cho cột ID (vì entity field là `String`, không phải `java.util.UUID`); Flyway 10.17.x + `flyway-database-postgresql` extra; multi-schema qua JDBC URL `?currentSchema=<svc>_svc` + per-service `spring.flyway.schemas`; pre-create 5 schemas qua Postgres init script (`docker-entrypoint-initdb.d/01-schemas.sql`) để tránh phụ thuộc quyền `CREATE SCHEMA` runtime.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Persist domain state (products, users, orders, payments, inventory) | Database (Postgres 16, multi-schema) | Backend (JPA/Hibernate) | Mỗi service sở hữu 1 schema → DB enforce isolation; JPA chỉ là access layer |
| Schema versioning + seed | Backend (Flyway 10 in each service) | Database | Migrations chạy tại service startup → tự động apply per-deploy |
| Domain logic (CRUD, validation, soft-delete) | Backend (service layer) | — | Service signatures giữ nguyên sau refactor; chỉ swap repo impl |
| Wire format (JSON DTO) | Backend (controller + DTO record) | Frontend (typed modules) | DTO records giữ shape FE đã codegen → zero diff OpenAPI |
| Health gating (services chờ DB ready) | Infrastructure (docker-compose healthcheck) | — | `depends_on: condition: service_healthy` cho phép startup ordering |
| Mock-data fallback removal | Frontend (Next.js) | — | Chỉ xảy ra sau khi backend round-trip xanh — frontend tier owns import cleanup |

---

## Key Technical Decisions (10 Discretion Items)

### 1. Postgres UUID column type — VARCHAR(36)

**Recommendation:** Dùng `VARCHAR(36)` (hoặc `CHAR(36)`) cho cột `id`, KHÔNG dùng native `uuid`.

**Rationale:** Entity field hiện tại là `String id` (CONTEXT D-04 lock). Nếu dùng native `uuid` Postgres + field `String` Java, Hibernate 6 sẽ nhận `Unable to convert PostgresUUID to String` qua pgjdbc — phải thêm `@JdbcTypeCode(SqlTypes.VARCHAR)` ép cast text. Trong khi đó, dùng `VARCHAR(36)` thẳng thì Hibernate auto-map `String ↔ varchar` không cần annotation. Hiệu năng disk cho ~10 products là không đáng kể; index B-tree trên VARCHAR(36) đủ nhanh cho scope MVP. Native `uuid` chỉ thật sự thắng khi (a) entity field là `java.util.UUID`, (b) hàng triệu rows. Cả 2 không apply.

**Snippet:**
```java
@Entity
@Table(name = "products", schema = "product_svc")
public class ProductEntity {
  @Id
  @Column(length = 36, nullable = false, updatable = false)
  private String id;
  // ... no @JdbcTypeCode needed
}
```

```sql
-- V1__init_schema.sql
CREATE TABLE product_svc.products (
  id VARCHAR(36) PRIMARY KEY,
  ...
);
```

**Source:** [Hibernate UUID mapping guide (in.relation.to)](https://in.relation.to/2022/05/12/orm-uuid-mapping/), [Vlad Mihalcea — Hibernate UUID identifiers](https://vladmihalcea.com/hibernate-and-uuid-identifiers/) — confirm `@JdbcTypeCode` chỉ cần khi field type mismatch DB type. [VERIFIED via official Hibernate 6 docs]

### 2. Flyway version — 10.17.x + `flyway-database-postgresql` REQUIRED

**Recommendation:** Spring Boot 3.3.2 quản lý Flyway 10.17.0 qua BOM. PHẢI explicit add `flyway-database-postgresql` vào pom.xml — nếu không sẽ ném `Unsupported Database: PostgreSQL 16.x` tại startup.

**Rationale:** Từ Flyway 10.0.0 trở đi, support DB-specific tách khỏi `flyway-core` thành module riêng. Spring Boot 3.3 BOM include `flyway-core` mặc định nhưng KHÔNG include `flyway-database-postgresql`. Đây là gotcha phổ biến nhất khi nâng từ Boot 3.2 → 3.3.

**Snippet (pom.xml mỗi service):**
```xml
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

**Source:** [Spring Boot issue #40893 — Flyway unsupported PostgreSQL after 3.3.0 upgrade](https://github.com/spring-projects/spring-boot/issues/40893), [Flyway issue #3807](https://github.com/flyway/flyway/issues/3807) [VERIFIED]

### 3. Multi-schema Flyway config

**Recommendation:** Pre-create 5 schemas qua Postgres init script; mỗi service set `spring.flyway.schemas` + `default-schema` trỏ schema riêng; `baseline-on-migrate: false` (greenfield, không cần baseline).

**Rationale:** Auto-create schema bằng Flyway yêu cầu user DB có quyền `CREATE SCHEMA` → tăng surface security. An toàn hơn là dùng Postgres init script chạy 1 lần duy nhất khi volume init lần đầu. Sau đó user `tmdt` chỉ cần quyền DML trên schema dedicated.

**Snippet (`db/init/01-schemas.sql` mount vào `/docker-entrypoint-initdb.d/`):**
```sql
CREATE SCHEMA IF NOT EXISTS user_svc;
CREATE SCHEMA IF NOT EXISTS product_svc;
CREATE SCHEMA IF NOT EXISTS order_svc;
CREATE SCHEMA IF NOT EXISTS payment_svc;
CREATE SCHEMA IF NOT EXISTS inventory_svc;
```

**Snippet (`application.yml` product-service):**
```yaml
spring:
  flyway:
    enabled: true
    schemas: product_svc
    default-schema: product_svc
    baseline-on-migrate: false
    locations: classpath:db/migration
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate.default_schema: product_svc
```

**Source:** [Flyway docs — schemas config](https://documentation.red-gate.com/fd/schemas-184127482.html) [CITED]

### 4. JDBC URL params + Hikari per-service

**Recommendation:** Dùng `?currentSchema=<svc>_svc` trong JDBC URL; Hikari defaults Spring Boot đủ (max-pool=10) — không tune.

**Snippet (`application.yml`):**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:postgres}:5432/tmdt?currentSchema=product_svc
    username: ${DB_USER:tmdt}
    password: ${DB_PASSWORD:tmdt}
    driver-class-name: org.postgresql.Driver
```

**Note:** `currentSchema` là hint cho `search_path` ở session-level — JPA queries không qualify schema (e.g., `SELECT * FROM products`) sẽ resolve đúng. Flyway tự override khi chạy migrations.

**Source:** [pgJDBC docs — connection params](https://jdbc.postgresql.org/documentation/use/) [CITED]

### 5. Soft-delete strategy — `@SQLRestriction`

**Recommendation:** Dùng Hibernate 6 `@SQLRestriction("deleted = false")` trên entity class; mặc định mọi `findAll()` / `findById()` tự lọc soft-deleted. Kèm `@SQLDelete` để `repository.delete(entity)` chuyển thành UPDATE.

**Rationale:** Pattern hiện tại (`InMemoryProductRepository.findAllProducts()` không filter `deleted`) là gap đã có ở v1.0 — refactor JPA là dịp đóng đúng chỗ. `@SQLRestriction` (Hibernate 6, replacement của deprecated `@Where`) gọn hơn nhiều so với viết `findByDeletedFalse()` thủ công khắp nơi. Khi cần admin xem soft-deleted, override bằng native query riêng.

**Snippet:**
```java
@Entity
@Table(name = "products", schema = "product_svc")
@SQLRestriction("deleted = false")
@SQLDelete(sql = "UPDATE product_svc.products SET deleted = true, updated_at = NOW() WHERE id = ?")
public class ProductEntity {
  // ...
  @Column(nullable = false)
  private boolean deleted = false;
}
```

**Source:** [Hibernate 6 user guide §soft-delete](https://docs.jboss.org/hibernate/orm/6.5/userguide/html_single/Hibernate_User_Guide.html#soft-delete) [VERIFIED]

### 6. BCrypt seed admin hash

**Recommendation:** Embed hash `$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy` (đây là hash chuẩn của chuỗi `admin123` với cost 10, salt `N9qo8uLOickgx2ZMRZoMye`).

**Rationale:** Phase 6 sẽ dùng `BCryptPasswordEncoder` (Spring Security default cost=10, prefix `$2a$`). Hash trên là reference verified trong nhiều test fixtures public. Nếu muốn tự generate: chạy 1 lần `java -jar bcrypt-cli.jar admin123` rồi paste — KHÔNG dùng hash random mỗi build (làm dirty migration history Flyway).

**[ASSUMED]** — Hash chuỗi cụ thể nên được verify lại bằng `BCrypt.checkpw("admin123", "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy")` trong 1 unit test trước khi commit V2 SQL. Planner thêm 1 task verify nhỏ.

**Snippet (V2__seed_dev_data.sql user-service):**
```sql
INSERT INTO user_svc.users (id, username, email, password_hash, roles, deleted, created_at, updated_at)
VALUES (
  '00000000-0000-0000-0000-000000000001',
  'admin',
  'admin@tmdt.local',
  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
  'ADMIN',
  false,
  NOW(),
  NOW()
);
```

### 7. Test refactor — minimal `@DataJpaTest` + Testcontainers Postgres

**Recommendation:** Visible-first ưu tiên: KHÔNG đổi H2 hoặc disable test. Dùng `@DataJpaTest` + Testcontainers Postgres `postgres:16-alpine` (singleton container reuse) — test chạy trên ENGINE thật, validate cả Flyway migrations lẫn JPA mapping. Nếu không muốn add dependency mới, fallback: chỉ giữ existing tests work bằng cách `@SpringBootTest` skip JPA via `@ActiveProfiles("test")` + `spring.autoconfigure.exclude` tắt JPA — nhưng matrix coverage giảm.

**Rationale:** H2 với Postgres dialect mode lie — `VARCHAR` indexes, `JSON` column, schema syntax đều khác. Mỗi giờ debug "test pass H2 nhưng fail Postgres prod" tốn nhiều hơn 1 lần add Testcontainers. Maven scope=test, singleton reuse → CI mỗi service chỉ start container 1 lần.

**Snippet (pom.xml):**
```xml
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

**Fallback if planner muốn cắt scope:** Disable JPA tests, giữ controller-level tests dùng MockMvc + mock service layer. Trade-off: migrations không có automated coverage cho đến phase test infrastructure.

**Source:** [Spring Boot — Testing JPA with Testcontainers](https://spring.io/guides/gs/testing-the-web-layer/) [CITED]

### 8. OpenAPI / Springdoc diff

**Recommendation:** Bắt buộc giữ controllers trả `XxxDto` (record) — KHÔNG trả `XxxEntity`. Nếu giữ đúng nguyên tắc này, OpenAPI schema diff = 0 (records cũ chỉ đổi tên class `ProductEntity` → `ProductDto`, field shape identical). Service layer chịu map.

**Risk identified:** Nếu lỡ inject `JpaRepository` thẳng vào controller hoặc dùng Spring Data REST → entity sẽ leak (fields `deleted`, `passwordHash`) vào schema → break FE typed modules. Planner đặt 1 verify step: `mvn spring-boot:run` mỗi service → fetch `/v3/api-docs` → diff với baseline JSON đã capture pre-refactor.

**Snippet pattern (service layer):**
```java
public ProductDto findById(String id) {
  ProductEntity entity = repo.findById(id).orElseThrow(...);
  return ProductMapper.toDto(entity);  // explicit boundary
}
```

### 9. docker-compose healthcheck ordering

**Recommendation:** Dùng `depends_on: postgres: condition: service_healthy` — pattern này đã GA trong Compose Spec từ 2020 (file format không cần `version: 3.x` ghi rõ; current `docker-compose.yml` repo này dùng implicit format mới, OK).

**Snippet:**
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

  product-service:
    build: ./sources/backend/product-service
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: dev
      DB_HOST: postgres
      DB_USER: tmdt
      DB_PASSWORD: tmdt
    ports: ["8082:8080"]

volumes:
  tmdt-pgdata:
```

**Note:** Phải lặp `depends_on` block cho cả 5 services (user/product/order/payment/inventory). Notification giữ nguyên (D-09).

**Source:** [Docker Compose docs — depends_on with healthcheck](https://docs.docker.com/compose/how-tos/startup-order/) [VERIFIED]

### 10. Validation Architecture — see dedicated section below.

---

## File-by-File Refactor Map

### Root

| File | Change |
|------|--------|
| `docker-compose.yml` | Thêm `postgres` service (image, volume, healthcheck, init script mount); 5 services backend (user/product/order/payment/inventory) thêm `depends_on: postgres: condition: service_healthy` + env vars `DB_HOST/DB_USER/DB_PASSWORD/SPRING_PROFILES_ACTIVE=dev`; declare `volumes: tmdt-pgdata`. Notification + gateway + frontend không động. |
| `db/init/01-schemas.sql` (NEW) | `CREATE SCHEMA IF NOT EXISTS` cho 5 schemas. Mount vào `/docker-entrypoint-initdb.d/`. |

### Per-service (5 services × pattern giống nhau)

Lấy product-service làm canonical mẫu; user/order/payment/inventory áp dụng cùng template với tên schema/entity/table tương ứng.

| File | Change |
|------|--------|
| `pom.xml` | Add 4 deps: `spring-boot-starter-data-jpa`, `org.postgresql:postgresql` (runtime), `org.flywaydb:flyway-core`, `org.flywaydb:flyway-database-postgresql`. Optional test: `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter`. |
| `src/main/resources/application.yml` | Add `spring.datasource.{url,username,password,driver-class-name}`, `spring.jpa.hibernate.ddl-auto=validate` + `hibernate.default_schema`, `spring.flyway.{enabled,schemas,default-schema,baseline-on-migrate,locations}`. Profile `dev`: `spring.flyway.locations: classpath:db/migration,classpath:db/seed-dev`. |
| `src/main/java/.../domain/ProductEntity.java` | Convert record → mutable class. Add `@Entity @Table(name="products", schema="product_svc")`, `@Id @Column(length=36)`, `@SQLRestriction("deleted = false")`, `@SQLDelete(...)`. No-arg constructor (protected) + all-args constructor + getters/setters. Static factory `create(...)` giữ pattern UUID. |
| `src/main/java/.../domain/CategoryEntity.java` | Same pattern (table=categories, schema=product_svc). |
| `src/main/java/.../domain/ProductDto.java` (NEW) | Record giữ shape cũ ProductEntity (id, name, slug, categoryId, price, status, createdAt, updatedAt — KHÔNG có `deleted`). |
| `src/main/java/.../domain/ProductMapper.java` (NEW) | Static `toDto(ProductEntity) → ProductDto` + `toEntity(CreateProductRequest) → ProductEntity`. |
| `src/main/java/.../repository/InMemoryProductRepository.java` | DELETE (replace bằng JPA repos). |
| `src/main/java/.../repository/ProductRepository.java` (NEW) | `interface ProductRepository extends JpaRepository<ProductEntity, String>` + custom queries nếu cần (e.g., `findBySlug(String)`). |
| `src/main/java/.../repository/CategoryRepository.java` (NEW) | Same pattern. |
| `src/main/java/.../service/ProductCrudService.java` | Update inject từ InMemory → ProductRepository. Wrap return values qua ProductMapper.toDto. Service method signatures GIỮ NGUYÊN — chỉ đổi internals. |
| `src/main/resources/db/migration/V1__init_schema.sql` (NEW) | DDL CREATE TABLE cho domain (xem Migration SQL Outline). |
| `src/main/resources/db/seed-dev/V2__seed_dev_data.sql` (NEW) | INSERT seed rows (xem Migration SQL Outline). |
| Existing tests | Update repo mocks → either Testcontainers `@DataJpaTest` (recommended) hoặc `@MockBean ProductRepository`. Nếu controller tests dùng MockMvc + mock service, không động. |

### Per-service specific tables

| Service | Schema | Tables |
|---------|--------|--------|
| user-service | `user_svc` | `users` (UserEntity) |
| product-service | `product_svc` | `products`, `categories` |
| order-service | `order_svc` | `orders` (OrderEntity, basic — Phase 8 thêm `order_items`) |
| payment-service | `payment_svc` | `payments` (PaymentEntity) |
| inventory-service | `inventory_svc` | `inventory_items` (InventoryEntity) |

### Frontend

| File | Change |
|------|--------|
| `sources/frontend/src/mock-data/products.ts` | DELETE (cuối phase, sau khi round-trip green) |
| `sources/frontend/src/mock-data/orders.ts` | DELETE |
| `sources/frontend/src/mock-data/` | DELETE folder |
| FE files import `@/mock-data/*` | Grep → liệt kê → 1 task xóa imports + thay bằng API call. Trong Phase 5, chỉ cần đảm bảo `npm run build` xanh sau khi xóa. (Wire FE lên gateway thật với UI rewire là Phase 7.) |

---

## Migration SQL Outline

### V1__init_schema.sql per service

**product-service** (`db/migration/V1__init_schema.sql`):
```sql
CREATE TABLE product_svc.categories (
  id VARCHAR(36) PRIMARY KEY,
  name VARCHAR(200) NOT NULL,
  slug VARCHAR(220) NOT NULL,
  deleted BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT uq_categories_slug UNIQUE (slug)
);

CREATE TABLE product_svc.products (
  id VARCHAR(36) PRIMARY KEY,
  name VARCHAR(300) NOT NULL,
  slug VARCHAR(320) NOT NULL,
  category_id VARCHAR(36) NOT NULL,
  price NUMERIC(12,2) NOT NULL,
  status VARCHAR(20) NOT NULL,
  deleted BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT uq_products_slug UNIQUE (slug),
  CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES product_svc.categories(id)
);
CREATE INDEX idx_products_category_id ON product_svc.products(category_id);
CREATE INDEX idx_products_status ON product_svc.products(status) WHERE deleted = FALSE;
```

**user-service** (`db/migration/V1__init_schema.sql`):
```sql
CREATE TABLE user_svc.users (
  id VARCHAR(36) PRIMARY KEY,
  username VARCHAR(80) NOT NULL,
  email VARCHAR(200) NOT NULL,
  password_hash VARCHAR(120) NOT NULL,
  roles VARCHAR(200) NOT NULL DEFAULT 'USER',  -- comma-separated; Phase 6 có thể normalize
  deleted BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT uq_users_username UNIQUE (username),
  CONSTRAINT uq_users_email UNIQUE (email)
);
```

**order-service** (`db/migration/V1__init_schema.sql`) — basic only, Phase 8 sẽ thêm order_items + shipping_address:
```sql
CREATE TABLE order_svc.orders (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL,
  total NUMERIC(12,2) NOT NULL,
  status VARCHAR(30) NOT NULL,
  deleted BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_orders_user_id ON order_svc.orders(user_id);
```

**payment-service** (`db/migration/V1__init_schema.sql`):
```sql
CREATE TABLE payment_svc.payments (
  id VARCHAR(36) PRIMARY KEY,
  order_id VARCHAR(36) NOT NULL,
  amount NUMERIC(12,2) NOT NULL,
  method VARCHAR(50) NOT NULL,
  status VARCHAR(30) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_payments_order_id ON payment_svc.payments(order_id);
```

**inventory-service** (`db/migration/V1__init_schema.sql`):
```sql
CREATE TABLE inventory_svc.inventory_items (
  id VARCHAR(36) PRIMARY KEY,
  product_id VARCHAR(36) NOT NULL,
  quantity INT NOT NULL DEFAULT 0,
  reserved INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT uq_inventory_product UNIQUE (product_id)
);
```

> FK cross-schema (e.g., orders.user_id → users.id) NOT enforced — vi phạm microservice boundary; eventual consistency. Service layer chịu validate.

### V2__seed_dev_data.sql per service

**user-service V2:**
```sql
INSERT INTO user_svc.users (id, username, email, password_hash, roles, deleted, created_at, updated_at)
VALUES
  ('00000000-0000-0000-0000-000000000001', 'admin', 'admin@tmdt.local',
   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ADMIN', FALSE, NOW(), NOW()),
  ('00000000-0000-0000-0000-000000000002', 'demo_user', 'demo@tmdt.local',
   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'USER', FALSE, NOW(), NOW());
```

**product-service V2** (5 categories + ~10 products):
```sql
INSERT INTO product_svc.categories (id, name, slug, deleted, created_at, updated_at) VALUES
  ('cat-electronics', 'Điện tử', 'dien-tu', FALSE, NOW(), NOW()),
  ('cat-fashion',     'Thời trang', 'thoi-trang', FALSE, NOW(), NOW()),
  ('cat-household',   'Gia dụng', 'gia-dung', FALSE, NOW(), NOW()),
  ('cat-books',       'Sách', 'sach', FALSE, NOW(), NOW()),
  ('cat-cosmetics',   'Mỹ phẩm', 'my-pham', FALSE, NOW(), NOW());

INSERT INTO product_svc.products (id, name, slug, category_id, price, status, deleted, created_at, updated_at) VALUES
  ('prod-001', 'Tai nghe bluetooth Sony WH-1000XM5', 'tai-nghe-sony-wh-1000xm5', 'cat-electronics', 7990000.00, 'ACTIVE', FALSE, NOW(), NOW()),
  ('prod-002', 'Bàn phím cơ Keychron K2', 'ban-phim-co-keychron-k2', 'cat-electronics', 2490000.00, 'ACTIVE', FALSE, NOW(), NOW()),
  ('prod-003', 'Áo thun cotton basic', 'ao-thun-cotton-basic', 'cat-fashion', 199000.00, 'ACTIVE', FALSE, NOW(), NOW()),
  ('prod-004', 'Quần jean slim-fit', 'quan-jean-slim-fit', 'cat-fashion', 549000.00, 'ACTIVE', FALSE, NOW(), NOW()),
  ('prod-005', 'Nồi cơm điện Cuckoo 1.8L', 'noi-com-dien-cuckoo-1-8l', 'cat-household', 3290000.00, 'ACTIVE', FALSE, NOW(), NOW()),
  ('prod-006', 'Bộ chăn ga gối cotton', 'bo-chan-ga-goi-cotton', 'cat-household', 890000.00, 'ACTIVE', FALSE, NOW(), NOW()),
  ('prod-007', 'Sách Clean Code - Robert C. Martin', 'sach-clean-code', 'cat-books', 320000.00, 'ACTIVE', FALSE, NOW(), NOW()),
  ('prod-008', 'Sách Atomic Habits - James Clear', 'sach-atomic-habits', 'cat-books', 180000.00, 'ACTIVE', FALSE, NOW(), NOW()),
  ('prod-009', 'Kem chống nắng Anessa SPF50', 'kem-chong-nang-anessa', 'cat-cosmetics', 489000.00, 'ACTIVE', FALSE, NOW(), NOW()),
  ('prod-010', 'Son môi MAC Ruby Woo', 'son-moi-mac-ruby-woo', 'cat-cosmetics', 690000.00, 'ACTIVE', FALSE, NOW(), NOW());
```

**order-service V2** (2 demo orders cho `demo_user`):
```sql
INSERT INTO order_svc.orders (id, user_id, total, status, deleted, created_at, updated_at) VALUES
  ('ord-demo-001', '00000000-0000-0000-0000-000000000002', 8489000.00, 'DELIVERED', FALSE, NOW() - INTERVAL '7 days', NOW() - INTERVAL '5 days'),
  ('ord-demo-002', '00000000-0000-0000-0000-000000000002', 500000.00,  'PENDING',   FALSE, NOW() - INTERVAL '1 day',  NOW() - INTERVAL '1 day');
```

**inventory-service V2** (stock cho 10 products):
```sql
INSERT INTO inventory_svc.inventory_items (id, product_id, quantity, reserved, created_at, updated_at) VALUES
  ('inv-001', 'prod-001', 25, 0, NOW(), NOW()),
  ('inv-002', 'prod-002', 40, 0, NOW(), NOW()),
  -- ... tương tự cho 8 products còn lại
  ('inv-010', 'prod-010', 60, 0, NOW(), NOW());
```

**payment-service V2:** không seed (payments tạo runtime khi checkout — Phase 8).

---

## Validation Architecture

> Mandatory section per CONTEXT discretion item #10 và Nyquist sampling.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 5.10 (inherited from Spring Boot 3.3.2 BOM) |
| Config file | None — defaults từ Spring Boot parent |
| Quick run command | `mvn -pl sources/backend/<service> test -Dtest=<TestClass>` |
| Full suite command | `mvn -f sources/backend/pom.xml test` (nếu có aggregator); nếu không, loop qua 5 services |
| Integration | Testcontainers `postgres:16-alpine` singleton reuse |

### Phase Requirements → Validation Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DB-01 | Postgres container start, healthcheck PASS, volume persist | smoke | `docker compose up -d postgres && docker compose ps --filter health=healthy` | ❌ Wave 0 |
| DB-02 | 5 services có deps đúng (data-jpa + postgresql + flyway-core + flyway-database-postgresql) | unit | `mvn -pl sources/backend/<svc> dependency:tree | grep -E 'flyway|postgresql|data-jpa'` | ❌ Wave 0 |
| DB-03 | application.yml datasource + flyway config valid; ddl-auto=validate; V1 schema applied | integration | `@DataJpaTest` mỗi service với Testcontainers | ❌ Wave 0 |
| DB-04 | InMemoryRepo gone; JpaRepository CRUD work; Entity↔DTO mapping zero leak | integration | `@DataJpaTest` save/findById/findAll/softDelete | ❌ Wave 0 |
| DB-05 | V2 seed dev profile populates 5 categories + 10 products + admin user + 2 orders + 10 inventory rows | integration | `@SpringBootTest @ActiveProfiles("dev")` + count rows | ❌ Wave 0 |
| DB-06 | Gateway round-trip GET /api/products → 10 seeded products; mock-data folder gone; FE flow chính (browse→cart→checkout→confirm) PASS | e2e | `docker compose up && curl http://localhost:8080/api/products` + Playwright subset | ❌ Wave 0 |

### Validation Dimensions (Nyquist)

1. **Build dimension**: Maven compile + package mỗi service xanh sau khi add deps + entity refactor.
2. **JPA mapping vs migration consistency**: `ddl-auto=validate` ép Hibernate verify entity ↔ V1 schema match — fail-fast nếu lệch.
3. **Schema isolation per service**: Mỗi service chỉ touch schema của mình; cross-schema query không được. Verify: query `pg_namespace` count = 5 schemas; mỗi service `flyway_schema_history` table nằm đúng schema.
4. **Seed correctness**: V2 chỉ chạy với profile `dev`; không chạy ở `test` (vì test dùng Testcontainers ephemeral). Verify counts: 5 categories, 10 products, 1 admin + 1 demo user, 2 orders, 10 inventory items.
5. **Integration dimension (per service)**: `@DataJpaTest` + Testcontainers chạy V1 migration → assert CRUD basic + softDelete `@SQLRestriction` filter đúng.
6. **Gateway round-trip dimension**: Compose up full stack → `curl http://localhost:8080/api/products` trả 10 products thật; payload JSON shape match OpenAPI baseline (no diff).
7. **OpenAPI contract dimension**: Capture `/v3/api-docs` JSON pre-refactor (baseline), post-refactor diff phải = 0 cho 5 services.
8. **FE flow E2E subset**: Sau khi xóa mock-data, FE build xanh + Playwright spec subset (browse → product detail → add to cart → checkout flow → confirmation) PASS.
9. **Mock-data deletion verification**: `git ls-files sources/frontend/src/mock-data/` empty; `grep -r "mock-data" sources/frontend/src/` no results.

### Sampling Rate

- **Per task commit:** `mvn -pl sources/backend/<service> test` (chỉ service đang động).
- **Per wave merge:** `mvn -f sources/backend test` toàn bộ 5 services + `docker compose up -d postgres` smoke.
- **Phase gate:** Full stack `docker compose up` + curl gateway round-trip + Playwright subset + grep mock-data clean.

### Wave 0 Gaps

- [ ] Add `flyway-database-postgresql` + Testcontainers deps vào pom.xml mỗi service (5 services).
- [ ] Create `db/init/01-schemas.sql` ở repo root.
- [ ] Capture baseline `/v3/api-docs` JSON cho 5 services trước khi refactor (compare-target).
- [ ] Identify Playwright spec subset minimum để cover browse→cart→checkout→confirm — mark là phase gate test.
- [ ] BCrypt hash verify task (1 unit test trong user-service kiểm `BCrypt.checkpw("admin123", "<hash>")`).

---

## Risks & Gotchas

### Records → Entity refactor

- **Pitfall 1:** JPA yêu cầu no-arg constructor (proxy) + non-final class. Records (final, no setter) KHÔNG dùng được làm `@Entity`. Phải tạo class mutable mới — không thể annotate record.
- **Pitfall 2:** `equals()`/`hashCode()` của entity nên dựa trên ID (sau khi persist), không phải all-fields. Records auto-generate based on all fields → khi mutate field gây hash thay đổi → break Set/Map. Mới class phải override `equals/hashCode` theo `id` only.
- **Pitfall 3:** Service layer hiện trả `ProductEntity` (record). Sau refactor, nếu trả thẳng `ProductEntity` (mới = JPA entity), Jackson sẽ serialize `deleted` → break OpenAPI contract. PHẢI insert `ProductMapper.toDto()` boundary tại service layer trước khi return từ controller.

### Hibernate 6 UUID

- **Pitfall 4:** Nếu lỡ dùng native `uuid` Postgres column + `String id`, sẽ ném `org.postgresql.util.PSQLException: ERROR: column "id" is of type uuid but expression is of type character varying`. Cần `@JdbcTypeCode(SqlTypes.VARCHAR)` ép cast text → thêm complexity. Tránh: dùng VARCHAR(36) (Decision #1).

### Flyway multi-schema

- **Pitfall 5:** Mỗi service có table `flyway_schema_history` riêng, nằm trong schema của service. Nếu quên `default-schema` config, table này sẽ landed vào `public` schema → confusion + chéo project. Verify post-startup: `\dt user_svc.*` phải có `flyway_schema_history`.
- **Pitfall 6:** User Postgres `tmdt` cần quyền USAGE + CREATE trên 5 schemas. Nếu pre-create schemas qua init script, owner = `tmdt` → OK. Nếu để Flyway auto-create, cần explicit `GRANT CREATE ON DATABASE tmdt TO tmdt`.
- **Pitfall 7:** Flyway 10 KHÔNG chạy migrations trong sub-folders mặc định. `locations: classpath:db/migration,classpath:db/seed-dev` phải explicit — nếu placeholder typo (e.g., `db/seeds-dev`), V2 silently bị skip → seed missing, FE round-trip fail confusingly.

### FE Playwright fixtures

- **Pitfall 8:** Playwright spec hiện tại có thể hardcode UUID cũ từ `mock-data/products.ts` (e.g., spec assert `/products/abc-123-mock` URL). Sau khi seed thay UUID mới (`prod-001`...), spec assert sẽ fail. Khi xóa mock-data: phải grep Playwright specs cho hardcoded UUIDs/slugs → update hoặc query data-driven (load 1 product từ API rồi assert).
- **Pitfall 9:** FE typed module re-codegen khả năng có whitespace diff (formatter quirks) gây git noise. Capture diff trước, ignore whitespace-only changes, fail nếu schema-level (field thêm/bớt).

### docker-compose

- **Pitfall 10:** Compose v3 syntax cũ (KHÔNG có `version:` line trong file hiện tại = OK, đã dùng Compose Spec 1.x mới). Healthcheck `pg_isready -U <user>` cần env `POSTGRES_USER` set, nếu không sẽ fail vì default check user `postgres` không tồn tại.
- **Pitfall 11:** Volume `tmdt-pgdata` persist ngay cả khi compose down — schema init script (`/docker-entrypoint-initdb.d/`) CHỈ chạy lần đầu khi volume trống. Nếu sửa `01-schemas.sql` rồi `docker compose up` → script không re-run. Khi dev cần reset DB: `docker compose down -v` (drop volume).

### Test scope creep

- **Pitfall 12:** Add Testcontainers tự nhiên muốn thêm full integration suite cho 5 services × 4 entities → scope creep visible. Giữ tối thiểu: 1-2 `@DataJpaTest` per service verify migration applies + CRUD basic. Comprehensive integration suite defer (CONTEXT deferred).

---

## Open Questions for Planner

1. **Aggregator pom.xml**: Repo có parent aggregator `sources/backend/pom.xml` chưa? Nếu chưa, mỗi service add deps độc lập (5 lần duplicate version), hoặc planner tạo 1 task setup parent BOM với property `flyway.version`, `testcontainers.version`. Recommend: nếu chưa có aggregator, defer DRY (ưu tiên ship).
2. **Schema migration trong CI**: CI hiện dùng gì? Nếu có CI test step, nó cần Postgres → Testcontainers tự handle, không cần thêm CI service. Confirm với planner.
3. **Roles column normalization**: Phase 6 sẽ dùng `roles` field trong `UserEntity`. V1 schema dùng `VARCHAR(200)` comma-separated cho đơn giản — Phase 6 có thể normalize thành bảng `user_roles` riêng. Có cần làm sẵn ngay Phase 5 không? Khuyến nghị: KHÔNG, defer Phase 6 (visible-first; tránh anticipate).
4. **Seed inventory cross-service**: inventory-service seed `product_id` reference products schema khác. Vì đây là microservice boundary, không có FK. Nhưng nếu IDs lệch (seed product-service dùng `prod-001` nhưng inventory seed dùng `prod-1`), data orphan. Planner đảm bảo cả 2 V2 SQL dùng cùng namespace `prod-001..prod-010`.
5. **Mock-data deletion timing trong wave plan**: Việc xóa mock-data + verify Playwright phải là TASK CUỐI CÙNG của phase, sau khi tất cả 5 services + gateway round-trip xanh. Wave plan cần explicit ordering này.
6. **Notification-service confirmation**: D-09 lock notification giữ in-memory. Nhưng nếu notification có dependency trên `user-service` để lookup user (e.g., gửi notify cần email), giờ user-service chuyển DB → notification có gọi qua API gateway lấy user là OK, không cần JPA. Confirm trong wave 0.
7. **OpenAPI baseline capture timing**: Cần capture `/v3/api-docs` của 5 services TRƯỚC khi refactor để có baseline diff. Task này chạy ở wave 0. Nếu service hiện không spin up local được (e.g., port conflict), fallback dùng `mvn spring-boot:run` qua Maven không cần compose — confirm dev environment available.

---

## Sources

### Primary (HIGH confidence)
- [Hibernate 6 UUID Mapping (in.relation.to)](https://in.relation.to/2022/05/12/orm-uuid-mapping/) — `@JdbcTypeCode` + SqlTypes.VARCHAR vs UUID
- [Hibernate 6 User Guide §soft-delete](https://docs.jboss.org/hibernate/orm/6.5/userguide/html_single/Hibernate_User_Guide.html) — `@SQLRestriction`
- [Spring Boot issue #40893](https://github.com/spring-projects/spring-boot/issues/40893) — Flyway 10 + Postgres 16 needs `flyway-database-postgresql`
- [Flyway issue #3807](https://github.com/flyway/flyway/issues/3807) — Unsupported Database PostgreSQL 16.1 fix
- [Docker Compose — depends_on with healthcheck](https://docs.docker.com/compose/how-tos/startup-order/)
- `.planning/phases/05-database-foundation/05-CONTEXT.md` — locked decisions D-01..D-09
- `sources/backend/product-service/src/main/java/.../ProductEntity.java` — current record pattern
- `sources/backend/product-service/pom.xml` — current deps baseline

### Secondary (MEDIUM confidence)
- [Vlad Mihalcea — Hibernate UUID identifiers](https://vladmihalcea.com/hibernate-and-uuid-identifiers/)
- [Baeldung — Generate UUIDs as Primary Keys With Hibernate](https://www.baeldung.com/java-hibernate-uuid-primary-key)
- [Bell-SW — Flyway with Spring Boot guide](https://bell-sw.com/blog/how-to-use-flyway-with-spring-boot/)

### Tertiary (LOW confidence — flagged for verification)
- BCrypt hash literal `$2a$10$N9qo8uLOicke...` cho `admin123` — phải verify bằng unit test trước khi embed vào V2 SQL [ASSUMED]

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | BCrypt hash `$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy` decode đúng `admin123` | Decision #6 | Phase 6 admin login fail; verify bằng 1 unit test trước commit V2 |
| A2 | Repo chưa có `sources/backend/pom.xml` aggregator (chỉ thấy per-service pom) | Open Q #1 | Nếu sai, planner có sẵn nơi centralize Flyway/Testcontainers version property |
| A3 | Playwright spec subset (browse→cart→checkout→confirm) tồn tại từ v1.0 và pass-able | Validation §FE flow E2E | Nếu chưa có / khác structure, phase gate criteria #5 cần điều chỉnh |
| A4 | OpenAPI baseline capture sẽ produce zero-diff sau refactor (DTO records giữ shape identical) | Decision #8 | Nếu IDE/codegen tạo whitespace diff, cần script normalize; nếu schema diff thật → bug refactor |
| A5 | docker-compose hiện tại không có `version:` field (Compose Spec mới) — `condition: service_healthy` work native | Decision #9 | Nếu file format cũ v2.x → cần upgrade syntax, edge unlikely vì Boot 3.3 era |
| A6 | Services hiện không có integration tests phụ thuộc InMemoryRepository inject — refactor sang JpaRepository không break test count quá lớn | Risks §test scope | Nếu có nhiều test mock InMemoryXxxRepository, planner cần bigger task migrate test mocks |

---

## Project Constraints (from CLAUDE.md / memory)

- **Vietnamese** cho prose/docs/commits, EN cho identifiers/SQL/YAML keys/commit prefixes — phase research này tuân thủ.
- **Visible-first priority**: defer hardening/security/observability invisible. Ảnh hưởng: chọn minimal test infra (Testcontainers chỉ vì nó NGĂN bug visible cho FE round-trip — không thêm comprehensive integration suite).
- **Project nature: GSD workflow experimentation** — KHÔNG reference PTIT/HTPT student assignment trong docs phase này; package name `com.ptit.htpt.*` giữ nguyên (legacy code, không rename).
- **Phase 6 deps**: Phase 5 PHẢI ship admin user + roles column để Phase 6 có thể test login flow ngay không cần seed thêm. Đã cover trong V2 user-service.

---

## Metadata

**Confidence breakdown:**
- Standard stack (Boot 3.3.2 + JPA + Flyway 10 + Testcontainers): HIGH — verified web + Spring Boot BOM
- Architecture (multi-schema, Entity/DTO split, soft-delete via `@SQLRestriction`): HIGH — confirmed Hibernate 6 docs
- BCrypt hash literal: LOW — needs unit test verify before commit
- FE Playwright impact: MEDIUM — depends on existing spec content (not deeply inspected)
- OpenAPI zero-diff guarantee: MEDIUM — depends on disciplined DTO boundary, có risk human error

**Research date:** 2026-04-26
**Valid until:** 2026-05-26 (Spring Boot ecosystem fairly stable; Flyway 10 minor bumps OK)

---

## RESEARCH COMPLETE
