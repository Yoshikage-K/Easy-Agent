package com.eaharness.plugin.dto;

public record ExtensionResponse(
        String pluginId,
        String extensionPoint,
        String extensionId,
        String implementationClass) {
}
