package net.thechunk.playpen.p3;

import org.json.JSONArray;
import org.json.JSONObject;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class PackageManager {

    List<IPackageResolver> resolvers = new LinkedList<>();

    public void addPackageResolver(IPackageResolver resolver) {
        resolvers.add(resolver);
    }

    public Package resolve(String id, String version) {
        Package p3 = null;
        for(IPackageResolver resolver : resolvers) {
            p3 = resolver.resolvePackage(id, version);
            if(p3 != null)
                return p3;
        }

        return null;
    }



    public Package readPackage(File file) throws PackageException {
        if(!ZipUtil.containsEntry(file, "/package.json")) {
            throw new PackageException("No package schema found");
        }

        byte[] schemaBytes = ZipUtil.unpackEntry(file, "/package.json");
        String schemaString = new String(schemaBytes);

        JSONObject schema = new JSONObject(schemaString);
        JSONObject meta = schema.getJSONObject("package");
        if(meta == null)
            throw new PackageException("Schema is invalid (no package metadata)");

        Package p3 = new Package();
        p3.setLocalPath(file.getPath());
        p3.setResolved(true);
        p3.setId(meta.getString("id"));
        p3.setVersion(meta.getString("version"));

        JSONObject parent = meta.getJSONObject("parent");
        if(parent != null) {
            Package p3parent = new Package();
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

        //TODO
    }
}
