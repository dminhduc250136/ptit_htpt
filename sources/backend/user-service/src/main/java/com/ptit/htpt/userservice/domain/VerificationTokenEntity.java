package com.ptit.htpt.userservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Phase 27 / Plan 27-02 (MAIL-02 D-05): JPA entity cho bảng `verification_tokens`.
 *
 * Dùng cho xác thực email (type=EMAIL_VERIFY) và reset mật khẩu (type=PASSWORD_RESET).
 * Token single-use + có hạn — chống replay (Pitfall 4, T-27-04):
 *   verifyAndConsume() dùng SELECT FOR UPDATE + check usedAt IS NULL + set usedAt trong 1 transaction.
 *
 * Accessor record-style (KHÔNG Lombok) — nhất quán với UserEntity convention.
 */
@Entity
@Table(name = "verification_tokens")
public class VerificationTokenEntity {

  @Id
  @Column(length = 64, nullable = false, updatable = false)
  private String token;

  @Column(name = "user_id", length = 36, nullable = false, updatable = false)
  private String userId;

  @Column(length = 20, nullable = false, updatable = false)
  private String type;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private Instant usedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  // JPA proxy
  protected VerificationTokenEntity() {}

  public VerificationTokenEntity(String token, String userId, String type, Instant expiresAt) {
    this.token = token;
    this.userId = userId;
    this.type = type;
    this.expiresAt = expiresAt;
    this.createdAt = Instant.now();
  }

  /** Đánh dấu token đã sử dụng (single-use T-27-04). */
  public void markUsed() {
    this.usedAt = Instant.now();
  }

  public String token() { return token; }
  public String userId() { return userId; }
  public String type() { return type; }
  public Instant expiresAt() { return expiresAt; }
  public Instant usedAt() { return usedAt; }
  public Instant createdAt() { return createdAt; }
}
