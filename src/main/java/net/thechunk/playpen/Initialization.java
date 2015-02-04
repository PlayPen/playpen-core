package net.thechunk.playpen;

import net.thechunk.playpen.p3.PackageManager;
import net.thechunk.playpen.p3.provision.ExpandStep;
import net.thechunk.playpen.p3.provision.StringTemplateStep;

public class Initialization {

    public static void packageManager(PackageManager pm) {
        pm.addProvisioningStep(new ExpandStep());
        pm.addProvisioningStep(new StringTemplateStep());
    }

    private Initialization() {}
}
