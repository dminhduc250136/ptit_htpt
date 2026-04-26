package com.ptit.htpt.userservice.repository;

import com.ptit.htpt.userservice.domain.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA repository cho `user_svc.users`. `findByUsername` + `findByEmail` cho Phase 6 auth.
 */
public interface UserRepository extends JpaRepository<UserEntity, String> {
  Optional<UserEntity> findByUsername(String username);
  Optional<UserEntity> findByEmail(String email);
}
