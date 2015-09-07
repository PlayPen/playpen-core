package net.thechunk.playpen.utils.process;

import com.zaxxer.nuprocess.NuProcess;
import com.zaxxer.nuprocess.NuProcessBuilder;
import com.zaxxer.nuprocess.codec.NuAbstractCharsetHandler;
import lombok.extern.log4j.Log4j2;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

@Log4j2
public class XProcess extends NuAbstractCharsetHandler {
    private List<String> command;
    private String workingDir;
    private NuProcess process = null;
    private List<IProcessListener> listeners = new CopyOnWriteArrayList<>();
    private OutputBuffer stdinBuffer = new OutputBuffer();
    private OutputBuffer stderrBuffer = new OutputBuffer();

    public XProcess(List<String> command, String workingDir) {
        super(StandardCharsets.UTF_8);
        this.command = command;
        this.workingDir = workingDir;
    }

    public void addListener(IProcessListener listener) {
        listeners.add(listener);
        listener.onProcessAttach(this);
    }

    public void removeListener(IProcessListener listener) {
        listeners.remove(listener);
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
        builder.setProcessListener(this);
        process = builder.start();

        try {
            process.waitFor(0, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("Interrupted while waiting for command to finish", e);
        }

        for(IProcessListener listener : listeners) {
            listener.onProcessEnd(this);
        }

        return true;
    }

    public boolean isRunning() {
        return process.isRunning();
    }

    public void stop() {
        process.destroy(true);
    }

    @Override
    protected void onStdoutChars(CharBuffer buffer, boolean closed, CoderResult coderResult) {
        stdinBuffer.append(buffer);
        // Just in case, consume the buffer too.
        buffer.position(buffer.limit());
    }

    @Override
    protected void onStderrChars(CharBuffer buffer, boolean closed, CoderResult coderResult) {
        stderrBuffer.append(buffer);
        // Just in case, consume the buffer too.
        buffer.position(buffer.limit());
    }

    @Override
    public void onExit(int exitCode) {
        super.onExit(exitCode);
    }

    protected void receiveOutput(String out) {
        for(IProcessListener listener : listeners) {
            listener.onProcessOutput(this, out);
        }
    }

    private class OutputBuffer extends ProcessBuffer {
        @Override
        protected void onOutput(String output) {
            receiveOutput(output);
        }
    }
}
