package net.thechunk.playpen.p3.resolver;

import lombok.extern.log4j.Log4j2;
import net.thechunk.playpen.p3.IPackageResolver;
import net.thechunk.playpen.p3.P3Package;
import net.thechunk.playpen.p3.PackageManager;

import java.io.File;
import java.util.Collection;
import java.util.List;

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
