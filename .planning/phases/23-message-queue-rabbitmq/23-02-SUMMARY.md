---
phase: 23
plan: 02
subsystem: notification-service
tags: [notification-service, persistence-bootstrap, jpa, flyway, schema-init, amqp, rabbitmq]
requires:
  - rabbitmq-broker-container
  - notification_svc-schema
provides:
  - notification-service-persistence-stack
  - notification_svc-dispatch_log-table
  - notification_svc-processed_events-table
  - spring-rabbitmq-config-3-services
  - mq-04-requirement-bootstrap
affects:
  - sources/backend/notification-service/pom.xml
  - sources/backend/notification-service/src/main/resources/application.yml
  - sources/backend/notification-service/src/main/resources/db/migration/V1__init_schema.sql
  - sources/backend/notification-service/src/test/resources/application-test.yml
  - sources/backend/order-service/pom.xml
  - sources/backend/order-service/src/main/resources/application.yml
  - sources/backend/inventory-service/pom.xml
  - sources/backend/inventory-service/src/main/resources/application.yml
tech_stack_added:
  - spring-boot-starter-data-jpa (notification-service)
  - spring-boot-starter-amqp (3 services)
  - flyway-core + flyway-database-postgresql (notification-service)
  - postgresql JDBC runtime (notification-service)
  - spring-rabbit-test + testcontainers:rabbitmq (3 services, test scope)
patterns_added:
  - amqp-publisher-confirm-correlated (D-04)
  - amqp-listener-retry-exp-backoff (D-07: 3 attempts, 1s→2s→4s, max 10s)
  - jpa-ddl-validate + flyway-default-schema (consistent với 4 service khác)
  - testcontainers-service-connection-ready (Wave 3 IT placeholder)
key_files_created:
  - sources/backend/notification-service/src/main/resources/db/migration/V1__init_schema.sql
  - sources/backend/notification-service/src/test/resources/application-test.yml
  - .planning/phases/23-message-queue-rabbitmq/23-02-SUMMARY.md
key_files_modified:
  - sources/backend/notification-service/pom.xml
  - sources/backend/notification-service/src/main/resources/application.yml
  - sources/backend/order-service/pom.xml
  - sources/backend/order-service/src/main/resources/application.yml
  - sources/backend/inventory-service/pom.xml
  - sources/backend/inventory-service/src/main/resources/application.yml
decisions:
  - "notification-service pom thừa hưởng nguyên block persistence từ inventory-service (parent BOM 3.3.2 auto-manage versions) — đảm bảo stack đồng nhất, dễ maintain"
  - "Spring profile dev seed-dev location áp dụng theo pattern 3 service khác — Wave 2 sẽ thêm V102 seed nếu cần demo data"
  - "ddl-auto=validate (KHÔNG update/create) — Flyway là single source-of-truth schema, JPA validate match (mitigate T-23-07 tampering threat)"
  - "spring.rabbitmq block giống hệt nhau ở 3 service (D-04 publisher confirm correlated + D-07 listener retry 3×exp backoff) — copy-paste có chủ ý cho consistency, KHÔNG tách shared yml fragment vì Spring Boot không support import yml cross-service"
  - "test/resources/application-test.yml override retry initial-interval=100ms / max-interval=500ms — IT chạy gọn (3 retry × 100ms thay vì 7s tổng) mà vẫn validate được retry flow"
  - "V1 migration idempotent (CREATE SCHEMA IF NOT EXISTS + CREATE TABLE IF NOT EXISTS) — safe co-exist với schema từ db/init/01-schemas.sql ở Plan 23-01 mà KHÔNG conflict với flyway_schema_history baseline"
metrics:
  duration: 2min
  completed_date: 2026-05-20
  tasks: 2
  files_changed: 8
---

# Phase 23 Plan 02: notification-service Persistence Bootstrap + RabbitMQ Config 3 Service Summary

Bootstrap notification-service từ stub (chỉ web + actuator + springdoc) lên stack persistence đầy đủ (JPA + Postgres + Flyway + AMQP + Testcontainers) — đáp ứng MQ-04 + chuẩn bị Wave 2 consumer. Đồng thời thêm `spring.rabbitmq` block (D-04 publisher confirm + D-07 retry) vào order-service và inventory-service để 3 service producer/consumer config đồng nhất.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Bootstrap 3 pom.xml — JPA + AMQP + Testcontainers stack | `cb0e90a` | notification/order/inventory pom.xml |
| 2 | notification-service yml + V1 migration + rabbitmq block 3 service | `6ac96c1` | 5 files (yml + V1 sql + test yml) |

