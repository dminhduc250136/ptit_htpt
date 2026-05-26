package com.ptit.htpt.paymentservice.messaging.tracing;

import org.slf4j.MDC;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;

/**
 * Producer set header X-Trace-Id từ MDC traceId snapshot.
 * Copy từ order-service analog (Phase 23).
 *
 * MDC có thể empty trong afterCommit callback — caller phải capture
 * traceId NGAY trong service method và pass instance này vào convertAndSend.
 */
public class TraceIdMessagePostProcessor implements MessagePostProcessor {
  public static final String HEADER = "X-Trace-Id";
  private final String traceIdSnapshot;

  public TraceIdMessagePostProcessor(String traceIdSnapshot) {
    this.traceIdSnapshot = traceIdSnapshot;
  }

  /** Factory: capture MDC traceId NGAY tại caller thread. */
  public static TraceIdMessagePostProcessor capture() {
    String id = MDC.get("traceId");
    return new TraceIdMessagePostProcessor(id != null ? id : "no-trace");
  }

  @Override
  public Message postProcessMessage(Message message) throws AmqpException {
    message.getMessageProperties().setHeader(HEADER, traceIdSnapshot);
    return message;
  }
}
