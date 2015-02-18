package net.thechunk.playpen.coordinator;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class VMShutdownThread extends Thread {
    @Override
    public void run() {
        log.info("Shutting down playpen");
        if(PlayPen.get() != null) {
            PlayPen.get().onVMShutdown();
        }
    }
}
