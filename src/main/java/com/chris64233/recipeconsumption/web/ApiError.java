package com.chris64233.recipeconsumption.web;

import java.time.Instant;
import java.util.Map;

/**
 * 统一错误响应体。
 */
public record ApiError(String error, String message, Instant timestamp, Map<String, String> details) {
    public ApiError(String error, String message) {
        this(error, message, Instant.now(), Map.of());
    }
}
