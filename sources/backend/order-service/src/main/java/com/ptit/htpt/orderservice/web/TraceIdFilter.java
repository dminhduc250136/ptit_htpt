package com.ptit.htpt.orderservice.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TraceIdFilter extends OncePerRequestFilter {
  public static final String HEADER_NAME = "X-Request-Id";
  public static final String ATTR_NAME = "traceId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    String traceId = request.getHeader(HEADER_NAME);
    if (traceId == null || traceId.isBlank()) {
      traceId = UUID.randomUUID().toString().replace("-", "");
    }

    request.setAttribute(ATTR_NAME, traceId);
    MDC.put(ATTR_NAME, traceId);
    response.setHeader(HEADER_NAME, traceId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(ATTR_NAME);
    }
  }
}

