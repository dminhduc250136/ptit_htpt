package com.ptit.htpt.orderservice.messaging.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topology RabbitMQ Phase 23 (D-02, D-09).
 *
 * Tất cả 3 service (order/inventory/notification) declare cùng beans — Spring AmqpAdmin
 * idempotent với matching arguments (RESEARCH §Open Q #3). Trùng declare KHÔNG gây
 * PRECONDITION_FAILED khi args identical.
 *
 * Topology order.events (Phase 23, GIỮ NGUYÊN):
 *   - Exchange `order.events` (topic, durable)
 *   - DLX `order.dlx` (direct, durable) + DLQ `order-events.dlq`
 *   - Queue `inventory.order-events` + `notification.order-events` bind key `order.#`
 *   - Both queues route to DLX khi reject/retry-exhaust
 *
 * Topology payment.events (Phase 26, MỚI — KHÔNG đụng order.events):
 *   - Exchange `payment.events` (topic, durable) — RIÊNG (T-26-12 anti RESEARCH anti-pattern)
 *   - DLX `payment.dlx` (direct, durable) + DLQ `payment-events.dlq`
 *   - Queue `order.payment-events` bind key `payment.#` tới exchange `payment.events`
 *   AmqpAdmin idempotent — cùng exchange với payment-service OK (args identical).
 *
 * RabbitTemplate setMandatory(true) để bật publisher-returns (D-04).
 */
@Configuration
public class RabbitMQConfig {

  // ---- order.events topology (Phase 23 — KHÔNG thay đổi) ----
  public static final String EXCHANGE = "order.events";
  public static final String DLX = "order.dlx";
  public static final String DLQ = "order-events.dlq";
  public static final String DLQ_ROUTING = "order-events";
  public static final String INVENTORY_QUEUE = "inventory.order-events";
  public static final String NOTIFICATION_QUEUE = "notification.order-events";
  public static final String BINDING_KEY = "order.#";
  public static final String ROUTING_KEY_ORDER_PLACED = "order.placed";
  // Phase 27: routing key cho event đổi trạng thái đơn hàng
  // Queue notification.order-events bind key "order.#" → tự route (D-03 Phase 23)
  public static final String ROUTING_KEY_ORDER_STATUS_CHANGED = "order.status-changed";

  // ---- payment.events topology (Phase 26 — consumer side order-service) ----
  public static final String PAYMENT_EXCHANGE = "payment.events";
  public static final String PAYMENT_DLX = "payment.dlx";
  public static final String PAYMENT_DLQ = "payment-events.dlq";
  public static final String PAYMENT_DLQ_ROUTING = "payment-events";
  public static final String ORDER_PAYMENT_QUEUE = "order.payment-events";
  public static final String PAYMENT_BINDING_KEY = "payment.#";

  @Bean
  public TopicExchange orderEventsExchange() {
    return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
  }

  @Bean
  public DirectExchange deadLetterExchange() {
    return ExchangeBuilder.directExchange(DLX).durable(true).build();
  }

  @Bean
  public Queue deadLetterQueue() {
    return QueueBuilder.durable(DLQ).build();
  }

  @Bean
  public Binding dlqBinding() {
    return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(DLQ_ROUTING);
  }

  @Bean
  public Queue inventoryQueue() {
    return QueueBuilder.durable(INVENTORY_QUEUE)
        .withArgument("x-dead-letter-exchange", DLX)
        .withArgument("x-dead-letter-routing-key", DLQ_ROUTING)
        .build();
  }

  @Bean
  public Binding inventoryBinding() {
    return BindingBuilder.bind(inventoryQueue()).to(orderEventsExchange()).with(BINDING_KEY);
  }

  @Bean
  public Queue notificationQueue() {
    return QueueBuilder.durable(NOTIFICATION_QUEUE)
        .withArgument("x-dead-letter-exchange", DLX)
        .withArgument("x-dead-letter-routing-key", DLQ_ROUTING)
        .build();
  }

  @Bean
  public Binding notificationBinding() {
    return BindingBuilder.bind(notificationQueue()).to(orderEventsExchange()).with(BINDING_KEY);
  }

  // ---- payment.events beans (Phase 26 — thêm mới, KHÔNG đụng order.events topology) ----

  @Bean
  public TopicExchange paymentEventsExchange() {
    return ExchangeBuilder.topicExchange(PAYMENT_EXCHANGE).durable(true).build();
  }

  @Bean
  public DirectExchange paymentDeadLetterExchange() {
    return ExchangeBuilder.directExchange(PAYMENT_DLX).durable(true).build();
  }

  @Bean
  public Queue paymentDeadLetterQueue() {
    return QueueBuilder.durable(PAYMENT_DLQ).build();
  }

  @Bean
  public Binding paymentDlqBinding() {
    return BindingBuilder.bind(paymentDeadLetterQueue()).to(paymentDeadLetterExchange())
        .with(PAYMENT_DLQ_ROUTING);
  }

  @Bean
  public Queue orderPaymentEventsQueue() {
    return QueueBuilder.durable(ORDER_PAYMENT_QUEUE)
        .withArgument("x-dead-letter-exchange", PAYMENT_DLX)
        .withArgument("x-dead-letter-routing-key", PAYMENT_DLQ_ROUTING)
        .build();
  }

  @Bean
  public Binding orderPaymentEventsBinding() {
    return BindingBuilder.bind(orderPaymentEventsQueue()).to(paymentEventsExchange())
        .with(PAYMENT_BINDING_KEY);
  }

  @Bean
  public Jackson2JsonMessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
    return new Jackson2JsonMessageConverter(objectMapper);
  }

  @Bean
  public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                       Jackson2JsonMessageConverter converter) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(converter);
    template.setMandatory(true); // bật publisher-returns (D-04)
    return template;
  }
}
