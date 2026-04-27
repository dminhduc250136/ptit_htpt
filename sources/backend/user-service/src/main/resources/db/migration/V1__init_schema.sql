-- Phase 5 / Plan 04 (DB-02): user-service initial schema.
-- Schema `user_svc` đã được pre-created bởi db/init/01-schemas.sql (Plan 02).
CREATE TABLE user_svc.users (
  id VARCHAR(36) PRIMARY KEY,
  username VARCHAR(80) NOT NULL,
  email VARCHAR(200) NOT NULL,
  password_hash VARCHAR(120) NOT NULL,
  roles VARCHAR(200) NOT NULL DEFAULT 'CUSTOMER',
  deleted BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT uq_users_username UNIQUE (username),
  CONSTRAINT uq_users_email UNIQUE (email),
  CONSTRAINT ck_users_role CHECK (roles IN ('ADMIN', 'CUSTOMER'))
);
