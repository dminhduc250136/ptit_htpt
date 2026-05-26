---
phase: 23
slug: message-queue-rabbitmq
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-20
---

# Phase 23 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Testcontainers (RabbitMQ + PostgreSQL) |
| **Config file** | `pom.xml` per service (Wave 0 thêm dependencies vào notification-service) |
| **Quick run command** | `mvn -pl <service> test -Dtest=<TestClass>` |
| **Full suite command** | `mvn -pl order-service,inventory-service,notification-service verify` |
| **Estimated runtime** | ~120s (Testcontainers start ~30s, 4 IT scenarios) |

---

## Sampling Rate

- **After every task commit:** Run unit tests của service đang sửa (`mvn -pl <svc> test`)
- **After every plan wave:** Run IT của service đang sửa (`mvn -pl <svc> verify -DskipTests=false`)
- **Before `/gsd-verify-work`:** Full IT suite phải green
- **Max feedback latency:** 120s

---

## Per-Task Verification Map

(Planner sẽ điền chi tiết khi tạo PLAN.md files — mapping từng task → command + acceptance criteria)

---

## Wave 0 Requirements

- [ ] `sources/backend/notification-service/pom.xml` — thêm `spring-boot-starter-data-jpa`, `org.postgresql:postgresql`, `org.flywaydb:flyway-core`, `org.testcontainers:postgresql`, `org.testcontainers:rabbitmq`, `org.testcontainers:junit-jupiter`
- [ ] `sources/backend/order-service/pom.xml`, `inventory-service/pom.xml`, `notification-service/pom.xml` — thêm `spring-boot-starter-amqp`, `org.testcontainers:rabbitmq`
- [ ] `sources/backend/notification-service/src/main/resources/application.yml` — datasource + flyway + rabbitmq config
- [ ] `sources/backend/notification-service/src/main/resources/db/migration/V1__init_schema.sql` — schema `notification_svc` + base tables
- [ ] `.planning/REQUIREMENTS.md` — backfill section MQ-01..MQ-05

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Management UI hiển thị exchange/queue/DLQ | MQ-01, MQ-05 | UI rendering không thể assert tự động dễ dàng | Sau `docker compose up`, mở `http://localhost:15672` (guest/guest), tab Exchanges thấy `order.events` (topic), tab Queues thấy `inventory.order-events`, `notification.order-events`, `order-events.dlq` |
| Demo cho hội đồng: đặt 1 đơn qua FE → quan sát message flow trong UI | MQ-02..MQ-05 | Cần observability trực quan | Đặt đơn → refresh UI → thấy publish 1 message → 2 consumer ACK → DB inventory.quantity giảm + dispatch_log có row |
| traceId xuyên service trong `docker compose logs` | MQ-06 | Log inspection thủ công | `docker compose logs order-service inventory-service notification-service | grep <traceId>` thấy 3 dòng từ 3 service |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (notification-service persistence bootstrap)
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
