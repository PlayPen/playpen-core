package net.thechunk.playpen.coordinator.network;

import io.netty.channel.Channel;
import lombok.Data;
import net.thechunk.playpen.coordinator.Server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
public class LocalCoordinator {
    private String uuid;

    private String key;

    private String name;

    private Map<String, Integer> resources = new HashMap<>();

    private Set<String> attributes = new HashSet<>();

    private Map<String, Server> servers = new HashMap<>();

    private Channel channel = null;
}
