package io.playpen.core.p3;

import lombok.Data;

import java.io.File;
import java.util.*;

@Data
public class PackageContext {
    private PackageManager packageManager;

    private File destination;

    private Map<String, String> properties = new HashMap<>();

    private Map<String, Integer> resources = new HashMap<>();

    private List<P3Package> dependencyChain = new ArrayList<>();

    private Object user;
}
