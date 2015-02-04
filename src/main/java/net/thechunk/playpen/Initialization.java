package net.thechunk.playpen;

import net.thechunk.playpen.p3.PackageManager;
import net.thechunk.playpen.p3.provision.ExpandStep;
import net.thechunk.playpen.p3.provision.StringTemplateStep;
import net.thechunk.playpen.p3.resolver.LocalRepositoryResolver;

public class Initialization {

    public static void packageManager(PackageManager pm) {
        pm.addPackageResolver(new LocalRepositoryResolver());

        pm.addProvisioningStep(new ExpandStep());
        pm.addProvisioningStep(new StringTemplateStep());
    }

    private Initialization() {}
}
