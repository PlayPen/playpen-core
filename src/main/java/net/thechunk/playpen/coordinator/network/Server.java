package net.thechunk.playpen.coordinator.network;

import lombok.Data;
import net.thechunk.playpen.p3.P3Package;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class Server {
    private P3Package p3;

    private String uuid;

    private String name;

    private Map<String, String> properties = new ConcurrentHashMap<>();

    private boolean active = false;

    public String getName() {
        if(name == null) {
            return uuid;
        }

        return name;
    }
}
