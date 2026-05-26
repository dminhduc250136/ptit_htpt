package com.ptit.htpt.userservice.web;

import com.ptit.htpt.userservice.api.ApiResponse;
import com.ptit.htpt.userservice.service.Range;
import com.ptit.htpt.userservice.service.UserChartsService;
import com.ptit.htpt.userservice.service.UserChartsService.SignupPoint;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 19 / Plan 19-02 (ADMIN-04): admin chart endpoint cho user signups.
 *
 * <p>Path {@code /admin/users/charts/signups} khớp gateway rewrite
 * {@code /api/users/admin/charts/signups} → {@code /admin/users/charts/signups}.
 *
 * <p>D-02: manual JWT role check qua {@link JwtRoleGuard#requireAdmin(String)}
 * (KHÔNG @PreAuthorize) — cùng pattern Phase 9 AdminStatsController.
 */
@RestController
@RequestMapping("/admin/users/charts")
public class AdminChartsController {

  private final UserChartsService chartsService;
  private final JwtRoleGuard jwtRoleGuard;

  public AdminChartsController(UserChartsService chartsService, JwtRoleGuard jwtRoleGuard) {
    this.chartsService = chartsService;
    this.jwtRoleGuard = jwtRoleGuard;
  }

  @GetMapping("/signups")
  public ApiResponse<List<SignupPoint>> signups(
      @RequestParam(value = "range", required = false, defaultValue = "30d") String range,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
    jwtRoleGuard.requireAdmin(authHeader);
    return ApiResponse.of(200, "User signups", chartsService.signupsByDay(Range.parse(range)));
  }
}
