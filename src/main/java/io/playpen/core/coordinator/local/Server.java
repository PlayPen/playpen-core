package io.playpen.core.coordinator.local;

import io.playpen.core.p3.P3Package;
import io.playpen.core.utils.process.XProcess;
import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class Server {
    private P3Package p3;

    private String uuid;

    private String name;

    private Map<String, String> properties = new ConcurrentHashMap<>();

    private String localPath;

    private XProcess process;

    private boolean freezeOnShutdown = false;
}
