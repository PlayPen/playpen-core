package net.thechunk.playpen.utils.process;

import lombok.extern.log4j.Log4j2;

import java.io.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Log4j2
public class XProcess {
    private List<String> command;
    private String workingDir;
    private Process process = null;
    private List<IProcessListener> listeners = new CopyOnWriteArrayList<>();
    private BufferedWriter inputWriter = null;

    public XProcess(List<String> command, String workingDir) {
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
        try {
            inputWriter.write(in);
            inputWriter.flush();
        } catch (IOException e) {
            log.error("Error while writing to input stream", e);
        }

        for(IProcessListener listener : listeners) {
            listener.onProcessInput(this, in);
        }
    }

    public boolean run() {
        try {
            process = new ProcessBuilder(command)
                    .directory(new File(workingDir))
                    .start();
        } catch (IOException e) {
            log.error("Unable to start process", e);
            return false;
        }

        StreamThread outThread = new StreamThread(this, process.getInputStream());
        StreamThread errThread = new StreamThread(this, process.getErrorStream());
        outThread.start();
        errThread.start();

        try (OutputStreamWriter osw = new OutputStreamWriter(process.getOutputStream());
            BufferedWriter writer = new BufferedWriter(osw)) {
            inputWriter = writer;
            process.waitFor();
        }
        catch(IOException e) {
            log.error("Exception while waiting for process", e);
        }
        catch(InterruptedException e) {
            log.error("Interrupted while waiting for process", e);
        }
        finally {
            inputWriter = null;

            for(IProcessListener listener : listeners) {
                listener.onProcessEnd(this);
            }
        }

        return true;
    }

    public boolean isRunning() {
        return process.isAlive();
    }

    public void stop() {
        process.destroy();
    }

    protected void receiveOutput(String out) {
        for(IProcessListener listener : listeners) {
            listener.onProcessOutput(this, out);
        }
    }

    @Log4j2
    private static class StreamThread extends Thread {
        private InputStream is = null;
        private XProcess proc = null;

        public StreamThread(XProcess proc, InputStream is) {
            this.is = is;
            this.proc = proc;
        }

        @Override
        public void run() {
            try(InputStreamReader isr = new InputStreamReader(is);
                BufferedReader reader = new BufferedReader(isr)) {
                String line = null;
                while((line = reader.readLine()) != null) {
                    proc.receiveOutput(line);
                }
            }
            catch(IOException e) {
                log.error("Error while reading input stream", e);
            }
        }
    }
}
