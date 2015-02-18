package net.thechunk.playpen.p3.resolver;

import lombok.extern.log4j.Log4j2;
import net.thechunk.playpen.Bootstrap;
import net.thechunk.playpen.p3.P3Package;
import net.thechunk.playpen.p3.PackageManager;

import java.io.File;
import java.nio.file.Paths;

@Log4j2
public class PromotedResolver extends LocalRepositoryResolver {
    public PromotedResolver() {
        super(Paths.get(Bootstrap.getHomeDir().getPath(), "packages").toFile());
    }

    @Override
    public P3Package resolvePackage(PackageManager pm, String id, String version) {
        if(!version.equals("promoted"))
            return null;

        log.info("Attempting promoted resolution of " + id);
        String realVersion = pm.getPromotedVersion(id);
        if(realVersion == null) {
            log.error("No promoted package for " + id + ", you're gunna have a bad time");
            return null;
        }

        P3Package p3 = super.resolvePackage(pm, id, realVersion);
        if(p3 == null) {
            log.error("Promoted package " + id + " at " + realVersion + " isn't in the local package repository!");
            return null;
        }

        return p3;
    }
}
