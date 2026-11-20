package com.kosh.ledger.web;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;

import jakarta.servlet.http.HttpServletRequest;

public record ApiError(
    int status,
    String error,
    String message,
    String path,
    Instant timestamp,
    Map<String, String> fieldErrors) {
    
        public static ApiError of(HttpStatus status, String message, HttpServletRequest request) {
            return new ApiError(status.value(), status.getReasonPhrase(), message, request.getRequestURI(), Instant.now(), Map.of());
        }
}
