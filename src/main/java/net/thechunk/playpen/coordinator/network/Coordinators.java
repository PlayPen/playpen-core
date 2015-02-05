package net.thechunk.playpen.coordinator.network;

import lombok.Getter;
import net.thechunk.playpen.ConfigException;

import java.util.HashMap;
import java.util.Map;

public class Coordinators {
    @Getter
    private static Coordinators instance = null;

    @Getter
    private Map<String, CoordinatorInfo> coordinators = new HashMap<>();

    private Map<String, CoordinatorInfo> active = new HashMap<>();

    public Coordinators() throws ConfigException {
        instance = this;


    }
}
