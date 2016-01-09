package io.playpen.core.coordinator.local;

import io.playpen.core.utils.process.IProcessListener;
import io.playpen.core.utils.process.XProcess;

public class ConsoleMessageListener implements IProcessListener {
    private String consoleId = null;

    private XProcess process = null;

    public ConsoleMessageListener(String id) {
        consoleId = id;
    }

    public void remove() {
        if(process != null)
            process.removeListener(this);
    }

    @Override
    public void onProcessAttach(XProcess proc) {
        process = proc;
    }

    @Override
    public void onProcessDetach(XProcess proc) {
    }

    @Override
    public void onProcessOutput(XProcess proc, String out) {
        Local.get().sendConsoleMessage(consoleId, out);
    }

    @Override
    public void onProcessInput(XProcess proc, String in) {
    }

    @Override
    public void onProcessEnd(XProcess proc) {
        Local.get().detachConsole(consoleId);
    }
}
