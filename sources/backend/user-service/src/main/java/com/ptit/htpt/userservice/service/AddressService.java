package com.ptit.htpt.userservice.service;

import com.ptit.htpt.userservice.domain.AddressDto;
import com.ptit.htpt.userservice.domain.AddressEntity;
import com.ptit.htpt.userservice.exception.AddressLimitExceededException;
import com.ptit.htpt.userservice.repository.AddressRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Phase 11 / Plan 11-01 (ACCT-05).
 * Service layer cho address CRUD + set-default.
 *
 * T-11-01-01: userId lấy từ JWT subject (controller extract Bearer → claims.sub).
 * T-11-01-02: Ownership check — address.userId().equals(userId) — else 403 FORBIDDEN.
 * T-11-01-03: Cap 10/user — countByUserId(userId) >= 10 → throw AddressLimitExceededException.
 * T-11-01-04: clearDefaultByUserId() trước set-default — DB partial unique index enforces SC-3.
 * T-11-01-05: findByUserIdOrderBy... filter by userId — user chỉ thấy addresses của mình.
 */
@Service
@Transactional
public class AddressService {

    private final AddressRepository addressRepo;

    public AddressService(AddressRepository addressRepo) {
        this.addressRepo = addressRepo;
    }

    /**
     * Lấy danh sách addresses của user hiện tại, sort: is_default DESC, created_at DESC.
     *
     * @param userId UUID string lấy từ JWT subject.
     * @return List AddressDto của user (T-11-01-05: filter by userId).
     */
    @Transactional(readOnly = true)
    public List<AddressDto> listAddresses(String userId) {
        return addressRepo.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId)
            .stream()
            .map(AddressDto::from)
            .toList();
    }

    /**
     * Tạo address mới cho user.
     * T-11-01-03: kiểm tra count >= 10 → throw AddressLimitExceededException → 422.
     *
     * @param userId UUID string lấy từ JWT subject.
     * @param req    AddressRequest validated body.
     * @return AddressDto của address vừa tạo.
     * @throws AddressLimitExceededException nếu đã có >= 10 addresses.
     */
    public AddressDto createAddress(String userId, AddressRequest req) {
        if (addressRepo.countByUserId(userId) >= 10) {
            throw new AddressLimitExceededException();
        }
        AddressEntity entity = AddressEntity.create(
            userId,
            req.fullName(),
            req.phone(),
            req.street(),
            req.ward(),
            req.district(),
            req.city()
        );
        if (req.isDefault()) {
            addressRepo.clearDefaultByUserId(userId);
            entity.setDefault(true);
        }
        return AddressDto.from(addressRepo.save(entity));
    }

    /**
     * Cập nhật toàn bộ fields của address (ngoại trừ is_default).
     * T-11-01-02: ownership check — 403 nếu userId không khớp.
     *
     * @param userId    UUID string lấy từ JWT subject.
     * @param addressId UUID của address cần update.
     * @param req       AddressRequest validated body.
     * @return AddressDto sau update.
     * @throws ResponseStatusException 404 nếu address không tồn tại; 403 nếu không phải owner.
     */
    public AddressDto updateAddress(String userId, String addressId, AddressRequest req) {
        AddressEntity entity = findAndCheckOwner(userId, addressId);
        entity.setFullName(req.fullName());
        entity.setPhone(req.phone());
        entity.setStreet(req.street());
        entity.setWard(req.ward());
        entity.setDistrict(req.district());
        entity.setCity(req.city());
        return AddressDto.from(addressRepo.save(entity));
    }

    /**
     * Xóa address (hard-delete, D-07).
     * T-11-01-02: ownership check — 403 nếu userId không khớp.
     *
     * @param userId    UUID string lấy từ JWT subject.
     * @param addressId UUID của address cần xóa.
     * @throws ResponseStatusException 404 nếu address không tồn tại; 403 nếu không phải owner.
     */
    public void deleteAddress(String userId, String addressId) {
        findAndCheckOwner(userId, addressId);
        addressRepo.deleteById(addressId);
    }

    /**
     * Set is_default=true cho address; clear is_default trên các address khác của cùng user.
     * T-11-01-04: clearDefaultByUserId + DB partial unique index đảm bảo SC-3.
     * T-11-01-02: ownership check — 403 nếu userId không khớp.
     *
     * @param userId    UUID string lấy từ JWT subject.
     * @param addressId UUID của address cần set default.
     * @return AddressDto sau set-default.
     * @throws ResponseStatusException 404 nếu address không tồn tại; 403 nếu không phải owner.
     */
    public AddressDto setDefault(String userId, String addressId) {
        AddressEntity entity = findAndCheckOwner(userId, addressId);
        addressRepo.clearDefaultByUserId(userId);
        entity.setDefault(true);
        return AddressDto.from(addressRepo.save(entity));
    }

    /**
     * Tìm address by ID và kiểm tra ownership.
     * T-11-01-02: Enforce — address.userId().equals(userId) — else 403 FORBIDDEN.
     */
    private AddressEntity findAndCheckOwner(String userId, String addressId) {
        AddressEntity entity = addressRepo.findById(addressId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
        if (!entity.userId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your address");
        }
        return entity;
    }
}
