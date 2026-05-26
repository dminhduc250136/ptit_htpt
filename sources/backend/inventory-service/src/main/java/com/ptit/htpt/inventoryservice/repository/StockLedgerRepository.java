package com.ptit.htpt.inventoryservice.repository;

import com.ptit.htpt.inventoryservice.domain.StockLedgerEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockLedgerRepository extends JpaRepository<StockLedgerEntity, Long> {
  List<StockLedgerEntity> findByOrderId(String orderId);
  List<StockLedgerEntity> findByEventId(String eventId);
}
