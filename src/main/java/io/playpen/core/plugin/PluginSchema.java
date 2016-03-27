package io.playpen.core.plugin;

import lombok.Value;

@Value
public class PluginSchema {
    private final String id;
    private final String version;
    private final String main;
}
