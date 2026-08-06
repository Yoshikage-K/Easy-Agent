package com.eaharness.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(@NotBlank String message) {
}
