package io.playpen.core.plugin;

import org.json.JSONObject;

import java.io.File;

public abstract class AbstractPlugin implements IPlugin {
    private PluginSchema schema = null;

    private JSONObject config = null;

    private File pluginDir = null;

    @Override
    public void setSchema(PluginSchema schema) {
        this.schema = schema;
    }

    @Override
    public PluginSchema getSchema() {
        return schema;
    }

    @Override
    public void setPluginDir(File pluginDir) {
        this.pluginDir = pluginDir;
    }

    @Override
    public File getPluginDir() {
        return pluginDir;
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
