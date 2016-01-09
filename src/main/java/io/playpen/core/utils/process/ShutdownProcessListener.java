package io.playpen.core.utils.process;

import io.playpen.core.coordinator.local.Local;
import io.playpen.core.coordinator.local.Server;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@AllArgsConstructor
public class ShutdownProcessListener implements IProcessListener {
    private final Server server;

    @Override
    public void onProcessAttach(XProcess proc) {

    }

    @Override
    public void onProcessDetach(XProcess proc) {

    }

    @Override
    public void onProcessOutput(XProcess proc, String out) {

    }

    @Override
    public void onProcessInput(XProcess proc, String in) {

    }

    @Override
    public void onProcessEnd(XProcess proc) {
        log.info("Server process for " + server.getUuid() + " has ended, informing network controller of server shutdown");
        Local.get().notifyServerShutdown(server.getUuid());
    }
}
