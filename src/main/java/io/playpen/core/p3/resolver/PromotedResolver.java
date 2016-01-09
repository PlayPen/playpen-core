package io.playpen.core.p3.resolver;

import io.playpen.core.p3.IPackageResolver;
import io.playpen.core.p3.P3Package;
import io.playpen.core.p3.PackageManager;
import lombok.extern.log4j.Log4j2;

import java.util.Collection;

@Log4j2
public class PromotedResolver implements IPackageResolver {
    @Override
    public P3Package resolvePackage(PackageManager pm, String id, String version) {
        if(!version.equals("promoted"))
            return null;

        log.info("Attempting promoted resolution of " + id);
        String realVersion = pm.getPromotedVersion(id);
        if(realVersion == null) {
            log.error("No promoted package for " + id + ", we're gunna have a bad time");
            return null;
        }

        if(realVersion.equals("promoted")) {
            log.error("'promoted' cannot be the promoted version of a package!");
            return null;
        }

        P3Package p3 = pm.resolve(id, realVersion);
        if(p3 == null) {
            log.error("Promoted package " + id + " at " + realVersion + " could not be resolved ");
            return null;
        }

        return p3;
    }

    @Override
    public Collection<P3Package.P3PackageInfo> getPackageList(PackageManager pm) {
        return null;
    }
}
