package com.ptit.htpt.notificationservice.api;

public record FieldErrorItem(
    String field,
    Object rejectedValue,
    String message
) {}

