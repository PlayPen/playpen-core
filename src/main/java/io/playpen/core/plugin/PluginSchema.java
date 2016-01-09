package io.playpen.core.plugin;

import lombok.Data;

@Data
public class PluginSchema {
    private String id;
    private String version;
    private String main;
}
