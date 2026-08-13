package com.eaharness.plugin.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "eaharness.plugin")
public class PluginProperties {
    private Path pluginsDirectory = Paths.get(".plugins").toAbsolutePath().normalize();
    private boolean autoStart;

    public Path getPluginsDirectory() {
        return pluginsDirectory;
    }

    public void setPluginsDirectory(Path pluginsDirectory) {
        this.pluginsDirectory = pluginsDirectory.toAbsolutePath().normalize();
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }
}
