package io.playpen.core.plugin;

import org.json.JSONObject;

public interface IPlugin {
    void setSchema(PluginSchema schema);

    PluginSchema getSchema();

    void setConfig(JSONObject config);

    JSONObject getConfig();

    boolean onStart();

    void onStop();
}
