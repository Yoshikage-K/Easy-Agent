package com.eaharness.plugin.core;

import com.eaharness.plugin.domain.PluginDescriptor;
import com.eaharness.plugin.exception.PluginException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;
import java.util.jar.JarFile;

public final class PluginDescriptorReader {
    private static final String[] DESCRIPTOR_PATHS = {
        "plugin.properties",
        "META-INF/eaharness-plugin.properties"
    };

    private PluginDescriptorReader() {
    }

    // 从 JAR 包中读取插件描述文件并组装成 PluginDescriptor。
    public static PluginDescriptor read(Path pluginPath) {
        Properties properties = new Properties();
        try (JarFile jar = new JarFile(pluginPath.toFile())) {
            InputStream descriptor = null;
            for (String descriptorPath : DESCRIPTOR_PATHS) {
                var entry = jar.getJarEntry(descriptorPath);
                if (entry != null) {
                    descriptor = jar.getInputStream(entry);
                    break;
                }
            }
            if (descriptor == null) {
                throw new PluginException("Plugin descriptor not found in " + pluginPath);
            }
            try (InputStream descriptorStream = descriptor) {
                properties.load(descriptorStream);
            }
        } catch (IOException exception) {
            throw new PluginException("Failed to read plugin descriptor: " + pluginPath, exception);
        }

        String pluginId = required(properties, "plugin.id", pluginPath);
        String version = required(properties, "plugin.version", pluginPath);
        return new PluginDescriptor(
                pluginId,
                version,
                properties.getProperty("plugin.class", "").trim(),
                properties.getProperty("plugin.description", "").trim(),
                pluginPath);
    }

    // 读取并校验必填配置项，缺失时直接抛出异常。
    private static String required(Properties properties, String key, Path path) {
        String value = properties.getProperty(key, "").trim();
        if (value.isEmpty()) throw new PluginException("Missing " + key + " in " + path);
        return value;
    }
}
