package net.thechunk.playpen.p3;

import org.json.JSONObject;

import java.io.File;

public interface IProvisioningStep {
    boolean runStep(PackageManager pm, Package p3, JSONObject config, File dest);
}
