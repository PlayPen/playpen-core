package net.thechunk.playpen.p3;

import lombok.Data;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Data
public class PackageContext {
    private PackageManager packageManager;

    private File destination;

    private Map<String, String> properties = new HashMap<>();

    private Map<String, Integer> resources = new HashMap<>();

    private List<P3Package> dependencyChain = new LinkedList<>();

    private Object user;
}
