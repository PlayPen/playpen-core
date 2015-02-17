package net.thechunk.playpen.p3;

public interface IProcessListener {
    void onProcessOutput(String out);
    void onProcessError(String err);
}
