package com.ptit.htpt.userservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Phase 5 / Plan 04 (DB-02..05): JPA entity cho bảng `user_svc.users`.
 * Refactor từ record `UserProfile` cũ (fullName/phone/blocked) sang model auth-focused
 * (username/passwordHash/roles) — phục vụ Phase 6 login/JWT.
 *
 * Phase 7 / Plan 03 (D-04): Thêm fullName + phone fields + setters + touch().
 *
 * Soft-delete qua @SQLRestriction + @SQLDelete (Hibernate 6).
 * Accessor giữ record-style (`username()`, `email()`, ...) để giảm churn cho service layer.
 */
@Entity
@Table(name = "users", schema = "user_svc")
@SQLRestriction("deleted = false")
@SQLDelete(sql = "UPDATE user_svc.users SET deleted = true, updated_at = NOW() WHERE id = ?")
public class UserEntity {

  @Id
  @Column(length = 36, nullable = false, updatable = false)
  private String id;

  @Column(nullable = false, length = 80, unique = true)
  private String username;

  @Column(nullable = false, length = 200, unique = true)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 120)
  private String passwordHash;

  @Column(nullable = false, length = 200)
  private String roles;

  @Column(name = "full_name", length = 120)
  private String fullName;

  @Column(length = 20)
  private String phone;

  @Column(nullable = false)
  private boolean deleted = false;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  // JPA proxy
  protected UserEntity() {}

  protected UserEntity(String id, String username, String email, String passwordHash,
                       String roles, String fullName, String phone,
                       boolean deleted, Instant createdAt, Instant updatedAt) {
    this.id = id;
    this.username = username;
    this.email = email;
    this.passwordHash = passwordHash;
    this.roles = roles;
    this.fullName = fullName;
    this.phone = phone;
    this.deleted = deleted;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static UserEntity create(String username, String email, String passwordHash, String roles) {
    Instant now = Instant.now();
    return new UserEntity(UUID.randomUUID().toString(), username, email, passwordHash,
        roles == null || roles.isBlank() ? "CUSTOMER" : roles, null, null, false, now, now);
  }

  public void update(String username, String email, String roles) {
    this.username = username;
    this.email = email;
    this.roles = roles;
    this.updatedAt = Instant.now();
  }

  public void changePasswordHash(String newPasswordHash) {
    this.passwordHash = newPasswordHash;
    this.updatedAt = Instant.now();
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
    this.updatedAt = Instant.now();
  }

  public void setPhone(String phone) {
    this.phone = phone;
    this.updatedAt = Instant.now();
  }

  public void setRoles(String roles) {
    this.roles = roles;
    this.updatedAt = Instant.now();
  }

  public void touch() {
    this.updatedAt = Instant.now();
  }

  public void softDelete() {
    this.deleted = true;
    this.updatedAt = Instant.now();
  }

  public String id() { return id; }
  public String username() { return username; }
  public String email() { return email; }
  public String passwordHash() { return passwordHash; }
  public String roles() { return roles; }
  public String fullName() { return fullName; }
  public String phone() { return phone; }
  public boolean deleted() { return deleted; }
  public Instant createdAt() { return createdAt; }
  public Instant updatedAt() { return updatedAt; }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof UserEntity that)) return false;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
