package com.eaharness.plugin.dto;

import jakarta.validation.constraints.NotBlank;

public record ExtensionExecuteRequest(
        @NotBlank String pluginId,
        @NotBlank String extensionId,
        String input) {
}
