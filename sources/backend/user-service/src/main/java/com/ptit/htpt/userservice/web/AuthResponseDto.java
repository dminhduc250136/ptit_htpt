package com.ptit.htpt.userservice.web;

import com.ptit.htpt.userservice.domain.UserDto;

/**
 * Phase 6 / Plan 01 (AUTH-01, AUTH-02, D-03): Response body cho register + login.
 *
 * Cùng shape cho cả hai endpoints (D-03).
 * Không có refreshToken field — deferred per CONTEXT.md Deferred section.
 * UserDto không có passwordHash field (T-06-03 threat mitigation verified).
 */
public record AuthResponseDto(
    String accessToken,
    UserDto user
) {}
