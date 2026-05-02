package com.ptit.htpt.userservice.web;

import com.ptit.htpt.userservice.api.ApiResponse;
import com.ptit.htpt.userservice.service.UserStatsService;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 9 / Plan 09-02 (UI-02). Admin-only stats — D-05 REVISED: manual JWT
 * role check (KHÔNG @PreAuthorize). Path /admin/users/stats khớp gateway
 * route `/api/users/admin/stats` → rewrite → `/admin/users/stats`.
 *
 * NOTE: AdminUserController cũng dùng @RequestMapping("/admin/users") — KHÔNG conflict
 * vì Spring map handler theo full URL pattern. `/stats` chưa được map ở AdminUserController.
 */
@RestController
@RequestMapping("/admin/users")
public class AdminStatsController {

  private final UserStatsService statsService;
  private final JwtRoleGuard jwtRoleGuard;

  public AdminStatsController(UserStatsService statsService, JwtRoleGuard jwtRoleGuard) {
    this.statsService = statsService;
    this.jwtRoleGuard = jwtRoleGuard;
  }

  @GetMapping("/stats")
  public ApiResponse<Map<String, Long>> stats(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
  ) {
    jwtRoleGuard.requireAdmin(authHeader);
    return ApiResponse.of(200, "User stats",
        Map.of("totalUsers", statsService.totalUsers()));
  }
}
