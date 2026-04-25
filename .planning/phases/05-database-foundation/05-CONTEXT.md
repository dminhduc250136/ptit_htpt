# Phase 5: Database Foundation - Context

**Gathered:** 2026-04-26
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 5 đưa Postgres thật vào stack: thêm Postgres container vào docker-compose, add JPA + Flyway dependency vào 5 services có entity (user/product/order/payment/inventory — notification giữ in-memory), refactor in-memory repos thành JPA repositories với migrations Flyway-managed, seed dev data minimal-realistic qua Flyway V2 profile-gated, và verify gateway round-trip qua FE trả seeded data thật từ DB. Sau verify: xóa `sources/frontend/src/mock-data/`.

**Trong scope:** docker-compose Postgres service + healthcheck + volume; JPA + Flyway dependency 5 services; application.yml datasource; V1 schema migration; V2 dev seed; refactor InMemoryXxxRepository → interface JpaRepository; FE flow chính (browse → cart → checkout → confirmation) PASS với data thật; xóa mock-data folder.

**Ngoài scope:** Auth flow thật (Phase 6); admin pages migrate (Phase 7); ProductEntity.stock + OrderItemEntity per-row + shippingAddress (Phase 8); production-grade DB ops (replication / backup automation / connection pooling tuning); category slug persist (deferred D10 từ v1.0 audit).

</domain>

<decisions>
## Implementation Decisions

### DB Topology
- **D-01**: 1 Postgres instance, 1 database `tmdt`, multi-schema — mỗi service 1 schema dedicated:
  - `user_svc`, `product_svc`, `order_svc`, `payment_svc`, `inventory_svc`
  - Mỗi service set `spring.datasource.hikari.schema` (hoặc `currentSchema` qua JDBC URL param `?currentSchema=user_svc`)
  - Flyway mỗi service quản lý schema riêng (`spring.flyway.schemas: <schema>` + `default-schema`)
  - **Why**: isolation tốt, backup dễ, ít connection sprawl hơn multi-DB; tuân thủ microservice boundary mà không over-engineer.
- **D-02**: Postgres image = `postgres:16-alpine`. Volume Docker named `tmdt-pgdata` cho persistence. Healthcheck dùng `pg_isready -U postgres -d tmdt`.

### Entity Refactor Strategy
- **D-03**: Tách rõ Entity (persistence) vs DTO (wire format).
  - Class mới `XxxEntity` mutable + `@Entity @Table(schema="<svc>") @Id` cho persistence
  - Record cũ đổi tên thành `XxxDto` (hoặc `XxxResponse`) cho payload API
  - Service layer chịu trách nhiệm map Entity ↔ DTO
  - **Why**: Tránh entity leak lên wire (tránh expose fields như `deleted`/`passwordHash` qua Jackson); tách trách nhiệm rõ; chuẩn bị tốt cho Phase 8 thêm fields persistence-only.
