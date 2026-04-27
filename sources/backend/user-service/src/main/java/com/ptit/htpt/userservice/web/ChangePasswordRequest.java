package com.ptit.htpt.userservice.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Phase 9 / Plan 09-03 (AUTH-07).
 * D-11: endpoint dedicated POST /users/me/password.
 *
 * Validation: newPassword min 8 + ít nhất 1 letter + 1 number (Claude's Discretion CONTEXT.md).
 * T-09-03-03 mitigated: KHÔNG override toString() — default record toString() include
 * field names/values nên KHÔNG log request body trong controller.
 */
public record ChangePasswordRequest(
    @NotBlank(message = "oldPassword required")
    String oldPassword,

    @NotBlank(message = "newPassword required")
    @Size(min = 8, message = "newPassword must be at least 8 characters")
    @Pattern(regexp = ".*[A-Za-z].*", message = "newPassword must contain at least 1 letter")
    @Pattern(regexp = ".*\\d.*", message = "newPassword must contain at least 1 number")
    String newPassword
) {}
