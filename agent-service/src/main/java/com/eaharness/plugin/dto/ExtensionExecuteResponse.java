package com.eaharness.plugin.dto;

public record ExtensionExecuteResponse(
        String pluginId,
        String extensionId,
        String result) {
}
