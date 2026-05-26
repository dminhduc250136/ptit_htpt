package com.ptit.htpt.inventoryservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * D-10: ghi audit từng lần đổi quantity. Per-item, per-eventId.
 * quantityChange âm khi trừ kho (order.placed); dương khi nhập kho (chưa hiện thực hoá ở Phase 23).
 */
@Entity
@Table(name = "stock_ledger")
public class StockLedgerEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "event_id", length = 36, nullable = false)
  private String eventId;

  @Column(name = "order_id", length = 36, nullable = false)
  private String orderId;

  @Column(name = "product_id", length = 36, nullable = false)
  private String productId;

  @Column(name = "quantity_change", nullable = false)
  private int quantityChange;

  @Column(name = "reason", length = 32, nullable = false)
  private String reason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected StockLedgerEntity() {}

  public StockLedgerEntity(String eventId, String orderId, String productId,
                           int quantityChange, String reason, Instant createdAt) {
    this.eventId = eventId;
    this.orderId = orderId;
    this.productId = productId;
    this.quantityChange = quantityChange;
    this.reason = reason;
    this.createdAt = createdAt;
  }

  public static StockLedgerEntity create(String eventId, String orderId, String productId,
                                         int quantityChange, String reason) {
    return new StockLedgerEntity(eventId, orderId, productId, quantityChange, reason, Instant.now());
  }

  public Long id() { return id; }
  public String eventId() { return eventId; }
  public String orderId() { return orderId; }
  public String productId() { return productId; }
  public int quantityChange() { return quantityChange; }
  public String reason() { return reason; }
  public Instant createdAt() { return createdAt; }
}
