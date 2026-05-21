# Demo Failure Isolation — Database Per Service (Phase 24)

Tài liệu hướng dẫn demo tính chịu lỗi độc lập khi 1 trong 6 postgres container chết, các service khác vẫn hoạt động bình thường.

## 1. Setup

```bash
# Khoi dong toan bo stack
docker compose up -d --build

# Doi healthcheck 60-90s cho 6 postgres container + 6 backend service + gateway + FE
docker compose ps

# Login admin de lay JWT
curl -s -X POST http://localhost:8080/api/users/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r .token
export TOKEN="<paste-token>"
```

## 2. Test isolation — stop postgres-order

**Buoc 2.1**: Stop `postgres-order` container

```bash
docker compose stop postgres-order
docker compose ps postgres-order   # State: exited
```

**Buoc 2.2**: Cac service khac van hoat dong (expect 200)

```bash
# user-service: lay profile current user
curl -s -w "\nHTTP %{http_code}\n" http://localhost:8080/api/users/me \
  -H "Authorization: Bearer $TOKEN"

# product-service: catalog
curl -s -w "\nHTTP %{http_code}\n" "http://localhost:8080/api/products?page=0&size=5"

# payment-service: health (neu expose)
curl -s -w "\nHTTP %{http_code}\n" http://localhost:8080/api/payments/health

# inventory-service: health
curl -s -w "\nHTTP %{http_code}\n" http://localhost:8080/api/inventory/health
```

Expect: HTTP 200 cho cac request tren — nhung service nay khong depend on postgres-order.

**Buoc 2.3**: order-service tra 503 DATABASE_UNAVAILABLE

```bash
curl -s -w "\nHTTP %{http_code}\n" "http://localhost:8080/api/orders" \
  -H "Authorization: Bearer $TOKEN"
```

Expect: HTTP 503 voi body:
```json
{
  "code": "DATABASE_UNAVAILABLE",
  "message": "Order database is temporarily unavailable",
  "status": 503,
  ...
}
```

**Buoc 2.4**: Start lai postgres-order

```bash
docker compose start postgres-order
# Doi ~60s cho HikariCP retry & reconnect
sleep 60
curl -s -w "\nHTTP %{http_code}\n" "http://localhost:8080/api/orders" \
  -H "Authorization: Bearer $TOKEN"
```

Expect: HTTP 200 (hoac 201 cho POST) — order-svc tu reconnect.

## 3. Recovery audit

Xem reconnect log:

```bash
docker compose logs order-service --since 2m | grep -i "hikari\|reconnect\|cannot.*connection"
```

Kiem tra DB rieng cua tung service:

```bash
docker exec postgres-user      psql -U user_svc      -d user_svc      -c "\dt"
docker exec postgres-product   psql -U product_svc   -d product_svc   -c "\dt"
docker exec postgres-order     psql -U order_svc     -d order_svc     -c "\dt"
docker exec postgres-inventory psql -U inventory_svc -d inventory_svc -c "\dt"
docker exec postgres-payment   psql -U payment_svc   -d payment_svc   -c "\dt"
docker exec postgres-chat      psql -U chat_svc      -d chat_svc      -c "\dt"
```

Moi DB chi co bang cua service tuong ung trong schema `public` (KHONG con schema `*_svc`).

## 4. Mo rong: stop nhieu DB cung luc

```bash
docker compose stop postgres-product postgres-inventory
# Catalog + inventory loi, nhung user/order/payment van hoat dong
curl -s -w "\nHTTP %{http_code}\n" http://localhost:8080/api/users/me -H "Authorization: Bearer $TOKEN"
# expect 200
curl -s -w "\nHTTP %{http_code}\n" http://localhost:8080/api/products  # expect 503 DATABASE_UNAVAILABLE
```

## Acceptance checklist (xem 24-VERIFICATION.md de tick ket qua)

- [ ] Stop 1 postgres → 4 service khac van 200
- [ ] Service co DB down tra 503 voi `code=DATABASE_UNAVAILABLE`
- [ ] Start lai → service tu reconnect trong <60s
- [ ] Volume rieng: `docker volume ls | grep pgdata-` show 6 volume
