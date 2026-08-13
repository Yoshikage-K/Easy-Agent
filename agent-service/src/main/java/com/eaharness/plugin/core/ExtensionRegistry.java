package com.eaharness.plugin.core;

import com.eaharness.plugin.annotation.Extension;
import com.eaharness.plugin.exception.PluginException;
import com.eaharness.plugin.spi.ExtensionPoint;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public class ExtensionRegistry {
    // 从索引文件和 ServiceLoader 两种来源加载扩展实现。
    public <T extends ExtensionPoint> List<T> load(PluginContainer container, Class<T> extensionPoint) {
        List<T> result = new ArrayList<>();
        try {
            var resources = container.getClassLoader().getResources("META-INF/eaharness-extensions.idx");
            while (resources.hasMoreElements()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        resources.nextElement().openStream(), StandardCharsets.UTF_8))) {
                    String className;
                    while ((className = reader.readLine()) != null) {
                        className = className.trim();
                        if (className.isEmpty() || className.startsWith("#")) continue;
                        Object extension = instantiate(container, className);
                        if (extensionPoint.isInstance(extension)) {
                            result.add(extensionPoint.cast(extension));
                        }
                    }
                }
            }
        } catch (IOException exception) {
            throw new PluginException("Failed to load extension index", exception);
        }
        ServiceLoader.load(extensionPoint, container.getClassLoader())
                .forEach(extension -> {
                    if (result.stream().noneMatch(existing -> existing.getClass().equals(extension.getClass()))) {
                        result.add(extension);
                    }
                });
        return result;
    }

    // 根据类名实例化扩展，并校验它是否带有 @Extension 标记。
    private Object instantiate(PluginContainer container, String className) {
        try {
            Class<?> type = Class.forName(className, true, container.getClassLoader());
            if (!type.isAnnotationPresent(Extension.class)) {
                throw new PluginException("Indexed class is not annotated with @Extension: " + className);
            }
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new PluginException("Failed to instantiate extension: " + className, exception);
        }
    }
}
