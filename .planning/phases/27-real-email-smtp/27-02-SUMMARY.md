---
phase: 27-real-email-smtp
plan: "02"
subsystem: user-service
tags: [rabbitmq, amqp, flyway, verification-token, security, tdd]
dependency_graph:
  requires: []
  provides:
    - "VerificationTokenService: sinh + verifyAndConsume token single-use"
    - "AccountEventPublisher: publish UserRegistered + PasswordResetRequested afterCommit"
    - "schema verification_tokens + users.email_verified (V102 + V103)"
    - "topology user.events exchange + notification.user-events queue"
  affects:
    - "user-service: thêm spring-boot-starter-amqp + RabbitMQ Producer topology"
    - "notification-service (Wave 3): sẽ consume từ notification.user-events"
tech_stack:
  added:
    - "spring-boot-starter-amqp (BOM 3.3.2)"
    - "spring-rabbit-test (scope test)"
  patterns:
    - "afterCommit publish (D-03) — đảm bảo event KHÔNG phát nếu DB rollback"
    - "SELECT FOR UPDATE pessimistic lock — single-use token (Pitfall 4, T-27-04)"
    - "SecureRandom 32 byte base64url — 256-bit entropy token (T-27-03)"
    - "Publisher Confirms 5s timeout (D-04) — detect NACK"
    - "TraceIdMessagePostProcessor — MDC capture caller thread (Pitfall 1)"
    - "TDD RED/GREEN — VerificationTokenServiceTest 5 cases"
key_files:
  created:
    - "sources/backend/user-service/src/main/resources/db/migration/V102__add_email_verified.sql"
    - "sources/backend/user-service/src/main/resources/db/migration/V103__create_verification_tokens.sql"
    - "sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/VerificationTokenEntity.java"
    - "sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/repository/VerificationTokenRepository.java"
    - "sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/VerificationTokenService.java"
    - "sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/messaging/tracing/TraceIdMessagePostProcessor.java"
    - "sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/messaging/event/UserEventEnvelope.java"
    - "sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/messaging/config/UserRabbitMQConfig.java"
    - "sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/messaging/publisher/AccountEventPublisher.java"
    - "sources/backend/user-service/src/test/java/com/ptit/htpt/userservice/service/VerificationTokenServiceTest.java"
  modified:
    - "sources/backend/user-service/pom.xml"
    - "sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/UserEntity.java"
    - "sources/backend/user-service/src/main/resources/application.yml"
decisions:
  - "VerificationTokenEntity dùng record-style accessors (KHÔNG Lombok) — nhất quán UserEntity convention"
  - "verifyAndConsume kiểm tra usedAt TRƯỚC expiresAt — đã dùng ưu tiên báo 410 GONE, không leak expiry info"
  - "UserRabbitMQConfig khai báo cả DLX + DLQ + notification.user-events queue — Wave 3 consumer có thể dùng ngay"
  - "user-service Producer only → KHÔNG có listener.simple.retry block trong application.yml"
  - "TraceIdMessagePostProcessor copy nguyên từ order-service, đổi package — đồng nhất convention"
metrics:
  duration: "4 phút"
  completed_date: "2026-05-22"
  tasks_completed: 3
  files_created: 10
  files_modified: 3
---

# Phase 27 Plan 02: Nền Tảng Token + RabbitMQ Producer user-service Summary

**One-liner:** Flyway V102+V103 schema token, VerificationTokenService single-use 256-bit token chống replay, AccountEventPublisher afterCommit cho user.events exchange.

## Completed Tasks

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 0 | Thêm spring-boot-starter-amqp + 2 Flyway migration | 24c70eb | pom.xml, V102, V103 |
| 1 RED | VerificationTokenServiceTest (TDD RED) | 4e7b4c0 | VerificationTokenServiceTest.java |
| 1 GREEN | UserEntity.emailVerified + VerificationTokenEntity/Repository/Service | 7400303 | UserEntity.java, VerificationTokenEntity.java, VerificationTokenRepository.java, VerificationTokenService.java |
| 2 | RabbitMQ Producer topology + AccountEventPublisher | 4ce3035 | TraceIdMessagePostProcessor, UserEventEnvelope, UserRabbitMQConfig, AccountEventPublisher, application.yml |

## What Was Built

