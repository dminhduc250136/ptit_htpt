# Product Service - Business Strategy (v2)

## Tóm tắt
Product Service tập trung vào catalog và review. Service này không còn là owner của stock lifecycle.

## Context Links
- Rules: [../02-business-rules.md](../02-business-rules.md)
- Architecture: [../../architecture/services/product-service.md](../../architecture/services/product-service.md)
- BA: [../../ba/uc-product-browse.md](../../ba/uc-product-browse.md), [../../ba/uc-product-review.md](../../ba/uc-product-review.md), [../../ba/uc-admin-product.md](../../ba/uc-admin-product.md)

## Ownership
- Product CRUD, category tree.
- Public browse/search/filter.
- Review/rating eligibility integration.
- Product publish/unpublish lifecycle.

## Không ownership
- Reserve/commit/release stock.
- VNPay/payment transactions.

## Core policies
| Chủ đề | Policy |
|---|---|
| Product status | DRAFT, ACTIVE, INACTIVE, OUT_OF_STOCK (derived from inventory availability) |
| Price model | price + salePrice(optional), effective price theo rule chung |
| Review eligibility | Chỉ cho đơn DELIVERED/COMPLETED |
| Review moderation | Admin có thể hide review vi phạm |

## Data collaboration
- Lấy availability từ inventory-service để hiển thị badge và validate add-to-cart.
- Nhận event OrderDelivered để mở quyền review.

## Event contracts
| Event | Trigger |
|---|---|
| ProductCreated/ProductUpdated | Product thay đổi |
| ReviewPosted/ReviewHidden | Review được tạo hoặc ẩn |

## KPI gợi ý
- Search conversion.
- Review coverage.
- Catalog update lead time.
- Product detail latency p95.
