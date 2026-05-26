package com.ptit.htpt.orderservice.service;

import com.ptit.htpt.orderservice.domain.CouponEntity;
import com.ptit.htpt.orderservice.exception.CouponErrorCode;
import com.ptit.htpt.orderservice.exception.CouponException;
import com.ptit.htpt.orderservice.repository.CouponRedemptionRepository;
import com.ptit.htpt.orderservice.repository.CouponRepository;
import com.ptit.htpt.orderservice.web.CouponDtos.CouponDto;
import com.ptit.htpt.orderservice.web.CouponDtos.CreateCouponRequest;
import com.ptit.htpt.orderservice.web.CouponDtos.UpdateCouponRequest;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 20 / Plan 20-02 (D-14): Admin CRUD coupon service.
 *
 * <p>5 operation: list (page/size/sort/q/active filter), getById, create,
 * update, setActive, delete.
 *
 * <p>D-14 hard-DELETE guard: chỉ xoá khi {@code countByCouponId == 0 && usedCount == 0}.
 * Nếu có redemption hoặc usedCount > 0 → throw COUPON_HAS_REDEMPTIONS (409).
 *
 * <p>Filter {@code q} (code contains, case-insensitive) áp dụng in-memory
 * sau khi load page — tránh SQL injection (T-20-02-07) và đủ cho volume nhỏ
 * MVP (~vài chục coupon).
 */
@Service
public class CouponService {

  private final CouponRepository couponRepository;
  private final CouponRedemptionRepository redemptionRepository;

  public CouponService(CouponRepository couponRepository,
                       CouponRedemptionRepository redemptionRepository) {
    this.couponRepository = couponRepository;
    this.redemptionRepository = redemptionRepository;
  }

  @Transactional(readOnly = true)
  public Page<CouponDto> list(int page, int size, String sort, String q, Boolean active) {
    int safePage = Math.max(page, 0);
    int safeSize = size <= 0 ? 20 : Math.min(size, 200);
    Pageable pageable = PageRequest.of(safePage, safeSize, parseSort(sort));

    Page<CouponEntity> all = couponRepository.findAll(pageable);

    List<CouponDto> filtered = all.stream()
        .filter(c -> q == null || q.isBlank()
            || c.code().toLowerCase().contains(q.toLowerCase()))
        .filter(c -> active == null || c.active() == active)
        .map(this::toDto)
        .toList();

    return new PageImpl<>(filtered, pageable, filtered.size());
  }

  @Transactional(readOnly = true)
  public CouponDto getById(String id) {
    return couponRepository.findById(id)
        .map(this::toDto)
        .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));
  }

  @Transactional
  public CouponDto create(CreateCouponRequest req) {
    CouponEntity c = CouponEntity.create(
        req.code(),
        req.type(),
        req.value(),
        req.minOrderAmount(),
        req.maxTotalUses(),
        req.expiresAt(),
        req.active() == null ? true : req.active()
    );
    return toDto(couponRepository.save(c));
  }

  @Transactional
  public CouponDto update(String id, UpdateCouponRequest req) {
    CouponEntity c = couponRepository.findById(id)
        .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));
    c.update(
        req.code(),
        req.type(),
        req.value(),
        req.minOrderAmount(),
        req.maxTotalUses(),
        req.expiresAt(),
        req.active() == null ? c.active() : req.active()
    );
    return toDto(couponRepository.save(c));
  }

  @Transactional
  public CouponDto setActive(String id, boolean active) {
    CouponEntity c = couponRepository.findById(id)
        .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));
    c.setActive(active);
    return toDto(couponRepository.save(c));
  }

  @Transactional
  public void delete(String id) {
    CouponEntity c = couponRepository.findById(id)
        .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));
    if (redemptionRepository.countByCouponId(id) > 0 || c.usedCount() > 0) {
      throw new CouponException(CouponErrorCode.COUPON_HAS_REDEMPTIONS);
    }
    couponRepository.delete(c);
  }

  private CouponDto toDto(CouponEntity c) {
    return new CouponDto(
        c.id(),
        c.code(),
        c.type().name(),
        c.value(),
        c.minOrderAmount(),
        c.maxTotalUses(),
        c.usedCount(),
        c.expiresAt(),
        c.active(),
        c.createdAt(),
        c.updatedAt()
    );
  }

  /** Parse "field,dir" pattern. Default: createdAt DESC. */
  private Sort parseSort(String sort) {
    if (sort == null || sort.isBlank()) {
      return Sort.by(Sort.Direction.DESC, "createdAt");
    }
    String[] parts = sort.split(",");
    String field = parts[0].trim();
    Sort.Direction dir = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
        ? Sort.Direction.DESC
        : Sort.Direction.ASC;
    return Sort.by(dir, field);
  }
}
