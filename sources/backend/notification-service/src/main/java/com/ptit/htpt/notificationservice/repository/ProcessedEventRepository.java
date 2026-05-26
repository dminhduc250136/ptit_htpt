package com.ptit.htpt.notificationservice.repository;

import com.ptit.htpt.notificationservice.domain.ProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * D-06: idempotency repo. Native INSERT ... ON CONFLICT DO NOTHING atomic:
 *  - return 1 nếu insert thực sự (eventId chưa tồn tại) → tiếp tục business
 *  - return 0 nếu duplicate (eventId đã tồn tại) → skip business + ACK
 *
 * <p>Pattern 4 (RESEARCH §391-403). Tách native query khỏi default method để Spring proxy
 * intercept @Modifying + @Transactional đúng.
 */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, String> {

  @Modifying
  @Transactional
  @Query(value = "INSERT INTO processed_events(event_id, event_type) "
               + "VALUES (:id, :type) ON CONFLICT (event_id) DO NOTHING",
         nativeQuery = true)
  int insertNative(@Param("id") String eventId, @Param("type") String eventType);

  default boolean insertIfAbsent(String eventId, String eventType) {
    return insertNative(eventId, eventType) == 1;
  }
}
