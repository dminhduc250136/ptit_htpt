package com.ptit.htpt.userservice.web;

import com.ptit.htpt.userservice.api.ApiResponse;
import com.ptit.htpt.userservice.domain.AddressDto;
import com.ptit.htpt.userservice.jwt.JwtUtils;
import com.ptit.htpt.userservice.service.AddressRequest;
import com.ptit.htpt.userservice.service.AddressService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Phase 11 / Plan 11-01 (ACCT-05).
 * REST endpoints /users/me/addresses/** cho address CRUD + set-default.
 *
 * T-11-01-01: userId luôn lấy từ JWT claims.sub (extractUserIdFromBearer) —
 * KHÔNG nhận userId từ path/body param. Pattern consistent với T-09-03-02.
 *
 * Gateway route: /api/users/me/addresses/** → user-svc /users/me/addresses/**
 * (đứng sau /api/users/me/** route — check api-gateway/application.yml).
 */
@RestController
@RequestMapping("/users/me/addresses")
public class AddressController {

    private final AddressService addressService;
    private final JwtUtils jwtUtils;

    public AddressController(AddressService addressService, JwtUtils jwtUtils) {
        this.addressService = addressService;
        this.jwtUtils = jwtUtils;
    }

    /**
     * GET /users/me/addresses — lấy danh sách addresses của user hiện tại.
     *
     * @param authHeader Authorization Bearer token.
     * @return 200 + List<AddressDto> sort is_default DESC, created_at DESC.
     */
    @GetMapping
    public ApiResponse<List<AddressDto>> listAddresses(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        String userId = extractUserIdFromBearer(authHeader);
        return ApiResponse.of(200, "OK", addressService.listAddresses(userId));
    }

    /**
     * POST /users/me/addresses — tạo address mới.
     * Nếu count >= 10 → 422 ADDRESS_LIMIT_EXCEEDED.
     *
     * @param authHeader Authorization Bearer token.
     * @param body       AddressRequest validated.
     * @return 201 + AddressDto vừa tạo.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AddressDto> createAddress(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @Valid @RequestBody AddressRequest body
    ) {
        String userId = extractUserIdFromBearer(authHeader);
        return ApiResponse.of(201, "Address created", addressService.createAddress(userId, body));
    }

    /**
     * PUT /users/me/addresses/{id} — cập nhật address.
     * 404 nếu không tồn tại; 403 nếu không phải owner.
     *
     * @param authHeader Authorization Bearer token.
     * @param id         UUID của address.
     * @param body       AddressRequest validated.
     * @return 200 + AddressDto updated.
     */
    @PutMapping("/{id}")
    public ApiResponse<AddressDto> updateAddress(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable String id,
            @Valid @RequestBody AddressRequest body
    ) {
        String userId = extractUserIdFromBearer(authHeader);
        return ApiResponse.of(200, "Updated", addressService.updateAddress(userId, id, body));
    }

    /**
     * DELETE /users/me/addresses/{id} — xóa address (hard-delete, D-07).
     * 404 nếu không tồn tại; 403 nếu không phải owner.
     *
     * @param authHeader Authorization Bearer token.
     * @param id         UUID của address.
     * @return 200 + {id: deleted-id}.
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, String>> deleteAddress(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable String id
    ) {
        String userId = extractUserIdFromBearer(authHeader);
        addressService.deleteAddress(userId, id);
        return ApiResponse.of(200, "Deleted", Map.of("id", id));
    }

    /**
     * PUT /users/me/addresses/{id}/default — set address là mặc định.
     * Clear is_default trên tất cả addresses khác của user trước khi set.
     * 404 nếu không tồn tại; 403 nếu không phải owner.
     *
     * @param authHeader Authorization Bearer token.
     * @param id         UUID của address.
     * @return 200 + AddressDto sau set-default.
     */
    @PutMapping("/{id}/default")
    public ApiResponse<AddressDto> setDefault(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable String id
    ) {
        String userId = extractUserIdFromBearer(authHeader);
        return ApiResponse.of(200, "Default set", addressService.setDefault(userId, id));
    }

    /**
     * Extract userId (JWT subject) từ Bearer token.
     * T-11-01-01: userId lấy từ JWT claims.sub — KHÔNG từ body hay path param.
     * Pattern copy từ UserMeController (T-09-03-02).
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
