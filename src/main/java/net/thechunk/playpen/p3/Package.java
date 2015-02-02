package net.thechunk.playpen.p3;

import lombok.Data;
import lombok.Getter;
import org.json.JSONObject;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.util.*;

@Data
public class Package {

    @Data
    public class ProvisioningStepConfig {
        String id;
        JSONObject config;
    }

    private String localPath;

    private boolean resolved;

    private String id;

    private String version;

    private Package parent;

    private Map<String, Double> resources = new HashMap<>();

    private Set<String> attributes = new HashSet<>();

    private Map<String, String> strings = new HashMap<>();

    private List<ProvisioningStepConfig> provisioningSteps = new ArrayList<>();

    private List<String> executionSteps = new ArrayList<>();
}
