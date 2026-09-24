package com.example.demo.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LimitRequest(
        @NotBlank @Size(max = 128) String apiKey,
        @Min(1) @Max(1_000_000_000) int limit,
        @Min(1) @Max(31_536_000) int windowSeconds) {
}
