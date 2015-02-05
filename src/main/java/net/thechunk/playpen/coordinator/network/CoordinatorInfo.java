package net.thechunk.playpen.coordinator.network;

import lombok.Data;

@Data
public class CoordinatorInfo {
    private String name = null;

    private String uuid = null;

    private String key = null;

    private CoordinatorStatus status = CoordinatorStatus.DEAD;

    private String ip = null;

    private int port = 0;
}
