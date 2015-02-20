package net.thechunk.playpen.plugin;

public class AbstractPlugin implements IPlugin {
    private PluginSchema schema = null;

    @Override
    public void setSchema(PluginSchema schema) {
        this.schema = schema;
    }

    @Override
    public PluginSchema getSchema() {
        return schema;
    }

    @Override
    public boolean onStart() {
        return true;
    }

    @Override
    public boolean onStop() {
        return true;
    }
}
