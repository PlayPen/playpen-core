package io.playpen.core.coordinator.network;

import io.playpen.core.p3.P3Package;
import lombok.Data;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class Server {
    private P3Package p3;

    private String uuid;

    private String name;

    private Map<String, String> properties = new ConcurrentHashMap<>();

    private boolean active = false;

    private LocalCoordinator coordinator = null;

    public String getName() {
        if(name == null) {
            return uuid;
        }

        return name;
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, coordinator == null ? "0" : coordinator.getUuid());
    }
}
