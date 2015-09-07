package net.thechunk.playpen.utils.process;

import java.nio.CharBuffer;

public abstract class ProcessBuffer {
    private final StringBuilder buffer = new StringBuilder(1024);

    public void append(CharBuffer buffer) {
        // Okay, I'll admit it: this thing is literally a Rube Goldberg machine. But it works well enough!
        char[] all = new char[buffer.remaining()];
        buffer.get(all);
        this.buffer.append(all);

        int newlineIdx = this.buffer.indexOf("\n");
        if (newlineIdx != -1) {
            onOutput(this.buffer.substring(0, newlineIdx + 1));
            this.buffer.delete(0, newlineIdx + 1);
        }
    }

    protected abstract void onOutput(String output);
}
