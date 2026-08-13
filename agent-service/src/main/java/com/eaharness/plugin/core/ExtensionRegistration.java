package com.eaharness.plugin.core;

import com.eaharness.plugin.spi.ExtensionPoint;

/** Runtime association between an extension instance and its owning plugin. */
public record ExtensionRegistration<T extends ExtensionPoint>(
        String pluginId,
        Class<T> extensionPointType,
        T extension) {
}
