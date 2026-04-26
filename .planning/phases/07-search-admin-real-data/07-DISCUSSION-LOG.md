# Phase 7: Search + Admin Real Data - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-26
**Phase:** 07-search-admin-real-data
**Areas discussed:** Gateway admin routing, Admin Products form, Admin Orders detail, Admin Users fields/edit

---

## Gray Area Selection

| Option | Description | Selected |
|--------|-------------|----------|
| Gateway admin routing | Backend /admin/* endpoints chưa expose qua gateway | ✓ |
| Admin Products form | Edit modal + category field | ✓ |
| Admin Orders: modal vs page | Detail page vs inline modal | ✓ |
| Admin Users display fields | fullName/username mismatch với UserDto | ✓ |

**User notes:** Hỏi thêm về "admin tồn kho" — ghi nhận là Deferred Idea (ngoài scope Phase 7).

---

## Gateway Admin Routing

| Option | Description | Selected |
|--------|-------------|----------|
| Per-service admin prefix | /api/products/admin/**, /api/orders/admin/**, /api/users/admin/** | ✓ |
| Unified /api/admin/** prefix | 1 gateway prefix routing theo service | |

**User's choice:** Per-service admin prefix (Recommended)
**Notes:** Phải đặt routes mới TRƯỚC general `/**` routes trong YAML để match đúng.

---

## Admin Products Form

| Option | Description | Selected |
|--------|-------------|----------|
| Reuse modal (add/edit mode) | 1 component, 2 mode | ✓ |
| Edit dialog riêng | 2 component separate | |

**User's choice:** Reuse modal (Recommended)

| Option | Description | Selected |
|--------|-------------|----------|
| Dropdown load từ backend | GET /api/products/admin/products/categories | ✓ |
| Text input (category name) | Đơn giản hơn nhưng UX kém | |

**User's choice:** Dropdown load từ backend (Recommended)

| Option | Description | Selected |
|--------|-------------|----------|
| Mở rộng ProductUpsertRequest | Thêm brand, thumbnailUrl, shortDescription, originalPrice | ✓ |
| Bám minimal (5 fields) | Chỉ name, slug, categoryId, price, status | |

**User's choice:** Mở rộng ProductUpsertRequest (Recommended)
**Notes:** ProductUpsertRequest hiện chỉ có 5 fields — thiếu brand/thumbnailUrl/description. Cần mở rộng backend để admin form có ích.

---

## Admin Orders Detail

| Option | Description | Selected |
|--------|-------------|----------|
| Dedicated detail page | /admin/orders/[id]/page.tsx | ✓ |
| Giữ inline modal | Ít code hơn nhưng không khớp requirement | |

**User's choice:** Dedicated detail page (Recommended)

| Option | Description | Selected |
|--------|-------------|----------|
| Giữ đủ 5 options | PENDING, CONFIRMED, SHIPPING, DELIVERED, CANCELLED | ✓ |
| Chỉ 3 status | PENDING, SHIPPED, DELIVERED | |

**User's choice:** Giữ đủ 5 options (Recommended)

---

## Admin Users Display Fields

**Context:** UserDto từ backend (Phase 6) có: id, username, email, roles, createdAt, updatedAt. Admin users page hiển fullName + phone — sẽ undefined với real data.

**User's decision:** Thêm fullName + phone vào UserEntity (nullable, Flyway V3 migration). Fields được populate sau qua `/account` profile page (Phase sau). Admin có thể edit qua modal.

| Option | Description | Selected |
|--------|-------------|----------|
| Admin-only populate | fullName/phone chỉ admin sửa | — |
| Register form collect | Thêm vào register | — |

**User's clarification:** Fields không required khi register. User fill sau ở `/account` page. Admin có thể set/update qua edit modal.

| Option | Description | Selected |
|--------|-------------|----------|
| PATCH roles only | Chỉ sửa role, không cần modal | |
| Full edit (fullName + phone + roles) | Modal đầy đủ | ✓ |

**User's choice:** Full edit modal (fullName + phone + roles)
**User's notes:** "nếu chỉ sửa role thì ko cần modal" — modal cần có đủ các fields để có ích.
**Implementation note:** Cần PATCH endpoint riêng (không dùng PUT vì UserUpsertRequest require passwordHash).

---

## Claude's Discretion

- JPA keyword filter approach (LIKE vs Specification)
- Product slug auto-generation khi create
- Toast notification implementation (state-based vs component)
- Loading skeleton pattern cho admin pages

## Deferred Ideas

- Admin inventory management — user hỏi, ngoài scope Phase 7
- User /account profile page — fullName/phone tự edit
- Product image upload (file upload thay URL)
