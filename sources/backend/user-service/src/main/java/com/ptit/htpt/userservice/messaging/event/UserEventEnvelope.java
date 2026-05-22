package com.ptit.htpt.userservice.messaging.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Phase 27 / Plan 27-02 (MAIL-02): JSON envelope cho user account events.
 *
 * eventType ∈ {"UserRegistered", "PasswordResetRequested"}
 * routing keys: "user.registered" / "user.password-reset"
 *
 * Contract với notification-service (Wave 3): consume từ queue notification.user-events,
 * deserialize UserPayload để gửi email xác minh / reset mật khẩu.
 */
public record UserEventEnvelope(
    String eventId,
    String eventType,
    String occurredAt,
    String traceId,
    UserPayload payload
) {
  public static UserEventEnvelope createUserRegistered(String traceId, UserPayload payload) {
    return new UserEventEnvelope(
        UUID.randomUUID().toString(),
        "UserRegistered",
        Instant.now().toString(),
        traceId,
        payload
    );
  }

  public static UserEventEnvelope createPasswordReset(String traceId, UserPayload payload) {
    return new UserEventEnvelope(
        UUID.randomUUID().toString(),
        "PasswordResetRequested",
        Instant.now().toString(),
        traceId,
        payload
    );
  }

  /**
   * Payload mang đủ dữ liệu để notification-service render email
   * mà KHÔNG cần gọi REST cross-service.
   *
   * actionUrl: URL chứa token (vd /verify-email?token=... hoặc /reset-password?token=...).
   */
  public record UserPayload(String userId, String email, String fullName, String actionUrl) {}
}
