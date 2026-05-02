package com.ptit.htpt.userservice.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Phase 11 / Plan 11-01 (ACCT-05).
 * Request body cho create/update address.
 *
 * Validation constraints theo D-04: column sizes (fullName 100, phone 20, street 200,
 * ward/district/city 100).
 */
public record AddressRequest(
    @NotBlank @Size(max = 100) String fullName,
    @NotBlank @Size(max = 20)  String phone,
    @NotBlank @Size(max = 200) String street,
    @NotBlank @Size(max = 100) String ward,
    @NotBlank @Size(max = 100) String district,
    @NotBlank @Size(max = 100) String city,
    boolean isDefault
) {}
