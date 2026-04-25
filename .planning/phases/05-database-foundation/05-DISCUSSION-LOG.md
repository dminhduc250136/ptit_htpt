# Phase 5: Database Foundation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-26
**Phase:** 05-database-foundation
**Areas discussed:** DB topology, Entity refactor strategy, Seed mechanism, Mock-data deletion + FE impact

---

## Gray Area Selection

| Option | Description | Selected |
|--------|-------------|----------|
| DB topology | 1 Postgres / multi-schema vs multi-DB vs shared | ✓ |
| Entity refactor strategy | Records → @Entity classes pattern | ✓ |
| Seed mechanism | Flyway V2 vs CommandLineRunner vs standalone | ✓ |
| Mock-data deletion + FE impact | Timing + FE imports cleanup | ✓ |

**User note:** "DB chuẩn, không cần match 100% mock-data FE." → Áp dụng cho seed area.

---

## DB Topology

| Option | Description | Selected |
|--------|-------------|----------|
| Multi-schema, 1 DB | 1 DB `tmdt`, mỗi service 1 schema | ✓ |
| Multi-database, 1 instance | 5 DBs trong cùng Postgres container | |
| Shared schema (public) | Tất cả services dùng `public` | |

**User's choice:** Multi-schema, 1 DB (Recommended)

| Option (Postgres image) | Description | Selected |
|--------|-------------|----------|
| postgres:16-alpine | LTS, lightweight | ✓ |
| postgres:15-alpine | Stable | |
| postgres:latest | Tracking | |

**User's choice:** postgres:16-alpine (Recommended)

---

## Entity Refactor Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Entity riêng + Record DTO | Tách persistence vs wire | ✓ |
| Convert record → mutable Entity (1 class) | Entity = DTO | |
| Hybrid: Entity + record projection | Entity write, record read | |

**User's choice:** Entity riêng + Record DTO (Recommended)

| Option (ID type) | Description | Selected |
|--------|-------------|----------|
| String UUID (giữ nguyên) | Không break contract | ✓ (revised) |
| java.util.UUID type | Type-safe Java side | |
| Long auto-increment | Traditional JPA | (initial pick, reverted) |

**User's choice:** Long auto-increment (initial) → revised về String UUID
**Notes:** User initial pick Long, sau đó revert: "ơ, nếu api phase 1 đã config là string thì cứ giữ, tôi chọn long vì ko biết thông tin vừa rồi". Final decision: giữ String UUID để không break FE contract Phase 1.

---

## Seed Mechanism

| Option | Description | Selected |
|--------|-------------|----------|
| Flyway V2, minimal realistic | Schema sạch, data thật nhưng không clone mock | ✓ |
| Flyway V2 sao chép mock-data | Match 100% UX | |
| CommandLineRunner Java-based | Insert qua repo trong runtime | |

**User's choice:** Flyway V2__seed_dev_data.sql, minimal realistic (Recommended)

| Option (profile) | Description | Selected |
|--------|-------------|----------|
| Profile `dev` chạy V2 | Schema separate khỏi seed | ✓ |
| Luôn seed | Đơn giản, không best practice | |
| Standalone script | Chạy bằng psql sau khi up | |

**User's choice:** Profile `dev` only (Recommended)

---

## Mock-Data Deletion + FE Impact

| Option | Description | Selected |
|--------|-------------|----------|
| Xóa cuối phase, sau round-trip PASS | An toàn nhất | ✓ |
| Xóa ngay khi DB-06 PASS | Aggressive | |
| Giữ folder, chỉ gỡ import | Vi phạm REQ DB-06 | |

**User's choice:** Xóa cuối phase, sau khi toàn bộ round-trip PASS (Recommended)

| Option (FE id type response) | Description | Selected |
|--------|-------------|----------|
| Update FE types theo OpenAPI codegen | Refresh contract | |
| Backend serialize Long → String | Hack giữ contract | |
| Backend giữ String UUID column | Đổi quyết định trước | ✓ |

**User's choice:** Backend giữ String UUID column → trigger revert D-04 ở trên.
**Notes:** Quyết định này thay thế Long auto-increment đã chọn trước đó.

---

## Claude's Discretion

- Postgres column type cho UUID (`uuid` native vs VARCHAR(36)) — researcher xác minh.
- JPA mapping details (constraints, indexes, lazy fetch) — planner quyết theo best practice.
- Flyway file naming pattern (V1__ vs V1.0.0__) — đơn giản trừ khi conflict.
- Hikari pool tuning — defaults đủ cho dev.
- Test refactor approach (`@DataJpaTest` + H2 vs Testcontainers) — planner quyết, ưu tiên đơn giản.

## Deferred Ideas

- CategoryEntity.slug persist (D10 v1.0 audit)
- Connection pool tuning, observability, replicas
- Notification-service migrate sang DB
- Field encryption / security hardening
- Comprehensive integration test infra
- Seed clone từ FE mock fixtures (nếu QA yêu cầu sau)
