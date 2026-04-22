package com.ptit.htpt.orderservice.api;

public record FieldErrorItem(
    String field,
    Object rejectedValue,
    String message
) {}

