package com.ptit.htpt.userservice.exception;

/**
 * Phase 9 / Plan 09-03 (AUTH-07).
 * D-11: wrong oldPassword → 422 với errorCode AUTH_INVALID_PASSWORD.
 *
 * Custom exception để GlobalExceptionHandler có thể bind chính xác errorCode
 * "AUTH_INVALID_PASSWORD" vào ApiErrorResponse.code — không thể dùng
 * ResponseStatusException.getReason() vì mapCommonCode() không cover 422.
 */
public class InvalidPasswordException extends RuntimeException {

    public InvalidPasswordException() {
        super("Mật khẩu hiện tại không đúng");
    }
}
