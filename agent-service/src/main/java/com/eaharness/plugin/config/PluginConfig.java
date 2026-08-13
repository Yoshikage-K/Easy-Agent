package com.eaharness.plugin.config;

import com.eaharness.plugin.core.MiniPluginManager;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PluginProperties.class)
public class PluginConfig {
    @Bean
    public MiniPluginManager pluginManager(PluginProperties properties) {
        return new MiniPluginManager(properties.getPluginsDirectory());
    }
}
