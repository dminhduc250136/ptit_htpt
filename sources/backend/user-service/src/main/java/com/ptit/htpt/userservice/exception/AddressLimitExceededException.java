package com.ptit.htpt.userservice.exception;

/**
 * Phase 11 / Plan 11-01 (ACCT-05, D-06).
 * T-11-01-03: Cap 10 addresses/user — throw khi countByUserId(userId) >= 10.
 * GlobalExceptionHandler map → 422 UNPROCESSABLE_ENTITY với code ADDRESS_LIMIT_EXCEEDED.
 */
public class AddressLimitExceededException extends RuntimeException {

    public AddressLimitExceededException() {
        super("ADDRESS_LIMIT_EXCEEDED");
    }
}
