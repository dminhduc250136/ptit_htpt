package com.ptit.htpt.inventoryservice.web;

import com.ptit.htpt.inventoryservice.api.ApiResponse;
import com.ptit.htpt.inventoryservice.service.InventoryCrudService;
import com.ptit.htpt.inventoryservice.service.InventoryCrudService.ItemUpsertRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public inventory endpoints. Phase 5 scope-cut: reservation paths removed (Phase 8 sẽ re-add).
 */
@RestController
@RequestMapping("/inventory")
public class InventoryController {
  private final InventoryCrudService inventoryCrudService;

  public InventoryController(InventoryCrudService inventoryCrudService) {
    this.inventoryCrudService = inventoryCrudService;
  }

  @GetMapping("/items")
  public ApiResponse<Map<String, Object>> listItems(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "updatedAt,desc") String sort
  ) {
    return ApiResponse.of(200, "Inventory items listed", inventoryCrudService.listItems(page, size, sort));
  }

  @GetMapping("/items/{id}")
  public ApiResponse<Object> getItem(@PathVariable String id) {
    return ApiResponse.of(200, "Inventory item loaded", inventoryCrudService.getItem(id));
  }

  @PostMapping("/items")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<Object> createItem(@Valid @RequestBody ItemUpsertRequest request) {
    return ApiResponse.of(201, "Inventory item created", inventoryCrudService.createItem(request));
  }

  @PutMapping("/items/{id}")
  public ApiResponse<Object> updateItem(@PathVariable String id, @Valid @RequestBody ItemUpsertRequest request) {
    return ApiResponse.of(200, "Inventory item updated", inventoryCrudService.updateItem(id, request));
  }

  @DeleteMapping("/items/{id}")
  public ApiResponse<Map<String, Object>> deleteItem(@PathVariable String id) {
    inventoryCrudService.deleteItem(id);
    return ApiResponse.of(200, "Inventory item deleted", Map.of("id", id, "deleted", true));
  }
}
