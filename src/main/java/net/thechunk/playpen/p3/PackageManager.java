package net.thechunk.playpen.p3;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.thechunk.playpen.utils.JSONUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
public class PackageManager {
    private List<IPackageResolver> resolvers = new LinkedList<>();

    private Map<String, IProvisioningStep> provisioningSteps = new HashMap<>();

    @Getter
    private Map<P3Package.P3PackageInfo, P3Package> packageCache = new ConcurrentHashMap<>();

    public void addPackageResolver(IPackageResolver resolver) {
        resolvers.add(resolver);
    }

    public void addProvisioningStep(IProvisioningStep step) {
        provisioningSteps.put(step.getStepId(), step);
    }

    public IProvisioningStep getProvisioningStep(String id) {
        if(provisioningSteps.containsKey(id))
            return provisioningSteps.get(id);

        return null;
    }

    public P3Package resolve(String id, String version) {
        log.info("Attempting package resolution for " + id + " at " + version);
        P3Package p3 = null;
        for(IPackageResolver resolver : resolvers) {
            p3 = resolver.resolvePackage(this, id, version);
            if(p3 != null) {
                log.info("Package resolved by " + resolver.getClass().getName());
                if(!p3.validate()) {
                    log.warn("Package failed to validate. Continuing resolution!");
                    continue;
                }

                P3Package.P3PackageInfo info = new P3Package.P3PackageInfo();
                info.setId(p3.getId());
                info.setVersion(p3.getVersion());
                packageCache.put(info, p3);

                return p3;
            }
        }

        log.error("Package could not be resolved!");
        return null;
    }

    public P3Package readSchema(String schema) throws PackageException {
        try {
            return readSchema(new JSONObject(schema));
        }
        catch(JSONException e) {
            throw new PackageException("Invalid package schema", e);
        }
    }

    public P3Package readSchema(JSONObject schema) throws PackageException {
        try {
            JSONObject meta = JSONUtils.safeGetObject(schema, "package");
            if (meta == null)
                throw new PackageException("Schema is invalid (no package metadata)");

            P3Package p3 = new P3Package();
            p3.setResolved(true);
            p3.setId(JSONUtils.safeGetString(meta, "id"));
            p3.setVersion(JSONUtils.safeGetString(meta, "version"));

            JSONObject parent = JSONUtils.safeGetObject(meta, "parent");
            if (parent != null) {
                P3Package p3parent = new P3Package();
                p3parent.setId(JSONUtils.safeGetString(parent, "id"));
                p3parent.setVersion(JSONUtils.safeGetString(parent, "version"));
                p3.setParent(p3parent);
            }

            JSONObject resources = JSONUtils.safeGetObject(meta, "resources");
            if (resources != null) {
                for (String key : resources.keySet()) {
                    p3.getResources().put(key, JSONUtils.safeGetInt(resources, key));
                }
            }

            JSONArray attributes = JSONUtils.safeGetArray(meta, "requires");
            if (attributes != null) {
                for (int i = 0; i < attributes.length(); ++i) {
                    p3.getAttributes().add(JSONUtils.safeGetString(attributes, i));
                }
            }

            JSONObject strings = JSONUtils.safeGetObject(schema, "strings");
            if (strings != null) {
                for (String key : strings.keySet()) {
                    p3.getStrings().put(key, JSONUtils.safeGetString(strings, key));
                }
            }

            JSONArray provision = JSONUtils.safeGetArray(schema, "provision");
            if (provision != null) {
                for (int i = 0; i < provision.length(); ++i) {
                    JSONObject obj = JSONUtils.safeGetObject(provision, i);
                    if (obj != null) {
                        IProvisioningStep step = getProvisioningStep(JSONUtils.safeGetString(obj, "id"));
                        if (step == null)
                            throw new PackageException("Unknown provisioning step \"" + JSONUtils.safeGetString(obj, "id") + "\"");

                        P3Package.ProvisioningStepConfig config = new P3Package.ProvisioningStepConfig();
                        config.setStep(step);
                        config.setConfig(obj);
                        p3.getProvisioningSteps().add(config);
                    }
                    else {
                        throw new PackageException("Provision step #" + i + " is not an object!");
                    }
                }
            }

            JSONArray execute = JSONUtils.safeGetArray(schema, "execute");
            if (execute != null) {
                for (int i = 0; i < execute.length(); ++i) {
                    P3Package.ExecutionStep step = new P3Package.ExecutionStep();
                    JSONObject jsonStep = JSONUtils.safeGetObject(execute, i);
                    if(jsonStep == null) {
                        throw new PackageException("Invalid json object for execution step #" + i);
                    }

                    step.setCommand(JSONUtils.safeGetString(jsonStep, "command"));
                    if(step.getCommand() == null) {
                        throw new PackageException("Invalid command string for execution step #" + i);
                    }

                    JSONArray args = JSONUtils.safeGetArray(jsonStep, "arguments");
                    if(args != null) {
                        for (int j = 0; j <args.length(); ++j) {
                            step.getArguments().add(JSONUtils.safeGetString(args, j));
                        }
                    }

                    p3.getExecutionSteps().add(step);
                }
            }

            if (!p3.validate()) {
                throw new PackageException("Package validation failed (check id, version, and parent metadata)!");
            }

            return p3;
        }
        catch(JSONException e) {
            throw new PackageException("Invalid package schema", e);
        }
    }

