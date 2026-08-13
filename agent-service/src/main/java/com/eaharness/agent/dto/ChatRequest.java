package com.eaharness.agent.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(@NotBlank String message) {
}
