package com.ptit.htpt.apigateway.auth;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;

/**
 * Phase 25: allow-list cấu hình trong application.yml (prefix {@code app.auth}).
 *
 * <ul>
 *   <li>{@code publicEndpoints} — endpoint bypass JWT (vẫn parse nếu có Bearer).
 *   <li>{@code adminPathPatterns} — endpoint yêu cầu role ADMIN.
 * </ul>
 *
 * Config-driven để dễ audit + dễ chỉnh khi thêm endpoint mới (D-04).
 */
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

  private List<PublicEndpoint> publicEndpoints = List.of();
  private List<String> adminPathPatterns = List.of();

  public List<PublicEndpoint> getPublicEndpoints() {
    return publicEndpoints;
  }

  public void setPublicEndpoints(List<PublicEndpoint> publicEndpoints) {
    this.publicEndpoints = publicEndpoints;
  }

  public List<String> getAdminPathPatterns() {
    return adminPathPatterns;
  }

  public void setAdminPathPatterns(List<String> adminPathPatterns) {
    this.adminPathPatterns = adminPathPatterns;
  }

  /** Một endpoint public: cặp (HTTP method, Ant path pattern). */
  public record PublicEndpoint(HttpMethod method, String pattern) {}
}
