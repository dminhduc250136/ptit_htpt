package com.ptit.htpt.productservice.web;

import com.ptit.htpt.productservice.api.ApiResponse;
import com.ptit.htpt.productservice.service.ReviewService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/products/{productId}/reviews")
public class ReviewController {

  private final ReviewService reviewService;

  @Value("${app.jwt.secret}")
  private String jwtSecret;

  public ReviewController(ReviewService reviewService) {
    this.reviewService = reviewService;
  }

  private SecretKey signingKey() {
    return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
  }

  private Claims parseToken(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
    }
    String token = authHeader.substring("Bearer ".length()).trim();
    try {
      return Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(token).getPayload();
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
    }
  }

  @GetMapping
  public ApiResponse<Map<String, Object>> listReviews(
      @PathVariable String productId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(defaultValue = "newest") String sort) {
    return ApiResponse.of(200, "Reviews listed",
        reviewService.listReviews(productId, page, size, sort));
  }

  /** Phase 21 REV-04: owner edit review trong cửa sổ 24h. */
  @PatchMapping("/{reviewId}")
  public ApiResponse<Map<String, Object>> editReview(
      @PathVariable String productId,
      @PathVariable String reviewId,
      @RequestHeader("Authorization") String auth,
      @Valid @RequestBody EditReviewRequest body) {
    Claims claims = parseToken(auth);
    String userId = claims.getSubject();
    return ApiResponse.of(200, "Review updated",
        reviewService.editReview(reviewId, userId, body.rating(), body.content()));
  }

  /** Phase 21 REV-04: owner soft-delete review (204 No Content). */
  @DeleteMapping("/{reviewId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void softDeleteReview(
      @PathVariable String productId,
      @PathVariable String reviewId,
      @RequestHeader("Authorization") String auth) {
    Claims claims = parseToken(auth);
    String userId = claims.getSubject();
    reviewService.softDeleteReview(reviewId, userId);
  }

  @GetMapping("/eligibility")
  public ApiResponse<Map<String, Boolean>> eligibility(
      @PathVariable String productId,
      @RequestHeader("Authorization") String auth) {
    Claims claims = parseToken(auth);
    String userId = claims.getSubject();
    boolean eligible = reviewService.checkEligibilityInternal(userId, productId);
    return ApiResponse.of(200, "Eligibility checked", Map.of("eligible", eligible));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<Map<String, Object>> createReview(
      @PathVariable String productId,
      @RequestHeader("Authorization") String auth,
      @Valid @RequestBody CreateReviewRequest request) {
    Claims claims = parseToken(auth);
    String userId = claims.getSubject();
    // D-10: fallback về username nếu token cũ chưa có claim 'name'
    String reviewerName = (String) claims.get("name");
    if (reviewerName == null || reviewerName.isBlank()) {
      Object username = claims.get("username");
      reviewerName = (username != null) ? username.toString() : userId;
    }
    Map<String, Object> created = reviewService.createReview(
        productId, userId, reviewerName, request.rating(), request.content());
    return ApiResponse.of(201, "Review created", created);
  }

  public record CreateReviewRequest(
      @Min(value = 1, message = "Rating phải từ 1 đến 5")
      @Max(value = 5, message = "Rating phải từ 1 đến 5")
      int rating,
      @Size(max = 500, message = "Nhận xét tối đa 500 ký tự")
      String content
  ) {}

  /** Phase 21 REV-04: edit body — rating nullable (chỉ sửa content được), content nullable (chỉ sửa rating). */
  public record EditReviewRequest(
      @Min(value = 1, message = "Rating phải từ 1 đến 5")
      @Max(value = 5, message = "Rating phải từ 1 đến 5")
      Integer rating,
      @Size(max = 500, message = "Nhận xét tối đa 500 ký tự")
      String content
  ) {}
}
