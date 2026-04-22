package com.ptit.htpt.userservice.service;

import com.ptit.htpt.userservice.domain.UserAddress;
import com.ptit.htpt.userservice.domain.UserProfile;
import com.ptit.htpt.userservice.repository.InMemoryUserRepository;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserCrudService {
  private final InMemoryUserRepository repository;

  public UserCrudService(InMemoryUserRepository repository) {
    this.repository = repository;
  }

  public Map<String, Object> listProfiles(int page, int size, String sort, boolean includeDeleted) {
    List<UserProfile> all = repository.findAllProfiles().stream()
        .filter(profile -> includeDeleted || !profile.deleted())
        .sorted(profileComparator(sort))
        .toList();
    return paginate(all, page, size);
  }

  public UserProfile getProfile(String id, boolean includeDeleted) {
    UserProfile profile = repository.findProfileById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile not found"));
    if (!includeDeleted && profile.deleted()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile not found");
    }
    return profile;
  }

  public UserProfile createProfile(ProfileUpsertRequest request) {
    UserProfile profile = UserProfile.create(request.email(), request.fullName(), request.phone());
    return repository.saveProfile(profile);
  }

  public UserProfile updateProfile(String id, ProfileUpsertRequest request) {
    UserProfile current = getProfile(id, true);
    UserProfile updated = current.update(request.email(), request.fullName(), request.phone());
    return repository.saveProfile(updated);
  }

  public void deleteProfile(String id) {
    UserProfile current = getProfile(id, true);
    repository.saveProfile(current.softDelete());
  }

  public UserProfile blockProfile(String id) {
    UserProfile current = getProfile(id, true);
    return repository.saveProfile(current.setBlocked(true));
  }

  public UserProfile unblockProfile(String id) {
    UserProfile current = getProfile(id, true);
    return repository.saveProfile(current.setBlocked(false));
  }

  public Map<String, Object> listAddresses(String userId, int page, int size, String sort, boolean includeDeleted) {
    List<UserAddress> all = repository.findAllAddresses().stream()
        .filter(address -> userId == null || userId.isBlank() || userId.equals(address.userId()))
        .filter(address -> includeDeleted || !address.deleted())
        .sorted(addressComparator(sort))
        .toList();
    return paginate(all, page, size);
  }

  public UserAddress getAddress(String id, boolean includeDeleted) {
    UserAddress address = repository.findAddressById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User address not found"));
    if (!includeDeleted && address.deleted()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User address not found");
    }
    return address;
  }

  public UserAddress createAddress(AddressUpsertRequest request) {
    UserAddress address = UserAddress.create(
        request.userId(),
        request.label(),
        request.addressLine(),
        request.city(),
        request.defaultAddress()
    );
    return repository.saveAddress(address);
  }

  public UserAddress updateAddress(String id, AddressUpsertRequest request) {
    UserAddress current = getAddress(id, true);
    UserAddress updated = current.update(
        request.userId(),
        request.label(),
        request.addressLine(),
        request.city(),
        request.defaultAddress()
    );
    return repository.saveAddress(updated);
  }

  public void deleteAddress(String id) {
    UserAddress current = getAddress(id, true);
    repository.saveAddress(current.softDelete());
  }

  private Comparator<UserProfile> profileComparator(String sort) {
    if (sort == null || sort.isBlank()) {
      return Comparator.comparing(UserProfile::updatedAt).reversed();
    }
    boolean desc = sort.endsWith(",desc");
    Comparator<UserProfile> comparator = sort.startsWith("fullName")
        ? Comparator.comparing(UserProfile::fullName, String.CASE_INSENSITIVE_ORDER)
        : Comparator.comparing(UserProfile::id);
    return desc ? comparator.reversed() : comparator;
  }

  private Comparator<UserAddress> addressComparator(String sort) {
    if (sort == null || sort.isBlank()) {
      return Comparator.comparing(UserAddress::updatedAt).reversed();
    }
    boolean desc = sort.endsWith(",desc");
    Comparator<UserAddress> comparator = sort.startsWith("city")
        ? Comparator.comparing(UserAddress::city, String.CASE_INSENSITIVE_ORDER)
        : Comparator.comparing(UserAddress::id);
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

  public record ProfileUpsertRequest(@Email @NotBlank String email, @NotBlank String fullName, @NotBlank String phone) {}

  public record AddressUpsertRequest(
      @NotBlank String userId,
      @NotBlank String label,
      @NotBlank String addressLine,
      @NotBlank String city,
      boolean defaultAddress
  ) {}
}
