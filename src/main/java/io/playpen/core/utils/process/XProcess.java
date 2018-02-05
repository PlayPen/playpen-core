package io.playpen.core.utils.process;

import com.zaxxer.nuprocess.NuProcess;
import com.zaxxer.nuprocess.NuProcessBuilder;
import com.zaxxer.nuprocess.codec.NuAbstractCharsetHandler;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

@Log4j2
public class XProcess extends NuAbstractCharsetHandler {
    public static final int MAX_LINE_STORAGE = 25;

    private final List<String> command;
    private final String workingDir;
    private NuProcess process = null;
    private final List<IProcessListener> listeners = new CopyOnWriteArrayList<>();
    private final OutputBuffer outputBuffer = new OutputBuffer();
    private final Map<String, String> environment;
    private boolean wait;

    @Getter
    private ConcurrentLinkedQueue<String> lastLines = new ConcurrentLinkedQueue<>();

    public XProcess(List<String> command, String workingDir, Map<String, String> environment, boolean wait) {
        super(StandardCharsets.UTF_8);
        this.command = command;
        this.workingDir = workingDir;
        this.environment = environment;
        this.wait = wait;
    }

    public void addListener(IProcessListener listener) {
        listeners.add(listener);
        listener.onProcessAttach(this);
    }

    public void removeListener(IProcessListener listener) {
        listeners.remove(listener);
        listener.onProcessDetach(this);
    }

    public void sendInput(String in) {
        byte[] inAsBytes = in.getBytes(StandardCharsets.UTF_8);
        process.writeStdin(ByteBuffer.wrap(inAsBytes));

        for(IProcessListener listener : listeners) {
            listener.onProcessInput(this, in);
        }
    }

    public boolean run() {
        NuProcessBuilder builder = new NuProcessBuilder(command);
        builder.setCwd(Paths.get(workingDir));
        builder.setProcessListener(this);
        builder.environment().putAll(environment);
        process = builder.start();

        if (wait) {
            try {
                process.waitFor(0, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.info("Interrupted while waiting for process to complete", e);
            }
        }

        return true;
    }

    public boolean isRunning() {
        return process.isRunning();
    }

    public void stop() {
        process.destroy(true);
        try {
            process.waitFor(5, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            log.info("Interrupted while waiting for process to shutdown", e);
        }
    }

    @Override
    protected void onStdoutChars(CharBuffer buffer, boolean closed, CoderResult coderResult) {
        outputBuffer.append(buffer);
    }

    @Override
    protected void onStderrChars(CharBuffer buffer, boolean closed, CoderResult coderResult) {
        onStdoutChars(buffer, closed, coderResult);
    }

    @Override
    public void onExit(int exitCode) {
        for (IProcessListener listener : listeners) {
            listener.onProcessEnd(this);
        }
    }

    protected void receiveOutput(String out) {
        for(IProcessListener listener : listeners) {
            listener.onProcessOutput(this, out);
        }

        lastLines.add(out);
        if (lastLines.size() > MAX_LINE_STORAGE)
            lastLines.remove();
    }

    private class OutputBuffer extends ProcessBuffer {
        @Override
        protected void onOutput(String output) {
            receiveOutput(output);
        }
    }
}
