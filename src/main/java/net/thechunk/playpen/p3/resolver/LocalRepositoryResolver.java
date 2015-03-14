package net.thechunk.playpen.p3.resolver;

import lombok.extern.log4j.Log4j2;
import net.thechunk.playpen.p3.IPackageResolver;
import net.thechunk.playpen.p3.P3Package;
import net.thechunk.playpen.p3.PackageException;
import net.thechunk.playpen.p3.PackageManager;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

@Log4j2
public class LocalRepositoryResolver implements IPackageResolver {
    private File localRepoDir = null;

    public LocalRepositoryResolver(File dir) {
        localRepoDir = dir;
    }

    @Override
    public P3Package resolvePackage(PackageManager pm, String id, String version) {
        if(!localRepoDir.exists() || !localRepoDir.isDirectory()) {
            log.error("Package repository at " + localRepoDir.getPath() + " doesn't exist!");
            return null;
        }

        File[] packageFiles = localRepoDir.listFiles((dir, name) -> {
            return name.endsWith(".p3");
        });

        for(File p3File : packageFiles) {
            if(!p3File.isFile())
                continue;

            P3Package p3 = null;
            try {
                p3 = pm.readPackage(p3File);
            }
            catch(PackageException e) {
                log.warn("Unable to read file " + p3File.getPath());
                continue;
            }

            if(id.equals(p3.getId()) && version.equals(p3.getVersion())) {
                log.info("Found matching package at " + p3File.getPath());
                return p3;
            }
        }

        return null;
    }

    @Override
    public Collection<P3Package.P3PackageInfo> getPackageList(PackageManager pm) {
        if(!localRepoDir.exists() || !localRepoDir.isDirectory()) {
            log.error("Package repository at " + localRepoDir.getPath() + " doesn't exist!");
            return null;
        }

        File[] packageFiles = localRepoDir.listFiles((dir, name) -> {
            return name.endsWith(".p3");
        });

        List<P3Package.P3PackageInfo> packages = new LinkedList<>();
        for(File p3File : packageFiles) {
            if(!p3File.isFile())
                continue;

            P3Package p3 = null;
            try {
                p3 = pm.readPackage(p3File);
            }
            catch(PackageException e) {
                log.warn("Unable to read file " + p3File.getPath());
                continue;
            }

            P3Package.P3PackageInfo info = new P3Package.P3PackageInfo();
            info.setId(p3.getId());
            info.setVersion(p3.getVersion());
            packages.add(info);
        }

        return packages;
    }
}