### Flyway Migrations
- **V102** (`ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT false`): cờ email_verified chuẩn bị cho MAIL-02 verify flow.
- **V103** (`CREATE TABLE IF NOT EXISTS verification_tokens`): bảng token xác minh/reset với PK token (64 chars), user_id, type CHECK, expires_at, used_at, 2 index (idx_vt_user_id + idx_vt_type_used partial).

### VerificationTokenEntity / Repository / Service
- **VerificationTokenEntity**: JPA entity record-style accessors, constructor public, `markUsed()` set usedAt = Instant.now().
- **VerificationTokenRepository**: `findByTokenForUpdate` với `@Lock(PESSIMISTIC_WRITE)` + JPQL — SELECT FOR UPDATE đảm bảo single-use trong concurrent requests (T-27-04 mitigation).
- **VerificationTokenService**:
  - `generateToken()`: SecureRandom 32 byte → base64url without padding = 43 ký tự, 256-bit entropy (T-27-03 mitigation).
  - `createToken(userId, type, ttl)`: save entity với expiresAt = now() + ttl.
  - `verifyAndConsume(token, expectedType)`: SELECT FOR UPDATE → check type → check usedAt → check expiresAt → markUsed + save. Thứ tự: usedAt trước expiresAt (đã dùng → 410 GONE, hết hạn → 410 GONE, sai type → 400 BAD_REQUEST).

### UserEntity.emailVerified
- Field `@Column(name="email_verified", nullable=false) private boolean emailVerified = false`.
- Accessor `emailVerified()` + mutator `setEmailVerified(boolean v)` + bump `updatedAt`.

### RabbitMQ Producer Topology
- **TraceIdMessagePostProcessor**: copy từ order-service, đổi package `userservice.messaging.tracing`. Header X-Trace-Id, factory `capture()` đọc MDC tại caller thread.
- **UserEventEnvelope**: record với `createUserRegistered` + `createPasswordReset` + nested `UserPayload(userId, email, fullName, actionUrl)`.
- **UserRabbitMQConfig**: topology `user.events` (TopicExchange) + `user.dlx` (DirectExchange) + `user-events.dlq` + `notification.user-events` (bind `user.#`, DLX args) + Jackson2JsonMessageConverter + RabbitTemplate mandatory.
- **AccountEventPublisher**: `publishUserRegistered` + `publishPasswordReset` afterCommit pattern, `doPublish` Publisher Confirms 5s, 5 log path.
- **application.yml**: thêm `spring.rabbitmq` block (producer-only, không listener config).

## TDD Gate Compliance

- RED gate: commit `4e7b4c0` — `test(27-02): add failing tests for VerificationTokenService (TDD RED)` — 5 test cases.
- GREEN gate: commit `7400303` — `feat(27-02): UserEntity.emailVerified + VerificationTokenEntity/Repository/Service`.
- REFACTOR: không cần — code đã clean.

## Deviations from Plan

Không có — plan thực thi đúng theo kế hoạch.

## Known Stubs

Không có stub. `AccountEventPublisher.publishUserRegistered` và `publishPasswordReset` được tạo nhưng chưa được gọi từ `AuthService` — sẽ wire ở Plan 27-03 (forgot-password + verify-email endpoints).

## Threat Surface Scan

Không có surface mới ngoài threat model của plan:
- `verification_tokens` table: đã nằm trong threat register T-27-03 + T-27-04 + T-27-05.
- `user.events` exchange: đã nằm trong T-27-06 (accept — broker docker nội bộ).

## Self-Check: PASSED

Files created:
- [x] V102__add_email_verified.sql
- [x] V103__create_verification_tokens.sql
- [x] VerificationTokenEntity.java
- [x] VerificationTokenRepository.java
- [x] VerificationTokenService.java
- [x] TraceIdMessagePostProcessor.java
- [x] UserEventEnvelope.java
- [x] UserRabbitMQConfig.java
- [x] AccountEventPublisher.java
- [x] VerificationTokenServiceTest.java

Commits:
- [x] 24c70eb — chore(27-02): thêm spring-boot-starter-amqp + Flyway V102 + V103
- [x] 4e7b4c0 — test(27-02): add failing tests for VerificationTokenService (TDD RED)
- [x] 7400303 — feat(27-02): UserEntity.emailVerified + VerificationTokenEntity/Repository/Service
- [x] 4ce3035 — feat(27-02): RabbitMQ Producer topology + AccountEventPublisher
