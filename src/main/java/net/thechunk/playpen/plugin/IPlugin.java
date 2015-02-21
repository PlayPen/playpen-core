package net.thechunk.playpen.plugin;

public interface IPlugin {
    void setSchema(PluginSchema schema);

    PluginSchema getSchema();

    boolean onStart();

    void onStop();
}
