package com.ptit.htpt.orderservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptit.htpt.orderservice.domain.OrderDto;
import com.ptit.htpt.orderservice.domain.OrderEntity;
import com.ptit.htpt.orderservice.domain.OrderItemEntity;
import com.ptit.htpt.orderservice.messaging.publisher.OrderEventPublisher;
import com.ptit.htpt.orderservice.repository.OrderItemRepository;
import com.ptit.htpt.orderservice.repository.OrderRepository;
import com.ptit.htpt.orderservice.service.CouponRedemptionService;
import com.ptit.htpt.orderservice.service.OrderCrudService;
import com.ptit.htpt.orderservice.service.OrderCrudService.CreateOrderCommand;
import com.ptit.htpt.orderservice.service.OrderCrudService.OrderItemRequest;
import com.ptit.htpt.orderservice.service.OrderCrudService.ShippingAddressRequest;
import com.ptit.htpt.orderservice.service.PaymentSessionClient;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Phase 26 / Plan 26-03 (D-09, D-10): Unit tests cho nhánh VNPAY trong
 * {@link OrderCrudService#createOrderFromCommand}.
 *
 * <p>Dùng Mockito plain unit test (pattern Phase 26-01 VNPayIpnControllerIT) — tránh Spring context
 * và Testcontainers để test nhanh và portable trên Windows env không có Docker.
 *
 * <p>Test coverage:
 *   1. VNPAY order → payment_status=PENDING, paymentUrl được set, KHÔNG publish OrderPlaced
 *   2. COD order   → publishOrderPlaced gọi 1 lần, paymentStatus=PENDING (default)
 *   3. PaymentSessionClient throw → ResponseStatusException propagate ra caller (lỗi cứng)
 */
@ExtendWith(MockitoExtension.class)
class OrderCrudServiceVNPayIT {

  @Mock private OrderRepository orderRepository;
  @Mock private OrderItemRepository orderItemRepository;
  @Mock private RestTemplate restTemplate;
  @Mock private CouponRedemptionService couponRedemptionService;
  @Mock private OrderEventPublisher orderEventPublisher;
  @Mock private PaymentSessionClient paymentSessionClient;

  private OrderCrudService service;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    service = new OrderCrudService(
        orderRepository, orderItemRepository, objectMapper, restTemplate,
        couponRedemptionService, orderEventPublisher, paymentSessionClient
    );
  }

  // ------------------------------------------------
  // Helper: tạo command
  // ------------------------------------------------

  private CreateOrderCommand vnpayCommand() {
    return new CreateOrderCommand(
        List.of(new OrderItemRequest("prod-1", "Laptop XYZ", 1, BigDecimal.valueOf(10_000_000))),
        new ShippingAddressRequest("123 Lê Lợi", "Phường 1", "Quận 1", "TP.HCM", "70000"),
        "VNPAY",
        null,
        null  // no coupon
    );
  }

  private CreateOrderCommand codCommand() {
    return new CreateOrderCommand(
        List.of(new OrderItemRequest("prod-2", "Chuột Logitech", 2, BigDecimal.valueOf(500_000))),
        new ShippingAddressRequest("456 Nguyễn Huệ", "Phường 2", "Quận 3", "TP.HCM", "70000"),
        "COD",
        null,
        null  // no coupon
    );
  }

  private OrderEntity savedEntity(String paymentMethod, BigDecimal total) {
    OrderEntity e = OrderEntity.create("user-1", total, "PENDING", null);
    e.setPaymentMethod(paymentMethod);
    return e;
  }

  // ------------------------------------------------
  // Test 1: VNPAY → PENDING, paymentUrl set, KHÔNG publish OrderPlaced
  // ------------------------------------------------

  @Test
  void createOrderFromCommand_vnpay_returnsPendingWithPaymentUrl_doesNotPublishOrderPlaced() {
    BigDecimal total = BigDecimal.valueOf(10_000_000);
    OrderEntity saved = savedEntity("VNPAY", total);

    // Mock stock validate — product GET trả null (best-effort skip)
    when(restTemplate.getForObject(anyString(), eq(java.util.Map.class))).thenReturn(null);
    when(orderRepository.save(any(OrderEntity.class))).thenReturn(saved);
    when(paymentSessionClient.createVNPaySession(anyString(), anyLong(), anyString(), isNull()))
        .thenReturn("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?token=abc123");

    OrderDto dto = service.createOrderFromCommand("user-1", vnpayCommand(), null);

    // Phải có paymentUrl
    assertThat(dto.paymentUrl()).isNotNull().contains("vnpayment.vn");
    // paymentStatus PENDING
    assertThat(dto.paymentStatus()).isEqualTo("PENDING");
    // KHÔNG publish OrderPlaced (D-09)
    verify(orderEventPublisher, never()).publishOrderPlaced(any());
  }

  // ------------------------------------------------
  // Test 2: COD → publishOrderPlaced gọi 1 lần
  // ------------------------------------------------

  @Test
  void createOrderFromCommand_cod_publishesOrderPlacedImmediately_noPaymentUrl() {
    BigDecimal total = BigDecimal.valueOf(1_000_000);
    OrderEntity saved = savedEntity("COD", total);

    when(restTemplate.getForObject(anyString(), eq(java.util.Map.class))).thenReturn(null);
    when(orderRepository.save(any(OrderEntity.class))).thenReturn(saved);

    OrderDto dto = service.createOrderFromCommand("user-1", codCommand(), null);

    // COD không có paymentUrl
    assertThat(dto.paymentUrl()).isNull();
    // PublishOrderPlaced gọi đúng 1 lần (D-10)
    verify(orderEventPublisher).publishOrderPlaced(any());
    // PaymentSessionClient KHÔNG được gọi
    verify(paymentSessionClient, never()).createVNPaySession(anyString(), anyLong(), anyString(), any());
  }

  // ------------------------------------------------
  // Test 3: PaymentSessionClient throw → ResponseStatusException propagate
  // ------------------------------------------------

  @Test
  void createOrderFromCommand_paymentSessionClientFails_throwsResponseStatusException() {
    BigDecimal total = BigDecimal.valueOf(5_000_000);
    OrderEntity saved = savedEntity("VNPAY", total);

    when(restTemplate.getForObject(anyString(), eq(java.util.Map.class))).thenReturn(null);
    when(orderRepository.save(any(OrderEntity.class))).thenReturn(saved);
    when(paymentSessionClient.createVNPaySession(anyString(), anyLong(), anyString(), isNull()))
        .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY,
            "Không tạo được phiên thanh toán VNPay"));

    assertThatThrownBy(() -> service.createOrderFromCommand("user-1", vnpayCommand(), null))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("VNPay");

    // OrderPlaced KHÔNG publish khi session tạo lỗi
    verify(orderEventPublisher, never()).publishOrderPlaced(any());
  }
}
