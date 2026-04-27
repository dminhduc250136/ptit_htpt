package com.ptit.htpt.userservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Phase 11 / Plan 11-01 (ACCT-05, D-04).
 * JPA entity ánh xạ bảng user_svc.addresses.
 *
 * Không dùng soft-delete — address dùng hard-delete (D-07).
 * Accessor record-style để giảm churn cho service layer.
 *
 * T-11-01-02: Ownership check (userId match) thực hiện ở service layer.
 * T-11-01-04: Partial unique index WHERE is_default=true ở DB đảm bảo SC-3.
 */
@Entity
@Table(name = "addresses", schema = "user_svc")
public class AddressEntity {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36, updatable = false)
    private String userId;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 200)
    private String street;

    @Column(nullable = false, length = 100)
    private String ward;

    @Column(nullable = false, length = 100)
    private String district;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // JPA proxy
    protected AddressEntity() {}

    private AddressEntity(String id, String userId, String fullName, String phone,
                          String street, String ward, String district, String city,
                          boolean isDefault, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.fullName = fullName;
        this.phone = phone;
        this.street = street;
        this.ward = ward;
        this.district = district;
        this.city = city;
        this.isDefault = isDefault;
        this.createdAt = createdAt;
    }

    /**
     * Factory method — tạo address mới với isDefault=false.
     * UUID sinh ngẫu nhiên, createdAt=Instant.now().
     */
    public static AddressEntity create(String userId, String fullName, String phone,
                                       String street, String ward, String district, String city) {
        return new AddressEntity(
            UUID.randomUUID().toString(),
            userId,
            fullName,
            phone,
            street,
            ward,
            district,
            city,
            false,
            Instant.now()
        );
    }

    // Setters
    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setStreet(String street) { this.street = street; }
    public void setWard(String ward) { this.ward = ward; }
    public void setDistrict(String district) { this.district = district; }
    public void setCity(String city) { this.city = city; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }

    // Accessors (record-style)
    public String id() { return id; }
    public String userId() { return userId; }
    public String fullName() { return fullName; }
    public String phone() { return phone; }
    public String street() { return street; }
    public String ward() { return ward; }
    public String district() { return district; }
    public String city() { return city; }
    public boolean isDefault() { return isDefault; }
    public Instant createdAt() { return createdAt; }
}
