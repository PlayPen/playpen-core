package io.playpen.core.p3;

import com.google.common.collect.Lists;
import io.playpen.core.Bootstrap;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
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
    private List<IPackageResolver> resolvers = new ArrayList<>();

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
        return promoted.get(id);
    }

    public boolean isPromotedVersion(String id, String version) {
        return Objects.equals(version, getPromotedVersion(id));
    }

    public synchronized boolean promote(P3Package p3) {
        if(!p3.isResolved()) {
            log.error("Cannot promote unresolved package " + p3.getId() + " at " + p3.getVersion());
            return false;
        }

        if(p3.getVersion().equalsIgnoreCase("promoted")) {
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
        return packageSteps.get(id);
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

    public Set<P3Package.P3PackageInfo> getPackageList() {
        Set<P3Package.P3PackageInfo> packages = new HashSet<>();
        for(IPackageResolver resolver : resolvers) {
            Collection<P3Package.P3PackageInfo> resolverPackages = resolver.getPackageList(this);
            if(resolverPackages != null) {
                packages.addAll(resolverPackages);
            }
        }

        return packages;
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
            JSONObject meta = schema.optJSONObject("package");
            if (meta == null)
                throw new PackageException("Schema is invalid (no package metadata)");

            P3Package p3 = new P3Package();
            p3.setResolved(true);
            p3.setId(meta.optString("id"));
            p3.setVersion(meta.optString("version"));

            JSONArray deps = meta.optJSONArray("depends");
            if (deps != null) {
                for(int i = 0; i < deps.length(); ++i) {
                    JSONObject dep = deps.getJSONObject(i);
                    P3Package depP3 = new P3Package();
                    depP3.setResolved(false);
                    depP3.setId(dep.optString("id"));
                    depP3.setVersion(dep.optString("version"));
                    p3.getDependencies().add(depP3);
                }
            }

            JSONObject resources = meta.optJSONObject("resources");
            if (resources != null) {
                for (String key : resources.keySet()) {
                    p3.getResources().put(key, resources.optInt(key));
                }
            }

            JSONArray attributes = meta.optJSONArray("requires");
            if (attributes != null) {
                for (int i = 0; i < attributes.length(); ++i) {
                    p3.getAttributes().add(attributes.optString(i));
                }
            }

            JSONObject strings = schema.optJSONObject("strings");
            if (strings != null) {
                for (String key : strings.keySet()) {
                    p3.getStrings().put(key, strings.optString(key));
                }
            }

            JSONArray provision = schema.optJSONArray("provision");
            if (provision != null) {
                for (int i = 0; i < provision.length(); ++i) {
                    JSONObject obj = provision.optJSONObject(i);
                    if (obj != null) {
                        String id = obj.optString("id");
                        IPackageStep step = getPackageStep(id);
                        if (step == null)
                            throw new PackageException("Unknown package step \"" + id + "\"");

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

            JSONArray execute = schema.optJSONArray("execute");
            if (execute != null) {
                for(int i = 0; i < execute.length(); ++i) {
                    JSONObject obj = execute.optJSONObject(i);
                    if(obj != null) {
                        String id = obj.optString("id");
                        IPackageStep step = getPackageStep(id);
                        if (step == null)
                            throw new PackageException("Unknown package step \"" + id + "\"");

                        P3Package.PackageStepConfig config = new P3Package.PackageStepConfig();
                        config.setStep(step);
                        config.setConfig(obj);
                        p3.getExecutionSteps().add(config);
                    }
                }
            }

            JSONArray shutdown = schema.optJSONArray("shutdown");
            if (shutdown != null) {
                for(int i = 0; i < shutdown.length(); ++i) {
                    JSONObject obj = shutdown.optJSONObject(i);
                    if(obj != null) {
                        String id = obj.optString("id");
                        IPackageStep step = getPackageStep(id);
                        if (step == null)
                            throw new PackageException("Unknown package step \"" + id + "\"");

                        P3Package.PackageStepConfig config = new P3Package.PackageStepConfig();
                        config.setStep(step);
                        config.setConfig(obj);
                        p3.getExecutionSteps().add(config);
                    }
                }
            }

            if (!p3.validate()) {
                throw new PackageException("Package validation failed (check id, version, and dependency metadata)!");
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

    public Map<String, String> buildProperties(P3Package initialP3) {
        Map<String, String> props = new HashMap<>();
        List<P3Package> chain = resolveDependencyChain(initialP3);
        if (chain == null)
            return props;

        for (P3Package p3 : chain) {
            props.putAll(p3.getStrings());
        }

        return props;
    }

    public boolean execute(ExecutionType type, P3Package initialP3, File destination, Map<String, String> properties, Object user) {
        log.info("Executing " + type + " " + initialP3.getId() + " (" + initialP3.getVersion() + ")");
        PackageContext ctx = new PackageContext();
        ctx.setUser(user);
        ctx.setDestination(destination);
        ctx.setPackageManager(this);
        ctx.setDependencyChain(resolveDependencyChain(initialP3));
        if(ctx.getDependencyChain() == null) {
            log.error("Unable to resolve dependency chain for " + initialP3.getId() + " (" + initialP3.getVersion() + ")");
            return false;
        }

        for(P3Package p3 : ctx.getDependencyChain()) {
            ctx.getResources().putAll(p3.getResources());
        }

        ctx.getProperties().putAll(properties);

        for(P3Package p3 : ctx.getDependencyChain()) {
            List<P3Package.PackageStepConfig> steps = null;
            switch(type)
            {
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
                if(!config.getStep().runStep(p3, ctx, config.getConfig())) {
                    log.error("Step failed!");
                    return false;
                }
            }
        }

        return true;
    }

    private List<P3Package> resolveDependencyChain(P3Package initialP3)
    {
        log.info("Building dependency chain for " + initialP3.getId() + " (" + initialP3.getVersion() + ")");
        List<P3Package> chain = new LinkedList<>();
        Queue<P3Package> toResolve = new LinkedList<>();
        Set<P3Package.P3PackageInfo> resolved = new HashSet<>();

        toResolve.add(initialP3);
        while(!toResolve.isEmpty()) {
            P3Package p3 = toResolve.remove();
            P3Package.P3PackageInfo info = new P3Package.P3PackageInfo();
            info.setId(p3.getId());
            info.setVersion(p3.getVersion());
            if(resolved.contains(info)) {
                log.error("Multiple dependency found with " + info.getId() + " (" + info.getVersion() + ")");
                return null; // TODO: Figure out if there's actually a circular dependency
            }

            if(!p3.isResolved())
                p3 = resolve(p3.getId(), p3.getVersion());

            if(p3 == null) {
                log.error("Unable to resolve " + info.getId() + " (" + info.getVersion() + ")");
                return null;
            }

            chain.add(p3);
            resolved.add(info);
            toResolve.addAll(p3.getDependencies());
        }

        log.info("Dependency chain resolution complete: " + chain.size() + " packages");

        return Lists.reverse(chain);
    }
}
