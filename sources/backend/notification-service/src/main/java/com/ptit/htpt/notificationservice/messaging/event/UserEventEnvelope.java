package com.ptit.htpt.notificationservice.messaging.event;

/**
 * JSON envelope cho event tài khoản người dùng: UserRegistered, PasswordResetRequested.
 *
 * <p>Đồng bộ với user-service UserEventEnvelope (Plan 27-02).
 * notification-service chỉ DESERIALIZE (consumer) — không cần factory method.
 * UserPayload mang đủ dữ liệu để gửi email mà KHÔNG gọi REST cross-service (D-11).
 *
 * <p>KHÔNG dùng @JsonTypeInfo/@JsonSubTypes vì UserPayload là typed cố định
 * (không có union type như OrderEventEnvelope) — Jackson deserialize trực tiếp.
 *
 * <p>KHÔNG Lombok — record Java.
 */
public record UserEventEnvelope(
    String eventId,
    String eventType,
    String occurredAt,
    String traceId,
    UserPayload payload
) {

  /**
   * Payload chứa thông tin người dùng và link hành động.
   * actionUrl là link đầy đủ đã dựng sẵn từ APP_BASE_URL + token (T-27-13: không client-cung-cấp URL).
   */
  public record UserPayload(
      String userId,
      String email,
      String fullName,
      String actionUrl
  ) {}
}
