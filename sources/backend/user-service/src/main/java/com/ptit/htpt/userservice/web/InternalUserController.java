package com.ptit.htpt.userservice.web;

import com.ptit.htpt.userservice.repository.UserRepository;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 27 fix (bug #5): endpoint nội bộ để service khác (vd order-service) lấy
 * email user mà KHÔNG cần JWT.
 *
 * <p>An toàn vì:
 *   - user-service KHÔNG expose port ra host (Phase 25) — chỉ container nội bộ docker network gọi được.
 *   - Path /internal/** KHÔNG nằm trong api-gateway routes nên client ngoài không tới được.
 *   - Chỉ trả 1 trường (email + fullName) — không leak thông tin nhạy cảm khác.
 *
 * <p>404 nếu user không tồn tại; trả body rỗng nếu email blank.
 */
@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

  private final UserRepository userRepository;

  public InternalUserController(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @GetMapping("/{id}/email")
  public ResponseEntity<Map<String, String>> getEmail(@PathVariable String id) {
    return userRepository.findById(id)
        .map(u -> ResponseEntity.ok(Map.of(
            "email", u.email() == null ? "" : u.email(),
            "fullName", u.fullName() == null ? "" : u.fullName()
        )))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
