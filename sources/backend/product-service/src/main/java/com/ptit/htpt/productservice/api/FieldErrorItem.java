package com.ptit.htpt.productservice.api;

public record FieldErrorItem(
    String field,
    Object rejectedValue,
    String message
) {}

