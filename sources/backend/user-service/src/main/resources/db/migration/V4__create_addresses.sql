-- Phase 11 / Plan 11-01 (ACCT-05, D-04, D-05)
-- addresses table: id UUID, user_id UUID FK (không enforce FK để tránh cross-schema issue),
-- full_name, phone, street, ward, district, city, is_default, created_at

CREATE TABLE user_svc.addresses (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id     UUID         NOT NULL,
    full_name   VARCHAR(100) NOT NULL,
    phone       VARCHAR(20)  NOT NULL,
    street      VARCHAR(200) NOT NULL,
    ward        VARCHAR(100) NOT NULL,
    district    VARCHAR(100) NOT NULL,
    city        VARCHAR(100) NOT NULL,
    is_default  BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- D-05: Partial unique index — chỉ 1 row is_default=true per user_id
-- Enforces SC-3 (concurrent set-default)
CREATE UNIQUE INDEX idx_addresses_user_default
    ON user_svc.addresses (user_id)
    WHERE is_default = true;

-- Index cho query by user_id (sort by created_at DESC)
CREATE INDEX idx_addresses_user_created
    ON user_svc.addresses (user_id, created_at DESC);
