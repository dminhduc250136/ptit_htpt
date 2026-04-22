# Product Service (v2)

## Tóm tắt
Product Service quản lý catalog và review. Tồn kho vật lý/reserve không nằm ở service này trong v2.

## Runtime
- Service name: product-service
- Port: 8080 (container), exposed 8082 ở compose

## Ownership
- Product/category CRUD.
- Product browse/search/filter APIs.
- Review/rating và moderation.

## Key APIs
- GET /products, GET /products/{slug}
- GET /categories, GET /categories/tree
- POST /products/{id}/reviews
- POST/PATCH /admin/products
- POST/PATCH /admin/categories

## Publish Events
- ProductCreated
- ProductUpdated
- ProductActivated/ProductDeactivated
- ReviewPosted

## Consume Events
- OrderDelivered (mở review eligibility).

## Không ownership
- reserve/commit/release stock.
- payment processing.

## Notes for AI
- Nếu task liên quan stock mutation, route sang inventory-service.
