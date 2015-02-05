package net.thechunk.playpen.coordinator.network;

import lombok.Getter;
import net.thechunk.playpen.Bootstrap;
import net.thechunk.playpen.ConfigException;
import net.thechunk.playpen.utils.AuthUtils;
import net.thechunk.playpen.utils.JSONUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// TODO: client keys
public class KeyStore {
    private static Logger logger = LogManager.getLogger(KeyStore.class);

    @Getter
    private static KeyStore instance = null;

    @Getter
    private Map<String, CoordinatorInfo> coordinators = new ConcurrentHashMap<>();

    public KeyStore() throws ConfigException {
        instance = this;

        try {
            JSONObject config = JSONUtils.parseJsonFile(Paths.get(Bootstrap.getHomeDir().getPath(), "keystore.json").toFile());

            JSONArray coords = config.getJSONArray("coordinators");
            for(int i = 0; i < coords.length(); ++i) {
                JSONObject coord = coords.getJSONObject(i);
                CoordinatorInfo info = new CoordinatorInfo();
                info.setUuid(coord.getString("uuid"));
                info.setKey(coord.getString("key"));
                coordinators.put(info.getUuid(), info);
            }
        }
        catch(Exception e) {
            throw new ConfigException("Unable to read keystore.json config", e);
        }

        logger.info("Loaded " + coordinators.size() + " coordinators");
    }

    public CoordinatorInfo authCoordinator(String uuid, String payload, String auth) {
        if(uuid == null || payload == null || auth == null)
            return null;

        if(!coordinators.containsKey(uuid))
            return null;

        CoordinatorInfo info = coordinators.get(uuid);
        if(AuthUtils.validateHash(auth, info.getKey(), payload)) {
            return info;
        }

        return null;
    }

    public CoordinatorInfo createCoordinator() {
        CoordinatorInfo info = new CoordinatorInfo();
        info.setUuid(UUID.randomUUID().toString());
        info.setKey(UUID.randomUUID().toString());
        coordinators.put(info.getUuid(), info);

        logger.info("Created new coordinator keypair : " + info.getUuid());
        return info;
    }

    public synchronized void save() {
        JSONArray coords = new JSONArray();
        for(CoordinatorInfo info : coordinators.values()) {
            JSONObject obj = new JSONObject();
            obj.put("uuid", info.getUuid());
            obj.put("key", info.getKey());
            coords.put(obj);
        }

        JSONObject config = new JSONObject();
        config.put("coordinators", coords);

        String jsonStr = config.toString(2);

        logger.info("Saving keystore...");
        try (FileOutputStream out = new FileOutputStream(Paths.get(Bootstrap.getHomeDir().getPath(), "keystore.json").toFile())) {
            out.write(jsonStr.getBytes());
        }
        catch(IOException e) {
            logger.error("Unable to save keystore", e);
        }

        logger.info("Saved keystore");
    }
}
