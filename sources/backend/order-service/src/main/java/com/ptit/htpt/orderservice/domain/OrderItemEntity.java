package com.ptit.htpt.orderservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items", schema = "order_svc")
public class OrderItemEntity {

  @Id
  @Column(length = 36, nullable = false, updatable = false)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", nullable = false)
  private OrderEntity order;

  @Column(name = "product_id", nullable = false, length = 36)
  private String productId;

  @Column(name = "product_name", nullable = false, length = 300)
  private String productName;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal unitPrice;

  @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
  private BigDecimal lineTotal;

  protected OrderItemEntity() {}

  public static OrderItemEntity create(OrderEntity order, String productId,
                                       String productName, int quantity,
                                       BigDecimal unitPrice) {
    OrderItemEntity item = new OrderItemEntity();
    item.id = UUID.randomUUID().toString();
    item.order = order;
    item.productId = productId;
    item.productName = productName;
    item.quantity = quantity;
    item.unitPrice = unitPrice;
    item.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    return item;
  }

  public String id() { return id; }
  public String productId() { return productId; }
  public String productName() { return productName; }
  public int quantity() { return quantity; }
  public BigDecimal unitPrice() { return unitPrice; }
  public BigDecimal lineTotal() { return lineTotal; }
}
