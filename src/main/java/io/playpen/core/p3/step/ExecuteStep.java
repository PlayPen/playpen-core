package io.playpen.core.p3.step;

import io.playpen.core.Bootstrap;
import io.playpen.core.coordinator.local.Local;
import io.playpen.core.coordinator.local.Server;
import io.playpen.core.p3.IPackageStep;
import io.playpen.core.p3.P3Package;
import io.playpen.core.p3.PackageContext;
import io.playpen.core.utils.STUtils;
import io.playpen.core.utils.process.FileProcessListener;
import io.playpen.core.utils.process.ShutdownProcessListener;
import io.playpen.core.utils.process.XProcess;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.stringtemplate.v4.ST;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Log4j2
public class ExecuteStep implements IPackageStep {
    @Override
    public String getStepId() {
        return "execute";
    }

    @Override
    public boolean runStep(P3Package p3, PackageContext ctx, JSONObject config) {
        Server server = null;
        if(ctx.getUser() instanceof Server) {
            server = (Server)ctx.getUser();
        }

        List<String> command = new ArrayList<>();
        try {
            command.add(config.getString("command"));

            JSONArray args = config.optJSONArray("arguments");
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
            if(useTemplate) {
                log.info("Running ST on command");
                for (int i = 0; i < command.size(); ++i) {
                    ST template = new ST(command.get(i));

                    STUtils.buildSTProperties(p3, ctx, template);

                    command.set(i, template.render());
                }
            }
        }

        Map<String, String> environment = new HashMap<>();
        if(config.has("environment")) {
            JSONObject environmentObj = config.getJSONObject("environment");
            for (String s : environmentObj.keySet()) {
                environment.put(s, environmentObj.getString(s));
            }
        }

        log.info("Running command " + command.get(0));

        XProcess proc = new XProcess(command, ctx.getDestination().toString(), environment, server == null);

        if(server != null) {
            log.info("Registering process with server " + server.getUuid());
            server.setProcess(proc);

            try {
                proc.addListener(new FileProcessListener(Paths.get(Bootstrap.getHomeDir().getPath(), "server-logs",
                        (Local.get().isUseNameForLogs() ? server.getSafeName() : server.getUuid()) + ".log").toFile()));
            }
            catch(IOException e) {
                log.warn("Unable to create log for server, no logging of console output will be done");
            }

            proc.addListener(new ShutdownProcessListener(server));
        }

        if(!proc.run()) {
            log.info("Command " + command.get(0) + " failed");
            return false;
        }

        return true;
    }
}
