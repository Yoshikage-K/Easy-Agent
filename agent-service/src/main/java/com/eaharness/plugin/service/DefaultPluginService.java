package com.eaharness.plugin.service;

import com.eaharness.plugin.config.PluginProperties;
import com.eaharness.plugin.core.MiniPluginManager;
import com.eaharness.plugin.core.PluginContainer;
import com.eaharness.plugin.core.ExtensionRegistration;
import com.eaharness.plugin.domain.PluginDescriptor;
import com.eaharness.plugin.dto.PluginLoadRequest;
import com.eaharness.plugin.dto.PluginResponse;
import com.eaharness.plugin.dto.ExtensionExecuteRequest;
import com.eaharness.plugin.dto.ExtensionExecuteResponse;
import com.eaharness.plugin.dto.ExtensionResponse;
import com.eaharness.plugin.exception.PluginException;
import com.eaharness.plugin.spi.PluginExtensionPoint;
import com.eaharness.plugin.spi.PluginService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DefaultPluginService implements PluginService {
    private final MiniPluginManager pluginManager;
    private final PluginProperties properties;

    public DefaultPluginService(MiniPluginManager pluginManager, PluginProperties properties) {
        this.pluginManager = pluginManager;
        this.properties = properties;
    }

    @Override
    public synchronized List<PluginResponse> list() {
        return pluginManager.getPlugins().stream().map(this::toResponse).toList();
    }

    @Override
    public synchronized List<PluginResponse> discover() {
        pluginManager.discover();
        if (properties.isAutoStart()) {
            pluginManager.getPlugins().forEach(plugin -> pluginManager.start(plugin.getDescriptor().pluginId()));
        }
        return list();
    }

    @Override
    public synchronized PluginResponse load(PluginLoadRequest request) {
        Path pluginPath = validatePluginPath(request.pluginPath());
        return toResponse(pluginManager.load(pluginPath));
    }

    @Override
    public synchronized PluginResponse start(String pluginId) {
        pluginManager.start(pluginId);
        return toResponse(require(pluginId));
    }

    @Override
    public synchronized PluginResponse stop(String pluginId) {
        pluginManager.stop(pluginId);
        return toResponse(require(pluginId));
    }

    @Override
    public synchronized PluginResponse unload(String pluginId) {
        PluginDescriptor descriptor = pluginManager.unload(pluginId);
        return new PluginResponse(descriptor.pluginId(), descriptor.version(), "UNLOADED",
                descriptor.pluginClass(), descriptor.description(), descriptor.pluginPath().toString());
    }

    @Override
    public synchronized PluginResponse reload(String pluginId) {
        return toResponse(pluginManager.reload(pluginId));
    }

    @Override
    public synchronized List<ExtensionResponse> extensions() {
        return pluginManager.getExtensionRegistrations(PluginExtensionPoint.class).stream()
                .map(this::toExtensionResponse)
                .toList();
    }

    @Override
    public synchronized ExtensionExecuteResponse executeExtension(
            ExtensionExecuteRequest request) {
        PluginExtensionPoint extension = pluginManager
                .getExtensionRegistrations(PluginExtensionPoint.class).stream()
                .filter(registration -> registration.pluginId().equals(request.pluginId()))
                .filter(registration -> registration.extension().extensionId().equals(request.extensionId()))
                .map(ExtensionRegistration::extension)
                .reduce((first, second) -> {
                    throw new PluginException("Duplicate extension: "
                            + request.pluginId() + "/" + request.extensionId());
                })
                .orElseThrow(() -> new PluginException(
                        "Extension not found: " + request.pluginId() + "/" + request.extensionId()));
        return new ExtensionExecuteResponse(
                request.pluginId(),
                extension.extensionId(),
                extension.execute(request.input()));
    }

    private ExtensionResponse toExtensionResponse(
            ExtensionRegistration<PluginExtensionPoint> registration) {
        PluginExtensionPoint extension = registration.extension();
        return new ExtensionResponse(
                registration.pluginId(),
                registration.extensionPointType().getName(),
                extension.extensionId(),
                extension.getClass().getName());
    }

    private PluginResponse toResponse(PluginDescriptor descriptor) {
        return new PluginResponse(descriptor.pluginId(), descriptor.version(), "RESOLVED",
                descriptor.pluginClass(), descriptor.description(), descriptor.pluginPath().toString());
    }

    private PluginResponse toResponse(PluginContainer container) {
        PluginDescriptor descriptor = container.getDescriptor();
        return new PluginResponse(descriptor.pluginId(), descriptor.version(), container.getState().name(),
                descriptor.pluginClass(), descriptor.description(), descriptor.pluginPath().toString());
    }

    private PluginContainer require(String pluginId) {
        return pluginManager.getPlugins().stream()
                .filter(plugin -> plugin.getDescriptor().pluginId().equals(pluginId))
                .findFirst()
                .orElseThrow(() -> new PluginException("Plugin not found: " + pluginId));
    }

    private Path validatePluginPath(String rawPath) {
        Path root = properties.getPluginsDirectory().toAbsolutePath().normalize();
        Path path = Path.of(rawPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path) || !path.toString().endsWith(".jar")) {
            throw new PluginException("Plugin path must be an existing JAR: " + path);
        }
        if (!path.startsWith(root)) {
            throw new PluginException("Plugin path must be inside the configured plugin directory");
        }
        return path;
    }
}
