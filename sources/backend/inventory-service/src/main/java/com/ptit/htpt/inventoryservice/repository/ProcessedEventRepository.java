package com.ptit.htpt.inventoryservice.repository;

import com.ptit.htpt.inventoryservice.domain.ProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * D-06 idempotency: insertIfAbsent dựa trên native ON CONFLICT DO NOTHING.
 * Return true nếu insert thực sự thêm row (event mới); false nếu duplicate (đã xử lý trước đó).
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
