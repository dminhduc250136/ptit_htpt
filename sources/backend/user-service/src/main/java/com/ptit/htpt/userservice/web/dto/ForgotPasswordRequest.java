package com.ptit.htpt.userservice.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Phase 27 / Plan 27-04 (MAIL-02): Request body cho POST /auth/password/forgot.
 *
 * Anti-enumeration (D-08, T-27-10): endpoint luôn trả 200 bất kể email tồn tại hay không.
 * Validation đơn giản: chỉ kiểm tra format email hợp lệ.
 */
public record ForgotPasswordRequest(
    @Email @NotBlank String email
) {}
