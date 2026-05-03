package com.ptit.htpt.userservice.repository;

import com.ptit.htpt.userservice.domain.UserEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * JPA repository cho `user_svc.users`. `findByUsername` + `findByEmail` cho Phase 6 auth.
 */
public interface UserRepository extends JpaRepository<UserEntity, String> {
  Optional<UserEntity> findByUsername(String username);
  Optional<UserEntity> findByEmail(String email);

  /**
   * Phase 19 / Plan 19-02 (ADMIN-04): aggregate user signups theo ngày từ {@code created_at}.
   *
   * <p>Pitfall #3 (RESEARCH): dùng {@code FUNCTION('DATE', col)} để Hibernate map ra
   * Postgres {@code DATE(col)} (KHÔNG raw {@code DATE()} JPQL — không support).
   * Nullable {@code from} dùng {@code cast(:from as timestamp) IS NULL} idiom (analog order-svc).
   *
   * @return list of {@code [java.sql.Date day, Long count]} sorted ASC by day.
   */
  @Query("""
      SELECT FUNCTION('DATE', u.createdAt) AS day, COUNT(u) AS cnt
      FROM UserEntity u
      WHERE (cast(:from as timestamp) IS NULL OR u.createdAt >= :from)
      GROUP BY FUNCTION('DATE', u.createdAt)
      ORDER BY day ASC
      """)
  List<Object[]> aggregateSignupsByDay(@Param("from") Instant from);
}
