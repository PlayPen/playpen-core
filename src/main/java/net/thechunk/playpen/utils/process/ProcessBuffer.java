package net.thechunk.playpen.utils.process;

import java.nio.CharBuffer;

public abstract class ProcessBuffer {
    private final StringBuilder buffer = new StringBuilder(1024);

    public void append(CharBuffer buffer) {
        // Okay, I'll admit it: this thing is literally a Rube Goldberg machine. But it works well enough!
        char[] all = new char[buffer.remaining()];
        buffer.get(all);
        this.buffer.append(all);

        int lastNewlineIdx = this.buffer.lastIndexOf("\n");
        if (lastNewlineIdx != -1) {
            String[] fullLines = this.buffer.substring(0, lastNewlineIdx + 1).split("\n");
            for (String fullLine : fullLines) {
                onOutput(fullLine);
            }
            this.buffer.delete(0, lastNewlineIdx + 1);
        }
    }

    protected abstract void onOutput(String output);
}
