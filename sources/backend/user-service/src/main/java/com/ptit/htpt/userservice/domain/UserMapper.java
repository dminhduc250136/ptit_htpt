package com.ptit.htpt.userservice.domain;

/** Entity -> DTO boundary tại service layer (RESEARCH §Decision #8). */
public final class UserMapper {
  private UserMapper() {}

  public static UserDto toDto(UserEntity e) {
    return new UserDto(
        e.id(),
        e.username(),
        e.email(),
        e.roles(),
        e.createdAt(),
        e.updatedAt()
    );
  }
}
