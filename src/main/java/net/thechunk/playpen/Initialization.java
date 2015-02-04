package net.thechunk.playpen;

import net.thechunk.playpen.p3.PackageManager;
import net.thechunk.playpen.p3.provision.ExpandStep;

public class Initialization {

    public static void packageManager(PackageManager pm) {
        pm.addProvisioningStep(new ExpandStep());
    }

    private Initialization() {}
}
