package com.eaharness.plugin.controller;

import com.eaharness.plugin.dto.PluginLoadRequest;
import com.eaharness.plugin.dto.PluginResponse;
import com.eaharness.plugin.dto.ExtensionExecuteRequest;
import com.eaharness.plugin.dto.ExtensionExecuteResponse;
import com.eaharness.plugin.dto.ExtensionResponse;
import com.eaharness.plugin.spi.PluginService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plugins")
@RequiredArgsConstructor
public class PluginController {
    private final PluginService pluginService;

    @PostMapping("/discover")
    public Map<String, List<PluginResponse>> discover() {
        return Map.of("plugins", pluginService.discover());
    }

    @PostMapping("/load")
    public PluginResponse load(@Valid @RequestBody PluginLoadRequest request) {
        return pluginService.load(request);
    }

    @PostMapping("/{pluginId}/start")
    public PluginResponse start(@PathVariable String pluginId) {
        return pluginService.start(pluginId);
    }

    @PostMapping("/{pluginId}/stop")
    public PluginResponse stop(@PathVariable String pluginId) {
        return pluginService.stop(pluginId);
    }

    @PostMapping("/{pluginId}/unload")
    public PluginResponse unload(@PathVariable String pluginId) {
        return pluginService.unload(pluginId);
    }

    @PostMapping("/{pluginId}/reload")
    public PluginResponse reload(@PathVariable String pluginId) {
        return pluginService.reload(pluginId);
    }

    @GetMapping
    public Map<String, List<PluginResponse>> list() {
        return Map.of("plugins", pluginService.list());
    }

    @GetMapping("/extensions")
    public Map<String, List<ExtensionResponse>> extensions() {
        return Map.of("extensions", pluginService.extensions());
    }

    @PostMapping("/extensions/execute")
    public ExtensionExecuteResponse executeExtension(
            @Valid @RequestBody ExtensionExecuteRequest request) {
        return pluginService.executeExtension(request);
    }
}
