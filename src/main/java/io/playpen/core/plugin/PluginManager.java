package io.playpen.core.plugin;

import io.playpen.core.Bootstrap;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.json.JSONException;
import org.json.JSONObject;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
public class PluginManager {
    @Getter
    private Map<String, IPlugin> plugins = new ConcurrentHashMap<>();

    public boolean loadPlugins() {
        if(!plugins.isEmpty()) {
            log.error("Cannot call PluginManager.loadPlugins() when there are already plugins loaded!");
            return false;
        }

        File pluginsDir = Paths.get(Bootstrap.getHomeDir().getPath(), "plugins").toFile();
        if(!pluginsDir.exists() || !pluginsDir.isDirectory()) {
            log.error("Plugins directory either does not exist or is not a file");
            return false;
        }

        File[] jarFiles = pluginsDir.listFiles((dir, name) -> {
            return name.endsWith(".jar");
        });

        for(File jarFile : jarFiles) {
            if(!jarFile.isFile())
                continue;

            log.debug("Attempting plugin load of " + jarFile.getPath());
            if(!ZipUtil.containsEntry(jarFile, "plugin.json")) {
                log.warn("Jar " + jarFile.getPath() + " does not contain a plugin.json");
                continue;
            }

            byte[] schemaBytes = ZipUtil.unpackEntry(jarFile, "plugin.json");
            String schemaString = new String(schemaBytes);

            PluginSchema schema = new PluginSchema();
            try {
                JSONObject config = new JSONObject(schemaString);
                schema.setId(config.getString("id"));
                schema.setVersion(config.getString("version"));
                schema.setMain(config.getString("main"));
            }
            catch(JSONException e) {
                log.warn("Unable to read plugin.json from " + jarFile.getPath(), e);
                continue;
            }

            if(plugins.containsKey(schema.getId())) {
                log.error("Multiple instances of plugin " + schema.getId() + " exist");
                return false;
            }

            File pluginDir = Paths.get(pluginsDir.getPath(), schema.getId()).toFile();
            if(!pluginDir.exists()) {
                pluginDir.mkdir();
            }

            JSONObject config = null;

            if(ZipUtil.containsEntry(jarFile, "config.json")) {
                File configFile = Paths.get(pluginDir.getPath(), "config.json").toFile();
                byte[] configBytes = null;
                if(!configFile.exists()) {
                    configBytes = ZipUtil.unpackEntry(jarFile, "config.json");
                    try {
                        Files.write(configFile.toPath(), configBytes);
                    }
                    catch(IOException e) {
                        log.error("Unable to copy " + schema.getId() + " config file", e);
                        return false;
                    }
                }
                else {
                    try {
                        configBytes = Files.readAllBytes(configFile.toPath());
                    }
                    catch(IOException e) {
                        log.error("Unable to read " + schema.getId() + " config file", e);
                        return false;
                    }
                }
                try {
                    config = new JSONObject(new String(configBytes));
                }
                catch(JSONException e) {
                    log.error("Unable to parse " + schema.getId() + " config file", e);
                    return false;
                }
            }

            IPlugin instance = null;

            try {
                PluginClassLoader loader = new PluginClassLoader(
                        new URL[]{jarFile.toURI().toURL()});

                Class mainClass = Class.forName(schema.getMain(), true, loader);
                if(!IPlugin.class.isAssignableFrom(mainClass)) {
                    log.warn("Main class " + schema.getMain() + " for plugin " + schema.getId() + " does not implement IPlugin");
                    continue;
                }

                instance = (IPlugin)mainClass.newInstance();
            }
            catch(MalformedURLException e) {
                log.warn("WTF? Shouldn't happen", e);
                continue;
            }
            catch(ClassNotFoundException e) {
                log.warn("Main class " + schema.getMain() + " for plugin " + schema.getId() + " not found", e);
                continue;
            }
            catch(InstantiationException e) {
                log.warn("Unable to instantiate main class " + schema.getMain() + " for plugin " + schema.getId(), e);
                continue;
            }
            catch(IllegalAccessException e) {
                log.warn("Illegal access while instantiating main class " + schema.getMain() + " for plugin " + schema.getId(), e);
                continue;
            }

            if(instance == null) { // just in case
                log.warn("Instance of main class " + schema.getMain() + " for plugin " + schema.getId() + " is null");
                continue;
            }

            instance.setSchema(schema);
            instance.setConfig(config);

            plugins.put(schema.getId(), instance);
            log.info("Loaded plugin " + schema.getId());
        }

        for(IPlugin plugin : plugins.values()) {
            log.info("Starting plugin " + plugin.getSchema().getId());
            try {
                if (!plugin.onStart()) {
                    log.error("Plugin " + plugin.getSchema().getId() + " failed to start");
                    return false;
                }
            }
            catch(Exception e) {
                log.fatal("Exception thrown by plugin " + plugin.getSchema().getId(), e);
                return false;
            }
        }

        log.info(plugins.size() + " plugins loaded");

        return true;
    }

    public void stopPlugins() {
        for(IPlugin plugin : plugins.values()) {
            try {
                plugin.onStop();
            }
            catch(Exception e) {
                log.warn("Encountered exception while stopping plugin " + plugin.getSchema().getId(), e);
            }
        }

        plugins.clear();
    }

    public IPlugin getPlugin(String id) {
        return plugins.get(id);
    }
}
