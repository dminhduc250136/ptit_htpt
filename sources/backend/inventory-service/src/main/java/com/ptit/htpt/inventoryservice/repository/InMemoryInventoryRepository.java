package com.ptit.htpt.inventoryservice.repository;

import com.ptit.htpt.inventoryservice.domain.InventoryItem;
import com.ptit.htpt.inventoryservice.domain.InventoryReservation;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryInventoryRepository {
  private final Map<String, InventoryItem> items = new LinkedHashMap<>();
  private final Map<String, InventoryReservation> reservations = new LinkedHashMap<>();

  public Collection<InventoryItem> findAllItems() {
    return items.values();
  }

  public Optional<InventoryItem> findItemById(String id) {
    return Optional.ofNullable(items.get(id));
  }

  public InventoryItem saveItem(InventoryItem item) {
    items.put(item.id(), item);
    return item;
  }

  public Collection<InventoryReservation> findAllReservations() {
    return reservations.values();
  }

  public Optional<InventoryReservation> findReservationById(String id) {
    return Optional.ofNullable(reservations.get(id));
  }

  public InventoryReservation saveReservation(InventoryReservation reservation) {
    reservations.put(reservation.id(), reservation);
    return reservation;
  }
}
