package io.playpen.core;

import io.playpen.core.coordinator.network.Network;
import io.playpen.core.coordinator.network.authenticator.DeprovisionAuthenticator;
import io.playpen.core.p3.PackageManager;
import io.playpen.core.p3.resolver.InMemoryCacheResolver;
import io.playpen.core.p3.resolver.LocalRepositoryResolver;
import io.playpen.core.p3.resolver.PromotedResolver;
import io.playpen.core.p3.step.*;

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
        pm.addPackageStep(new ExpandAssetsStep());
        pm.addPackageStep(new CopyStep());
    }

    public static void networkCoordinator(Network net) {
        net.addAuthenticator("deprovision", new DeprovisionAuthenticator());
    }

    private Initialization() {}
}
