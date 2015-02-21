package net.thechunk.playpen.plugin;

import org.json.JSONObject;

public abstract class AbstractPlugin implements IPlugin {
    private PluginSchema schema = null;

    private JSONObject config = null;

    @Override
    public void setSchema(PluginSchema schema) {
        this.schema = schema;
    }

    @Override
    public PluginSchema getSchema() {
        return schema;
    }

    @Override
    public void setConfig(JSONObject config) {
        this.config = config;
    }

    @Override
    public JSONObject getConfig() {
        return config;
    }

    @Override
    public boolean onStart() {
        return true;
    }

    @Override
    public void onStop() {
    }
}
