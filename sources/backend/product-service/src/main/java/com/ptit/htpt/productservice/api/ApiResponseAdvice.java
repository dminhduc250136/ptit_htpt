package com.ptit.htpt.productservice.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Set;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {
  private static final Set<String> SKIP_PREFIXES = Set.of(
      "/actuator",
      "/v3/api-docs",
      "/swagger-ui",
      "/swagger-resources"
  );
  private static final Set<String> SKIP_EXACT = Set.of("/swagger-ui.html");

  @Override
  public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
    Class<?> paramType = returnType.getParameterType();
    return !CharSequence.class.isAssignableFrom(paramType);
  }

  @Override
  public @Nullable Object beforeBodyWrite(
      @Nullable Object body,
      MethodParameter returnType,
      MediaType selectedContentType,
      Class<? extends HttpMessageConverter<?>> selectedConverterType,
      ServerHttpRequest request,
      ServerHttpResponse response
  ) {
    if (body == null) {
      return null;
    }
    if (!isJson(selectedContentType)) {
      return body;
    }
    if (body instanceof ApiResponse<?> || body instanceof ApiErrorResponse) {
      return body;
    }
    if (body instanceof CharSequence) {
      return body;
    }

    String path = extractPath(request);
    if (shouldSkip(path)) {
      return body;
    }

    int status = 200;
    String message = "OK";
    if (response instanceof ServletServerHttpResponse servletResp) {
      status = servletResp.getServletResponse().getStatus();
      HttpStatus httpStatus = HttpStatus.resolve(status);
      if (httpStatus != null) {
        message = httpStatus.getReasonPhrase();
      }
    }
    return new ApiResponse<>(Instant.now(), status, message, body);
  }

  private boolean isJson(MediaType contentType) {
    if (contentType == null) {
      return false;
    }
    return MediaType.APPLICATION_JSON.includes(contentType) || contentType.getSubtype().endsWith("+json");
  }

  private boolean shouldSkip(String path) {
    if (path == null || path.isBlank()) {
      return false;
    }
    if (SKIP_EXACT.contains(path)) {
      return true;
    }
    return SKIP_PREFIXES.stream().anyMatch(path::startsWith);
  }

  private String extractPath(ServerHttpRequest request) {
    if (request instanceof ServletServerHttpRequest servletReq) {
      HttpServletRequest r = servletReq.getServletRequest();
      return r.getRequestURI();
    }
    return request.getURI().getPath();
  }
}

