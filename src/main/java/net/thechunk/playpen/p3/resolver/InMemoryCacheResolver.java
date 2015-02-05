package net.thechunk.playpen.p3.resolver;

import net.thechunk.playpen.p3.IPackageResolver;
import net.thechunk.playpen.p3.P3Package;
import net.thechunk.playpen.p3.PackageManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

public class InMemoryCacheResolver implements IPackageResolver {
    private static final Logger logger = LogManager.getLogger(InMemoryCacheResolver.class);

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
            logger.warn("In-memory package " + id + " at " + version + " is invalid, removing from cache");
            return null;
        }

        return p3;
    }
}
