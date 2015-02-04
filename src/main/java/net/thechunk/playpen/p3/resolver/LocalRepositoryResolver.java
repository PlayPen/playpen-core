package net.thechunk.playpen.p3.resolver;

import net.thechunk.playpen.Bootstrap;
import net.thechunk.playpen.p3.IPackageResolver;
import net.thechunk.playpen.p3.P3Package;
import net.thechunk.playpen.p3.PackageException;
import net.thechunk.playpen.p3.PackageManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Paths;

public class LocalRepositoryResolver implements IPackageResolver {
    private static Logger logger = LogManager.getLogger(LocalRepositoryResolver.class);

    @Override
    public P3Package resolvePackage(PackageManager pm, String id, String version) {
        File localRepoDir = Paths.get(Bootstrap.getHomeDir().getPath(), "packages").toFile();
        if(!localRepoDir.exists() || !localRepoDir.isDirectory()) {
            logger.error("Package repository directory doesn't exist!");
            return null;
        }

        File[] packageFiles = localRepoDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".p3");
            }
        });

        for(File p3File : packageFiles) {
            if(!p3File.isFile())
                continue;

            P3Package p3 = null;
            try {
                p3 = pm.readPackage(p3File);
            }
            catch(PackageException e) {
                logger.warn("Unable to read file " + p3File.getPath());
                continue;
            }

            if(id.equals(p3.getId()) && version.equals(p3.getVersion())) {
                logger.info("Found matching package at " + p3File.getPath());
                return p3;
            }
        }

        return null;
    }
}
