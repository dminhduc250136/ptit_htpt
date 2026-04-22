# Business Rules - Cross-cutting (v2)

## Tóm tắt
Bộ rule chung cho toàn hệ thống theo kiến trúc 6 backend services. Mỗi rule có ID để tham chiếu từ BA và technical spec.

## Context Links
- Overview: [00-business-overview.md](./00-business-overview.md)
- Service rules: [services/](./services/)

## Ownership Matrix
| Domain | Owner service | Ghi chú |
|---|---|---|
| Auth/Profile/Address | user-service | user identity và access |
| Catalog/Category/Review | product-service | không sở hữu stock mutation |
| Cart/Order lifecycle | order-service | không xử lý payment gateway trực tiếp |
| Payment transaction | payment-service | VNPay return/IPN, payment state |
| Stock lifecycle | inventory-service | reserve/commit/release |
| Notification | notification-service | email/system async |

## BR-PRICING
| ID | Rule |
|---|---|
| BR-PRICING-01 | Product có price và salePrice (optional). Giá hiệu lực = salePrice ?? price. |
| BR-PRICING-02 | Đơn vị VND integer, không số thập phân. |
| BR-PRICING-03 | Checkout dùng current price, không dùng snapshot cũ. |
| BR-PRICING-04 | Phí ship: HCM/HN 30k, tỉnh khác 50k, free ship từ 3 triệu. |
| BR-PRICING-05 | COD fee 20k nếu subtotal < 1 triệu. |

## BR-INVENTORY
| ID | Rule |
|---|---|
| BR-INVENTORY-01 | inventory-service là owner duy nhất của reserve/commit/release. |
| BR-INVENTORY-02 | Reserve khi order created (PENDING). |
| BR-INVENTORY-03 | Commit khi payment success hoặc COD confirmed theo policy. |
| BR-INVENTORY-04 | Release khi payment fail, order cancel, hoặc timeout. |
| BR-INVENTORY-05 | reserved TTL mặc định 30 phút cho paid flow. |
| BR-INVENTORY-06 | Low-stock alert khi available <= 5. |

## BR-ORDER
| ID | Rule |
|---|---|
| BR-ORDER-01 | States: PENDING, PAID, CONFIRMED, PROCESSING, SHIPPED, DELIVERED, COMPLETED, CANCELLED, REFUNDED. |
| BR-ORDER-02 | Customer chỉ cancel khi PENDING. |
| BR-ORDER-03 | Admin có thể cancel tới CONFIRMED theo policy nội bộ. |
| BR-ORDER-04 | DELIVERED sau 7 ngày auto COMPLETED. |
| BR-ORDER-05 | Mọi transition phải có audit log và domain event. |

## BR-PAYMENT
| ID | Rule |
|---|---|
| BR-PAYMENT-01 | payment-service xử lý VNPay return/IPN và xác thực chữ ký. |
| BR-PAYMENT-02 | IPN là source of truth cho trạng thái thanh toán. |
| BR-PAYMENT-03 | Callback phải idempotent theo txnRef + responseCode. |
| BR-PAYMENT-04 | vnp_Amount = orderTotal * 100 theo spec VNPay. |
| BR-PAYMENT-05 | Payment failure dẫn đến cancel flow và release stock. |

## BR-NOTIFICATION
| ID | Rule |
|---|---|
| BR-NOTI-01 | notification-service consume event và gửi async, không chặn API sync. |
| BR-NOTI-02 | Milestone bắt buộc: register, order placed, paid, shipped, delivered, cancelled. |
| BR-NOTI-03 | Mỗi thông báo cần notificationId để dedupe retry. |
| BR-NOTI-04 | Retry 3 lần, sau đó chuyển DLQ/manual review. |

## BR-SECURITY-GATEWAY
| ID | Rule |
|---|---|
| BR-GW-01 | Mọi request FE đi qua API Gateway. |
| BR-GW-02 | Gateway verify JWT ở edge cho protected routes. |
| BR-GW-03 | Gateway áp dụng rate-limit cho login/register/payment callbacks. |
| BR-GW-04 | Gateway thêm trace/correlation headers cho downstream services. |

## Mapping Rule -> BA
| Rule group | BA chính |
|---|---|
| BR-PRICING | UC-CART, UC-CHECKOUT-PAYMENT |
| BR-INVENTORY | UC-CART, UC-CHECKOUT-PAYMENT, UC-ADMIN-PRODUCT |
| BR-ORDER | UC-ORDER-TRACKING, UC-ADMIN-ORDER |
| BR-PAYMENT | UC-CHECKOUT-PAYMENT, UC-ADMIN-ORDER |
| BR-NOTIFICATION | UC-AUTH, UC-CHECKOUT-PAYMENT, UC-ORDER-TRACKING |
| BR-SECURITY-GATEWAY | Tất cả UC |
