package com.ptit.htpt.userservice.domain;

/** Entity -> DTO boundary tại service layer (RESEARCH §Decision #8).
 *
 * Phase 7 / Plan 03 (D-04): Map thêm fullName + phone.
 * Phase 10 / Plan 10-01 (D-06): Map hasAvatar=false (avatar defer per D-08).
 */
public final class UserMapper {
  private UserMapper() {}

  public static UserDto toDto(UserEntity e) {
    return new UserDto(
        e.id(),
        e.username(),
        e.email(),
        e.roles(),
        e.fullName(),     // D-04
        e.phone(),        // D-04
        false,            // D-06: hasAvatar Phase 10 luôn false (avatar defer per D-08)
        e.createdAt(),
        e.updatedAt()
    );
  }
}
