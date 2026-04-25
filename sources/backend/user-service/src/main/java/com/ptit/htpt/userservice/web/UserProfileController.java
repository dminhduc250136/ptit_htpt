package com.ptit.htpt.userservice.web;

import com.ptit.htpt.userservice.api.ApiResponse;
import com.ptit.htpt.userservice.service.UserCrudService;
import com.ptit.htpt.userservice.service.UserCrudService.AddressUpsertRequest;
import com.ptit.htpt.userservice.service.UserCrudService.ProfileUpsertRequest;
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
    return ApiResponse.of(200, "User profiles listed", userCrudService.listProfiles(page, size, sort, false));
  }

  @GetMapping("/profiles/{id}")
  public ApiResponse<Object> getProfile(@PathVariable String id) {
    return ApiResponse.of(200, "User profile loaded", userCrudService.getProfile(id, false));
  }

  @PostMapping("/profiles")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<Object> createProfile(@Valid @RequestBody ProfileUpsertRequest request) {
    return ApiResponse.of(201, "User profile created", userCrudService.createProfile(request));
  }

  @PutMapping("/profiles/{id}")
  public ApiResponse<Object> updateProfile(@PathVariable String id, @Valid @RequestBody ProfileUpsertRequest request) {
    return ApiResponse.of(200, "User profile updated", userCrudService.updateProfile(id, request));
  }

  @DeleteMapping("/profiles/{id}")
  public ApiResponse<Map<String, Object>> deleteProfile(@PathVariable String id) {
    userCrudService.deleteProfile(id);
    return ApiResponse.of(200, "User profile soft deleted", Map.of("id", id, "deleted", true));
  }

  @GetMapping("/addresses")
  public ApiResponse<Map<String, Object>> listAddresses(
      @RequestParam(required = false) String userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "updatedAt,desc") String sort
  ) {
    return ApiResponse.of(200, "User addresses listed", userCrudService.listAddresses(userId, page, size, sort, false));
  }

  @GetMapping("/addresses/{id}")
  public ApiResponse<Object> getAddress(@PathVariable String id) {
    return ApiResponse.of(200, "User address loaded", userCrudService.getAddress(id, false));
  }

  @PostMapping("/addresses")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<Object> createAddress(@Valid @RequestBody AddressUpsertRequest request) {
    return ApiResponse.of(201, "User address created", userCrudService.createAddress(request));
  }

  @PutMapping("/addresses/{id}")
  public ApiResponse<Object> updateAddress(@PathVariable String id, @Valid @RequestBody AddressUpsertRequest request) {
    return ApiResponse.of(200, "User address updated", userCrudService.updateAddress(id, request));
  }

  @DeleteMapping("/addresses/{id}")
  public ApiResponse<Map<String, Object>> deleteAddress(@PathVariable String id) {
    userCrudService.deleteAddress(id);
    return ApiResponse.of(200, "User address soft deleted", Map.of("id", id, "deleted", true));
  }
}
