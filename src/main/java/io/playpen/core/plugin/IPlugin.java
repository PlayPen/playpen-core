package io.playpen.core.plugin;

import org.json.JSONObject;

import java.io.File;

public interface IPlugin {
    void setSchema(PluginSchema schema);

    PluginSchema getSchema();

    void setPluginDir(File pluginDir);

    File getPluginDir();

    void setConfig(JSONObject config);

    JSONObject getConfig();

    boolean onStart();

    void onStop();
}
