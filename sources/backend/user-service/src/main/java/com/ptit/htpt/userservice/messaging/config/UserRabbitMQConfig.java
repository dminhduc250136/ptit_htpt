package com.ptit.htpt.userservice.messaging.config;

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
 * Phase 27 / Plan 27-02 (MAIL-02): Topology RabbitMQ cho user.events.
 *
 * Exchange: user.events (TopicExchange durable)
 * DLX: user.dlx (DirectExchange durable) → DLQ: user-events.dlq
 * Queue: notification.user-events (bind user.# → nhận cả registered + password-reset)
 *
 * user-service là PRODUCER only → KHÔNG có listener.simple.retry config.
 * AmqpAdmin tự declare idempotent khi khởi động (matching args).
 */
@Configuration
public class UserRabbitMQConfig {

  public static final String USER_EXCHANGE = "user.events";
  public static final String USER_DLX = "user.dlx";
  public static final String USER_DLQ = "user-events.dlq";
  public static final String USER_DLQ_ROUTING = "user-events";
  public static final String USER_NOTIFICATION_QUEUE = "notification.user-events";
  public static final String USER_BINDING_KEY = "user.#";
  public static final String ROUTING_KEY_REGISTERED = "user.registered";
  public static final String ROUTING_KEY_PASSWORD_RESET = "user.password-reset";

  @Bean
  public TopicExchange userEventsExchange() {
    return ExchangeBuilder.topicExchange(USER_EXCHANGE).durable(true).build();
  }

  @Bean
  public DirectExchange userDeadLetterExchange() {
    return ExchangeBuilder.directExchange(USER_DLX).durable(true).build();
  }

  @Bean
  public Queue userDeadLetterQueue() {
    return QueueBuilder.durable(USER_DLQ).build();
  }

  @Bean
  public Binding userDlqBinding() {
    return BindingBuilder.bind(userDeadLetterQueue()).to(userDeadLetterExchange()).with(USER_DLQ_ROUTING);
  }

  @Bean
  public Queue userNotificationQueue() {
    return QueueBuilder.durable(USER_NOTIFICATION_QUEUE)
        .withArgument("x-dead-letter-exchange", USER_DLX)
        .withArgument("x-dead-letter-routing-key", USER_DLQ_ROUTING)
        .build();
  }

  @Bean
  public Binding userNotificationBinding() {
    return BindingBuilder.bind(userNotificationQueue()).to(userEventsExchange()).with(USER_BINDING_KEY);
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
