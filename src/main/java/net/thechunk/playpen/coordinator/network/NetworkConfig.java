package net.thechunk.playpen.coordinator.network;

import lombok.Data;
import net.thechunk.playpen.Bootstrap;
import net.thechunk.playpen.ConfigException;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Data
public class NetworkConfig {
    public static NetworkConfig readConfig() throws ConfigException {
        File configFile = Paths.get(Bootstrap.getHomeDir().getPath(), "network.json").toFile();
        if(!configFile.exists())
            throw new ConfigException("network.json does not exist!");

        String configString = null;
        try {
            configString = new String(Files.readAllBytes(configFile.toPath()));
        }
        catch(IOException e) {
            throw new ConfigException("Unable to read network.json", e);
        }

        NetworkConfig config = new NetworkConfig();
        try {
            JSONObject jsonConfig = new JSONObject(configString);
            config.ip = jsonConfig.getString("ip");
            config.port = jsonConfig.getInt("port");
        }
        catch(JSONException e) {
            throw new ConfigException("Unable to parse network.json", e);
        }

        return config;
    }

    private String ip;

    private int port;
}
