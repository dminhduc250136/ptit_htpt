package com.ptit.htpt.userservice.service;

import com.ptit.htpt.userservice.domain.UserDto;
import com.ptit.htpt.userservice.domain.UserEntity;
import com.ptit.htpt.userservice.domain.UserMapper;
import com.ptit.htpt.userservice.repository.UserRepository;
import com.ptit.htpt.userservice.web.UpdateMeRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Phase 10 / Plan 10-01 (ACCT-03). Service layer cho profile read/update.
 *
 * Cô lập profile concern khỏi UserCrudService (admin) + UserPasswordService (password).
 * D-05: userId luôn lấy từ JWT subject (controller extract Bearer → claims.sub).
 */
@Service
@Transactional
public class UserProfileService {

    private final UserRepository userRepo;

    public UserProfileService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    /**
     * Lấy profile của user hiện tại.
     *
     * @param userId UUID string lấy từ JWT subject (controller parse Bearer header).
     * @return UserDto với hasAvatar=false (Phase 10 — avatar defer per D-08).
     * @throws ResponseStatusException 404 nếu userId không tìm thấy trong DB.
     */
    @Transactional(readOnly = true)
    public UserDto getMe(String userId) {
        UserEntity entity = userRepo.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return UserMapper.toDto(entity);
    }

    /**
     * Cập nhật profile của user hiện tại (partial update — nullable optional fields).
     *
     * @param userId UUID string lấy từ JWT subject (controller parse Bearer header).
     * @param req    {fullName?, phone?} — nullable fields, validate chỉ khi not-null.
     * @return UserDto updated với hasAvatar=false (Phase 10 — avatar defer per D-08).
     * @throws ResponseStatusException 404 nếu userId không tìm thấy trong DB.
     */
    public UserDto updateMe(String userId, UpdateMeRequest req) {
        UserEntity entity = userRepo.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (req.fullName() != null) entity.setFullName(req.fullName());
        if (req.phone() != null) entity.setPhone(req.phone());
        entity.touch();
        return UserMapper.toDto(userRepo.save(entity));
    }
}
