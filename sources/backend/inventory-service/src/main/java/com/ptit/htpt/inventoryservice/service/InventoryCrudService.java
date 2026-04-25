package com.ptit.htpt.inventoryservice.service;

import com.ptit.htpt.inventoryservice.domain.InventoryItem;
import com.ptit.htpt.inventoryservice.domain.InventoryReservation;
import com.ptit.htpt.inventoryservice.repository.InMemoryInventoryRepository;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventoryCrudService {
  private final InMemoryInventoryRepository repository;

  public InventoryCrudService(InMemoryInventoryRepository repository) {
    this.repository = repository;
  }

  public Map<String, Object> listItems(int page, int size, String sort, boolean includeDeleted) {
    List<InventoryItem> all = repository.findAllItems().stream()
        .filter(item -> includeDeleted || !item.deleted())
        .sorted(itemComparator(sort))
        .toList();
    return paginate(all, page, size);
  }

  public InventoryItem getItem(String id, boolean includeDeleted) {
    InventoryItem item = repository.findItemById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found"));
    if (!includeDeleted && item.deleted()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found");
    }
    return item;
  }

  public InventoryItem createItem(ItemUpsertRequest request) {
    InventoryItem item = InventoryItem.create(request.sku(), request.name(), request.quantity());
    return repository.saveItem(item);
  }

  public InventoryItem updateItem(String id, ItemUpsertRequest request) {
    InventoryItem current = getItem(id, true);
    InventoryItem updated = current.update(request.sku(), request.name(), request.quantity());
    return repository.saveItem(updated);
  }

  public InventoryItem adjustQuantity(String id, QuantityAdjustRequest request) {
    InventoryItem current = getItem(id, true);
    return repository.saveItem(current.adjustQuantity(request.quantity()));
  }

  public void deleteItem(String id) {
    InventoryItem current = getItem(id, true);
    repository.saveItem(current.softDelete());
  }

  public Map<String, Object> listReservations(int page, int size, String sort, boolean includeDeleted) {
    List<InventoryReservation> all = repository.findAllReservations().stream()
        .filter(reservation -> includeDeleted || !reservation.deleted())
        .sorted(reservationComparator(sort))
        .toList();
    return paginate(all, page, size);
  }

  public InventoryReservation getReservation(String id, boolean includeDeleted) {
    InventoryReservation reservation = repository.findReservationById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory reservation not found"));
    if (!includeDeleted && reservation.deleted()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory reservation not found");
    }
    return reservation;
  }

  public InventoryReservation createReservation(ReservationUpsertRequest request) {
    InventoryReservation reservation = InventoryReservation.create(
        request.itemId(),
        request.orderId(),
        request.quantity(),
        request.status()
    );
    return repository.saveReservation(reservation);
  }

  public InventoryReservation updateReservation(String id, ReservationUpsertRequest request) {
    InventoryReservation current = getReservation(id, true);
    InventoryReservation updated = current.update(
        request.itemId(),
        request.orderId(),
        request.quantity(),
        request.status()
    );
    return repository.saveReservation(updated);
  }

  public void deleteReservation(String id) {
    InventoryReservation current = getReservation(id, true);
    repository.saveReservation(current.softDelete());
  }

  private Comparator<InventoryItem> itemComparator(String sort) {
    if (sort == null || sort.isBlank()) {
      return Comparator.comparing(InventoryItem::updatedAt).reversed();
    }
    boolean desc = sort.endsWith(",desc");
    Comparator<InventoryItem> comparator = sort.startsWith("name")
        ? Comparator.comparing(InventoryItem::name, String.CASE_INSENSITIVE_ORDER)
        : Comparator.comparing(InventoryItem::id);
    return desc ? comparator.reversed() : comparator;
  }

  private Comparator<InventoryReservation> reservationComparator(String sort) {
    if (sort == null || sort.isBlank()) {
      return Comparator.comparing(InventoryReservation::updatedAt).reversed();
    }
    boolean desc = sort.endsWith(",desc");
    Comparator<InventoryReservation> comparator = sort.startsWith("status")
        ? Comparator.comparing(InventoryReservation::status, String.CASE_INSENSITIVE_ORDER)
        : Comparator.comparing(InventoryReservation::id);
    return desc ? comparator.reversed() : comparator;
  }

  private <T> Map<String, Object> paginate(List<T> source, int page, int size) {
    int safePage = Math.max(page, 0);
    int safeSize = size <= 0 ? 20 : Math.min(size, 100);
    int totalElements = source.size();
    int from = Math.min(safePage * safeSize, totalElements);
    int to = Math.min(from + safeSize, totalElements);
    List<T> content = new ArrayList<>(source.subList(from, to));
    int totalPages = totalElements == 0 ? 1 : (int) Math.ceil((double) totalElements / safeSize);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("content", content);
    result.put("totalElements", totalElements);
    result.put("totalPages", totalPages);
    result.put("currentPage", safePage);
    result.put("pageSize", safeSize);
    result.put("isFirst", safePage <= 0);
    result.put("isLast", safePage >= Math.max(totalPages - 1, 0));
    return result;
  }

  public record ItemUpsertRequest(@NotBlank String sku, @NotBlank String name, @Min(0) int quantity) {}

  public record QuantityAdjustRequest(@Min(0) int quantity) {}

  public record ReservationUpsertRequest(
      @NotBlank String itemId,
      @NotBlank String orderId,
      @Min(1) int quantity,
      @NotBlank String status
  ) {}
}
