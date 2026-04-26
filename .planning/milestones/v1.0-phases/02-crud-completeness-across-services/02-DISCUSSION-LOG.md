# Phase 2: CRUD Completeness Across Services - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md - this log preserves the alternatives considered.

**Date:** 2026-04-22
**Phase:** 02-crud-completeness-across-services
**Areas discussed:** CRUD scope by service, endpoint contract and pagination, admin/public boundary, gateway rollout strategy

---

## CRUD scope by service

| Option | Description | Selected |
|--------|-------------|----------|
| CRUD đầy đủ cho tất cả services | Create/Read/List/Update/Delete cho toàn bộ 6 services | ✓ |
| Domain-specific CRUD | Cho phép một số service chỉ lifecycle, không ép CRUD đầy đủ | |
| Bổ sung tối thiểu để pass milestone | Chỉ vá endpoint còn thiếu | |

**User's choice:** CRUD đầy đủ cho tất cả services.
**Notes:** Người dùng muốn baseline completeness rõ ràng cho toàn phase.

| Option | Description | Selected |
|--------|-------------|----------|
| Soft delete mặc định | Dùng status/isDeleted, hard delete chỉ ngoại lệ | ✓ |
| Hard delete mặc định | Xóa vật lý trực tiếp | |
| Mixed theo service | Mỗi service một policy riêng ngay từ đầu | |

**User's choice:** Soft delete mặc định.
**Notes:** Ưu tiên an toàn dữ liệu và khả năng audit.

---

## Endpoint contract and pagination

| Option | Description | Selected |
|--------|-------------|----------|
| REST resource chuẩn | GET list/detail, POST, PUT/PATCH, DELETE | ✓ |
| Verb-based endpoints | /create, /update, /delete | |
| Hybrid | Trộn REST + verb-based | |

**User's choice:** REST resource chuẩn.
**Notes:** Muốn naming dễ hiểu, đồng bộ với gateway prefix hiện tại.

| Option | Description | Selected |
|--------|-------------|----------|
| page/size/sort + standardized list metadata | content, totalElements, totalPages, currentPage, pageSize, isFirst, isLast | ✓ |
| offset/limit | Chuẩn offset-based | |
| Per-service format | Mỗi service tự định nghĩa | |

**User's choice:** page/size/sort + standardized list metadata.
**Notes:** Đồng bộ với shape frontend mock đang dùng.

---

## Admin/public boundary

| Option | Description | Selected |
|--------|-------------|----------|
| Tách rõ prefix admin/public | Route admin riêng + auth policy riêng | ✓ |
| Chung route, tách bằng role check | Không tách prefix route | |
| Mixed | Tách một phần, phần còn lại chung route | |

**User's choice:** Tách rõ prefix admin/public.
**Notes:** Muốn boundary rõ, tránh trộn quyền truy cập.

**User's choice (admin scope):** Product, Inventory, Order, User.
**Notes:** Đây là các domain bắt buộc có admin endpoints trong Phase 2.

---

## Gateway rollout strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Giữ nguyên prefix hiện có | /api/users, /api/products, /api/orders, /api/payments, /api/inventory, /api/notifications | ✓ |
| Đổi prefix mới | Refactor toàn bộ path | |
| Thêm version ngay | /api/v1/... trong Phase 2 | |

**User's choice:** Giữ nguyên prefix hiện có.
**Notes:** Tránh phá tương thích frontend/gateway hiện tại.

| Option | Description | Selected |
|--------|-------------|----------|
| Route update + contract smoke cùng nhịp | Cập nhật gateway song song mở rộng service endpoint | ✓ |
| Service trước, gateway sau | Gom đổi gateway ở cuối phase | |
| Gateway chạm tối thiểu | Chỉ cập nhật khi bắt buộc | |

**User's choice:** Route update + contract smoke cùng nhịp.
**Notes:** Ưu tiên phát hiện mismatch sớm trong từng cụm thay đổi.

---

## Claude's Discretion

- DTO decomposition chi tiết và naming conventions cấp field.
- Các trường hợp ngoại lệ cần hard delete thật sự theo từng service.
- Tổ chức package/controller nội bộ miễn bảo toàn contract đã chốt.

## Deferred Ideas

None.