- **D-04**: ID giữ nguyên `String` UUID — KHÔNG đổi sang Long.
  - **Why**: API contract Phase 1 đã config String UUID; FE typed modules + Playwright E2E đều hardcode dạng string. Đổi Long sẽ break FE flow chính (vi phạm success criteria #5 "FE flow chính vẫn PASS"). User explicit confirm: "nếu api phase 1 đã config là string thì cứ giữ".
  - **How**: Entity dùng `@Id @Column(length=36) private String id;` + factory `UUID.randomUUID().toString()` (giống record cũ). Postgres column dùng VARCHAR(36) hoặc native `uuid` type — **researcher cần xác nhận** type tối ưu kèm index strategy.

### Seed Mechanism
- **D-05**: Seed qua Flyway `V2__seed_dev_data.sql` (per-service), minimal realistic — KHÔNG sao chép 100% mock-data FE.
  - Dataset: ~10 products thực tế trong domain TMĐT, 5 categories (điện tử, thời trang, gia dụng, sách, mỹ phẩm hoặc tương tự), 1 admin user (`admin/admin123` BCrypt hash hardcoded trong SQL), 2-3 orders demo cho user-service.
  - **Why**: User explicit: "DB chuẩn, không cần match 100% mock-data FE". Tránh effort parse TS → SQL; ưu tiên schema sạch + realistic data hơn UX clone.
  - **Implication FE**: Có thể có UX surprise nhỏ (product names khác mock) — chấp nhận được, FE flow chính vẫn pass logic.
- **D-06**: Seed gated bằng Spring profile `dev`.
  - Layout file: `src/main/resources/db/migration/V1__init_schema.sql` (luôn chạy) + `src/main/resources/db/seed-dev/V2__seed_dev_data.sql`
  - Config: `spring.flyway.locations=classpath:db/migration` mặc định; profile `dev` override thành `classpath:db/migration,classpath:db/seed-dev`
  - docker-compose set `SPRING_PROFILES_ACTIVE=dev` cho 5 services
  - **Why**: Schema separate khỏi seed — nếu sau này deploy prod-like chỉ cần switch profile, không phải xóa migration.

### Mock-Data Deletion + FE Migration
- **D-07**: Xóa `sources/frontend/src/mock-data/` ở cuối phase, sau khi toàn bộ round-trip xanh.
  - Sequence: refactor 5 services → docker compose up green → verify GET /api/products qua gateway trả seeded data → grep FE imports `mock-data/` → gỡ tất cả imports → xóa folder → re-run FE flow + Playwright (subset chính: browse → cart → checkout → confirmation).
  - **Why**: An toàn nhất, tránh break FE midway. Aggressive deletion (xóa trước rồi fix) risky vì có thể có dynamic imports ẩn.
- **D-08**: FE contract KHÔNG thay đổi (hệ quả D-04).
  - `id: string` giữ nguyên trên FE typed modules.
  - Springdoc OpenAPI codegen pipeline regenerate sau khi backend refactor — verify diff zero (chỉ check, không cần re-publish nếu types không đổi).
  - Nếu Playwright E2E phụ thuộc UUID specific từ mock data → phải update test fixtures match seed mới (researcher + planner cần list test impact).

### Notification-Service
- **D-09**: notification-service KHÔNG add JPA/Flyway/Postgres trong phase này (xác nhận theo REQ DB-02 "notification có thể giữ in-memory nếu chỉ dispatch"). Giảm scope, tránh effort không cần thiết.

### Claude's Discretion
- Chi tiết JPA mapping (column types, indexes, constraints) — researcher + planner quyết theo best practice.
- Flyway file naming pattern chi tiết (V1.0.0__ vs V1__) — chọn V1__/V2__ đơn giản trừ khi có conflict.
- Hikari pool size + JDBC URL params — defaults Spring Boot 3.3 đủ cho dev; tuning defer.
- Test refactor approach (mockJpaRepository vs @DataJpaTest vs Testcontainers) — planner quyết, ưu tiên đơn giản trừ khi REQ ép.
- Loại column UUID Postgres (`uuid` native vs VARCHAR(36)) — researcher xác minh, recommend native uuid nếu Hibernate 6 + Spring Boot 3.3 hỗ trợ smooth.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Roadmap & Requirements
- `.planning/ROADMAP.md` §"Phase 5: Database Foundation" — goal, success criteria, REQ mapping
- `.planning/REQUIREMENTS.md` §"C0. Database Foundation (DB-01..06)" — 6 atomic requirements với behavioral spec
- `.planning/REQUIREMENTS.md` §"Audit Finding: DB Layer Hiện Trạng" — bối cảnh tại sao C0 block C1/C2/C3

### Codebase Maps
- `.planning/codebase/STACK.md` — Spring Boot 3.3.2, Java 17, Maven multi-module, 7 services
- `.planning/codebase/STRUCTURE.md` — service layout `sources/backend/<svc>/src/main/java/com/ptit/htpt/<svc>/`
- `.planning/codebase/ARCHITECTURE.md` — gateway routing, service boundaries
- `.planning/codebase/CONVENTIONS.md` — code style, package layout (api/domain/repository/service/web)

### Existing Code (must read before refactor)
- `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/domain/ProductEntity.java` — record pattern hiện tại (mẫu cho 5 services)
- `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/repository/InMemoryProductRepository.java` — repository pattern cần refactor
- `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductCrudService.java` — service layer pattern
- `sources/backend/<other 4 services>/repository/InMemoryXxxRepository.java` — tất cả 4 repos còn lại
- `sources/backend/product-service/src/main/resources/application.yml` — application.yml hiện tại (chưa có datasource)
- `docker-compose.yml` — file root, cần thêm Postgres service + depends_on healthcheck
- `sources/backend/<svc>/pom.xml` — cần add 3 dependencies (data-jpa, postgresql, flyway-core)

### Frontend Impact
- `sources/frontend/src/mock-data/products.ts` + `orders.ts` — REFERENCE only cho seed shape (không clone 100%); phải xóa cuối phase
- Frontend typed API modules (Springdoc OpenAPI codegen output) — verify zero diff sau refactor backend

### Project Memory (auto-loaded)
- `~/.claude/projects/.../memory/feedback_priority.md` — visible-first ưu tiên, defer hardening
- `~/.claude/projects/.../memory/feedback_language.md` — Vietnamese cho chat/docs/commits

### Prior Milestone Context
- `.planning/milestones/v1.0-phases/02-crud-completeness-across-services/02-CONTEXT.md` — pattern CRUD đã thiết lập trên in-memory layer (cần preserve API contract khi migrate JPA)
- `.planning/v1.0-MILESTONE-AUDIT.md` — audit phát hiện missing DB layer (root cause cho phase 5)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **ApiErrorResponse + GlobalExceptionHandler**: 5 services đã có envelope chuẩn — JPA exceptions (DataIntegrityViolation, EntityNotFound) cần map vào envelope hiện tại, không tạo handler mới.
- **TraceIdFilter**: đã có per-service — JPA query logging có thể leverage MDC nếu cần (defer).
- **Springdoc Swagger**: đã có pipeline OpenAPI → FE typed modules. Sau refactor entity → DTO, verify OpenAPI schema không đổi (DTO field shape giữ nguyên).
- **Service layer (ProductCrudService, etc.)**: signature methods giữ nguyên sau refactor — chỉ đổi repository implementation (in-memory → JPA). Service code phần lớn không động.
- **Maven multi-module**: parent pom có thể centralize JPA + Postgres + Flyway version để 5 services consistent (researcher cân nhắc).

### Established Patterns
- **Package layout**: `api/` (envelope) / `domain/` (entity records) / `repository/` (data access) / `service/` (business logic) / `web/` (controllers + filters). Refactor giữ pattern này — `domain/` chứa @Entity classes mới + DTO records, `repository/` chứa interface JpaRepository.
- **ID generation**: UUID String factory pattern (`UUID.randomUUID().toString()` trong record `create()` static method). Giữ pattern này trong Entity factory.
- **Soft delete**: ProductEntity có `boolean deleted` + `softDelete()` method. JPA Entity giữ field này, repositories có thể dùng `@SQLRestriction` hoặc explicit `findByDeletedFalse()` — planner quyết.
- **In-memory repo public API**: methods `findAllX()`, `findXById(String)`, `saveX(X)` — JpaRepository extends interface có sẵn `findAll()`, `findById(String)`, `save(X)` — gần tương đương, service layer change tối thiểu.

### Integration Points
- **docker-compose.yml**: thêm `postgres` service, 5 services `depends_on: postgres: condition: service_healthy`, env vars `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` + `SPRING_PROFILES_ACTIVE=dev`.
- **application.yml** (5 services): thêm `spring.datasource.*` + `spring.jpa.hibernate.ddl-auto: validate` + `spring.flyway.*` (locations, schemas, default-schema, baseline-on-migrate=false vì greenfield).
- **pom.xml** (5 services): add `spring-boot-starter-data-jpa`, `org.postgresql:postgresql`, `org.flywaydb:flyway-core` (+ `flyway-database-postgresql` từ Flyway 10+ để support Postgres 16). Spring Boot 3.3.2 → Flyway 10.x.
- **Frontend**: tìm tất cả `import ... from '@/mock-data/...'` → gỡ → verify FE build green → xóa folder.

### Constraints / Gotchas
- **Records → Entity**: JPA proxy yêu cầu no-arg constructor và non-final class — records không dùng được. Phải tạo class mới mutable.
- **Hibernate 6 + Postgres uuid type**: nếu chọn native `uuid` cần đảm bảo driver mapping (`@JdbcTypeCode(SqlTypes.VARCHAR)` hoặc default mapping). Researcher xác minh.
- **Multi-schema Flyway**: Mỗi service phải set `spring.flyway.schemas: <schema_name>` + `default-schema: <schema_name>` để tránh đụng schema khác. Schemas phải pre-created (Postgres init script tạo 5 schemas) hoặc Flyway auto-create với quyền `CREATE SCHEMA`.
- **BCrypt seed admin**: hardcode hash trong SQL (vd `$2a$10$...` cho `admin123`) — researcher generate hash sẵn và embed.
- **Test impact**: existing unit tests có thể mock InMemoryRepository — sau refactor chuyển sang JpaRepository interface, tests dùng `@DataJpaTest` + H2/Testcontainers. Planner cân scope test refactor (visible-first ưu tiên — có thể giữ minimal test passing, defer comprehensive integration test).

</code_context>

<specifics>
## Specific Ideas

- **Schema names**: theo convention `<service-prefix>_svc` → `user_svc`, `product_svc`, `order_svc`, `payment_svc`, `inventory_svc`. Đơn giản và rõ.
- **Seed admin**: username `admin`, password `admin123` (BCrypt), role `ADMIN`. Hardcode trong V2__seed_dev_data.sql user-service.
- **Volume name**: `tmdt-pgdata` (project-prefix để không đụng các project khác trên cùng Docker host).
- **Database name**: `tmdt`.
- **Postgres user/password dev**: `tmdt` / `tmdt` (hoặc tương tự — minimal, dev-only, defer security tới production work).

</specifics>

<deferred>
## Deferred Ideas

- **CategoryEntity.slug persist** — đang dùng slugify fallback (deferred D10 v1.0 audit). Không trong scope phase 5.
- **Connection pool tuning** — Hikari defaults đủ cho dev; tuning thuộc v1.2 hardening.
- **Centralized DB observability** (slow query log, connection metrics) — defer (visible-first).
- **Read replicas / sharding** — không cần, scope MVP.
- **JPA test infrastructure (Testcontainers)** — nếu planner thấy minimal `@DataJpaTest` + H2 đủ cover thì không cần Testcontainers ngay; defer toàn bộ comprehensive test infra sang phase chuyên về testing.
- **Notification-service migrate sang DB** — nếu sau này notification cần persist (vd outbox pattern), tạo phase riêng. Hiện tại in-memory đủ.
- **Seed data clone từ FE mock-data** — user explicit từ chối; nếu sau này QA yêu cầu fixtures cố định, tạo task riêng.
- **Field encryption (PII trong UserEntity)** — defer security work sang v1.2.

</deferred>

---

*Phase: 05-database-foundation*
*Context gathered: 2026-04-26*
