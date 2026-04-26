package com.ptit.htpt.userservice.web;

import com.ptit.htpt.userservice.api.ApiResponse;
import com.ptit.htpt.userservice.service.UserCrudService;
import com.ptit.htpt.userservice.service.UserCrudService.UserUpsertRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * User-facing endpoints (non-admin). Phase 5 refactor: user model giờ là
 * username/email/roles (auth-focused) thay cho fullName/phone/blocked.
 * Address endpoints REMOVED — defer Phase 8 (PATTERNS scope-cut).
 */
@RestController
@RequestMapping("/users")
public class UserProfileController {
  private final UserCrudService userCrudService;

  public UserProfileController(UserCrudService userCrudService) {
    this.userCrudService = userCrudService;
  }

  @GetMapping("/profiles")
  public ApiResponse<Map<String, Object>> listProfiles(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "updatedAt,desc") String sort
  ) {
    return ApiResponse.of(200, "User profiles listed", userCrudService.listUsers(page, size, sort));
  }

  @GetMapping("/profiles/{id}")
  public ApiResponse<Object> getProfile(@PathVariable String id) {
    return ApiResponse.of(200, "User profile loaded", userCrudService.getUser(id));
  }

  @PostMapping("/profiles")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<Object> createProfile(@Valid @RequestBody UserUpsertRequest request) {
    return ApiResponse.of(201, "User profile created", userCrudService.createUser(request));
  }

  @PutMapping("/profiles/{id}")
  public ApiResponse<Object> updateProfile(@PathVariable String id, @Valid @RequestBody UserUpsertRequest request) {
    return ApiResponse.of(200, "User profile updated", userCrudService.updateUser(id, request));
  }

  @DeleteMapping("/profiles/{id}")
  public ApiResponse<Map<String, Object>> deleteProfile(@PathVariable String id) {
    userCrudService.deleteUser(id);
    return ApiResponse.of(200, "User profile soft deleted", Map.of("id", id, "deleted", true));
  }
}
