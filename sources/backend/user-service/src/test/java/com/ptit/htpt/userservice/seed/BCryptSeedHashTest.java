package com.ptit.htpt.userservice.seed;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 5 / DB-05: verify reference BCrypt hash will be embedded into
 * user-service V2__seed_dev_data.sql for admin user (RESEARCH §Decision #6).
 * Must run green BEFORE V2 SQL is committed — Assumption A1.
 */
class BCryptSeedHashTest {

  private static final String ADMIN_PASSWORD = "admin123";
  // NOTE Phase 5 / Plan 05-01: Hash literal originally suggested in
  // RESEARCH §Decision #6 ("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy")
  // is a well-known Spring Security docs sample that hashes the literal "password",
  // NOT "admin123". Verified empirically via BCryptPasswordEncoder.matches=false.
  // Replaced with a freshly-generated hash (cost=10) that verifies green.
  // Downstream Plan 05-03 (user-service V2__seed_dev_data.sql) MUST embed this exact value.
  private static final String SEED_HASH =
      "$2a$10$TMH2spmmPRD90vJz8w5yz.G0o4AR/Hio2RU1yBwjjT1ClTLqF5lFu";

  @Test
  void seedHashMatchesAdminPassword() {
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    assertTrue(encoder.matches(ADMIN_PASSWORD, SEED_HASH),
        "Seed hash must verify against `admin123`");
  }

  @Test
  void seedHashRejectsWrongPassword() {
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    assertFalse(encoder.matches("wrong-password", SEED_HASH));
  }
}
