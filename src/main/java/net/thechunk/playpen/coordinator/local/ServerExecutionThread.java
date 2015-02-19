package net.thechunk.playpen.coordinator.local;

import lombok.extern.log4j.Log4j2;
import net.thechunk.playpen.p3.ExecutionType;
import net.thechunk.playpen.p3.P3Package;
import net.thechunk.playpen.p3.PackageException;

import java.io.File;
import java.nio.file.Paths;

@Log4j2
public class ServerExecutionThread extends Thread {
    private Server server = null;

    public ServerExecutionThread(Server server) {
        this.server = server;
    }

    @Override
    public void run() {
        log.info("Executing server " + server.getUuid());

        if(Local.get().getPackageManager().execute(
                ExecutionType.EXECUTE,
                server.getP3(),
                new File(server.getLocalPath()),
                server.getProperties(),
                server)) {
            log.info("Server " + server.getUuid() + " execution completed successfully");
        }
        else {
            log.error("Server " + server.getUuid() + " execution did not complete successfully");
        }

        log.info("Notifying LC of server shutdown");
        Local.get().notifyServerShutdown(server.getUuid());
    }
}
