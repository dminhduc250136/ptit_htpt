# User Service - Business Strategy (v2)

## Tóm tắt
User Service chịu trách nhiệm danh tính người dùng: authentication, profile, address, và quản trị user cho admin.

## Context Links
- Rules: [../02-business-rules.md](../02-business-rules.md)
- Architecture: [../../architecture/services/user-service.md](../../architecture/services/user-service.md)
- BA: [../../ba/uc-auth.md](../../ba/uc-auth.md), [../../ba/uc-user-profile.md](../../ba/uc-user-profile.md), [../../ba/uc-admin-user.md](../../ba/uc-admin-user.md)

## Ownership
- Register, login, logout, refresh token.
- Password reset và account lock policy.
- Profile và address book.
- Admin block/unblock user.

## Không ownership
- Catalog, order, payment, inventory.
- Gửi thông báo trực tiếp (chỉ phát event).

## Core policies
| Chủ đề | Policy |
|---|---|
| Roles | CUSTOMER, ADMIN |
| Password | >= 8 ký tự, có chữ + số, bcrypt |
| Login lock | 5 fail/15 phút -> lock 30 phút |
| Token | access TTL 1h, refresh TTL 7d |
| Address | Tối đa 5 địa chỉ/user, 1 default |

## Event contracts
| Event | Trigger | Consumer |
|---|---|---|
| UserRegistered | Register thành công | notification-service |
| UserBlocked | Admin block user | order-service (optional policy), notification-service |
| UserUnblocked | Admin unblock user | notification-service |

## KPI gợi ý
- Login success rate.
- Password reset success rate.
- Blocked-user anomaly rate.
- p95 login latency.
