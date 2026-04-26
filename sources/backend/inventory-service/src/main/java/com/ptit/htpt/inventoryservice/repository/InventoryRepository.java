package com.ptit.htpt.inventoryservice.repository;

import com.ptit.htpt.inventoryservice.domain.InventoryEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<InventoryEntity, String> {
  Optional<InventoryEntity> findByProductId(String productId);
}
