package io.playpen.core.plugin;

import lombok.Data;

import java.util.List;

@Data
public class PluginSchema {
    private String id;
    private String version;
    private String main;
    private List<String> files;
}
