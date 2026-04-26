package com.ptit.htpt.userservice.domain;

import java.time.Instant;

/**
 * Wire format — controller response. KHÔNG có `passwordHash`, KHÔNG có `deleted`
 * (Pitfall 3 / RESEARCH §Decision #8 — tránh leak qua Jackson).
 *
 * Phase 7 / Plan 03 (D-04): Thêm fullName + phone fields (nullable).
 */
public record UserDto(
    String id,
    String username,
    String email,
    String roles,
    String fullName,      // nullable — D-04
    String phone,         // nullable — D-04
    Instant createdAt,
    Instant updatedAt
) {}
