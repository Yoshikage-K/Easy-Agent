package com.eaharness.plugin.core;

import java.net.URL;
import java.net.URLClassLoader;

/** Parent-last loader for plugin implementation classes and bundled libraries. */
public class PluginClassLoader extends URLClassLoader {
    // 使用父优先的基础类加载器加载方式创建插件类加载器。
    public PluginClassLoader(URL pluginUrl, ClassLoader parent) {
        super(new URL[]{pluginUrl}, parent);
    }

    // 优先从插件包加载类，只有核心类和 SPI 才交给父加载器。
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                if (isParentFirst(name)) {
                    loaded = super.loadClass(name, false);
                } else {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException notInPlugin) {
                        loaded = super.loadClass(name, false);
                    }
                }
            }
            if (resolve) resolveClass(loaded);
            return loaded;
        }
    }

    // 判断某些基础包是否必须由父加载器优先加载。
    private boolean isParentFirst(String name) {
        return name.startsWith("java.")
                || name.startsWith("javax.")
                || name.startsWith("jdk.")
                || name.startsWith("sun.")
                || name.startsWith("com.eaharness.plugin.spi.")
                || name.startsWith("com.eaharness.plugin.annotation.")
                || name.startsWith("org.slf4j.");
    }
}
