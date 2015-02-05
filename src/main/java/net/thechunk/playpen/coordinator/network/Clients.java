package net.thechunk.playpen.coordinator.network;

import lombok.Getter;
import net.thechunk.playpen.Bootstrap;
import net.thechunk.playpen.ConfigException;
import net.thechunk.playpen.utils.JSONUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class Clients {
    @Getter
    private static Clients instance = null;

    @Getter
    private Map<String, CoordinatorInfo> coordinators = new HashMap<>();

    @Getter
    private Map<String, CoordinatorInfo> dyingCoordinators = new HashMap<>();

    @Getter
    private Map<String, CoordinatorInfo> aliveCoordinators = new HashMap<>();

    public Clients() throws ConfigException {
        instance = this;

        try {
            JSONObject config = JSONUtils.parseJsonFile(Paths.get(Bootstrap.getHomeDir().getPath(), "clients.json").toFile());

            JSONArray coords = config.getJSONArray("coordinators");
            for(int i = 0; i < coords.length(); ++i) {
                JSONObject coord = coords.getJSONObject(i);
                CoordinatorInfo info = new CoordinatorInfo();
                info.setName(JSONUtils.safeGetString(coord, "name"));
                info.setUuid(coord.getString("uuid"));
                info.setKey(coord.getString("key"));
                coordinators.put(info.getUuid(), info);
            }
        }
        catch(Exception e) {
            throw new ConfigException("Unable to read clients.json config", e);
        }
    }
}
