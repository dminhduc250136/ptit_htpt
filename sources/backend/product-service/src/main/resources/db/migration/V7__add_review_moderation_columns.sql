-- V7__add_review_moderation_columns.sql
-- Phase 21 / Plan 01: REV-04 + REV-06 — moderation columns + partial UNIQUE.
-- Pre-V7 verify A4: ReviewEntity không có @SQLRestriction; visibility lọc ở repo layer.
-- Pre-V7 verify A8: OK — ProductEntity.updateRatingStats null-safe (avgRating != null ? avgRating : ZERO)
--                   và clamp reviewCount ≥ 0; recompute reset về (0, 0) khi không còn review hợp lệ.

ALTER TABLE product_svc.reviews
  ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE NULL,
  ADD COLUMN IF NOT EXISTS hidden     BOOLEAN NOT NULL DEFAULT FALSE;

-- Drop old UNIQUE constraint (V4: uq_review_product_user) — KHÔNG dùng được vì block re-review sau soft-delete (D-06).
ALTER TABLE product_svc.reviews
  DROP CONSTRAINT IF EXISTS uq_review_product_user;

-- Re-create as PARTIAL unique index — chỉ enforce khi review chưa soft-delete.
CREATE UNIQUE INDEX IF NOT EXISTS uq_review_product_user_active
  ON product_svc.reviews (product_id, user_id)
  WHERE deleted_at IS NULL;

-- Index hỗ trợ admin filter + public list visibility WHERE.
CREATE INDEX IF NOT EXISTS idx_reviews_visibility
  ON product_svc.reviews (product_id, hidden, deleted_at);
