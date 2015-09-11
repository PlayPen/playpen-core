package net.thechunk.playpen.utils.process;

import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ProcessBuffer implements a way to turn {@code CharBuffers} from NuProcess into by-line output.
 *
 * This class is thread-safe (the buffer is protected by a lock).
 */
public abstract class ProcessBuffer {
    private final StringBuilder buffer = new StringBuilder(1024);
    private static final String LINE_SEPERATOR = System.lineSeparator();
    private final Lock lock = new ReentrantLock();

    public void append(CharBuffer buffer) {
        // Okay, I'll admit it: this thing is literally a Rube Goldberg machine. But it works well enough!
        char[] all = new char[buffer.remaining()];
        buffer.get(all);

        List<String> lines = Collections.emptyList();

        lock.lock();
        try {
            this.buffer.append(all);

            int lastNewlineIdx = this.buffer.lastIndexOf(LINE_SEPERATOR);
            if (lastNewlineIdx != -1) {
                int stop = lastNewlineIdx + LINE_SEPERATOR.length();
                lines = Arrays.asList(this.buffer.substring(0, stop).split(LINE_SEPERATOR));
                this.buffer.delete(0, stop);
            }
        } finally {
            lock.unlock();
        }

        lines.stream().filter(line -> !line.isEmpty()).forEach(this::onOutput);
    }

    protected abstract void onOutput(String output);
}
