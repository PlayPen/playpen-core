package net.thechunk.playpen.p3;

import org.json.JSONObject;

import java.io.File;

public interface IProvisioningStep {
    String getStepId();

    boolean runStep(PackageManager pm, P3Package p3, JSONObject config, File dest);
}
