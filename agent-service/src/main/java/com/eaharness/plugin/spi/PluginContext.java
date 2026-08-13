package com.eaharness.plugin.spi;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PluginContext {
    private final String pluginId;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    public PluginContext(String pluginId) {
        this.pluginId = pluginId;
    }

    public String getPluginId() {
        return pluginId;
    }

    public void put(String key, Object value) {
        attributes.put(key, value);
    }

    public Object get(String key) {
        return attributes.get(key);
    }
}
