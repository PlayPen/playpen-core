package io.playpen.core.p3.step;

import io.playpen.core.coordinator.local.Server;
import io.playpen.core.p3.IPackageStep;
import io.playpen.core.p3.P3Package;
import io.playpen.core.p3.PackageContext;
import io.playpen.core.utils.JSONUtils;
import io.playpen.core.utils.STUtils;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.stringtemplate.v4.ST;

import java.io.File;
import java.io.IOException;

@Log4j2
public class CopyStep implements IPackageStep {
    @Override
    public String getStepId() {
        return "copy-directory";
    }

    @Override
    public boolean runStep(P3Package p3, PackageContext ctx, JSONObject config) {
        String from = JSONUtils.safeGetString(config, "from");
        if(from == null) {
            log.error("'from' is not defined as a string in config");
            return false;
        }

        String to = JSONUtils.safeGetString(config, "to");
        if (to == null) {
            log.error("'to' is not defined as a string in config");
            return false;
        }

        ST templateFrom = new ST(from);
        STUtils.buildSTProperties(p3, ctx, templateFrom);

        ST templateTo = new ST(to);
        STUtils.buildSTProperties(p3, ctx, templateTo);

        from = templateFrom.render();
        to = templateTo.render();

        log.info("Copying from " + from + " to " + to);
        try {
            FileUtils.copyDirectory(new File(from), new File(to));
        } catch (IOException e) {
            log.error("Unable to copy directory", e);
            return false;
        }

        return true;
    }
}
