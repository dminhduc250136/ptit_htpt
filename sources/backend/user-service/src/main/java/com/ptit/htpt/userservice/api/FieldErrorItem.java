package com.ptit.htpt.userservice.api;

public record FieldErrorItem(
    String field,
    Object rejectedValue,
    String message
) {}

