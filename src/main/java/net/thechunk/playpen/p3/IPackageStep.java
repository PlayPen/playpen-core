package net.thechunk.playpen.p3;

import org.json.JSONObject;

public interface IPackageStep {
    String getStepId();

    boolean runStep(P3Package p3, PackageContext ctx, JSONObject config);
}
