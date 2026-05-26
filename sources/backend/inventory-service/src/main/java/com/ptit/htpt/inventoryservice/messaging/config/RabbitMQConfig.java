package com.ptit.htpt.inventoryservice.messaging.config;

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
 * Topology RabbitMQ Phase 23 (D-02, D-09) — declare ở inventory-service.
 * Identical với order-service và notification-service (AmqpAdmin idempotent).
 */
@Configuration
public class RabbitMQConfig {

  public static final String EXCHANGE = "order.events";
  public static final String DLX = "order.dlx";
  public static final String DLQ = "order-events.dlq";
  public static final String DLQ_ROUTING = "order-events";
  public static final String INVENTORY_QUEUE = "inventory.order-events";
  public static final String NOTIFICATION_QUEUE = "notification.order-events";
  public static final String BINDING_KEY = "order.#";
  public static final String ROUTING_KEY_ORDER_PLACED = "order.placed";

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

  @Bean
  public Jackson2JsonMessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
    return new Jackson2JsonMessageConverter(objectMapper);
  }

  @Bean
  public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                       Jackson2JsonMessageConverter converter) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(converter);
    template.setMandatory(true);
    return template;
  }
}
