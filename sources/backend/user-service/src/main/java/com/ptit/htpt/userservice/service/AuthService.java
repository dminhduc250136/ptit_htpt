package com.ptit.htpt.userservice.service;

import com.ptit.htpt.userservice.domain.UserEntity;
import com.ptit.htpt.userservice.domain.UserMapper;
import com.ptit.htpt.userservice.domain.VerificationTokenEntity;
import com.ptit.htpt.userservice.jwt.JwtUtils;
import com.ptit.htpt.userservice.messaging.event.UserEventEnvelope;
import com.ptit.htpt.userservice.messaging.publisher.AccountEventPublisher;
import com.ptit.htpt.userservice.repository.UserRepository;
import com.ptit.htpt.userservice.web.AuthResponseDto;
import com.ptit.htpt.userservice.web.LoginRequest;
import com.ptit.htpt.userservice.web.RegisterRequest;
import jakarta.transaction.Transactional;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Phase 6 / Plan 01 (AUTH-01, AUTH-02): Business logic cho register + login.
 * Phase 27 / Plan 27-04 (MAIL-02): register hook publish event xác minh;
 *   thêm verifyEmail + forgotPassword + resetPassword.
 *
 * Error strategy: ResponseStatusException → GlobalExceptionHandler tự động serialize → ApiErrorResponse.
 * T-06-01 mitigated: generic "Invalid credentials" cho cả email-not-found và password-wrong.
 * T-06-03 mitigated: trả UserDto (không có passwordHash).
 * D-07: register vẫn phát JWT ngay — login KHÔNG bị hard-gate bởi email_verified.
 * D-08 (T-27-10): forgotPassword luôn trả void dù email không tồn tại — chống enumeration.
 */
@Service
@Transactional
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final VerificationTokenService verificationTokenService;
    private final AccountEventPublisher accountEventPublisher;
    private final String appBaseUrl;

    public AuthService(UserRepository userRepo,
                       PasswordEncoder passwordEncoder,
                       JwtUtils jwtUtils,
                       VerificationTokenService verificationTokenService,
                       AccountEventPublisher accountEventPublisher,
                       @Value("${APP_BASE_URL:http://localhost:3000}") String appBaseUrl) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.verificationTokenService = verificationTokenService;
        this.accountEventPublisher = accountEventPublisher;
        this.appBaseUrl = appBaseUrl;
    }

    /**
     * Đăng ký user mới.
     * Sau khi lưu user, sinh EMAIL_VERIFY token và publish event UserRegistered (MAIL-02).
     * JWT vẫn phát ngay — email_verified KHÔNG hard-gate login (D-07).
     *
     * @throws ResponseStatusException 409 CONFLICT nếu username hoặc email đã tồn tại
     */
    public AuthResponseDto register(RegisterRequest req) {
        if (userRepo.findByUsername(req.username()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        if (userRepo.findByEmail(req.email()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }
        String hash = passwordEncoder.encode(req.password());
        UserEntity entity = UserEntity.create(req.username(), req.email(), hash, "USER");
        userRepo.save(entity);

        // Phase 27: sinh token xác minh email + publish event (MAIL-02)
        String verifyToken = verificationTokenService.createToken(
            entity.id(), "EMAIL_VERIFY", Duration.ofHours(24)).token();
        String verifyUrl = appBaseUrl + "/verify-email?token=" + verifyToken;
        accountEventPublisher.publishUserRegistered(new UserEventEnvelope.UserPayload(
            entity.id(), entity.email(),
            entity.fullName() != null ? entity.fullName() : entity.username(),
            verifyUrl));

        // D-07: JWT vẫn phát ngay, KHÔNG chờ email xác minh
        String token = jwtUtils.issueToken(entity.id(), entity.username(), entity.fullName(), entity.roles());
        return new AuthResponseDto(token, UserMapper.toDto(entity));
    }

    /**
     * Xác thực user và phát JWT.
     *
     * @throws ResponseStatusException 401 UNAUTHORIZED nếu email không tồn tại hoặc password sai
     *     (T-06-01: generic message — không tiết lộ field nào sai)
     */
    public AuthResponseDto login(LoginRequest req) {
        UserEntity entity = userRepo.findByEmail(req.email())
            .orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(req.password(), entity.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        String token = jwtUtils.issueToken(entity.id(), entity.username(), entity.fullName(), entity.roles());
        return new AuthResponseDto(token, UserMapper.toDto(entity));
    }

    /**
     * Xác minh email qua token single-use.
     * Lật cờ email_verified = true khi token hợp lệ.
     *
     * @throws ResponseStatusException 400 nếu token không hợp lệ/sai loại
     * @throws ResponseStatusException 410 GONE nếu token đã dùng hoặc hết hạn
     */
    @Transactional
    public void verifyEmail(String token) {
        VerificationTokenEntity vt = verificationTokenService.verifyAndConsume(token, "EMAIL_VERIFY");
        UserEntity user = userRepo.findById(vt.userId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tài khoản không tồn tại"));
        user.setEmailVerified(true);
        userRepo.save(user);
    }

    /**
     * Xử lý yêu cầu đặt lại mật khẩu.
     * Anti-enumeration (D-08, T-27-10): KHÔNG throw dù email không tồn tại — luôn return void.
     * Nếu email tồn tại: sinh PASSWORD_RESET token + publish event PasswordResetRequested.
     */
    @Transactional
    public void forgotPassword(String email) {
        userRepo.findByEmail(email).ifPresent(user -> {
            String resetToken = verificationTokenService.createToken(
                user.id(), "PASSWORD_RESET", Duration.ofHours(1)).token();
            String resetUrl = appBaseUrl + "/reset-password?token=" + resetToken;
            accountEventPublisher.publishPasswordReset(new UserEventEnvelope.UserPayload(
                user.id(), user.email(),
                user.fullName() != null ? user.fullName() : user.username(),
                resetUrl));
        });
        // Không throw dù email không tồn tại — chống email enumeration (T-27-10)
    }

    /**
     * Đặt lại mật khẩu bằng token reset hợp lệ.
     *
     * @throws ResponseStatusException 400 nếu token không hợp lệ/sai loại
     * @throws ResponseStatusException 410 GONE nếu token đã dùng hoặc hết hạn
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        VerificationTokenEntity vt = verificationTokenService.verifyAndConsume(token, "PASSWORD_RESET");
        UserEntity user = userRepo.findById(vt.userId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tài khoản không tồn tại"));
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        userRepo.save(user);
    }
}
