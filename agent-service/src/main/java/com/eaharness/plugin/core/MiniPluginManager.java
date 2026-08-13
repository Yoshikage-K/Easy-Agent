package com.eaharness.plugin.core;

import com.eaharness.plugin.domain.PluginDescriptor;
import com.eaharness.plugin.domain.PluginState;
import com.eaharness.plugin.exception.PluginException;
import com.eaharness.plugin.spi.AgentPlugin;
import com.eaharness.plugin.spi.ExtensionPoint;
import com.eaharness.plugin.spi.PluginExtensionPoint;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MiniPluginManager implements AutoCloseable {
    private final Path pluginsDirectory;
    private final Map<String, PluginContainer> plugins = new ConcurrentHashMap<>();
    private final ExtensionRegistry extensionRegistry = new ExtensionRegistry();
    private final Object lifecycleLock = new Object();

    // 初始化插件管理器并把插件目录规范化为绝对路径。
    public MiniPluginManager(Path pluginsDirectory) {
        this.pluginsDirectory = pluginsDirectory.toAbsolutePath().normalize();
    }

    // 扫描插件目录并加载尚未注册的 JAR 插件。
    public List<PluginDescriptor> discover() {
        synchronized (lifecycleLock) {
            try {
                Files.createDirectories(pluginsDirectory);
                try (var paths = Files.list(pluginsDirectory)) {
                    paths.filter(path -> path.toString().endsWith(".jar"))
                            .sorted()
                            .filter(path -> plugins.values().stream().noneMatch(
                                    plugin -> plugin.getDescriptor().pluginPath().equals(path)))
                            .forEach(this::load);
                }
                return plugins.values().stream()
                        .map(PluginContainer::getDescriptor)
                        .sorted(Comparator.comparing(PluginDescriptor::pluginId))
                        .toList();
            } catch (IOException exception) {
                throw new PluginException("Failed to scan plugin directory", exception);
            }
        }
    }

    // 加载指定路径的插件 JAR，并把它放入运行时容器。
    public PluginDescriptor load(Path pluginPath) {
        synchronized (lifecycleLock) {
            Path path = validatePath(pluginPath);
            PluginDescriptor descriptor = PluginDescriptorReader.read(path);
            if (plugins.containsKey(descriptor.pluginId())) {
                throw new PluginException("Plugin already loaded: " + descriptor.pluginId());
            }
            try {
                PluginClassLoader classLoader = new PluginClassLoader(path.toUri().toURL(), getClass().getClassLoader());
                AgentPlugin plugin = instantiatePlugin(descriptor, classLoader);
                plugins.put(descriptor.pluginId(), new PluginContainer(descriptor, classLoader, plugin));
                return descriptor;
            } catch (Exception exception) {
                throw new PluginException("Failed to load plugin: " + descriptor.pluginId(), exception);
            }
        }
    }

    // 启动指定插件，如果插件已经启动则直接返回当前状态。
    public PluginState start(String pluginId) {
        synchronized (lifecycleLock) {
            PluginContainer container = require(pluginId);
            if (container.getState() == PluginState.STARTED) return container.getState();
            try {
                if (container.getPlugin() != null) container.getPlugin().start(container.getContext());
                if (!container.hasLoadedExtensions(PluginExtensionPoint.class)) {
                    container.cacheExtensions(
                            PluginExtensionPoint.class,
                            extensionRegistry.load(container, PluginExtensionPoint.class));
                }
                container.setState(PluginState.STARTED);
                return container.getState();
            } catch (Exception exception) {
                container.setState(PluginState.FAILED);
                throw new PluginException("Failed to start plugin: " + pluginId, exception);
            }
        }
    }

    // 停止指定插件，如果插件尚未启动则直接返回当前状态。
    public PluginState stop(String pluginId) {
        synchronized (lifecycleLock) {
            PluginContainer container = require(pluginId);
            if (container.getState() != PluginState.STARTED) return container.getState();
            try {
                if (container.getPlugin() != null) container.getPlugin().stop();
                container.setState(PluginState.STOPPED);
                return container.getState();
            } catch (Exception exception) {
                container.setState(PluginState.FAILED);
                throw new PluginException("Failed to stop plugin: " + pluginId, exception);
            }
        }
    }

    // 卸载指定插件，并关闭对应的类加载器和扩展缓存。
    public PluginDescriptor unload(String pluginId) {
        synchronized (lifecycleLock) {
            PluginContainer container = require(pluginId);
            if (container.getState() == PluginState.STARTED) stop(pluginId);
            try {
                container.clearExtensions();
                container.getClassLoader().close();
                PluginDescriptor descriptor = container.getDescriptor();
                plugins.remove(pluginId);
                return descriptor;
            } catch (IOException exception) {
                throw new PluginException("Failed to unload plugin: " + pluginId, exception);
            }
        }
    }

    // 先卸载旧版本，再按原路径重新加载插件。
    public PluginDescriptor reload(String pluginId) {
        Path path = require(pluginId).getDescriptor().pluginPath();
        unload(pluginId);
        return load(path);
    }

    // 收集当前已启动插件暴露出来的扩展实现。
    public <T extends ExtensionPoint> List<T> getExtensions(Class<T> extensionPoint) {
        return getExtensionRegistrations(extensionPoint).stream()
                .map(ExtensionRegistration::extension)
                .toList();
    }

    // 返回扩展实例及其所属插件，供需要精准路由的业务使用。
    public <T extends ExtensionPoint> List<ExtensionRegistration<T>> getExtensionRegistrations(
            Class<T> extensionPoint) {
        List<ExtensionRegistration<T>> registrations = new ArrayList<>();
        for (PluginContainer container : plugins.values()) {
            if (container.getState() == PluginState.STARTED) {
                if (!container.hasLoadedExtensions(extensionPoint)) {
                    container.cacheExtensions(extensionPoint, extensionRegistry.load(container, extensionPoint));
                }
                container.getCachedExtensions(extensionPoint).stream()
                        .filter(extensionPoint::isInstance)
                        .map(extensionPoint::cast)
                        .forEach(extension -> registrations.add(new ExtensionRegistration<>(
                                container.getDescriptor().pluginId(), extensionPoint, extension)));
            }
        }
        return registrations;
    }

    // 返回当前已加载插件的快照，并按插件 ID 排序。
    public List<PluginContainer> getPlugins() {
        return plugins.values().stream()
                .sorted(Comparator.comparing(plugin -> plugin.getDescriptor().pluginId()))
                .toList();
    }

    // 关闭管理器时依次卸载所有插件。
    @Override
    public void close() {
        synchronized (lifecycleLock) {
            new ArrayList<>(plugins.keySet()).forEach(pluginId -> {
                try { unload(pluginId); } catch (RuntimeException ignored) { }
            });
        }
    }

    // 根据插件描述创建插件实例，未配置入口类时返回空。
    private AgentPlugin instantiatePlugin(PluginDescriptor descriptor, PluginClassLoader classLoader)
            throws ReflectiveOperationException {
        if (descriptor.pluginClass().isBlank()) return null;
        Class<?> type = Class.forName(descriptor.pluginClass(), true, classLoader);
        if (!AgentPlugin.class.isAssignableFrom(type)) {
            throw new PluginException("Plugin class must implement AgentPlugin: " + descriptor.pluginClass());
        }
        return (AgentPlugin) type.getDeclaredConstructor().newInstance();
    }

    // 从插件 ID 找到对应容器，找不到就抛出异常。
    private PluginContainer require(String pluginId) {
        PluginContainer plugin = plugins.get(pluginId);
        if (plugin == null) throw new PluginException("Plugin not found: " + pluginId);
        return plugin;
    }

    // 校验插件文件必须是目录内存在的 JAR 文件。
    private Path validatePath(Path rawPath) {
        Path root = pluginsDirectory.toAbsolutePath().normalize();
        Path path = rawPath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path) || !path.toString().endsWith(".jar")) {
            throw new PluginException("Plugin path must be an existing JAR: " + path);
        }
        if (!path.startsWith(root)) {
            throw new PluginException("Plugin path must be inside " + root);
        }
        return path;
    }
}
