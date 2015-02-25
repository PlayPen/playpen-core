package net.thechunk.playpen.p3.step;

import lombok.extern.log4j.Log4j2;
import net.thechunk.playpen.coordinator.local.Server;
import net.thechunk.playpen.p3.IPackageStep;
import net.thechunk.playpen.p3.P3Package;
import net.thechunk.playpen.p3.PackageContext;
import net.thechunk.playpen.utils.JSONUtils;
import net.thechunk.playpen.utils.STUtils;
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

        String str = JSONUtils.safeGetString(config, "string");
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
