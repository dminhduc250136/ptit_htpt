package com.ptit.htpt.userservice.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Phase 6 / Plan 01 (AUTH-02, D-02): Request body cho POST /auth/login.
 *
 * Login key là email (D-02) — không dùng username để login.
 */
public record LoginRequest(
    @NotBlank @Email String email,
    @NotBlank String password
) {}
