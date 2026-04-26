package com.ptit.htpt.userservice.service;

import com.ptit.htpt.userservice.domain.UserEntity;
import com.ptit.htpt.userservice.domain.UserMapper;
import com.ptit.htpt.userservice.jwt.JwtUtils;
import com.ptit.htpt.userservice.repository.UserRepository;
import com.ptit.htpt.userservice.web.AuthResponseDto;
import com.ptit.htpt.userservice.web.LoginRequest;
import com.ptit.htpt.userservice.web.RegisterRequest;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Phase 6 / Plan 01 (AUTH-01, AUTH-02): Business logic cho register + login.
 *
 * Error strategy: ResponseStatusException → GlobalExceptionHandler tự động serialize → ApiErrorResponse.
 * T-06-01 mitigated: generic "Invalid credentials" cho cả email-not-found và password-wrong.
 * T-06-03 mitigated: trả UserDto (không có passwordHash).
 */
@Service
@Transactional
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public AuthService(UserRepository userRepo, PasswordEncoder passwordEncoder, JwtUtils jwtUtils) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
    }

    /**
     * Đăng ký user mới.
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
        String token = jwtUtils.issueToken(entity.id(), entity.username(), entity.roles());
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
        String token = jwtUtils.issueToken(entity.id(), entity.username(), entity.roles());
        return new AuthResponseDto(token, UserMapper.toDto(entity));
    }
}
