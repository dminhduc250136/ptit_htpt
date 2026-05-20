package com.ptit.htpt.notificationservice.repository;

import com.ptit.htpt.notificationservice.domain.DispatchLogEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * D-14: repo cho dispatch_log. Lookup theo eventId (debug/audit) hoặc recipientUserId (user history).
 */
public interface DispatchLogRepository extends JpaRepository<DispatchLogEntity, String> {

  List<DispatchLogEntity> findByEventId(String eventId);

  List<DispatchLogEntity> findByRecipientUserId(String recipientUserId);
}
