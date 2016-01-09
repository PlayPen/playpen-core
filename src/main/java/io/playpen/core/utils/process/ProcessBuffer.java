package io.playpen.core.utils.process;

import java.nio.CharBuffer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ProcessBuffer implements a way to turn {@code CharBuffers} from NuProcess into by-line output.
 *
 * This class is thread-safe (the buffer is protected by a lock).
 */
public abstract class ProcessBuffer {
    private final StringBuilder rumpBuffer = new StringBuilder(128);
    private volatile boolean rumpBufferHasContents = false;
    private final Lock lock = new ReentrantLock();

    public void append(CharBuffer buffer) {
        // Okay, I'll admit it: this thing is literally a Rube Goldberg machine. But it works well enough!
        StringBuilder found = new StringBuilder();

        if (rumpBufferHasContents) {
            lock.lock();
            try {
                found.append(rumpBuffer);
                rumpBuffer.delete(0, rumpBuffer.length());
                rumpBufferHasContents = false;
            } finally {
                lock.unlock();
            }
        }

        for (int i = 0; i < buffer.remaining(); i++) {
            char c = buffer.get(i);
            if (c == '\r') {
                // When it looks like a hammer, there must be a sickle too.
                continue;
            }
            if (c == '\n') {
                if (found.length() == 0)
                    continue;
                onOutput(found.toString());
                found.delete(0, found.length());
            } else {
                found.append(c);
            }
        }

        // Consume the buffer.
        buffer.position(buffer.remaining());

        if (found.length() != 0) {
            lock.lock();
            try {
                this.rumpBuffer.append(found);
                rumpBufferHasContents = true;
            } finally {
                lock.unlock();
            }
        }
    }

    protected abstract void onOutput(String output);
}
