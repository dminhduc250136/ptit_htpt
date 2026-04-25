package com.ptit.htpt.apigateway.gateway;

public record FieldErrorItem(String field, Object rejectedValue, String message) {}

