package com.eaharness.plugin.spi;

public interface AgentPlugin {

    default void start(PluginContext context) {
    }

    default void stop() {
    }
}
