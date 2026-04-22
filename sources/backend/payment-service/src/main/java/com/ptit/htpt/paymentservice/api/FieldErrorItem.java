package com.ptit.htpt.paymentservice.api;

public record FieldErrorItem(
    String field,
    Object rejectedValue,
    String message
) {}