## pom.xml Diff Summary (3 files)

**notification-service/pom.xml — thêm 10 dependency:**
- Persistence stack (5): `spring-boot-starter-data-jpa`, `postgresql` (runtime), `flyway-core`, `flyway-database-postgresql`
- Messaging (1): `spring-boot-starter-amqp`
- Test scope (5): `spring-boot-starter-test`, `testcontainers:postgresql`, `testcontainers:junit-jupiter`, `testcontainers:rabbitmq`, `spring-rabbit-test`

**order-service/pom.xml — thêm 3 dependency (đã có persistence stack từ Phase 5):**
- `spring-boot-starter-amqp` (producer)
- `testcontainers:rabbitmq`, `spring-rabbit-test` (test)

**inventory-service/pom.xml — thêm 3 dependency (đã có persistence stack từ Phase 5):**
- `spring-boot-starter-amqp` (consumer)
- `testcontainers:rabbitmq`, `spring-rabbit-test` (test)

## application.yml Diff Summary (3 files)

**notification-service/application.yml — REPLACE toàn bộ:**
- `spring.datasource`: url với `currentSchema=notification_svc`, env-var fallback DB_HOST/PORT/NAME/USER/PASSWORD
- `spring.jpa`: ddl-auto=validate, default_schema=notification_svc, format_sql=true, open-in-view=false
- `spring.flyway`: enabled, schemas=notification_svc, default-schema=notification_svc, locations=classpath:db/migration, baseline-on-migrate=false
- `spring.rabbitmq`: publisher-confirm-type=correlated + publisher-returns=true + listener.simple (acknowledge-mode=auto, prefetch=10, default-requeue-rejected=false, retry enabled max-attempts=3 initial-interval=1000 multiplier=2.0 max-interval=10000)
- Giữ server.port=8080, management actuator, springdoc paths
- Profile `dev`: thêm `db/seed-dev` location

**order-service + inventory-service/application.yml — thêm `spring.rabbitmq` block:**
- Cùng nội dung như notification (D-04 confirm + D-07 retry config) — chèn ngay sau `flyway` block, trước `server`
- KHÔNG đụng datasource/jpa/flyway/jwt existing

## V1__init_schema.sql DDL

```sql
CREATE SCHEMA IF NOT EXISTS notification_svc;

-- dispatch_log (D-14): log mỗi lần consumer xử lý OrderPlaced
CREATE TABLE IF NOT EXISTS notification_svc.dispatch_log (
  id VARCHAR(36) PRIMARY KEY,
  event_id VARCHAR(36) NOT NULL,
  recipient_user_id VARCHAR(36) NOT NULL,
  channel VARCHAR(16) NOT NULL,
  subject VARCHAR(255) NOT NULL,
  body TEXT NOT NULL,
  status VARCHAR(16) NOT NULL,
  sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_dispatch_log_event ON notification_svc.dispatch_log(event_id);
CREATE INDEX IF NOT EXISTS idx_dispatch_log_user ON notification_svc.dispatch_log(recipient_user_id);

-- processed_events (D-06): idempotency table, PK event_id atomic INSERT ON CONFLICT
CREATE TABLE IF NOT EXISTS notification_svc.processed_events (
  event_id VARCHAR(36) PRIMARY KEY,
  event_type VARCHAR(64) NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_processed_events_type ON notification_svc.processed_events(event_type);
```

**3 tables sau migration:** `dispatch_log`, `processed_events`, `flyway_schema_history` (auto-tạo bởi Flyway).

## application-test.yml (Wave 3 IT prep)

```yaml
spring:
  jpa: { hibernate: { ddl-auto: validate }, properties: { hibernate: { default_schema: notification_svc } } }
  flyway: { enabled: true, schemas: notification_svc, default-schema: notification_svc, locations: classpath:db/migration }
  rabbitmq:
    listener:
      simple:
        retry:
          initial-interval: 100   # 1s production → 100ms IT
          max-interval: 500       # 10s production → 500ms IT
```

Datasource + rabbitmq.host/port để Testcontainers `@ServiceConnection` inject runtime (chưa cần khai ở đây).

