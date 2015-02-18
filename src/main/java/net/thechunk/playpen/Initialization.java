package net.thechunk.playpen;

import net.thechunk.playpen.p3.PackageManager;
import net.thechunk.playpen.p3.resolver.InMemoryCacheResolver;
import net.thechunk.playpen.p3.resolver.LocalRepositoryResolver;
import net.thechunk.playpen.p3.step.ExecuteStep;
import net.thechunk.playpen.p3.step.ExpandStep;
import net.thechunk.playpen.p3.step.StringTemplateStep;

import java.nio.file.Paths;

public class Initialization {

    public static void packageManager(PackageManager pm) {
        // In-memory cache
        pm.addPackageResolver(new InMemoryCacheResolver());

        // Main package repository
        pm.addPackageResolver(new LocalRepositoryResolver(Paths.get(Bootstrap.getHomeDir().getPath(), "packages").toFile()));

        // Package cache
        pm.addPackageResolver(new LocalRepositoryResolver(Paths.get(Bootstrap.getHomeDir().getPath(), "cache", "packages").toFile()));

        pm.addPackageStep(new ExpandStep());
        pm.addPackageStep(new StringTemplateStep());
        pm.addPackageStep(new ExecuteStep());
    }

    private Initialization() {}
}
