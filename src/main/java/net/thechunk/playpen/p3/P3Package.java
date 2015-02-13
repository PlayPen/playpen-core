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

    @Data
    public static class ExecutionStep {
        String command;
        List<String> arguments = new ArrayList<>();
    }

    @Data
    public static class P3PackageInfo {
        private String id;
        private String version;

        @Override
        public boolean equals(Object other) {
            if(other instanceof P3PackageInfo) {
                P3PackageInfo o = (P3PackageInfo)other;
                return id.equals(o.getId()) && version.equals(o.getVersion());
            }

            return false;
        }

        @Override
        public int hashCode() {
            return id.hashCode() ^ version.hashCode();
        }
    }

    private String localPath;

    private boolean resolved;

    private String id;

    private String version;

    private P3Package parent;

    private Map<String, Integer> resources = new HashMap<>();

    private Set<String> attributes = new HashSet<>();

    private Map<String, String> strings = new HashMap<>();

    private List<ProvisioningStepConfig> provisioningSteps = new ArrayList<>();

    private List<ExecutionStep> executionSteps = new ArrayList<>();

    /**
     * Checks to make sure required fields are filled. Does not check resolution status!
     */
    public boolean validate() {
        if(id == null || id.isEmpty() || version == null || version.isEmpty())
            return false;

        if(parent != null && !parent.validate())
            return false;

        return true;
    }
}
