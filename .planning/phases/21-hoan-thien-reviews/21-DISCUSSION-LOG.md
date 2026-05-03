# Phase 21: Hoàn Thiện Reviews - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-02
**Phase:** 21-hoan-thien-reviews
**Mode:** `--auto --chain` (Claude chọn recommended defaults; chain → plan → execute sau)
**Areas discussed:** Edit/Delete authorization, Visibility & avg_rating semantics, Sort UX & API, Admin moderation UI

---

## A. Edit/Delete authorization rules (REV-04)

| Option | Description | Selected |
|--------|-------------|----------|
| Edit window 24h grace (within 24h) | Spec ghi "24h sau publish (configurable)" — diễn giải grace period | ✓ |
| Edit chỉ sau 24h cool-down | Cho phép edit chỉ KHI > 24h | |
| Edit không giới hạn thời gian | Bỏ window | |

**Selected:** Within-24h grace period, configurable qua `app.reviews.edit-window-hours`.
**Notes:** Phù hợp convention Amazon/Shopee; FE đọc giá trị từ BE config endpoint, KHÔNG hard-code.

| Option | Description | Selected |
|--------|-------------|----------|
| Soft-delete (`deleted_at`) | Giữ row, set timestamp | ✓ |
| Hard-delete | Xoá khỏi DB | |

**Selected:** Soft-delete cho author (spec REQ-04 yêu cầu rõ).
**Notes:** Admin được phép hard-delete riêng (xem area D).

| Option | Description | Selected |
|--------|-------------|----------|
| UNIQUE block re-review sau soft-delete | Giữ constraint hiện tại | |
| Partial UNIQUE (WHERE deleted_at IS NULL) | Author re-review được sau khi xoá | ✓ |

**Selected:** Partial UNIQUE — hợp lý khi delete = "rút lại đánh giá".

---

## B. Visibility & avg_rating semantics

| Option | Description | Selected |
|--------|-------------|----------|
| Recompute exclude deleted only | Hidden reviews vẫn count vào rating | |
| Recompute exclude deleted AND hidden | Hidden không ảnh hưởng rating | ✓ |

**Selected:** Exclude both — hidden = vi phạm policy, không tính.
**Notes:** Trigger recompute ở 5 path mới (edit-rating-changed, author-delete, admin-hide, admin-unhide, admin-hard-delete).

| Option | Description | Selected |
|--------|-------------|----------|
| Author thấy review của mình (cả deleted/hidden) | Transparent về moderation | |
| Author KHÔNG thấy deleted/hidden của mình | Sạch, để re-submit | ✓ |

**Selected:** Không thấy — match spec REV-06 "user không thấy nhưng admin vẫn list".

---

## C. Sort UX & API contract (REV-05)

| Option | Description | Selected |
|--------|-------------|----------|
| 3 sort modes (newest/rating_desc/rating_asc) | Helpful defer | ✓ |
| 4 sort modes incl. helpful | Cần votes system | |

**Selected:** 3 modes — REV-05 spec ghi "helpful defer".

| Option | Description | Selected |
|--------|-------------|----------|
| Native `<select>` dropdown | Đơn giản, accessible mặc định | ✓ |
| Custom segmented buttons | Visual hơn nhưng tốn CSS | |
| Tabs | Nặng cho 3 options | |

**Selected:** Native select — đồng nhất với admin filter pattern.

| Option | Description | Selected |
|--------|-------------|----------|
| URL `?sort=` persistence | Share link giữ sort | ✓ |
| Local state only | Không persist | |

**Selected:** URL persistence (spec REV-05 yêu cầu rõ `?sort=`).

---

## D. Admin moderation UI (REV-06)

| Option | Description | Selected |
|--------|-------------|----------|
| Table layout (như /admin/products) | Dense, sort-able | ✓ |
| Card list | Visual hơn, ít row/screen | |

**Selected:** Table — match existing admin patterns.

| Option | Description | Selected |
|--------|-------------|----------|
| Filter dropdown 4 trạng thái (all/visible/hidden/deleted) | Admin xem cả review tác giả đã xoá | ✓ |
| Filter chỉ visible/hidden | Ẩn deleted khỏi admin | |

**Selected:** 4-state filter — admin cần thấy soft-deleted để phát hiện abuse.

| Option | Description | Selected |
|--------|-------------|----------|
| Admin "Xoá" = hard delete | Vĩnh viễn dọn DB | ✓ |
| Admin "Xoá" = soft delete như author | Reversible | |

**Selected:** Hard delete cho admin — khác semantic với author (admin là dọn spam, không phải rút lại).

| Option | Description | Selected |
|--------|-------------|----------|
| Hide = `hidden BOOLEAN` column | Spec REV-06 chỉ định | ✓ |
| Hide = thêm row vào bảng `moderation_actions` | Audit log đầy đủ | |

**Selected:** Boolean column theo spec; audit log defer.

| Option | Description | Selected |
|--------|-------------|----------|
| Pagination server-side `?page=&size=20` | Nhất quán với /admin/products | ✓ |
| Pagination client-side (infinite scroll) | Không phù hợp admin | |

**Selected:** Server-side pagination.

| Option | Description | Selected |
|--------|-------------|----------|
| Keyword search trên admin list | Filter content/reviewer | |
| Không search (filter dropdown đủ) | Defer keyword | ✓ |

**Selected:** Defer keyword search — phase scope giữ tối giản.

---

## E. Database migration

| Option | Description | Selected |
|--------|-------------|----------|
| V7 single migration (deleted_at + hidden + UNIQUE migrate) | 1 file | ✓ |
| V7 + V8 tách 2 migration | Atomic concerns riêng | |

**Selected:** Single V7 — concerns tightly coupled (cùng feature, cùng review).

---

## Claude's Discretion

- CSS layout chính xác cho action buttons trong review item
- Component confirmation dialog (reuse vs window.confirm)
- Resolve productSlug ở admin list (SQL JOIN vs FE batch)
- Optimistic update vs full refetch sau edit/delete (chọn full refetch)
- Toast wording chính xác

## Deferred Ideas

- REV-05 helpful sort (cần votes system)
- Admin keyword search trên /admin/reviews
- Bulk moderation actions
- Audit log admin moderation
- Edit history / revisions
- Email notify reviewer khi bị hide
- Rate limit author edit