## Acceptance Criteria

**Task 1:**
- [x] `grep -c 'spring-boot-starter-data-jpa' notification/pom.xml` = 1
- [x] `grep -c 'spring-boot-starter-amqp' notification/pom.xml` = 1
- [x] `grep -c 'spring-boot-starter-amqp' order/pom.xml` = 1
- [x] `grep -c 'spring-boot-starter-amqp' inventory/pom.xml` = 1 (verified: 3 pom.xml × 1 = 3 occurrences tổng)
- [x] `grep -c 'org.testcontainers' notification/pom.xml` = 3 (postgresql + junit-jupiter + rabbitmq)
- [x] `grep -c 'flyway-database-postgresql' notification/pom.xml` = 1
- [x] XML well-formed (Edit không báo lỗi syntax; mvn verify defer cho Wave 3)

**Task 2:**
- [x] V1__init_schema.sql tồn tại
- [x] application-test.yml tồn tại
- [x] `grep 'default_schema: notification_svc' notification/application.yml` = 1
- [x] `grep 'publisher-confirm-type: correlated'` 3 service application.yml = 3 total (verified)
- [x] `grep -c 'CREATE TABLE IF NOT EXISTS notification_svc' V1__init_schema.sql` = 2 (dispatch_log + processed_events)
- [x] `grep 'on-profile: dev' notification/application.yml` = 1
- [x] V1 sql có comment tiếng Việt "Phase 23"

## Deviations from Plan

**None** — plan executed exactly as written. Cả 2 task theo action block chính xác, không có Rule 1/2/3 deviation phát sinh. Plan đã rất chi tiết (full YAML/SQL content có sẵn) nên thực thi straightforward.

## Threat Mitigations Applied

- **T-23-07 Tampering (Flyway baseline mismatch):** `ddl-auto: validate` + `baseline-on-migrate: false` — chỉ Flyway tạo schema, JPA chỉ validate match. Khi notification-service start với schema cũ không có 2 bảng, Flyway sẽ tạo; nếu schema có sẵn từ db/init (Plan 23-01) đã CREATE SCHEMA, V1 chạy IF NOT EXISTS sẽ tạo 2 bảng. JPA validate match.
- **T-23-06 Information Disclosure (dispatch_log body):** Schema isolation qua `currentSchema=notification_svc` + JPA default_schema=notification_svc — service khác kết nối với currentSchema riêng sẽ không "thấy" được table này qua bare relation name.
- **T-23-08 DoS (dispatch_log unbounded):** Accept disposition — defer rotation/archival sang phase ops (đã document).

## Verification (Manual — defer khi Docker runtime available)

- [ ] `cd sources/backend/notification-service && mvn -q compile` succeed (defer: Maven CLI chưa khả dụng trên Windows env này)
- [ ] `docker compose up notification-service`: log "Successfully applied 1 migration to schema \"notification_svc\""
- [ ] `psql -h localhost -U tmdt -d tmdt -c "\dt notification_svc.*"` liệt kê 3 bảng (dispatch_log, processed_events, flyway_schema_history)
- [ ] notification-service start không ERROR; Spring banner xuất hiện
- [ ] 3 service application.yml đều có `spring.rabbitmq` block đầy đủ (✅ verified bằng grep)

Static config verification đã pass qua grep counts; runtime check defer cho Wave 2/3 khi consumer code có và Docker khả dụng.

## Self-Check: PASSED

- FOUND: sources/backend/notification-service/pom.xml (modified — 10 deps added)
- FOUND: sources/backend/notification-service/src/main/resources/application.yml (replaced)
- FOUND: sources/backend/notification-service/src/main/resources/db/migration/V1__init_schema.sql (new)
- FOUND: sources/backend/notification-service/src/test/resources/application-test.yml (new)
- FOUND: sources/backend/order-service/pom.xml (modified — 3 deps added)
- FOUND: sources/backend/order-service/src/main/resources/application.yml (rabbitmq block added)
- FOUND: sources/backend/inventory-service/pom.xml (modified — 3 deps added)
- FOUND: sources/backend/inventory-service/src/main/resources/application.yml (rabbitmq block added)
- FOUND: cb0e90a (Task 1 commit)
- FOUND: 6ac96c1 (Task 2 commit)
- FOUND: .planning/phases/23-message-queue-rabbitmq/23-02-SUMMARY.md (file này)
