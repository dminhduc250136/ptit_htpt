package com.ptit.htpt.orderservice.exception;

import java.util.List;

/**
 * Thrown when one or more order items exceed available stock.
 * Mapped to 409 CONFLICT by GlobalExceptionHandler with domainCode=STOCK_SHORTAGE.
 */
public class StockShortageException extends RuntimeException {

  private final List<StockShortageItem> shortageItems;

  public StockShortageException(List<StockShortageItem> shortageItems) {
    super("One or more items exceed available stock");
    this.shortageItems = shortageItems;
  }

  public List<StockShortageItem> shortageItems() {
    return shortageItems;
  }

  /** Per-item shortage detail (productId, productName, requested quantity, available stock). */
  public record StockShortageItem(
      String productId,
      String productName,
      int requested,
      int available
  ) {}
}
