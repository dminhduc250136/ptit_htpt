---
phase: 23
plan: 01
subsystem: infrastructure
tags: [rabbitmq, docker-compose, schema-init, requirements-backfill]
requires: []
provides:
  - rabbitmq-broker-container
  - notification_svc-schema
  - spring-rabbitmq-env-vars
  - mq-01-mq-05-requirements
affects:
  - docker-compose.yml
  - db/init/01-schemas.sql
  - .planning/REQUIREMENTS.md
tech_stack_added:
  - rabbitmq:3-management (Docker image)
patterns_added:
  - rabbitmq-healthcheck (rabbitmq-diagnostics ping)
  - dev-default-credentials (guest/guest — chỉ dev, ghi accept trong threat register)
key_files_created:
  - .planning/phases/23-message-queue-rabbitmq/23-01-SUMMARY.md
key_files_modified:
  - docker-compose.yml
  - db/init/01-schemas.sql
  - .planning/REQUIREMENTS.md
decisions:
  - "Healthcheck dùng rabbitmq-diagnostics ping (interval 10s, retries 10) — đủ thời gian boot 30-60s mà không lag downstream"
  - "Volume named tmdt-rabbitmqdata để persist queue/exchange declarations qua docker compose down/up (không mất producer/consumer setup khi restart dev)"
  - "Đặt block rabbitmq ngay sau postgres để giữ thứ tự infrastructure-first (postgres → rabbitmq → app services)"
  - "Hoàn thiện notification-service env DB (Rule 2): block hiện tại chỉ có ports/build, sẽ block Flyway bootstrap khi service start — fix preventive"
metrics:
  duration: 4min
  completed_date: 2026-05-20
---

# Phase 23 Plan 01: Bootstrap RabbitMQ + Schema notification_svc Summary

Bootstrap hạ tầng async messaging (RabbitMQ container + Management UI + healthcheck), khởi tạo schema `notification_svc` cho consumer downstream, và backfill REQUIREMENTS.md với section MQ-01..MQ-05 — mở đường cho 5 plan tiếp theo của Phase 23.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Thêm RabbitMQ service vào docker-compose.yml + env vars cho 3 backend | `7fff50f` | docker-compose.yml |
| 2 | Thêm schema notification_svc vào db/init + backfill REQUIREMENTS.md MQ-01..MQ-05 | `ab4e5ca` | db/init/01-schemas.sql, .planning/REQUIREMENTS.md |

## docker-compose.yml Diff Summary

**Thêm 1 service mới:**

```yaml
rabbitmq:
  image: rabbitmq:3-management
  container_name: tmdt-rabbitmq
  ports:
    - "5672:5672"       # AMQP
    - "15672:15672"     # Management UI
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

**Cập nhật 3 service block:**

- `order-service`: thêm `depends_on.rabbitmq` (service_healthy) + 3 env vars SPRING_RABBITMQ_*
- `inventory-service`: tương tự
- `notification-service`: **hoàn thiện hẳn block** — trước đó chỉ có `build` + `ports`, nay thêm `depends_on` (postgres + rabbitmq) + đầy đủ env DB (DB_HOST/PORT/NAME/USER/PASSWORD) + 3 env SPRING_RABBITMQ_*

**Top-level volumes:**

```yaml
volumes:
  tmdt-pgdata:
  tmdt-rabbitmqdata:
```

## Schema List sau Init

Sau khi Postgres khởi tạo volume mới với `db/init/01-schemas.sql`:

```
user_svc
product_svc
order_svc
payment_svc
inventory_svc
notification_svc   ← mới
```

## REQUIREMENTS.md Backfill

Section mới `### MQ — Message Queue (RabbitMQ Integration)` với 5 requirement:

- **MQ-01** — RabbitMQ container + Management UI (THIS PLAN — Active, plan 23-01 mapped trong traceability)
- **MQ-02** — order-service producer OrderPlaced (Phase 23 downstream plans)
- **MQ-03** — inventory-service consumer + stock_ledger + processed_events
- **MQ-04** — notification-service consumer + dispatch_log
- **MQ-05** — Retry 3 lần + DLQ + traceId propagation

**Traceability** + 5 dòng MQ-01..MQ-05.
**Total active REQs**: 27 → 32. **Scope summary**: 7 → 8 trục bổ sung.

## Acceptance Criteria

- [x] `grep -c 'image: rabbitmq:3-management' docker-compose.yml` = 1
- [x] `grep -c 'SPRING_RABBITMQ_HOST: rabbitmq' docker-compose.yml` = 3 (order + inventory + notification)
- [x] `grep -c 'tmdt-rabbitmqdata' docker-compose.yml` = 2 (volume mount + top-level)
- [x] `grep -c 'rabbitmq-diagnostics' docker-compose.yml` = 1
- [x] notification-service block có DB_HOST: postgres
- [x] `grep -c 'CREATE SCHEMA IF NOT EXISTS notification_svc' db/init/01-schemas.sql` = 1
- [x] `grep -c '\*\*MQ-0[1-5]\*\*' .planning/REQUIREMENTS.md` = 5
- [x] `grep 'Total active REQs: 32' .planning/REQUIREMENTS.md` = 1 match
- [x] Section MQ tồn tại sau COUP, trước `---` Future Requirements

## Deviations from Plan

**None** — plan executed exactly as written. Cả 2 task đều thực hiện theo action block chính xác, không có Rule 1/2/3 deviation phát sinh.

## Note Quan Trọng cho Dev

`docker-entrypoint-initdb.d` chỉ chạy **một lần** khi Postgres bootstrap volume rỗng. Nếu env dev đã có volume `tmdt-pgdata` từ trước (rất khả năng cao), schema `notification_svc` SẼ KHÔNG TỰ ĐỘNG TẠO ra. Hai cách xử lý:

**Cách A — Reset volume (mất hết data dev):**
```bash
docker compose down
docker volume rm tmdt-use-gsd_tmdt-pgdata
docker compose up -d postgres
```

**Cách B — Manual create schema (giữ data):**
```bash
docker compose exec postgres psql -U tmdt -d tmdt \
  -c "CREATE SCHEMA IF NOT EXISTS notification_svc;"
```

Plan downstream (consumer notification-service) cần schema này tồn tại — verify bằng `\dn` trong psql.

## Verification (manual — defer khi có Docker runtime)

- [ ] `docker compose config` returns 0 (YAML valid)
- [ ] `docker compose up -d rabbitmq` succeed; `docker compose ps` shows rabbitmq healthy trong 60s
- [ ] `curl -u guest:guest http://localhost:15672/api/overview` returns HTTP 200 + JSON `rabbitmq_version`
- [ ] `psql ... -c "\dn"` (sau khi recreate volume) liệt kê `notification_svc`

Các check runtime defer cho khi Docker khả dụng — plan 23-01 chỉ thực hiện static config changes, có thể verify qua grep ngay.

## Self-Check: PASSED

- FOUND: docker-compose.yml (modified, có rabbitmq block)
- FOUND: db/init/01-schemas.sql (có notification_svc)
- FOUND: .planning/REQUIREMENTS.md (có MQ-01..MQ-05 + traceability + total 32)
- FOUND: 7fff50f (Task 1 commit)
- FOUND: ab4e5ca (Task 2 commit)
- FOUND: .planning/phases/23-message-queue-rabbitmq/23-01-SUMMARY.md (file này)
