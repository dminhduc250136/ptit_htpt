package com.ptit.htpt.userservice.web;

import com.ptit.htpt.userservice.api.ApiResponse;
import com.ptit.htpt.userservice.jwt.JwtUtils;
import com.ptit.htpt.userservice.service.UserPasswordService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Phase 9 / Plan 09-03 (AUTH-07). Endpoint user-self /me/* group.
 *
 * Phase 10 sẽ extend với GET/PATCH /me cho profile editing — controller này
 * là foundation. Trong Phase 9 chỉ map password change.
 *
 * Gateway route /api/users/me/** ĐỨNG TRƯỚC user-service-base (xem application.yml).
 *
 * T-09-03-02 mitigated: userId lấy từ JWT subject — KHÔNG nhận userId từ path/body.
 * T-09-03-03 mitigated: KHÔNG log request body; Bearer token parse error chỉ throw generic 401.
 * D-11: wrong oldPassword → InvalidPasswordException → GlobalExceptionHandler → 422 AUTH_INVALID_PASSWORD.
 */
@RestController
@RequestMapping("/users/me")
public class UserMeController {

    private final UserPasswordService passwordService;
    private final JwtUtils jwtUtils;

    public UserMeController(UserPasswordService passwordService, JwtUtils jwtUtils) {
        this.passwordService = passwordService;
        this.jwtUtils = jwtUtils;
    }

    /**
     * POST /users/me/password — đổi password sau khi re-auth với oldPassword.
     *
     * @param authHeader Authorization Bearer token (required để xác định userId).
     * @param body       {oldPassword, newPassword} — validated @Size(min=8) + @Pattern.
     * @return 200 + {changed: true} nếu thành công.
     * @throws ResponseStatusException 401 nếu thiếu/invalid Authorization header.
     * @throws InvalidPasswordException → GlobalExceptionHandler → 422 AUTH_INVALID_PASSWORD nếu sai oldPassword.
     */
    @PostMapping("/password")
    public ApiResponse<Map<String, Object>> changePassword(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @Valid @RequestBody ChangePasswordRequest body
    ) {
        String userId = extractUserIdFromBearer(authHeader);
        passwordService.changePassword(userId, body);
        return ApiResponse.of(200, "Đã đổi mật khẩu", Map.of("changed", true));
    }

    /**
     * Extract userId (JWT subject) từ Bearer token.
     * T-09-03-02: userId lấy từ JWT claims.sub — KHÔNG từ body hay path param.
     */
    private String extractUserIdFromBearer(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Missing or invalid Authorization header");
        }
        String token = authHeader.substring("Bearer ".length()).trim();
        try {
            Claims claims = jwtUtils.parseToken(token);
            return claims.getSubject();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }
    }
}
