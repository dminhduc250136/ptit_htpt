package com.ptit.htpt.userservice.web;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Phase 10 / Plan 10-01 (ACCT-03).
 * DTO record cho PATCH /users/me body — partial update (nullable optional fields).
 *
 * Per CONTEXT.md Claude's Discretion + PATTERNS.md §2:
 * - @Size/@Pattern chỉ kick in khi field not-null → nullable optional fields tương thích.
 * - KHÔNG dùng @NotBlank để cho phép partial update (chỉ gửi field cần thay đổi).
 */
public record UpdateMeRequest(
    @Size(min = 1, max = 120, message = "Họ tên phải từ 1 đến 120 ký tự")
    String fullName,   // nullable — chỉ validate khi not-null

    @Pattern(regexp = "^\\+?[0-9\\s-]{7,20}$",
             message = "Số điện thoại không hợp lệ")
    String phone       // nullable — chỉ validate khi not-null
) {}
