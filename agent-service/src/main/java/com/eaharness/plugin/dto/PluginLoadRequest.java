package com.eaharness.plugin.dto;

import jakarta.validation.constraints.NotBlank;

public record PluginLoadRequest(@NotBlank String pluginPath) {
}
