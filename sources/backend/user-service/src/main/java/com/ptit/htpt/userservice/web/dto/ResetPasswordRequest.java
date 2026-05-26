package com.ptit.htpt.userservice.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Phase 27 / Plan 27-04 (MAIL-02): Request body cho POST /auth/password/reset.
 *
 * token: token nhận từ email reset-password (single-use, 256-bit entropy).
 * newPassword: mật khẩu mới tối thiểu 6 ký tự (nhất quán với RegisterRequest constraint).
 */
public record ResetPasswordRequest(
    @NotBlank String token,
    @NotBlank @Size(min = 6) String newPassword
) {}
