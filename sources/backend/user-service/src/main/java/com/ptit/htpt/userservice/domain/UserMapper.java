package com.ptit.htpt.userservice.domain;

/** Entity -> DTO boundary tại service layer (RESEARCH §Decision #8).
 *
 * Phase 7 / Plan 03 (D-04): Map thêm fullName + phone.
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
        e.createdAt(),
        e.updatedAt()
    );
  }
}
