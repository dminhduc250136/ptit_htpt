package com.ptit.htpt.userservice.service;

import com.ptit.htpt.userservice.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 9 / Plan 09-02 (UI-02): Admin stats service cho user-service.
 * Cung cấp số liệu thật cho admin dashboard KPI cards.
 */
@Service
public class UserStatsService {

  private final UserRepository userRepo;

  public UserStatsService(UserRepository userRepo) {
    this.userRepo = userRepo;
  }

  @Transactional(readOnly = true)
  public long totalUsers() {
    return userRepo.count();
  }
}
