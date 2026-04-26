package com.ptit.htpt.userservice.service;

import com.ptit.htpt.userservice.domain.UserDto;
import com.ptit.htpt.userservice.domain.UserEntity;
import com.ptit.htpt.userservice.domain.UserMapper;
import com.ptit.htpt.userservice.repository.UserRepository;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Phase 5 / Plan 04: refactor in-memory -> JPA. Schema mới (username/passwordHash/roles)
 * thay cho UserProfile cũ (fullName/phone/blocked) — phục vụ Phase 6 auth.
 *
 * Address logic + block/unblock + listProfiles legacy: REMOVED khỏi user-service
 * (PATTERNS §scope-cut: UserAddress defer Phase 8; block/unblock không tương thích
 * model auth-focused — chuyển sang admin disable qua roles="DISABLED" nếu cần).
 *
 * Service trả `UserDto` ra controller (boundary tại đây) — entity KHÔNG leak.
 */
@Service
@Transactional
public class UserCrudService {
  private final UserRepository userRepo;

  public UserCrudService(UserRepository userRepo) {
    this.userRepo = userRepo;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> listUsers(int page, int size, String sort) {
    List<UserEntity> all = userRepo.findAll().stream()
        .sorted(userComparator(sort))
        .toList();
    return paginate(all.stream().map(UserMapper::toDto).toList(), page, size);
  }

  @Transactional(readOnly = true)
  public UserDto getUser(String id) {
    return UserMapper.toDto(loadUser(id));
  }

  public UserDto createUser(UserUpsertRequest request) {
    if (userRepo.findByUsername(request.username()).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
    }
    if (userRepo.findByEmail(request.email()).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
    }
    UserEntity entity = UserEntity.create(
        request.username(),
        request.email(),
        // Plain text accepted at this layer; Phase 6 auth-service sẽ wrap với BCryptPasswordEncoder.
        // Plan 04 scope: chỉ persistence; admin tạo user qua admin tool có hash sẵn,
        // hoặc Phase 6 register endpoint sẽ hash trước khi gọi service này.
        request.passwordHash(),
        request.roles() == null || request.roles().isBlank() ? "USER" : request.roles()
    );
    return UserMapper.toDto(userRepo.save(entity));
  }

  public UserDto updateUser(String id, UserUpsertRequest request) {
    UserEntity current = loadUser(id);
    current.update(request.username(), request.email(),
        request.roles() == null || request.roles().isBlank() ? current.roles() : request.roles());
    if (request.passwordHash() != null && !request.passwordHash().isBlank()) {
      current.changePasswordHash(request.passwordHash());
    }
    return UserMapper.toDto(userRepo.save(current));
  }

  public void deleteUser(String id) {
    UserEntity current = loadUser(id);
    current.softDelete();
    userRepo.save(current);
  }

  private UserEntity loadUser(String id) {
    return userRepo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
  }

  private Comparator<UserEntity> userComparator(String sort) {
    if (sort == null || sort.isBlank()) {
      return Comparator.comparing(UserEntity::updatedAt).reversed();
    }
    boolean desc = sort.endsWith(",desc");
    Comparator<UserEntity> comparator = sort.startsWith("username")
        ? Comparator.comparing(UserEntity::username, String.CASE_INSENSITIVE_ORDER)
        : Comparator.comparing(UserEntity::id);
    return desc ? comparator.reversed() : comparator;
  }

  private <T> Map<String, Object> paginate(List<T> source, int page, int size) {
    int safePage = Math.max(page, 0);
    int safeSize = size <= 0 ? 20 : Math.min(size, 100);
    int totalElements = source.size();
    int from = Math.min(safePage * safeSize, totalElements);
    int to = Math.min(from + safeSize, totalElements);
    List<T> content = new ArrayList<>(source.subList(from, to));
    int totalPages = totalElements == 0 ? 1 : (int) Math.ceil((double) totalElements / safeSize);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("content", content);
    result.put("totalElements", totalElements);
    result.put("totalPages", totalPages);
    result.put("currentPage", safePage);
    result.put("pageSize", safeSize);
    result.put("isFirst", safePage <= 0);
    result.put("isLast", safePage >= Math.max(totalPages - 1, 0));
    return result;
  }

  public record UserUpsertRequest(
      @NotBlank @Size(max = 80) String username,
      @Email @NotBlank @Size(max = 200) String email,
      @NotBlank @Size(max = 120) String passwordHash,
      String roles
  ) {}
}
