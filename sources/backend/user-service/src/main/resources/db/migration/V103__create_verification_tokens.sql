-- Phase 27 / Plan 27-02: bảng token xác minh/reset (MAIL-02 D-05)
CREATE TABLE IF NOT EXISTS verification_tokens (
  token       VARCHAR(64)  PRIMARY KEY,
  user_id     VARCHAR(36)  NOT NULL,
  type        VARCHAR(20)  NOT NULL CHECK (type IN ('EMAIL_VERIFY', 'PASSWORD_RESET')),
  expires_at  TIMESTAMPTZ  NOT NULL,
  used_at     TIMESTAMPTZ  NULL,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_vt_user_id ON verification_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_vt_type_used ON verification_tokens(type, used_at) WHERE used_at IS NULL;
