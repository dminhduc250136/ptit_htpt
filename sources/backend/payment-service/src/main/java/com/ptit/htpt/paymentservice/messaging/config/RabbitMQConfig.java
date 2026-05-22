package com.ptit.htpt.paymentservice.messaging.config;

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
 * Topology RabbitMQ Phase 26 — payment events (RESEARCH §Pattern 3, Anti-Pattern).
 *
 * Exchange RIÊNG payment.events — KHÔNG reuse order.events
 * (order.events bind order.# → inventory + notification consume nhầm nếu reuse).
 *
 * Topology:
 *   - Exchange `payment.events` (topic, durable)
 *   - DLX `payment.dlx` (direct) + DLQ `payment-events.dlq`
 *   - Routing keys: payment.succeeded, payment.failed
 *
 * AmqpAdmin idempotent — order-service consumer side có thể declare cùng exchange OK.
 */
@Configuration
public class RabbitMQConfig {

  public static final String EXCHANGE = "payment.events";
  public static final String DLX = "payment.dlx";
  public static final String DLQ = "payment-events.dlq";
  public static final String DLQ_ROUTING = "payment-events";
  public static final String ROUTING_KEY_SUCCEEDED = "payment.succeeded";
  public static final String ROUTING_KEY_FAILED = "payment.failed";

  @Bean
  public TopicExchange paymentEventsExchange() {
    return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
  }

  @Bean
  public DirectExchange paymentDeadLetterExchange() {
    return ExchangeBuilder.directExchange(DLX).durable(true).build();
  }

  @Bean
  public Queue paymentDeadLetterQueue() {
    return QueueBuilder.durable(DLQ).build();
  }

  @Bean
  public Binding paymentDlqBinding() {
    return BindingBuilder.bind(paymentDeadLetterQueue()).to(paymentDeadLetterExchange()).with(DLQ_ROUTING);
  }

  @Bean
  public Jackson2JsonMessageConverter paymentJacksonMessageConverter(ObjectMapper objectMapper) {
    return new Jackson2JsonMessageConverter(objectMapper);
  }

  @Bean
  public RabbitTemplate paymentRabbitTemplate(ConnectionFactory connectionFactory,
                                               Jackson2JsonMessageConverter paymentJacksonMessageConverter) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(paymentJacksonMessageConverter);
    template.setMandatory(true); // bật publisher-returns
    return template;
  }
}
