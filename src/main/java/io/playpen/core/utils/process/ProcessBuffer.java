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
    private CharBuffer rumpBuffer;

    public synchronized void append(CharBuffer buffer) {
        CharBuffer combinedBuffer;
        if (rumpBuffer != null) {
            combinedBuffer = CharBuffer.allocate(rumpBuffer.length() + buffer.length());
            combinedBuffer.put(rumpBuffer);
            combinedBuffer.put(buffer);
            combinedBuffer.flip();
            rumpBuffer = null;
        } else {
            combinedBuffer = buffer;
        }

        int lastNewline = 0;
        boolean finishedLine = false;
        for (int i = 0; i < combinedBuffer.remaining(); i++) {
            char c = combinedBuffer.get(i);
            finishedLine = false;
            if (c == '\n' && i != combinedBuffer.length()) {
                int previousLastNewline = lastNewline;
                lastNewline = i + 1; // "Advance" the virtual position
                if (previousLastNewline + 1 == i)
                    continue;

                boolean hasR = combinedBuffer.length() > 1 && combinedBuffer.get(i - 1) == '\r';
                CharBuffer sliced = (CharBuffer) combinedBuffer.slice().position(previousLastNewline).limit(i - (hasR ? 1 : 0));
                onOutput(sliced.toString());
                finishedLine = true;
            }
        }

        // Consume the buffer.
        if (!finishedLine) {
            this.rumpBuffer = CharBuffer.allocate(combinedBuffer.length() - lastNewline);
            this.rumpBuffer.put((CharBuffer) combinedBuffer.slice().position(lastNewline).limit(combinedBuffer.length()));
            this.rumpBuffer.flip();
        }
        buffer.position(buffer.remaining());
    }

    protected abstract void onOutput(String output);
}
