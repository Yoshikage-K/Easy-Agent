package com.eaharness.plugin.spi;

/** Minimal executable extension point exposed by EA-Harness to external plugins. */
public interface PluginExtensionPoint extends ExtensionPoint {
    String name();

    /** Stable identifier used to route a request to one extension implementation. */
    default String extensionId() {
        return name();
    }

    String execute(String input);
}
