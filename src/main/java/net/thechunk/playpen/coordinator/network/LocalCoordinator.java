package net.thechunk.playpen.coordinator.network;

import io.netty.channel.Channel;
import lombok.Data;
import net.thechunk.playpen.coordinator.Server;
import org.eclipse.jetty.util.ConcurrentHashSet;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class LocalCoordinator {
    private String uuid;

    private String key;

    private String name;

    private Map<String, Integer> resources = new ConcurrentHashMap<>();

    private Set<String> attributes = new ConcurrentHashSet<>();

    private Map<String, Server> servers = new ConcurrentHashMap<>();

    private Channel channel = null;

    private boolean enabled = false;
}
