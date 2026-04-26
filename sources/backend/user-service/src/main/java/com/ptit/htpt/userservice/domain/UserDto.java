package com.ptit.htpt.userservice.domain;

import java.time.Instant;

/**
 * Wire format — controller response. KHÔNG có `passwordHash`, KHÔNG có `deleted`
 * (Pitfall 3 / RESEARCH §Decision #8 — tránh leak qua Jackson).
 */
public record UserDto(
    String id,
    String username,
    String email,
    String roles,
    Instant createdAt,
    Instant updatedAt
) {}
