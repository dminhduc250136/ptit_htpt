package com.ptit.htpt.userservice.service;

import com.ptit.htpt.userservice.domain.VerificationTokenEntity;
import com.ptit.htpt.userservice.repository.VerificationTokenRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Phase 27 / Plan 27-02 (MAIL-02): Sinh token xác minh/reset + verify single-use.
 *
 * generateToken(): SecureRandom 32 byte → base64url without padding = 43 ký tự (256-bit entropy).
 * Entropy đủ để chống brute-force (T-27-03 mitigation).
 *
 * verifyAndConsume(): SELECT FOR UPDATE → check used_at IS NULL + expires_at > now() + type match
 * → markUsed() + save trong cùng transaction → chống replay (T-27-04 mitigation, Pitfall 4).
 *
 * KHÔNG Lombok — explicit constructor injection (1 dep).
 */
@Service
public class VerificationTokenService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final VerificationTokenRepository repository;

  public VerificationTokenService(VerificationTokenRepository repository) {
    this.repository = repository;
  }

  /**
   * Sinh token ngẫu nhiên 43 ký tự URL-safe (base64url without padding).
   * 32 byte = 256 bit entropy — không brute-force khả thi (T-27-03).
   */
  public String generateToken() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * Tạo và lưu token mới cho userId.
   *
   * @param userId      user cần token
   * @param type        "EMAIL_VERIFY" hoặc "PASSWORD_RESET"
   * @param ttl         thời hạn sống (vd Duration.ofHours(24))
   * @return entity đã lưu
   */
  @Transactional
  public VerificationTokenEntity createToken(String userId, String type, Duration ttl) {
    String token = generateToken();
    Instant expiresAt = Instant.now().plus(ttl);
    VerificationTokenEntity entity = new VerificationTokenEntity(token, userId, type, expiresAt);
    return repository.save(entity);
  }

  /**
   * Verify và consume token (single-use).
   *
   * SELECT FOR UPDATE đảm bảo concurrent requests không thể dùng cùng token (Pitfall 4).
   *
   * @param token        token từ request
   * @param expectedType loại token mong đợi
   * @return entity đã markUsed
   * @throws ResponseStatusException 400 nếu token không tồn tại hoặc sai type
   * @throws ResponseStatusException 410 GONE nếu token đã dùng hoặc hết hạn
   */
  @Transactional
  public VerificationTokenEntity verifyAndConsume(String token, String expectedType) {
    VerificationTokenEntity entity = repository.findByTokenForUpdate(token)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token không hợp lệ"));

    if (!expectedType.equals(entity.type())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token không đúng loại");
    }

    if (entity.usedAt() != null) {
      throw new ResponseStatusException(HttpStatus.GONE, "Token đã được sử dụng");
    }

    if (entity.expiresAt().isBefore(Instant.now())) {
      throw new ResponseStatusException(HttpStatus.GONE, "Token đã hết hạn");
    }

    entity.markUsed();
    return repository.save(entity);
  }
}
