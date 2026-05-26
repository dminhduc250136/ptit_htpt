package com.ptit.htpt.orderservice.exception;

/**
 * Phase 20 / Plan 20-02 (D-11): 8 error code coupon system với HTTP status +
 * Vietnamese default message.
 *
 * <p>Mỗi code = 1 trong 8 fail mode preview/redeem (D-08) + admin DELETE guard
 * (D-14). Field {@code code} của ApiErrorResponse dùng tên enum (ví dụ
 * {@code "COUPON_EXPIRED"}) để FE discriminate.
 *
 * <p>HTTP status mapping (D-11):
 * <ul>
 *   <li>404 NOT_FOUND — coupon không tồn tại trong DB.
 *   <li>422 UNPROCESSABLE_ENTITY — input hợp lệ nhưng business rule reject
 *       (inactive/expired/min order).
 *   <li>409 CONFLICT — race-lose / đã redeem / hết lượt / có redemption.
 * </ul>
 */
public enum CouponErrorCode {
  COUPON_NOT_FOUND(404, "Mã giảm giá không tồn tại"),
  COUPON_INACTIVE(422, "Mã giảm giá đã bị vô hiệu hoá"),
  COUPON_EXPIRED(422, "Mã giảm giá đã hết hạn"),
  COUPON_MIN_ORDER_NOT_MET(422, "Đơn hàng không đạt giá trị tối thiểu để dùng mã này"),
  COUPON_ALREADY_REDEEMED(409, "Bạn đã sử dụng mã giảm giá này"),
  COUPON_MAX_USES_REACHED(409, "Mã giảm giá đã hết lượt sử dụng"),
  COUPON_CONFLICT_OR_EXHAUSTED(409, "Mã giảm giá không còn khả dụng"),
  COUPON_HAS_REDEMPTIONS(409, "Mã giảm giá đã có người dùng — vui lòng tắt thay vì xoá");

  public final int httpStatus;
  public final String defaultMessageVi;

  CouponErrorCode(int httpStatus, String defaultMessageVi) {
    this.httpStatus = httpStatus;
    this.defaultMessageVi = defaultMessageVi;
  }
}
