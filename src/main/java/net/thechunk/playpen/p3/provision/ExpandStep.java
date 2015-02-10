package net.thechunk.playpen.p3.provision;

import lombok.extern.log4j.Log4j2;
import net.thechunk.playpen.p3.IProvisioningStep;
import net.thechunk.playpen.p3.PackageContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.zeroturnaround.zip.ZipException;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;

@Log4j2
public class ExpandStep implements IProvisioningStep {
    @Override
    public String getStepId() {
        return "expand";
    }

    @Override
    public boolean runStep(PackageContext ctx, JSONObject config) {
        log.info("Expanding package to " + ctx.getDestination().getPath());
        try {
            ZipUtil.unpack(new File(ctx.getP3().getLocalPath()), ctx.getDestination());
        }
        catch(ZipException e) {
            log.error("Unable to expand package", e);
            return false;
        }

        return true;
    }
}
