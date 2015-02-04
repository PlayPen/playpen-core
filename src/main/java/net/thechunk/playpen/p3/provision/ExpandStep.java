package net.thechunk.playpen.p3.provision;

import net.thechunk.playpen.p3.IProvisioningStep;
import net.thechunk.playpen.p3.PackageContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.zeroturnaround.zip.ZipException;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;

public class ExpandStep implements IProvisioningStep {
    private static final Logger logger = LogManager.getLogger(ExpandStep.class);

    @Override
    public String getStepId() {
        return "expand";
    }

    @Override
    public boolean runStep(PackageContext ctx, JSONObject config) {
        logger.info("Expanding package to " + ctx.getDestination().getPath());
        try {
            ZipUtil.unpack(new File(ctx.getP3().getLocalPath()), ctx.getDestination());
        }
        catch(ZipException e) {
            logger.error("Unable to expand package", e);
            return false;
        }

        return true;
    }
}
