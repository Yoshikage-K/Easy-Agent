package com.eaharness.plugin.spi;

import com.eaharness.plugin.dto.PluginLoadRequest;
import com.eaharness.plugin.dto.PluginResponse;
import com.eaharness.plugin.dto.ExtensionExecuteRequest;
import com.eaharness.plugin.dto.ExtensionExecuteResponse;
import com.eaharness.plugin.dto.ExtensionResponse;
import java.util.List;

public interface PluginService {
    List<PluginResponse> list();

    List<PluginResponse> discover();

    PluginResponse load(PluginLoadRequest request);

    PluginResponse start(String pluginId);

    PluginResponse stop(String pluginId);

    PluginResponse unload(String pluginId);

    PluginResponse reload(String pluginId);

    List<ExtensionResponse> extensions();

    ExtensionExecuteResponse executeExtension(ExtensionExecuteRequest request);
}
