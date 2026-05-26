package com.ptit.htpt.productservice.web;

import com.ptit.htpt.productservice.api.ApiResponse;
import com.ptit.htpt.productservice.service.ReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 21 / Plan 02 — REV-06 admin moderation endpoints.
 *
 * <p>URL: {@code /admin/products/reviews} (Finding 1 — gateway rewrite
 * {@code /api/products/admin/reviews/...} → {@code /admin/products/reviews/...}).
 *
 * <p>Authorization: mọi handler gọi {@link JwtRoleGuard#requireAdmin(String)} đầu tiên
 * → 401 nếu thiếu Bearer / 403 nếu roles không chứa ADMIN.
 */
@RestController
@RequestMapping("/admin/products/reviews")
public class AdminReviewController {

  private final ReviewService reviewService;
  private final JwtRoleGuard jwtRoleGuard;

  public AdminReviewController(ReviewService reviewService, JwtRoleGuard jwtRoleGuard) {
    this.reviewService = reviewService;
    this.jwtRoleGuard = jwtRoleGuard;
  }

  @GetMapping
  public ApiResponse<Map<String, Object>> list(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "all") String filter) {
    jwtRoleGuard.requireAdmin(auth);
    return ApiResponse.of(200, "Admin reviews listed",
        reviewService.listAdminReviews(page, size, filter));
  }

  @PatchMapping("/{reviewId}/visibility")
  public ApiResponse<Map<String, Object>> setVisibility(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
      @PathVariable String reviewId,
      @Valid @RequestBody VisibilityRequest body) {
    jwtRoleGuard.requireAdmin(auth);
    reviewService.setVisibility(reviewId, body.hidden());
    return ApiResponse.of(200, "Visibility updated",
        Map.of("id", reviewId, "hidden", body.hidden()));
  }

  @DeleteMapping("/{reviewId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void hardDelete(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
      @PathVariable String reviewId) {
    jwtRoleGuard.requireAdmin(auth);
    reviewService.hardDelete(reviewId);
  }

  public record VisibilityRequest(@NotNull Boolean hidden) {}
}
