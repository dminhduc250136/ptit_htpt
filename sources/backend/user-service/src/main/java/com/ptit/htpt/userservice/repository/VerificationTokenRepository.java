package com.ptit.htpt.userservice.repository;

import com.ptit.htpt.userservice.domain.VerificationTokenEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Phase 27 / Plan 27-02 (MAIL-02): Repository cho verification_tokens.
 *
 * findByTokenForUpdate dùng SELECT FOR UPDATE (pessimistic lock) để đảm bảo single-use:
 * 2 request concurrent dùng cùng token — chỉ 1 transaction có thể acquire lock + set usedAt
 * (T-27-04 Replay mitigation, Pitfall 4 RESEARCH).
 */
public interface VerificationTokenRepository extends JpaRepository<VerificationTokenEntity, String> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT v FROM VerificationTokenEntity v WHERE v.token = :token")
  Optional<VerificationTokenEntity> findByTokenForUpdate(@Param("token") String token);
}
