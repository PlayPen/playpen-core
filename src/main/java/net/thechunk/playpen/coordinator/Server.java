package net.thechunk.playpen.coordinator;

import lombok.Data;
import net.thechunk.playpen.p3.P3Package;

import java.util.HashMap;
import java.util.Map;

@Data
public class Server {
    private P3Package p3;

    private String uuid;

    private String name;

    private Map<String, String> properties = new HashMap<>();

    private String path;
}
