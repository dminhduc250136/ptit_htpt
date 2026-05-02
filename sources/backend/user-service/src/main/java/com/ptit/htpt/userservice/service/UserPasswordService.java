package com.ptit.htpt.userservice.service;

import com.ptit.htpt.userservice.domain.UserEntity;
import com.ptit.htpt.userservice.exception.InvalidPasswordException;
import com.ptit.htpt.userservice.repository.UserRepository;
import com.ptit.htpt.userservice.web.ChangePasswordRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Phase 9 / Plan 09-03 (AUTH-07). Cô lập password concern khỏi UserCrudService.
 *
 * D-10: sau success — KHÔNG rotate JWT, KHÔNG force logout (token cũ vẫn valid 24h).
 * D-11: wrong oldPassword → 422 errorCode AUTH_INVALID_PASSWORD (qua InvalidPasswordException).
 *
 * T-09-03-01 mitigated: BCrypt verify oldPassword BẮT BUỘC trước khi update hash mới.
 * T-09-03-02 mitigated: userId lấy từ JWT subject (do controller extract) — không nhận từ body.
 */
@Service
@Transactional
public class UserPasswordService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    public UserPasswordService(UserRepository userRepo, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Đổi password user sau khi verify BCrypt oldPassword.
     *
     * @param userId UUID string lấy từ JWT subject (controller parse Bearer header).
     * @param req    DTO chứa oldPassword + newPassword (validated @Size/@Pattern đã pass).
     * @throws ResponseStatusException 404 nếu userId không tìm thấy trong DB.
     * @throws InvalidPasswordException 422 AUTH_INVALID_PASSWORD nếu oldPassword sai.
     */
    public void changePassword(String userId, ChangePasswordRequest req) {
        UserEntity entity = userRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // T-09-03-01: BCrypt verify BẮT BUỘC — wrong oldPassword → throw InvalidPasswordException (422).
        if (!passwordEncoder.matches(req.oldPassword(), entity.passwordHash())) {
            throw new InvalidPasswordException();
        }

        String newHash = passwordEncoder.encode(req.newPassword());
        // UserEntity là JPA mutable class với changePasswordHash() (Phase 7 / Plan 03).
        entity.changePasswordHash(newHash);
        userRepo.save(entity);
        // D-10: KHÔNG invalidate token, KHÔNG rotate JWT — token cũ vẫn valid 24h.
    }
}
