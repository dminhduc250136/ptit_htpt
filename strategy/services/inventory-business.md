# Inventory Service - Business Strategy (v2)

## Tóm tắt
Inventory Service là owner duy nhất cho vòng đời tồn kho: reserve, commit, release. Mục tiêu là đảm bảo nhất quán tồn kho khi checkout concurrency cao.

## Context Links
- Rules: [../02-business-rules.md](../02-business-rules.md)
- Architecture: [../../architecture/services/inventory-service.md](../../architecture/services/inventory-service.md)
- BA: [../../ba/uc-cart.md](../../ba/uc-cart.md), [../../ba/uc-checkout-payment.md](../../ba/uc-checkout-payment.md), [../../ba/uc-admin-product.md](../../ba/uc-admin-product.md)

## Ownership
- Quản lý available/reserved stock.
- Reserve khi order created.
- Commit khi thanh toán thành công hoặc COD confirmed.
- Release khi fail/cancel/timeout.
- Low-stock alert và inventory audit.

## Không ownership
- Product metadata (name/spec/category).
- Payment callback.

## Core policies
| Chủ đề | Policy |
|---|---|
| Atomicity | Mọi mutation stock chạy transaction + idempotency |
| TTL reserve | Mặc định 30 phút cho paid flow |
| Low stock | available <= 5 tạo alert |
| Negative guard | Không cho available < 0 |

## Event contracts
| Event consume | Event publish |
|---|---|
| OrderPlaced | StockReserved/StockReservationFailed |
| PaymentSucceeded | StockCommitted |
| PaymentFailed, OrderCancelled | StockReleased |

## KPI gợi ý
- Stock consistency.
- Reserve success rate.
- Oversell incidents.
- Low-stock alert accuracy.
