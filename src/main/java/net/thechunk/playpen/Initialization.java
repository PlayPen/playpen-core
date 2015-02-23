package net.thechunk.playpen;

import net.thechunk.playpen.p3.PackageManager;
import net.thechunk.playpen.p3.resolver.InMemoryCacheResolver;
import net.thechunk.playpen.p3.resolver.LocalRepositoryResolver;
import net.thechunk.playpen.p3.resolver.PromotedResolver;
import net.thechunk.playpen.p3.step.*;

import java.nio.file.Paths;

public class Initialization {

    public static void packageManager(PackageManager pm) {
        // Promoted, should always come first
        pm.addPackageResolver(new PromotedResolver());

        // In-memory cache
        pm.addPackageResolver(new InMemoryCacheResolver());

        // Main package repository
        pm.addPackageResolver(new LocalRepositoryResolver(Paths.get(Bootstrap.getHomeDir().getPath(), "packages").toFile()));

        // Package cache
        pm.addPackageResolver(new LocalRepositoryResolver(Paths.get(Bootstrap.getHomeDir().getPath(), "cache", "packages").toFile()));

        pm.addPackageStep(new ExpandStep());
        pm.addPackageStep(new StringTemplateStep());
        pm.addPackageStep(new ExecuteStep());
        pm.addPackageStep(new PipeStep());
        pm.addPackageStep(new AssetPackageStep());
    }

    private Initialization() {}
}
