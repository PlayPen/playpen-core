package io.playpen.core.p3.step;

import io.playpen.core.p3.IPackageStep;
import io.playpen.core.p3.P3Package;
import io.playpen.core.p3.PackageContext;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.zeroturnaround.zip.ZipException;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;

@Log4j2
public class ExpandStep implements IPackageStep {
    @Override
    public String getStepId() {
        return "expand";
    }

    @Override
    public boolean runStep(P3Package p3, PackageContext ctx, JSONObject config) {
        log.info("Expanding package to " + ctx.getDestination().getPath());
        try {
            ZipUtil.unpack(new File(p3.getLocalPath()), ctx.getDestination());
        }
        catch(ZipException e) {
            log.error("Unable to expand package", e);
            return false;
        }

        return true;
    }
}