    public P3Package readPackage(File file) throws PackageException {
        try {
            if (!ZipUtil.containsEntry(file, "package.json")) {
                throw new PackageException("No package schema found");
            }

            byte[] schemaBytes = ZipUtil.unpackEntry(file, "package.json");
            String schemaString = new String(schemaBytes);

            JSONObject schema = new JSONObject(schemaString);
            P3Package p3 = readSchema(schema);
            p3.setLocalPath(file.getPath());

            return p3;
        }
        catch(JSONException e) {
            throw new PackageException("Invalid package schema", e);
        }
    }

    public boolean provision(P3Package p3, File destination, Map<String, String> properties) {
        return internalProvision(p3, destination, new HashSet<P3Package.P3PackageInfo>(), null) != null;
    }

    private P3Package internalProvision(P3Package p3, File destination, Set<P3Package.P3PackageInfo> loaded, P3Package child) {
        log.info("Provisioning " + p3.getId() + " at " + p3.getVersion());

        P3Package.P3PackageInfo p3info = new P3Package.P3PackageInfo();
        p3info.setId(p3.getId());
        p3info.setVersion(p3.getVersion());
        if(loaded.contains(p3info)) {
            log.error("Circular dependency found with " + p3.getId() + " at " + p3.getVersion());
            return null;
        }

        loaded.add(p3info);

        if(destination.exists() && !destination.isDirectory()) {
            log.error("Unable to provision package (destination is not a directory)");
            return null;
        }

        String oldId = p3.getId();
        String oldVersion = p3.getVersion();
        if(!p3.isResolved()) {
            p3 = resolve(p3.getId(), p3.getVersion());
        }

        if(p3 == null) {
            log.error("Unable to resolve package " + oldId + " at " + oldVersion);
            return null;
        }

        if(child != null) {
            p3.getResources().putAll(child.getResources());
            p3.getAttributes().addAll(child.getAttributes());
            p3.getStrings().putAll(child.getStrings());
        }

        if(p3 == null || !p3.isResolved()) {
            log.error("Unable to resolve package " + oldId + " at " + oldVersion);
            return null;
        }

        if(p3.getParent() != null) {
            P3Package parent = internalProvision(p3.getParent(), destination, loaded, p3);
            if (parent == null) {
                log.error("Unable to provision parent package " + p3.getParent().getId() + " at " + p3.getParent().getVersion());
                return null;
            }

            p3.setParent(parent);

            p3.getResources().putAll(parent.getResources());
            p3.getAttributes().addAll(parent.getAttributes());
            p3.getStrings().putAll(parent.getStrings());
        }

        PackageContext context = new PackageContext();
        context.setPackageManager(this);
        context.setP3(p3);
        context.setDestination(destination);

        for(P3Package.ProvisioningStepConfig config : p3.getProvisioningSteps()) {
            log.info("provision step - " + config.getStep().getStepId());
            if(!config.getStep().runStep(context, config.getConfig())) {
                log.error("Step failed!");
                return null;
            }
        }

        log.info("Provision of " + p3.getId() + " at " + p3.getVersion() + " finished!");
        return p3;
    }
}
