# Phase 24 — Summary: Database Per Service

**Hoàn thành:** 2026-05-21
**Branch:** workspace/phase-24-25-microservice-hardening

## Mục tiêu đạt được

Chuyển hạ tầng CSDL từ **"1 postgres container shared / nhiều schema"** sang **"mỗi service một postgres container riêng + credential riêng"**, đáp ứng yêu cầu 3.4 (database-per-service) và mục 4 (chịu lỗi độc lập) của đề chủ đề 4.

## Commits

| Plan | Commit | Nội dung |
|------|--------|----------|
| 24-01 | `c667620` | Tách 6 postgres container (user/product/order/inventory/payment/chat), bỏ host port, volume riêng |
| 24-02 | `28704a2` | Bỏ `default_schema` ở 5 `application.yml`, dùng `public`; refactor 47 file (migration SQL + JPA entity) bỏ schema-prefix; thêm `hikari.initialization-fail-timeout=-1` |
| 24-03 | `5428c1d` | FE chat (`schema-init.ts` + `messages-repo.ts`) dùng `postgres-chat` / DB `chat_svc` |
| 24-04 | (commit này) | `@ExceptionHandler` 503 `DATABASE_UNAVAILABLE` ở 5 service; `docs/db-isolation-demo.md`; VERIFICATION + SUMMARY |

## Thay đổi breaking

- **Volume cũ `tmdt-pgdata` bị bỏ.** Dev upgrade từ branch cũ phải chạy `docker compose down -v` trước khi `up` để tạo lại 6 DB riêng từ Flyway migration.
- Postgres không còn expose host port → truy vấn dev qua `docker exec -it postgres-<svc> psql -U <svc>_svc -d <svc>_svc`.
- notification-service KHÔNG có DB riêng (Phase 23 chưa merge — chưa có `dispatch_log`). Khi Phase 23 merge sẽ bổ sung `postgres-notification` (per D-02).

## Verification

Xem `24-VERIFICATION.md`. Code-level checks PASS toàn bộ. Runtime smoke test (Docker) defer cho user chạy theo `docs/db-isolation-demo.md` — môi trường session không có Docker daemon.

## Deferred ideas (từ CONTEXT)

- **D-19** DB user least-privilege (read-only vs write user) — defer.
- **D-20** Secrets management (Vault / Docker secrets) — defer; password hardcode trong compose cho demo.
- **D-21** Connection pool tuning per service — defer; giữ HikariCP default.

## Tiếp theo

Phase 25 — Gateway JWT Edge Authentication.
