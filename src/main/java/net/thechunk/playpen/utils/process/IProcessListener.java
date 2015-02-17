package net.thechunk.playpen.utils.process;

import net.thechunk.playpen.utils.process.XProcess;

public interface IProcessListener {
    void onProcessOutput(XProcess proc, String out);
    void onProcessEnd(XProcess proc);
}
