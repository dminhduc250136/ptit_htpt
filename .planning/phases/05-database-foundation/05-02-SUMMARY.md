---
phase: 05-database-foundation
plan: 02
subsystem: database
tags: [docker-compose, postgres, healthcheck, init-script, multi-schema]

requires:
  - phase: 05-database-foundation
    plan: 01
    provides: 5 OpenAPI baselines + verified BCrypt hash (gating Wave 3 seed)
provides:
  - Postgres 16-alpine container declared trong docker-compose với healthcheck pg_isready
  - 5 schemas (user_svc, product_svc, order_svc, payment_svc, inventory_svc) pre-created qua init script
  - Volume tmdt-pgdata persistent
  - 5 services backend wired depends_on postgres condition: service_healthy + DB_* env vars
affects:
  - 05-03..05-07 (Wave 3 — 5 services refactor JPA/Flyway sẽ dùng postgres healthy + schemas pre-created)
  - 05-08 (smoke test toàn stack)

tech-stack:
  added:
    - "postgres:16-alpine (Docker image)"
  patterns:
    - "Init script qua /docker-entrypoint-initdb.d/ mount để pre-create multi-schema"
    - "Healthcheck pg_isready -U tmdt -d tmdt với interval 5s, retries 10"
    - "depends_on condition: service_healthy để tránh race với Flyway migration"
    - "Named volume tmdt-pgdata cho persistence cross-restart"

key-files:
  created:
    - db/init/01-schemas.sql
  modified:
    - docker-compose.yml

key-decisions:
  - "Postgres service block đặt ĐẦU services: trước api-gateway — convention infra-first"
  - "Notification-service KHÔNG add depends_on postgres (D-09 in-memory)"
  - "API gateway + frontend KHÔNG động — vẫn giữ depends_on backend services hiện có"
  - "Port 5432:5432 expose để dev tool host (psql/DBeaver) có thể connect trực tiếp"
  - "Verify cycle bao gồm volume persistence test (stop/start + grep schema) — đảm bảo data survive restart trước khi Wave 3 chạy migration"

requirements-completed: [DB-01]

duration: ~10min
completed: 2026-04-26
---

# Phase 5 Plan 02: Postgres Infrastructure Summary

**Postgres 16-alpine container wired vào docker-compose với 5 schemas pre-created + healthcheck PASS — Wave 3 unblocked**

## Performance

- **Duration:** ~10 min
- **Started:** 2026-04-25T17:57:57Z
- **Completed:** 2026-04-26 (smoke verify xanh)
- **Tasks:** 3 (Task 2.1 init script, Task 2.2 docker-compose modify, Task 2.3 smoke test — auto-approved trong auto mode)
- **Files modified:** 2 (1 NEW + 1 MODIFY)

## Accomplishments

- Tạo `db/init/01-schemas.sql` chứa đúng 5 dòng `CREATE SCHEMA IF NOT EXISTS` cho `user_svc`, `product_svc`, `order_svc`, `payment_svc`, `inventory_svc`.
- Modify `docker-compose.yml`:
  - Thêm `postgres` service (image `postgres:16-alpine`, env `POSTGRES_DB/USER/PASSWORD=tmdt`, healthcheck `pg_isready`, mount `./db/init:/docker-entrypoint-initdb.d:ro`, volume `tmdt-pgdata`, port 5432).
  - Wire 5 services backend (user/product/order/payment/inventory) `depends_on: postgres: condition: service_healthy` + 6 env vars (`SPRING_PROFILES_ACTIVE=dev`, `DB_HOST=postgres`, `DB_PORT=5432`, `DB_NAME=tmdt`, `DB_USER=tmdt`, `DB_PASSWORD=tmdt`).
  - Notification-service KHÔNG động (D-09).
  - API gateway + frontend KHÔNG động.
  - `volumes:` block thêm `tmdt-pgdata`.
- Smoke test PASS:
  - `docker compose up -d postgres` → container healthy trong ~6s (< 30s success criterion).
  - `docker compose exec postgres psql -U tmdt -d tmdt -c "\dn"` liệt kê đủ 5 schemas + `public`.
  - Volume persistence test: `stop` → `start` → schema `user_svc` vẫn còn → PASS.
  - `docker compose down` (no `-v`) → volume `agent-a60a38734b1d51504_tmdt-pgdata` còn lại cho Wave 3.

## Task Commits

