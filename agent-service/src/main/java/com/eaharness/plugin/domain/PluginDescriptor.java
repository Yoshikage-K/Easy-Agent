package com.eaharness.plugin.domain;

import java.nio.file.Path;

public record PluginDescriptor(
        String pluginId,
        String version,
        String pluginClass,
        String description,
        Path pluginPath) {
}
