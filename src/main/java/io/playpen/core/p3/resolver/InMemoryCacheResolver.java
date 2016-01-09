package io.playpen.core.p3.resolver;

import io.playpen.core.p3.IPackageResolver;
import io.playpen.core.p3.P3Package;
import io.playpen.core.p3.PackageManager;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.util.Collection;

@Log4j2
public class InMemoryCacheResolver implements IPackageResolver {
    @Override
    public P3Package resolvePackage(PackageManager pm, String id, String version) {
        P3Package.P3PackageInfo info = new P3Package.P3PackageInfo();
        info.setId(id);
        info.setVersion(version);

        P3Package p3 = pm.getPackageCache().getOrDefault(info, null);
        if(p3 == null)
            return null;

        File p3File = new File(p3.getLocalPath());
        if(!p3File.exists() || !p3File.isFile()) {
            pm.getPackageCache().remove(info);
            log.warn("In-memory package " + id + " at " + version + " is invalid, removing from cache");
            return null;
        }

        return p3;
    }

    @Override
    public Collection<P3Package.P3PackageInfo> getPackageList(PackageManager pm) {
        return null;
    }
}
