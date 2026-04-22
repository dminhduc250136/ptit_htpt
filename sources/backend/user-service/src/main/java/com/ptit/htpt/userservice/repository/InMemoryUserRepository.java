package com.ptit.htpt.userservice.repository;

import com.ptit.htpt.userservice.domain.UserAddress;
import com.ptit.htpt.userservice.domain.UserProfile;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryUserRepository {
  private final Map<String, UserProfile> profiles = new LinkedHashMap<>();
  private final Map<String, UserAddress> addresses = new LinkedHashMap<>();

  public Collection<UserProfile> findAllProfiles() {
    return profiles.values();
  }

  public Optional<UserProfile> findProfileById(String id) {
    return Optional.ofNullable(profiles.get(id));
  }

  public UserProfile saveProfile(UserProfile profile) {
    profiles.put(profile.id(), profile);
    return profile;
  }

  public Collection<UserAddress> findAllAddresses() {
    return addresses.values();
  }

  public Optional<UserAddress> findAddressById(String id) {
    return Optional.ofNullable(addresses.get(id));
  }

  public UserAddress saveAddress(UserAddress address) {
    addresses.put(address.id(), address);
    return address;
  }
}
