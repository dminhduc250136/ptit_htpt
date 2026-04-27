# Phase 10: User-Svc Schema Cluster + Profile Editing - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-27
**Phase:** 10-user-svc-schema-profile-editing
**Areas discussed:** Settings page layout, Avatar serve & fallback, GET /users/me response shape, Navbar user state

---

## Settings page layout

| Option | Description | Selected |
|--------|-------------|----------|
| Sections dọc | Single page với 3 sections độc lập (Profile / Avatar / Security), mỗi section có button riêng | ✓ |
| Tab navigation | Tabs nằm ngang Profile | Avatar | Security — phức tạp hơn | |

**User's choice:** Sections dọc

---

| Option | Description | Selected |
|--------|-------------|----------|
| Silent discard | Navigate ngay khi user rời page với dirty form | ✓ |
| Unsaved changes toast | Toast nhắc nhở nhưng vẫn navigate | |

**User's choice:** Silent discard

---

| Option | Description | Selected |
|--------|-------------|----------|
| Toast "Đã cập nhật" | Popup 3s consistent với admin CRUD | ✓ |
| Inline success banner | Banner xếp trên form (như password form Phase 9) | |

**User's choice:** Toast "Đã cập nhật"

---

## Avatar serve & fallback

| Option | Description | Selected |
|--------|-------------|----------|
| img src trực tiếp | src="/api/users/{id}/avatar" — browser fetch binary | ✓ |
| Blob URL | fetch + URL.createObjectURL — thêm bước, cần cleanup | |

**User's choice:** img src trực tiếp *(note: avatar defer Phase 10, decision recorded for future reference)*

---

| Option | Description | Selected |
|--------|-------------|----------|
| Initials circle | CSS circle với chữ đầu fullName, màu từ userId hash | ✓ |
| Placeholder image | Icon user generic SVG | |

**User's choice:** Initials circle (áp dụng ngay Phase 10 vì avatar defer)

---

| Option | Description | Selected |
|--------|-------------|----------|
| Timestamp query param | ?v=timestamp sau upload → browser re-fetch | ✓ |
| Page reload | router.refresh() toàn trang | |

**User's choice:** Timestamp query param *(recorded for future when avatar ships)*

---

## GET /users/me response shape

| Option | Description | Selected |
|--------|-------------|----------|
| Extend UserDto hiện có | Thêm hasAvatar: boolean vào UserDto | ✓ |
| MeResponse riêng | DTO riêng không lộ roles — cleaner API contract | |

**User's choice:** Extend UserDto hiện có — `{id, username, email, fullName, phone, roles, hasAvatar: boolean, createdAt, updatedAt}`

---

| Option | Description | Selected |
|--------|-------------|----------|
| Bắt buộc Bearer token | 401 nếu thiếu, userId từ JWT claims.sub | ✓ |
| Public endpoint | Không phù hợp — lộ profile | |

**User's choice:** Bắt buộc Bearer token

---

## Navbar user state

| Option | Description | Selected |
|--------|-------------|----------|
| AuthContext / React Context | setUser() sau PATCH /me → navbar re-render ngay | ✓ (initial) |
| localStorage + window event | Custom event dispatch — coupling qua global events | |
| Re-fetch mỗi navigation | GET /me mỗi route change — overhead | |

*Note: Sau khi user feedback về scope simplification, **đổi sang localStorage + router.refresh()** (simpler, không cần Context):*

| Option | Description | Selected |
|--------|-------------|----------|
| localStorage + reload sau save | Update localStorage → router.refresh() | ✓ |
| AuthContext đơn giản | Context provider nhẹ | |

**Final decision:** localStorage + router.refresh()

---

## Claude's Discretion

- Phone validation regex (VN format vs loose)
- Màu initials circle
- Toast implementation (inline state vs react-hot-toast)
- PATCH /me body: partial nullable fields

## Deferred Ideas

- Avatar upload đầy đủ (ACCT-04) — V3 migration, multipart, Thumbnailator, binary serve
- AuthContext full provider
- Unsaved changes guard (beforeunload)

## Key Context Note

User xác nhận mid-discussion: dự án mục đích chính là demo **distributed system + deployment pattern** (không phải hoàn thiện e-commerce features). Avatar feature bị defer để simplify và close milestone nhanh hơn. Approach "đủ dùng" cho toàn bộ account features.
