package net.thechunk.playpen.p3;

import net.thechunk.playpen.utils.JSONUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.util.*;

public class PackageManager {

    private static final Logger logger = LogManager.getLogger(PackageManager.class);

    private List<IPackageResolver> resolvers = new LinkedList<>();

    private Map<String, IProvisioningStep> provisioningSteps = new HashMap<>();

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
        P3Package p3 = null;
        for(IPackageResolver resolver : resolvers) {
            p3 = resolver.resolvePackage(id, version);
            if(p3 != null)
                return p3;
        }

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
                    p3.getResources().put(key, resources.getDouble(key));
                }
            }

            JSONArray attributes = JSONUtils.safeGetArray(meta, "requires");
            if (attributes != null) {
                for (int i = 0; i < attributes.length(); ++i) {
                    p3.getAttributes().add(attributes.getString(i));
                }
            }

            JSONObject strings = JSONUtils.safeGetObject(schema, "strings");
            if (strings != null) {
                for (String key : strings.keySet()) {
                    p3.getStrings().put(key, strings.getString(key));
                }
            }

            JSONArray provision = JSONUtils.safeGetArray(schema, "provision");
            if (provision != null) {
                for (int i = 0; i < provision.length(); ++i) {
                    JSONObject obj = provision.getJSONObject(i);
                    if (obj != null) {
                        IProvisioningStep step = getProvisioningStep(JSONUtils.safeGetString(obj, "id"));
                        if (step == null)
                            throw new PackageException("Unknown provisioning step \"" + JSONUtils.safeGetString(obj, "id") + "\"");

                        P3Package.ProvisioningStepConfig config = new P3Package.ProvisioningStepConfig();
                        config.setStep(step);
                        config.setConfig(obj);
                        p3.getProvisioningSteps().add(config);
                    }
                }
            }

            JSONArray execute = JSONUtils.safeGetArray(schema, "execute");
            if (execute != null) {
                for (int i = 0; i < execute.length(); ++i) {
                    p3.getExecutionSteps().add(execute.getString(i));
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

    public boolean provision(P3Package p3, File destination) {
        return internalProvision(p3, destination, new HashSet<P3Package.P3PackageInfo>());
    }

    private boolean internalProvision(P3Package p3, File destination, Set<P3Package.P3PackageInfo> loaded) {
        logger.info("Provisioning " + p3.getId() + " at " + p3.getVersion());

        P3Package.P3PackageInfo p3info = new P3Package.P3PackageInfo();
        p3info.setId(p3.getId());
        p3info.setVersion(p3.getVersion());
        if(loaded.contains(p3info)) {
            logger.error("Circular dependency found with " + p3.getId() + " at " + p3.getVersion());
            return false;
        }

        loaded.add(p3info);

        if(destination.exists() && !destination.isDirectory()) {
            logger.error("Unable to provision package (destination is not a directory)");
            return false;
        }

        String oldId = p3.getId();
        String oldVersion = p3.getVersion();
        if(!p3.isResolved()) {
            p3 = resolve(p3.getId(), p3.getVersion());
        }

        if(p3 == null || !p3.isResolved()) {
            logger.error("Unable to fully resolve package " + oldId + " at " + oldVersion);
            return false;
        }

        if(p3.getParent() != null) {
            if (!internalProvision(p3.getParent(), destination, loaded)) {
                logger.error("Unable to provision parent package " + p3.getParent().getId() + " at " + p3.getParent().getVersion());
                return false;
            }
        }

        PackageContext context = new PackageContext();
        context.setPackageManager(this);
        context.setP3(p3);
        context.setDestination(destination);

        for(P3Package.ProvisioningStepConfig config : p3.getProvisioningSteps()) {
            logger.info("provision step - " + config.getStep().getStepId());
            if(!config.getStep().runStep(context, config.getConfig())) {
                logger.error("Step failed!");
                return false;
            }
        }

        logger.info("Provision of " + p3.getId() + " at " + p3.getVersion() + " finished!");
        return true;
    }
}
