package com.ptit.htpt.userservice.domain;

/**
 * Phase 11 / Plan 11-01 (ACCT-05).
 * Wire format cho address response — không expose internal entity trực tiếp.
 *
 * createdAt là ISO-8601 string để serialization nhất quán.
 */
public record AddressDto(
    String id,
    String userId,
    String fullName,
    String phone,
    String street,
    String ward,
    String district,
    String city,
    boolean isDefault,
    String createdAt  // ISO-8601 string
) {
    public static AddressDto from(AddressEntity e) {
        return new AddressDto(
            e.id(),
            e.userId(),
            e.fullName(),
            e.phone(),
            e.street(),
            e.ward(),
            e.district(),
            e.city(),
            e.isDefault(),
            e.createdAt().toString()
        );
    }
}
