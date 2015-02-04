package net.thechunk.playpen.p3;

import org.json.JSONArray;
import org.json.JSONObject;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class PackageManager {

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

    public P3Package readPackage(File file) throws PackageException {
        if(!ZipUtil.containsEntry(file, "/package.json")) {
            throw new PackageException("No package schema found");
        }

        byte[] schemaBytes = ZipUtil.unpackEntry(file, "/package.json");
        String schemaString = new String(schemaBytes);

        JSONObject schema = new JSONObject(schemaString);
        JSONObject meta = schema.getJSONObject("package");
        if(meta == null)
            throw new PackageException("Schema is invalid (no package metadata)");

        P3Package p3 = new P3Package();
        p3.setLocalPath(file.getPath());
        p3.setResolved(true);
        p3.setId(meta.getString("id"));
        p3.setVersion(meta.getString("version"));

        JSONObject parent = meta.getJSONObject("parent");
        if(parent != null) {
            P3Package p3parent = new P3Package();
            p3parent.setId(parent.getString("id"));
            p3parent.setVersion(parent.getString("version"));
            p3.setParent(p3parent);
        }

        JSONObject resources = meta.getJSONObject("resources");
        if(resources != null) {
            for(String key : resources.keySet()) {
                p3.getResources().put(key, resources.getDouble(key));
            }
        }

        JSONArray attributes = meta.getJSONArray("requires");
        if(attributes != null) {
            for (int i = 0; i < attributes.length(); ++i) {
                p3.getAttributes().add(attributes.getString(i));
            }
        }

        JSONObject strings = schema.getJSONObject("strings");
        if(strings != null) {
            for (String key : strings.keySet()) {
                p3.getStrings().put(key, strings.getString(key));
            }
        }

        JSONArray provision = schema.getJSONArray("provision");
        if(provision != null) {
            for(int i = 0; i < provision.length(); ++i) {
                JSONObject obj = provision.getJSONObject(i);
                if(obj != null) {
                    IProvisioningStep step = getProvisioningStep(obj.getString("id"));
                    if(step == null)
                        throw new PackageException("Unknown provisioning step \"" + obj.getString("id") + "\"");

                    P3Package.ProvisioningStepConfig config = new P3Package.ProvisioningStepConfig();
                    config.setStep(step);
                    config.setConfig(obj);
                    p3.getProvisioningSteps().add(config);
                }
            }
        }

        JSONArray execute = schema.getJSONArray("execute");
        if(execute != null) {
            for(int i = 0; i < execute.length(); ++i) {
                p3.getExecutionSteps().add(execute.getString(i));
            }
        }

        if(!p3.validate()) {
            throw new PackageException("Package validation failed (check id, version, and parent metadata)!");
        }

        return p3;
    }
}
