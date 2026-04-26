-- Phase 5 / Plan 04 (DB-05): dev-only seed (profile=dev) cho user-service.
-- BCrypt hash $2a$10$TMH2spmmPRD90vJz8w5yz.G0o4AR/Hio2RU1yBwjjT1ClTLqF5lFu
-- = `admin123` (verified Plan 01, file baseline/bcrypt-hash-verified.txt).
-- Cross-service IDs (Plan 04 §<cross_service_ids>) — KHÔNG đổi literal:
--   admin: 00000000-0000-0000-0000-000000000001
--   demo:  00000000-0000-0000-0000-000000000002
INSERT INTO user_svc.users (id, username, email, password_hash, roles, deleted, created_at, updated_at)
VALUES
  ('00000000-0000-0000-0000-000000000001', 'admin',     'admin@tmdt.local',
   '$2a$10$TMH2spmmPRD90vJz8w5yz.G0o4AR/Hio2RU1yBwjjT1ClTLqF5lFu', 'ADMIN', FALSE, NOW(), NOW()),
  ('00000000-0000-0000-0000-000000000002', 'demo_user', 'demo@tmdt.local',
   '$2a$10$TMH2spmmPRD90vJz8w5yz.G0o4AR/Hio2RU1yBwjjT1ClTLqF5lFu', 'USER',  FALSE, NOW(), NOW());
