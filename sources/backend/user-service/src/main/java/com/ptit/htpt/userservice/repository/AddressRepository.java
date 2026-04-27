package com.ptit.htpt.userservice.repository;

import com.ptit.htpt.userservice.domain.AddressEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Phase 11 / Plan 11-01 (ACCT-05).
 * JPA repository cho user_svc.addresses.
 *
 * findByUserIdOrderByIsDefaultDescCreatedAtDesc: T-11-01-05 — filter by userId đảm bảo
 * user chỉ thấy addresses của mình.
 *
 * clearDefaultByUserId: dùng @Modifying + @Query để bulk-clear is_default trước khi
 * set default mới — T-11-01-04 (partial unique index enforces SC-3 at DB level).
 */
public interface AddressRepository extends JpaRepository<AddressEntity, String> {

    List<AddressEntity> findByUserIdOrderByIsDefaultDescCreatedAtDesc(String userId);

    long countByUserId(String userId);

    @Modifying
    @Query("UPDATE AddressEntity a SET a.isDefault = false WHERE a.userId = :userId AND a.isDefault = true")
    void clearDefaultByUserId(@Param("userId") String userId);
}
