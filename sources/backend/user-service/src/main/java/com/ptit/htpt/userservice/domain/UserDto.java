package com.ptit.htpt.userservice.domain;

import java.time.Instant;

/**
 * Wire format — controller response. KHÔNG có `passwordHash`, KHÔNG có `deleted`
 * (Pitfall 3 / RESEARCH §Decision #8 — tránh leak qua Jackson).
 *
 * Phase 7 / Plan 03 (D-04): Thêm fullName + phone fields (nullable).
 * Phase 10 / Plan 10-01 (D-06): Thêm hasAvatar boolean — Phase 10 luôn false (avatar defer per D-08).
 */
public record UserDto(
    String id,
    String username,
    String email,
    String roles,
    String fullName,      // nullable — D-04
    String phone,         // nullable — D-04
    boolean hasAvatar,    // D-06: Phase 10 luôn false (avatar defer per D-08)
    Instant createdAt,
    Instant updatedAt
) {}
