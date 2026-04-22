# UC-ADMIN-PRODUCT: Admin quản lý catalog và tồn kho (v2)

## Tóm tắt
Admin quản lý product/category tại product-service và quản lý tồn kho tại inventory-service. Đây là điểm tách boundary quan trọng trong kiến trúc v2.

## Context Links
- Strategy: [../strategy/services/product-business.md](../strategy/services/product-business.md)
- Architecture: [../architecture/services/product-service.md](../architecture/services/product-service.md)
- Technical Spec: [../technical-spec/ts-admin-product.md](../technical-spec/ts-admin-product.md)

## Actors
- Primary: Admin
- Secondary: Inventory Service, API Gateway

## Preconditions
- User role ADMIN.

## Main Flows
### A. Product management (product-service)
1. Admin tạo/sửa product metadata: name, sku, category, price, salePrice, status, media.
2. product-service validate business rules catalog.
3. product-service phát ProductCreated/ProductUpdated.

### B. Category management (product-service)
1. Admin CRUD category tree 2 cấp.
2. product-service kiểm tra category không bị xóa khi còn product active.

### C. Inventory management (inventory-service)
1. Admin mở modal cập nhật tồn kho.
2. FE gọi inventory endpoint với delta/reason.
3. inventory-service cập nhật ledger available/reserved và ghi stock log.
4. inventory-service phát event low stock nếu cần.

## Alternative/Exception Flows
- SKU trùng -> SKU_EXISTS.
- salePrice không hợp lệ -> INVALID_SALE_PRICE.
- Stock mutation âm -> STOCK_WOULD_BE_NEGATIVE.
- Thiếu reason khi cập nhật kho -> REASON_REQUIRED.

## Service Touchpoints
| Concern | Service |
|---|---|
| Product CRUD, category, review moderation | product-service |
| Stock update, reserve ledger, stock log | inventory-service |
| Admin auth, routing | api-gateway |

## Business Rules
- BR-PRICING-01..05.
- BR-INVENTORY-01..06.
- BR-PRODUCT-01..07 (tham chiếu TS).

## Acceptance Criteria
- [ ] Product metadata và stock mutation đi qua đúng service owner.
- [ ] Stock update luôn có audit reason.
- [ ] Không cho stock âm.
- [ ] Low stock được phát hiện và hiển thị cho admin.

## NFR
- Product admin list p95 < 500ms.
- Stock update p95 < 400ms.
- Audit trail đầy đủ cho mọi cập nhật kho.
