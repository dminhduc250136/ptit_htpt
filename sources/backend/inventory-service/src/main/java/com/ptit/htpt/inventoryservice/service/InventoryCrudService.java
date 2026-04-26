package com.ptit.htpt.inventoryservice.service;

import com.ptit.htpt.inventoryservice.domain.InventoryDto;
import com.ptit.htpt.inventoryservice.domain.InventoryEntity;
import com.ptit.htpt.inventoryservice.domain.InventoryMapper;
import com.ptit.htpt.inventoryservice.repository.InventoryRepository;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Inventory CRUD service — JPA-backed, schema {@code inventory_svc}.
 *
 * <p>Phase 5 scope-cut: reservation flow removed (record cũ có {@link
 * com.ptit.htpt.inventoryservice.domain InventoryReservation} + reservation paths trong
 * controllers). Phase 8 sẽ re-introduce reservation entity + stock decrement on order checkout.
 */
@Service
public class InventoryCrudService {
  private final InventoryRepository inventoryRepository;

  public InventoryCrudService(InventoryRepository inventoryRepository) {
    this.inventoryRepository = inventoryRepository;
  }

  public Map<String, Object> listItems(int page, int size, String sort) {
    List<InventoryDto> all = inventoryRepository.findAll().stream()
        .sorted(itemComparator(sort))
        .map(InventoryMapper::toDto)
        .toList();
    return paginate(all, page, size);
  }

  public InventoryDto getItem(String id) {
    return InventoryMapper.toDto(loadItem(id));
  }

  public InventoryDto createItem(ItemUpsertRequest request) {
    InventoryEntity entity = InventoryEntity.create(request.productId(), request.quantity(), request.reserved());
    try {
      return InventoryMapper.toDto(inventoryRepository.save(entity));
    } catch (DataIntegrityViolationException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "Inventory item already exists for product " + request.productId(), ex);
    }
  }

  public InventoryDto updateItem(String id, ItemUpsertRequest request) {
    InventoryEntity current = loadItem(id);
    if (!current.productId().equals(request.productId())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "productId is immutable; create a new inventory item instead");
    }
    current.update(request.quantity(), request.reserved());
    return InventoryMapper.toDto(inventoryRepository.save(current));
  }

  public InventoryDto adjustQuantity(String id, QuantityAdjustRequest request) {
    InventoryEntity current = loadItem(id);
    current.adjustQuantity(request.quantity());
    return InventoryMapper.toDto(inventoryRepository.save(current));
  }

  public void deleteItem(String id) {
    InventoryEntity current = loadItem(id);
    inventoryRepository.delete(current);
  }

  private InventoryEntity loadItem(String id) {
    return inventoryRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found"));
  }

  private Comparator<InventoryEntity> itemComparator(String sort) {
    if (sort == null || sort.isBlank()) {
      return Comparator.comparing(InventoryEntity::updatedAt).reversed();
    }
    boolean desc = sort.endsWith(",desc");
    Comparator<InventoryEntity> comparator = sort.startsWith("productId")
        ? Comparator.comparing(InventoryEntity::productId)
        : Comparator.comparing(InventoryEntity::id);
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

  public record ItemUpsertRequest(
      @NotBlank String productId,
      @Min(0) int quantity,
      @Min(0) int reserved
  ) {}

  public record QuantityAdjustRequest(@Min(0) int quantity) {}
}
