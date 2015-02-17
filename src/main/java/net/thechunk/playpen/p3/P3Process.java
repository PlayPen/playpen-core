package net.thechunk.playpen.p3;

import java.util.LinkedList;
import java.util.List;

public class P3Process {
    private Process process = null;
    private List<IProcessListener> listeners = new LinkedList<>();

    public P3Process(P3Package p3, String path) {
        // TODO
    }

    public void addListener(IProcessListener listener) {
        listeners.add(listener);
    }

    public void removeListener(IProcessListener listener) {
        listeners.remove(listener);
    }

    public void sendInput(String in) {
        // TODO
    }
}
