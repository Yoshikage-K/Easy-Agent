package com.eaharness.plugin.dto;

public record PluginResponse(
        String pluginId,
        String version,
        String state,
        String pluginClass,
        String description,
        String path) {
}