1. **Task 2.1: Tạo db/init/01-schemas.sql** — `853a629` (feat)
2. **Task 2.2: Modify docker-compose.yml — postgres service + 5 backend wiring** — `ed7fd4d` (feat)
3. **Task 2.3: Smoke test Postgres + healthcheck + 5 schemas** — checkpoint auto-approved (auto mode), evidence trong section "Smoke Test Evidence" bên dưới

## Files Created/Modified

- `db/init/01-schemas.sql` — NEW, 5 dòng CREATE SCHEMA
- `docker-compose.yml` — MODIFIED, +71 dòng (postgres service block + 5 services env+depends_on + volumes block)

## Decisions Made

- **Postgres service đặt đầu `services:`** — trước api-gateway để rõ infra-first dependency order (visual hint cho người đọc compose file).
- **Init script tên `01-schemas.sql`** — số prefix cho phép thêm `02-extensions.sql` (vd pgcrypto) sau này nếu cần. Postgres entrypoint chạy theo alphabetical order.
- **Healthcheck retries=10 interval=5s** — tổng 50s budget, dư cho cold start khoảng 5-10s thực tế (RESEARCH §Decision #9 default).
- **Port 5432 expose ra host** — dev tool có thể connect trực tiếp (dev convenience). Production sẽ override compose.

## Smoke Test Evidence

```
$ docker compose ps postgres
NAME                                 STATUS                   PORTS
agent-a60a38734b1d51504-postgres-1   Up 6 seconds (healthy)   0.0.0.0:5432->5432/tcp

$ docker compose exec postgres psql -U tmdt -d tmdt -c "\dn"
          List of schemas
     Name      |       Owner
---------------+-------------------
 inventory_svc | tmdt
 order_svc     | tmdt
 payment_svc   | tmdt
 product_svc   | tmdt
 public        | pg_database_owner
 user_svc      | tmdt
(6 rows)

$ docker compose stop postgres && docker compose start postgres
$ docker compose exec postgres psql -U tmdt -d tmdt -c "\dn" | grep user_svc
 user_svc      | tmdt   ← persistence OK

$ docker compose down  (KHÔNG -v)
$ docker volume ls | grep tmdt-pgdata
local  agent-a60a38734b1d51504_tmdt-pgdata   ← volume kept
```

## Deviations from Plan

None - plan thực thi chính xác theo PATTERNS.md §H + §I + Task spec.

## Authentication Gates

None.

## Issues Encountered

- Hook `PreToolUse:Write` cảnh báo về `docker-compose.yml` đã được modify mặc dù file đã được Read trước đó (full output đã hiện đủ 48 dòng). Runtime đã accept edit ("has been updated successfully") — không block. Continue thực thi.

## TDD Gate Compliance

Plan này KHÔNG gate `type: tdd` (plan frontmatter `type: execute`). Không cần RED/GREEN cycle.

## User Setup Required

None — Docker daemon đã sẵn, image `postgres:16-alpine` auto-pulled.

## Next Phase Readiness

- **Wave 3 (Plans 05-03 đến 05-07) sẵn sàng:** 5 services có thể declare datasource URL `jdbc:postgresql://postgres:5432/tmdt?currentSchema=<svc>_svc` + Flyway sẽ migrate vào schema đã pre-created. Khi 5 services start, Postgres đã healthy nên không có race condition.
- **Volume `tmdt-pgdata` persist:** Wave 3 có thể `compose down/up` nhiều lần để test migration mà không mất schema (nếu cần reset, dùng `down -v`).
- **Mount `./db/init:/docker-entrypoint-initdb.d:ro`:** Init script chỉ chạy lần đầu volume init. Nếu Wave 3 cần thêm schema, hoặc dùng Flyway hoặc reset volume.

## Self-Check: PASSED

- File `db/init/01-schemas.sql` — FOUND (5 CREATE SCHEMA lines)
- File `docker-compose.yml` — FOUND (postgres service block + 5 backend wired + volumes block)
- Commit `853a629` — FOUND (Task 2.1)
- Commit `ed7fd4d` — FOUND (Task 2.2)
- `docker compose config --quiet` — exit 0 (YAML valid)
- `docker compose up -d postgres` → `(healthy)` in ~6s
- 5 schemas present (`\dn` evidence above)
- Volume persistence test PASS

---
*Phase: 05-database-foundation*
*Completed: 2026-04-26*
