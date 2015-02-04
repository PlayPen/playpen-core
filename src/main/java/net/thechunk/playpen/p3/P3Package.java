package net.thechunk.playpen.p3;

import lombok.Data;
import org.json.JSONObject;

import java.util.*;

@Data
public class P3Package {

    @Data
    public static class ProvisioningStepConfig {
        IProvisioningStep step;
        JSONObject config;
    }

    private String localPath;

    private boolean resolved;

    private String id;

    private String version;

    private P3Package parent;

    private Map<String, Double> resources = new HashMap<>();

    private Set<String> attributes = new HashSet<>();

    private Map<String, String> strings = new HashMap<>();

    private List<ProvisioningStepConfig> provisioningSteps = new ArrayList<>();

    private List<String> executionSteps = new ArrayList<>();

    boolean validate() {
        if(id == null || id.isEmpty() || version == null || version.isEmpty())
            return false;

        if(parent != null && !parent.validate())
            return false;

        return true;
    }
}
