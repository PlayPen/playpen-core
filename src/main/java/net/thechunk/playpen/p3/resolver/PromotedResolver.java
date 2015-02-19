package net.thechunk.playpen.p3.resolver;

import lombok.extern.log4j.Log4j2;
import net.thechunk.playpen.Bootstrap;
import net.thechunk.playpen.p3.IPackageResolver;
import net.thechunk.playpen.p3.P3Package;
import net.thechunk.playpen.p3.PackageManager;

import java.nio.file.Paths;

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
}
