package io.playpen.core.coordinator.network;

import io.netty.channel.Channel;
import io.playpen.core.coordinator.network.authenticator.IAuthenticator;
import io.playpen.core.networking.TransactionInfo;
import io.playpen.core.p3.P3Package;
import io.playpen.core.protocol.Commands;
import lombok.Data;
import lombok.extern.log4j.Log4j2;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

@Data
@Log4j2
public class LocalCoordinator {
    private String uuid;

    private String key;

    private String name;

    private String keyName = "";

    private Map<String, Integer> resources = new ConcurrentHashMap<>();

    private Set<String> attributes = new ConcurrentSkipListSet<>();

    private Map<String, Server> servers = new ConcurrentHashMap<>();

    private Channel channel = null;

    private boolean enabled = false;

    /**
     * Setting this to true will prevent automatic selection of this coordinator for provisioning operations.
     * This must be manually set and unset, but will also reset if the network coordinator is restarted.
     */
    private boolean restricted = false;

    private List<IAuthenticator> authenticators = new ArrayList<>();

    public String getName() {
        if(name == null) {
            return uuid;
        }

        return name;
    }

    public boolean isEnabled() {
        return enabled && channel != null && channel.isActive();
    }

    public Server getServer(String idOrName) {
        if(servers.containsKey(idOrName))
            return servers.get(idOrName);

        for(Server server : servers.values()) {
            if(server.getName() != null && server.getName().equals(idOrName))
                return server;
        }

        return null;
    }

    public Map<String, Integer> getAvailableResources() {
        Map<String, Integer> used = new HashMap<>();
        for(Map.Entry<String, Integer> entry : resources.entrySet()) {
            Integer value = entry.getValue();
            for(Server server : servers.values()) {
                value -= server.getP3().getResources().getOrDefault(entry.getKey(), 0);
            }

            used.put(entry.getKey(), value);
        }

        return used;
    }

    public boolean canProvisionPackage(P3Package p3) {
        for(String attr : p3.getAttributes()) {
            if(!attributes.contains(attr)) {
                log.warn("Coordinator " + getUuid() + " doesn't have attribute " + attr + " for " + p3.getId() + " at " + p3.getVersion());
                return false;
            }
        }

        Map<String, Integer> resources = getAvailableResources();
        for(Map.Entry<String, Integer> entry : p3.getResources().entrySet()) {
            if(!resources.containsKey(entry.getKey())) {
                log.warn("Coordinator " + getUuid() + " doesn't have resource " + entry.getKey() + " for " + p3.getId() + " at " + p3.getVersion());
                return false;
            }

            if(resources.get(entry.getKey()) - entry.getValue() < 0) {
                log.warn("Coordinator " + getUuid() + " doesn't have enough of resource " + entry.getKey() + " for " + p3.getId() + " at " + p3.getVersion());
                return false;
            }
        }

        return true;
    }

    public Server createServer(P3Package p3, String name, Map<String, String> properties) {
        if(!p3.isResolved()) {
            log.error("Cannot create server for unresolved package");
            return null;
        }

        if(!canProvisionPackage(p3)) {
            log.error("Coordinator " + getUuid() + " failed provision check for package " + p3.getId() + " at " + p3.getVersion());
            return null;
        }

        Server server = new Server();
        server.setUuid(UUID.randomUUID().toString());
        while(servers.containsKey(server.getUuid()))
            server.setUuid(UUID.randomUUID().toString());

        server.setP3(p3);
        server.setName(name);
        server.getProperties().putAll(properties);
        server.setCoordinator(this);
        servers.put(server.getUuid(), server);

        return server;
    }

    /**
     * Normalized resource usage is the sum of (resource / max resource) divided by the
     * number of resources. This should be between 0 and 1.
     */
    public double getNormalizedResourceUsage() {
        double result = 0.0;
        Map<String, Integer> available = getAvailableResources();
        for(Map.Entry<String, Integer> max : resources.entrySet()) {
            if(max.getValue() <= 0) // wat
                continue;

            Integer used = available.getOrDefault(max.getKey(), 0);
            result += used.doubleValue() / max.getValue().doubleValue();
        }

        return result;
    }

    public boolean authenticate(Commands.BaseCommand command, TransactionInfo info)
    {
        if (authenticators.isEmpty())
            return true;

        for (IAuthenticator auth : authenticators)
        {
            if (auth.hasAccess(command, info, this))
                return true;
        }

        return false;
    }
}
