package com.ptit.htpt.userservice.web;

import com.ptit.htpt.userservice.api.ApiResponse;
import com.ptit.htpt.userservice.service.UserCrudService;
import com.ptit.htpt.userservice.service.UserCrudService.AdminUserPatchRequest;
import com.ptit.htpt.userservice.service.UserCrudService.UserUpsertRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {
  private final UserCrudService userCrudService;

  public AdminUserController(UserCrudService userCrudService) {
    this.userCrudService = userCrudService;
  }

  @GetMapping
  public ApiResponse<Map<String, Object>> listUsers(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "updatedAt,desc") String sort
  ) {
    return ApiResponse.of(200, "Admin users listed", userCrudService.listUsers(page, size, sort));
  }

  @GetMapping("/{id}")
  public ApiResponse<Object> getUser(@PathVariable String id) {
    return ApiResponse.of(200, "Admin user loaded", userCrudService.getUser(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<Object> createUser(@Valid @RequestBody UserUpsertRequest request) {
    return ApiResponse.of(201, "Admin user created", userCrudService.createUser(request));
  }

  @PutMapping("/{id}")
  public ApiResponse<Object> updateUser(@PathVariable String id, @Valid @RequestBody UserUpsertRequest request) {
    return ApiResponse.of(200, "Admin user updated", userCrudService.updateUser(id, request));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Map<String, Object>> deleteUser(@PathVariable String id) {
    userCrudService.deleteUser(id);
    return ApiResponse.of(200, "Admin user soft deleted", Map.of("id", id, "deleted", true));
  }

  // D-05: Partial update — dùng cho admin edit modal (không yêu cầu passwordHash)
  @PatchMapping("/{id}")
  public ApiResponse<Object> patchUser(
      @PathVariable String id,
      @RequestBody AdminUserPatchRequest request     // không dùng @Valid — tất cả fields nullable
  ) {
    return ApiResponse.of(200, "Admin user patched", userCrudService.patchUser(id, request));
  }
}
