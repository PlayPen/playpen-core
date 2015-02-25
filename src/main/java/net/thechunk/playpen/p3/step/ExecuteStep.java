package net.thechunk.playpen.p3.step;

import lombok.extern.log4j.Log4j2;
import net.thechunk.playpen.Bootstrap;
import net.thechunk.playpen.coordinator.local.Server;
import net.thechunk.playpen.p3.IPackageStep;
import net.thechunk.playpen.p3.PackageContext;
import net.thechunk.playpen.utils.JSONUtils;
import net.thechunk.playpen.utils.STUtils;
import net.thechunk.playpen.utils.process.FileProcessListener;
import net.thechunk.playpen.utils.process.XProcess;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.stringtemplate.v4.ST;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Log4j2
public class ExecuteStep implements IPackageStep {
    @Override
    public String getStepId() {
        return "execute";
    }

    @Override
    public boolean runStep(PackageContext ctx, JSONObject config) {
        Server server = null;
        if(ctx.getUser() instanceof Server) {
            server = (Server)ctx.getUser();
        }

        List<String> command = new ArrayList<>();
        try {
            command.add(config.getString("command"));

            JSONArray args = JSONUtils.safeGetArray(config, "arguments");
            if(args != null) {
                for(int i = 0; i < args.length(); ++i) {
                    command.add(args.getString(i));
                }
            }
        }
        catch(JSONException e) {
            log.error("Configuration error", e);
            return false;
        }

        if(config.has("template")) {
            boolean useTemplate = config.getBoolean("template");
            if(!useTemplate) {
                log.info("Running ST on command");
                for (int i = 0; i < command.size(); ++i) {
                    ST template = new ST(command.get(i));

                    STUtils.buildSTProperties(ctx.getP3(), ctx.getProperties(), template);

                    command.add(i, template.render());
                }
            }
        }

        log.info("Running command " + command.get(0));

        XProcess proc = new XProcess(command, ctx.getDestination().toString());

        if(server != null) {
            log.info("Registering process with server " + server.getUuid());
            server.setProcess(proc);

            try {
                proc.addListener(new FileProcessListener(Paths.get(Bootstrap.getHomeDir().getPath(), "server-logs", server.getUuid() + ".log").toFile()));
            }
            catch(IOException e) {
                log.warn("Unable to create log for server, no logging of console output will be done");
            }
        }

        if(!proc.run()) {
            log.info("Command " + command.get(0) + " failed");
            return false;
        }

        return true;
    }
}
