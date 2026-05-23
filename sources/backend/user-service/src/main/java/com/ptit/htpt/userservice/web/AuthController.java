package com.ptit.htpt.userservice.web;

import com.ptit.htpt.userservice.api.ApiResponse;
import com.ptit.htpt.userservice.service.AuthService;
import com.ptit.htpt.userservice.web.dto.ForgotPasswordRequest;
import com.ptit.htpt.userservice.web.dto.ResetPasswordRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 6 / Plan 01 (AUTH-01, AUTH-02, D-12): HTTP layer cho auth endpoints.
 * Phase 27 / Plan 27-04 (MAIL-02): Thêm 3 endpoint public — verify-email + password/forgot + password/reset.
 *
 * Path /auth maps qua API gateway như /api/users/auth/* (D-12).
 *
 * CRITICAL: Trả ApiResponse<AuthResponseDto> manually để tránh ApiResponseAdvice double-wrap.
 * ApiResponseAdvice pass-through khi body instanceof ApiResponse<?> [VERIFIED: ApiResponseAdvice.java].
 * Nếu trả plain AuthResponseDto, FE sẽ nhận { data: { data: { accessToken, user } } } (double-wrapped).
 *
 * 3 endpoint mới: verify-email + forgot + reset — KHÔNG yêu cầu JWT (public, không cần đăng nhập).
 * Anti-enumeration (D-08, T-27-10): forgot luôn trả 200 bất kể email tồn tại hay không.
 */
@RestController
@RequestMapping("/auth")
@Validated
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /auth/register — đăng ký user mới, trả 201 + JWT + UserDto.
     * Phase 27: sau register, phát event UserRegistered (MAIL-02); JWT vẫn phát ngay (D-07).
     *
     * @return 201 Created với ApiResponse chứa accessToken và UserDto (không có passwordHash)
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponseDto> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.of(201, "Registered successfully", authService.register(request));
    }

    /**
     * POST /auth/login — xác thực credentials, trả 200 + JWT + UserDto.
     *
     * @return 200 OK với ApiResponse chứa accessToken và UserDto
     */
    @PostMapping("/login")
    public ApiResponse<AuthResponseDto> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.of(200, "Login successful", authService.login(request));
    }

    /**
     * POST /auth/logout — client-side discard only (D-05).
     *
     * Backend không cần xử lý gì — token vẫn valid đến khi hết hạn.
     * FE xóa token khỏi localStorage + zero auth_present + user_role cookies.
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.of(200, "Logged out", null);
    }

    /**
     * GET /auth/verify-email?token=... — xác minh email qua single-use token.
     * Public endpoint (không cần JWT) — link trong email register.
     *
     * @return 200 OK khi token hợp lệ + email_verified lật thành true
     * @throws ResponseStatusException 400 token không hợp lệ/sai loại
     * @throws ResponseStatusException 410 GONE token đã dùng hoặc hết hạn
     */
    @GetMapping("/verify-email")
    public ApiResponse<Void> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ApiResponse.of(200, "Email xác minh thành công", null);
    }

    /**
     * POST /auth/password/forgot — yêu cầu đặt lại mật khẩu.
     * Public endpoint (không cần JWT).
     *
     * Anti-enumeration (D-08, T-27-10): luôn trả 200 bất kể email tồn tại hay không.
     *
     * @return 200 OK (luôn luôn — không tiết lộ email có tồn tại hay không)
     */
    @PostMapping("/password/forgot")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ApiResponse.of(200, "Nếu email tồn tại, link đặt lại đã được gửi", null);
    }

    /**
     * POST /auth/password/reset — đặt lại mật khẩu bằng token từ email.
     * Public endpoint (không cần JWT).
     *
     * @return 200 OK khi đặt lại thành công
     * @throws ResponseStatusException 400 token không hợp lệ/sai loại
     * @throws ResponseStatusException 410 GONE token đã dùng hoặc hết hạn
     */
    @PostMapping("/password/reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ApiResponse.of(200, "Mật khẩu đã được đặt lại", null);
    }
}
