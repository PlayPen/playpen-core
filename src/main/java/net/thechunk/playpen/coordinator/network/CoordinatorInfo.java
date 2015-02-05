package net.thechunk.playpen.coordinator.network;

import lombok.Data;

@Data
public class CoordinatorInfo {
    private String name = null;

    private String uuid = null;

    private String key = null;

    private boolean active = false;
}
