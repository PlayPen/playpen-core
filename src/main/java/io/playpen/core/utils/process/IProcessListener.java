package io.playpen.core.utils.process;

public interface IProcessListener {
    void onProcessAttach(XProcess proc);
    void onProcessDetach(XProcess proc);
    void onProcessOutput(XProcess proc, String out);
    void onProcessInput(XProcess proc, String in);
    void onProcessEnd(XProcess proc);
}
