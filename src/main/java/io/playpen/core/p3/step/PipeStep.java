package io.playpen.core.p3.step;

import io.playpen.core.coordinator.local.Server;
import io.playpen.core.p3.IPackageStep;
import io.playpen.core.p3.P3Package;
import io.playpen.core.p3.PackageContext;
import io.playpen.core.utils.STUtils;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.stringtemplate.v4.ST;

@Log4j2
public class PipeStep implements IPackageStep {
    @Override
    public String getStepId() {
        return "pipe";
    }

    @Override
    public boolean runStep(P3Package p3, PackageContext ctx, JSONObject config) {
        if(!(ctx.getUser() instanceof Server)) {
            log.error("Must be executed on a local coordinator");
            return false;
        }

        Server server = (Server)ctx.getUser();
        if(server.getProcess() == null) {
            log.error("No process found to pipe to");
            return false;
        }

        if(!server.getProcess().isRunning()) {
            log.warn("Server process is not running, continuing");
            return true;
        }

        String str = config.optString("string");
        if(str == null) {
            log.error("'string' is not defined as a string in config");
            return false;
        }

        if(config.has("template")) {
            boolean useTemplate = config.getBoolean("template");
            if(useTemplate) {
                log.info("Running ST on string");
                ST template = new ST(str);

                STUtils.buildSTProperties(p3, ctx, template);

                str = template.render();
            }
        }

        log.info("Piping string to process");
        server.getProcess().sendInput(str + '\n');
        return true;
    }
}
