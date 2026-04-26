package com.ptit.htpt.inventoryservice.domain;

public final class InventoryMapper {
  private InventoryMapper() {}

  public static InventoryDto toDto(InventoryEntity e) {
    return new InventoryDto(
        e.id(), e.productId(), e.quantity(), e.reserved(),
        e.createdAt(), e.updatedAt()
    );
  }
}
