package com.ptit.htpt.notificationservice.messaging.tracing;

import org.slf4j.MDC;

/**
 * D-16: consumer set MDC traceId từ AMQP header X-Trace-Id trước business logic; remove trong finally.
 * Đây không phải Spring AOP interceptor — chỉ helper utility gọi tay từ listener method.
 */
public final class TraceIdConsumerInterceptor {
  public static final String HEADER = "X-Trace-Id";
  public static final String MDC_KEY = "traceId";

  private TraceIdConsumerInterceptor() {}

  public static void enter(String traceIdHeader) {
    String id = (traceIdHeader != null && !traceIdHeader.isBlank()) ? traceIdHeader : "no-trace";
    MDC.put(MDC_KEY, id);
  }

  public static void exit() {
    MDC.remove(MDC_KEY);
  }
}
