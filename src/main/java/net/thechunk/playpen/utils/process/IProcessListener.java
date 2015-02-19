package net.thechunk.playpen.utils.process;

public interface IProcessListener {
    void onProcessAttach(XProcess proc);
    void onProcessDetach(XProcess proc);
    void onProcessOutput(XProcess proc, String out);
    void onProcessEnd(XProcess proc);
}
