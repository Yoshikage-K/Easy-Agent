package com.eaharness.plugin.core;

import com.eaharness.plugin.domain.PluginDescriptor;
import com.eaharness.plugin.domain.PluginState;
import com.eaharness.plugin.spi.AgentPlugin;
import com.eaharness.plugin.spi.PluginContext;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class PluginContainer {
    private final PluginDescriptor descriptor;
    private final PluginClassLoader classLoader;
    private final AgentPlugin plugin;
    private final PluginContext context;
    private final Map<Class<? extends com.eaharness.plugin.spi.ExtensionPoint>, List<Object>> extensionsByPoint =
            new HashMap<>();
    @Setter
    private PluginState state = PluginState.RESOLVED;

    public PluginContainer(PluginDescriptor descriptor, PluginClassLoader classLoader, AgentPlugin plugin) {
        this.descriptor = descriptor;
        this.classLoader = classLoader;
        this.plugin = plugin;
        this.context = new PluginContext(descriptor.pluginId());
    }

    public boolean hasLoadedExtensions(Class<? extends com.eaharness.plugin.spi.ExtensionPoint> extensionPoint) {
        return extensionsByPoint.containsKey(extensionPoint);
    }

    public void cacheExtensions(
            Class<? extends com.eaharness.plugin.spi.ExtensionPoint> extensionPoint,
            List<?> extensions) {
        extensionsByPoint.put(extensionPoint, new ArrayList<>(extensions));
    }

    public List<Object> getCachedExtensions(
            Class<? extends com.eaharness.plugin.spi.ExtensionPoint> extensionPoint) {
        return extensionsByPoint.getOrDefault(extensionPoint, List.of());
    }

    public void clearExtensions() {
        extensionsByPoint.clear();
    }

}
