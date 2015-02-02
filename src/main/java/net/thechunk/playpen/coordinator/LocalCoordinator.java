package net.thechunk.playpen.coordinator;

import lombok.Data;

import java.util.Map;
import java.util.Set;

@Data
public class LocalCoordinator {
    String name;

    String uuid;

    String secretKey;

    Map<String, Float> resources;

    Set<String> attributes;
}
