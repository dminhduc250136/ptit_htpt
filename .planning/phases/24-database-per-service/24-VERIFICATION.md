# Phase 24 — Verification

**Phase:** 24 Database Per Service
**Verified:** 2026-05-21
**Trạng thái:** Code complete — runtime verification CHỜ user chạy Docker (môi trường dev không có Docker daemon + Maven CLN).

## Code-level verification (đã thực hiện)

| Check | Kết quả |
|-------|---------|
| `docker-compose.yml` có 6 postgres-* container riêng | ✅ PASS |
| Postgres container KHÔNG expose host port (D-04) | ✅ PASS — không có `ports:` block nào cho postgres-* |
| Volume riêng per service (6 volume `pgdata-*`), xóa `tmdt-pgdata` | ✅ PASS |
| `depends_on` mỗi backend service chỉ trỏ postgres riêng (D-11) | ✅ PASS |
| Frontend env trỏ `postgres-chat` / `chat_svc` (D-09) | ✅ PASS |
| 5 `application.yml` bỏ `default_schema` (jpa + flyway) | ✅ PASS — commit 28704a2 |
| `hikari.initialization-fail-timeout=-1` thêm vào 5 service | ✅ PASS |
| Grep `<svc>_svc\.` trong Java/SQL → 0 reference thực thi | ✅ PASS — commit 28704a2 |
| 5 service thêm `@ExceptionHandler` 503 `DATABASE_UNAVAILABLE` (D-14) | ✅ PASS |
| `ApiErrorResponse.of(...)` signature 7-arg khớp call site | ✅ PASS — verified record signature 5 service |
| `docs/db-isolation-demo.md` tồn tại, 4 section | ✅ PASS |

## Runtime verification (CHỜ user — cần Docker)

Môi trường hiện tại KHÔNG có Docker daemon đang chạy + KHÔNG có Maven CLI (build qua Dockerfile multi-stage). Các bước sau user phải tự chạy:

- [ ] `docker compose down -v && docker compose up -d --build` → tất cả container Up + healthy
- [ ] Compile: thực hiện ngầm trong `docker compose build` — nếu 5 service build OK nghĩa là `@ExceptionHandler` mới compile sạch
- [ ] Theo `docs/db-isolation-demo.md` §2: stop `postgres-order` → user/product/payment/inventory vẫn 200; order trả 503 `DATABASE_UNAVAILABLE`
- [ ] §2.4: start lại `postgres-order` → reconnect <60s
- [ ] §3: `docker exec postgres-product psql -U product_svc -d product_svc -c "\dt"` → bảng nằm trong `public`, KHÔNG có schema `product_svc`
- [ ] `docker volume ls | grep pgdata-` → 6 volume

## Acceptance D-XX

| Decision | Trạng thái |
|----------|-----------|
| D-01..D-05 (topology compose) | ✅ code complete |
| D-06..D-08 (schema + migrations) | ✅ code complete |
| D-09 (postgres-chat) | ✅ code complete |
| D-10..D-12 (app config) | ✅ code complete |
| D-13 (isolation demo doc) | ✅ `docs/db-isolation-demo.md` |
| D-14 (error contract 503) | ✅ code complete — runtime test chờ user |
| D-17, D-18 (docs/README) | ✅ code complete |

## Ghi chú

- Runtime smoke test bị defer do constraint môi trường (no Docker daemon trong session). Đây KHÔNG phải gap thiết kế — chỉ là giới hạn thực thi. User chạy `docs/db-isolation-demo.md` để hoàn tất xác nhận.
