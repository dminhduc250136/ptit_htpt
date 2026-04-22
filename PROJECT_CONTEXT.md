# THÔNG TIN DỰ ÁN (PROJECT CONTEXT) - v2

## 1. Tổng quan
- Lĩnh vực: Thương mại điện tử (single-vendor tech store).
- Kiến trúc: FE + API Gateway + 6 backend services.
- Mục tiêu: Giúp AI map đúng service ownership trước khi đề xuất code.

## 2. Kiến trúc chi tiết
👉 Xem [architecture/00-overview.md](architecture/00-overview.md) để hiểu:
- Container diagram + service boundaries
- Integration pattern (sync REST + async events)
- Cross-cutting concerns (JWT, rate-limit, trace, audit)

## 3. Service Ownership (Quy tắc bắt buộc)
| Service | Chủ sở hữu | ❌ Cấm |
|---|---|---|
| **user-service** | auth, profile, address | order, payment, inventory |
| **product-service** | catalog, category, review | reserve/commit/release stock |
| **order-service** | cart, order lifecycle | payment callback, stock mutation |
| **payment-service** | payment transaction, VNPay callback | order state |
| **inventory-service** | available/reserved stock | product metadata |
| **notification-service** | template, delivery retry | business state |
| **api-gateway** | edge auth, routing, rate-limit | domain logic |

**Quy tắc cốt lõi:**
1. Không đặt payment callback logic trong order-service.
2. Không đặt stock mutation trong product-service.
3. Notification là async side effect, không chặn request path.
4. Không service nào truy cập DB của service khác.

## 4. Nguyên tắc cho AI khi làm task
1. Đọc theo thứ tự: **PROJECT_CONTEXT → README → UC → architecture/services/**
2. Xác định owner service trước khi thiết kế API/code.
3. Ghi rõ planned contract nếu endpoint chưa implement.
4. Ưu tiên cập nhật docs khi thay đổi boundary hoặc event contracts.

## 5. Tài liệu liên quan
- **Kiến trúc**: [architecture/](architecture/) — source of truth technical design
- **Business context**: [strategy/](strategy/) — KPI, vision, business rules
- **Use cases**: [ba/](ba/) — organized by service (user-service/, product-service/, order-service/)
- **Technical specs**: [technical-spec/](technical-spec/) — organized by service
- **Navigation**: [README.md](README.md) — decision guide + quick reference
