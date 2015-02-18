package net.thechunk.playpen.utils.process;

public interface IProcessListener {
    void onProcessOutput(XProcess proc, String out);
    void onProcessEnd(XProcess proc);
}
