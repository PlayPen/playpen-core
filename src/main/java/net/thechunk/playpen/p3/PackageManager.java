package net.thechunk.playpen.p3;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.thechunk.playpen.Bootstrap;
import net.thechunk.playpen.utils.JSONUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
public class PackageManager {
    private List<IPackageResolver> resolvers = new LinkedList<>();

    private Map<String, IPackageStep> packageSteps = new ConcurrentHashMap<>();

    @Getter
    private Map<P3Package.P3PackageInfo, P3Package> packageCache = new ConcurrentHashMap<>();

    private Map<String, String> promoted = new ConcurrentHashMap<>();

    @Getter
    @Setter
    private IPackageResolver fallbackResolver = null;

    public PackageManager() {
        try {
            File packagesFile = Paths.get(Bootstrap.getHomeDir().getPath(), "packages.json").toFile();
            String packagesStr = new String(Files.readAllBytes(packagesFile.toPath()));
            JSONObject config = new JSONObject(packagesStr);
            JSONObject packages = config.getJSONObject("promoted");
            for(String key : packages.keySet()) {
                promoted.put(key, packages.getString(key));
            }
        }
        catch(Exception e) {
            log.error("Unable to read packages.json (promoted packages may not be loaded)", e);
        }
    }

    public String getPromotedVersion(String id) {
        return promoted.getOrDefault(id, null);
    }

    public synchronized boolean promote(P3Package p3) {
        if(!p3.isResolved()) {
            log.error("Cannot promote unresolved package " + p3.getId() + " at " + p3.getVersion());
            return false;
        }

        if(p3.getVersion().equals("promoted")) {
            log.error("Cannot promote package of version 'promoted'");
            return false;
        }

        log.info("Promoted " + p3.getId() + " at " + p3.getVersion());
        promoted.put(p3.getId(), p3.getVersion());

        try {
            File packagesFile = Paths.get(Bootstrap.getHomeDir().getPath(), "packages.json").toFile();
            String packagesStr = new String(Files.readAllBytes(packagesFile.toPath()));
            JSONObject config = new JSONObject(packagesStr);
            JSONObject packages = config.getJSONObject("promoted");
            packages.put(p3.getId(), p3.getVersion());
            config.put("promoted", packages);
            String jsonStr = config.toString(2);
            try (FileOutputStream out = new FileOutputStream(packagesFile)) {
                IOUtils.write(jsonStr, out);
            }

            log.info("Saved packages.json");
        }
        catch(Exception e) {
            log.error("Unable to save promoted packages", e);
        }

        return true;
    }

    public void addPackageResolver(IPackageResolver resolver) {
        resolvers.add(resolver);
    }

    public void addPackageStep(IPackageStep step) {
        packageSteps.put(step.getStepId(), step);
    }

    public IPackageStep getPackageStep(String id) {
        return packageSteps.getOrDefault(id, null);
    }

    public P3Package resolve(String id, String version) {
        return resolve(id, version, true);
    }

    public P3Package resolve(String id, String version, boolean allowFallback) {
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

        if(allowFallback && fallbackResolver != null) {
            p3 = fallbackResolver.resolvePackage(this, id, version);
            if(p3 != null) {
                log.info("Package resolved by fallback " + fallbackResolver.getClass().getName());
                if(!p3.validate()) {
                    log.warn("Package failed to validate!");
                    return null;
                }

                // fallbacks do not add to the package cache

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
                        IPackageStep step = getPackageStep(JSONUtils.safeGetString(obj, "id"));
                        if (step == null)
                            throw new PackageException("Unknown package step \"" + JSONUtils.safeGetString(obj, "id") + "\"");

                        P3Package.PackageStepConfig config = new P3Package.PackageStepConfig();
                        config.setStep(step);
                        config.setConfig(obj);
                        p3.getProvisionSteps().add(config);
                    }
                    else {
                        throw new PackageException("Provision step #" + i + " is not an object!");
                    }
                }
            }

            JSONArray execute = JSONUtils.safeGetArray(schema, "execute");
            if (execute != null) {
                for(int i = 0; i < execute.length(); ++i) {
                    JSONObject obj = JSONUtils.safeGetObject(execute, i);
                    if(obj != null) {
                        IPackageStep step = getPackageStep(JSONUtils.safeGetString(obj, "id"));
                        if(step == null)
                            throw new PackageException("Unknown package step \"" + JSONUtils.safeGetString(obj, "id") + "\"");

                        P3Package.PackageStepConfig config = new P3Package.PackageStepConfig();
                        config.setStep(step);
                        config.setConfig(obj);
                        p3.getExecutionSteps().add(config);
                    }
                }
            }

            JSONArray shutdown = JSONUtils.safeGetArray(schema, "shutdown");
            if(shutdown != null) {
                for(int i = 0; i < shutdown.length(); ++i) {
                    JSONObject obj = JSONUtils.safeGetObject(shutdown, i);
                    if(obj != null) {
                        IPackageStep step = getPackageStep(JSONUtils.safeGetString(obj, "id"));
                        if(step == null)
                            throw new PackageException("Unknown package step \"" + JSONUtils.safeGetString(obj, "id") + "\"");

                        P3Package.PackageStepConfig config = new P3Package.PackageStepConfig();
                        config.setStep(step);
                        config.setConfig(obj);
                        p3.getShutdownSteps().add(config);
                    }
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

    public boolean execute(ExecutionType type, P3Package p3, File destination, Map<String, String> properties, Object user) {
        return internalExecute(type, p3, destination, properties, user, new HashSet<P3Package.P3PackageInfo>(), null) != null;
    }

    private P3Package internalExecute(ExecutionType type, P3Package p3, File destination, Map<String, String> properties, Object user,
                                      Set<P3Package.P3PackageInfo> loaded, P3Package child) {
        log.info("Executing " + type + " " + p3.getId() + " at " + p3.getVersion());

        P3Package.P3PackageInfo p3info = new P3Package.P3PackageInfo();
        p3info.setId(p3.getId());
        p3info.setVersion(p3.getVersion());
        if(loaded.contains(p3info)) {
            log.error("Circular dependency found with " + p3.getId() + " at " + p3.getVersion());
            return null;
        }

        loaded.add(p3info);

        if(destination.exists() && !destination.isDirectory()) {
            log.error("Unable to execute package (destination is not a directory)");
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
            P3Package parent = internalExecute(type, p3.getParent(), destination, properties, user, loaded, p3);
            if (parent == null) {
                log.error("Unable to execute parent package " + p3.getParent().getId() + " at " + p3.getParent().getVersion());
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
        context.setProperties(properties);
        context.setUser(user);

        List<P3Package.PackageStepConfig> steps = null;
        switch(type) {
            case PROVISION:
                steps = p3.getProvisionSteps();
                break;

            case EXECUTE:
                steps = p3.getExecutionSteps();
                break;

            case SHUTDOWN:
                steps = p3.getShutdownSteps();
                break;
        }
        for(P3Package.PackageStepConfig config : steps) {
            log.info("package step - " + config.getStep().getStepId());
            if(!config.getStep().runStep(context, config.getConfig())) {
                log.error("Step failed!");
                return null;
            }
        }

        log.info("Execution " + type + " of " + p3.getId() + " at " + p3.getVersion() + " finished!");
        return p3;
    }
}
