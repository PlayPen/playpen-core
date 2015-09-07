package net.thechunk.playpen.utils.process;

import java.nio.CharBuffer;

public abstract class ProcessBuffer {
    private final StringBuilder buffer = new StringBuilder(1024);
    private static final String LINE_SEPERATOR = System.lineSeparator();

    public void append(CharBuffer buffer) {
        // Okay, I'll admit it: this thing is literally a Rube Goldberg machine. But it works well enough!
        char[] all = new char[buffer.remaining()];
        buffer.get(all);
        this.buffer.append(all);

        int lastNewlineIdx = this.buffer.lastIndexOf(LINE_SEPERATOR);
        if (lastNewlineIdx != -1) {
            int stop = lastNewlineIdx + LINE_SEPERATOR.length();
            String[] fullLines = this.buffer.substring(0, stop).split(LINE_SEPERATOR);
            for (String fullLine : fullLines) {
                if (!fullLine.isEmpty())
                    onOutput(fullLine);
            }
            this.buffer.delete(0, stop);
        }
    }

    protected abstract void onOutput(String output);
}
