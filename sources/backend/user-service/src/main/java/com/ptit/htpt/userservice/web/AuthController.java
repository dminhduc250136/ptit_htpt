package com.ptit.htpt.userservice.web;

import com.ptit.htpt.userservice.api.ApiResponse;
import com.ptit.htpt.userservice.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 6 / Plan 01 (AUTH-01, AUTH-02, D-12): HTTP layer cho auth endpoints.
 *
 * Path /auth maps qua API gateway như /api/users/auth/* (D-12).
 *
 * CRITICAL: Trả ApiResponse<AuthResponseDto> manually để tránh ApiResponseAdvice double-wrap.
 * ApiResponseAdvice pass-through khi body instanceof ApiResponse<?> [VERIFIED: ApiResponseAdvice.java].
 * Nếu trả plain AuthResponseDto, FE sẽ nhận { data: { data: { accessToken, user } } } (double-wrapped).
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
}
