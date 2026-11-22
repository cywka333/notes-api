package com.example.notes.auth;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.login-rate-limit")
record LoginRateLimitProperties(@Min(1) int attempts, @Min(1) int windowSeconds) {
}
