package com.ptit.htpt.orderservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ptit.htpt.orderservice.domain.OrderEntity;
import com.ptit.htpt.orderservice.messaging.consumer.PaymentEventListener;
import com.ptit.htpt.orderservice.messaging.event.PaymentEventEnvelope;
import com.ptit.htpt.orderservice.messaging.event.PaymentEventEnvelope.PaymentPayload;
import com.ptit.htpt.orderservice.repository.OrderRepository;
import com.ptit.htpt.orderservice.repository.ProcessedEventRepository;
import com.ptit.htpt.orderservice.service.OrderCrudService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

/**
 * Phase 26 / Plan 26-03 (T-26-09, D-09): Unit tests cho {@link PaymentEventListener}.
 *
 * <p>Phase 26.1 / Plan 26.1-02 (D-07): cập nhật assertions và payload helper để dùng
 * {@code paymentTransactionNo} thay {@code vnpTransactionNo} (field rename MoMo migration).
 *
 * <p>Dùng Mockito plain unit test — tránh Spring context và Testcontainers (env Windows không Docker).
 *
 * <p>Test coverage:
 *   1. PaymentSucceeded → order.paymentStatus=PAID, paymentTransactionNo saved, publishOrderPlaced gọi
 *   2. PaymentFailed    → order.paymentStatus=FAILED, publishOrderPlaced KHÔNG gọi
 *   3. Duplicate eventId (cùng eventId gửi 2 lần) → chỉ xử lý 1 lần (idempotent)
 */
@ExtendWith(MockitoExtension.class)
class PaymentEventListenerIT {

  @Mock private ProcessedEventRepository processedEventRepository;
  @Mock private OrderRepository orderRepository;
  @Mock private OrderCrudService orderCrudService;

  private PaymentEventListener listener;

  @BeforeEach
  void setUp() {
    listener = new PaymentEventListener(processedEventRepository, orderRepository, orderCrudService);
  }

  // ------------------------------------------------
  // Helper: tạo envelopes và order
  // ------------------------------------------------

  private PaymentEventEnvelope succeededEnvelope(String eventId, String orderId) {
    return new PaymentEventEnvelope(
        eventId, "PaymentSucceeded", "2026-05-22T10:00:00Z", "trace-1",
        // Phase 26.1 (D-07): field rename vnpTransactionNo → paymentTransactionNo
        new PaymentPayload(orderId, "session-1", "MOMO-TXN-001",
            BigDecimal.valueOf(10_000_000), "VND")
    );
  }

  private PaymentEventEnvelope failedEnvelope(String eventId, String orderId) {
    return new PaymentEventEnvelope(
        eventId, "PaymentFailed", "2026-05-22T10:01:00Z", "trace-2",
        new PaymentPayload(orderId, "session-2", null,
            BigDecimal.valueOf(5_000_000), "VND")
    );
  }

  private OrderEntity pendingOrder(String orderId) {
    OrderEntity e = OrderEntity.create("user-1", BigDecimal.valueOf(10_000_000), "PENDING", null);
    // Phase 26.1 (D-08): đổi paymentMethod VNPAY → MOMO
    e.setPaymentMethod("MOMO");
    return e;
  }

  // ------------------------------------------------
  // Test 1: PaymentSucceeded → PAID + vnpTransactionNo + publishOrderPlaced
  // ------------------------------------------------

  @Test
  void onPaymentEvent_succeeded_setsStatusPaidAndPublishesOrderPlaced() {
    String orderId = "order-abc";
    PaymentEventEnvelope envelope = succeededEnvelope("evt-001", orderId);
    OrderEntity order = pendingOrder(orderId);

    when(processedEventRepository.insertIfAbsent("evt-001", "PaymentSucceeded")).thenReturn(true);
    when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));
    when(orderRepository.save(any(OrderEntity.class))).thenReturn(order);

    listener.onPaymentEvent(envelope, "trace-1");

    // paymentStatus = PAID
    assertThat(order.paymentStatus()).isEqualTo("PAID");
    // paymentTransactionNo ghi vào entity (Phase 26.1 D-07: rename từ vnpTransactionNo)
    assertThat(order.paymentTransactionNo()).isEqualTo("MOMO-TXN-001");
    // publishOrderPlacedForOrder được gọi (D-09 trừ kho sau PAID)
    verify(orderCrudService).publishOrderPlacedForOrder(order);
  }

  // ------------------------------------------------
  // Test 2: PaymentFailed → FAILED, KHÔNG publish OrderPlaced
  // ------------------------------------------------

  @Test
  void onPaymentEvent_failed_setsStatusFailedDoesNotPublishOrderPlaced() {
    String orderId = "order-xyz";
    PaymentEventEnvelope envelope = failedEnvelope("evt-002", orderId);
    OrderEntity order = pendingOrder(orderId);

    when(processedEventRepository.insertIfAbsent("evt-002", "PaymentFailed")).thenReturn(true);
    when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));
    when(orderRepository.save(any(OrderEntity.class))).thenReturn(order);

    listener.onPaymentEvent(envelope, "trace-2");

    // paymentStatus = FAILED
    assertThat(order.paymentStatus()).isEqualTo("FAILED");
    // KHÔNG publish OrderPlaced
    verify(orderCrudService, never()).publishOrderPlacedForOrder(any());
  }

  // ------------------------------------------------
  // Test 3: Duplicate eventId → chỉ xử lý 1 lần (idempotent T-26-09)
  // ------------------------------------------------

  @Test
  void onPaymentEvent_duplicateEventId_skipsBusinessLogicOnSecondAttempt() {
    String orderId = "order-dup";
    PaymentEventEnvelope envelope = succeededEnvelope("evt-dup", orderId);

    // insertIfAbsent trả false cho lần 2 (event đã processed)
    when(processedEventRepository.insertIfAbsent("evt-dup", "PaymentSucceeded")).thenReturn(false);

    listener.onPaymentEvent(envelope, null);

    // OrderRepository KHÔNG được gọi (skip business logic)
    verify(orderRepository, never()).findByIdWithItems(anyString());
    // publishOrderPlacedForOrder KHÔNG được gọi
    verify(orderCrudService, never()).publishOrderPlacedForOrder(any());
  }
}
