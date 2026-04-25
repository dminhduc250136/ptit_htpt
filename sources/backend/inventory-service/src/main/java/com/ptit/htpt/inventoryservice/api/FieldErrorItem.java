package com.ptit.htpt.inventoryservice.api;

public record FieldErrorItem(
    String field,
    Object rejectedValue,
    String message
) {}

