package net.thechunk.playpen.coordinator.network;

import io.netty.channel.Channel;
import lombok.Data;
import net.thechunk.playpen.coordinator.Server;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

@Data
public class LocalCoordinator {
    private String uuid;

    private String key;

    private String name;

    private Map<String, Integer> resources = new ConcurrentHashMap<>();

    private Set<String> attributes = new ConcurrentSkipListSet<>();

    private Map<String, Server> servers = new ConcurrentHashMap<>();

    private Channel channel = null;

    private boolean enabled = false;
}
