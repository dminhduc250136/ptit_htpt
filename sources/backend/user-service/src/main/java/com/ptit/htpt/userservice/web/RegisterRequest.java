package com.ptit.htpt.userservice.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Phase 6 / Plan 01 (AUTH-01, D-01): Request body cho POST /auth/register.
 *
 * 3 fields theo D-01: username, email, password.
 * fullName và phone không có (removed per CONTEXT.md D-01).
 * confirmPassword validate FE-side — không gửi lên backend.
 */
public record RegisterRequest(
    @NotBlank @Size(min = 3, max = 80) String username,
    @NotBlank @Email String email,
    @NotBlank @Size(min = 6, max = 100) String password
) {}
