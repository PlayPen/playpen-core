package io.playpen.core.utils.process;

import lombok.extern.log4j.Log4j2;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

@Log4j2
public class FileProcessListener implements IProcessListener {
    private final BlockingDeque<String> messageQueue = new LinkedBlockingDeque<>();
    private final Thread logWriter;
    private volatile boolean stopped = false;

    public FileProcessListener(File file) throws IOException {
        logWriter = new Thread(() -> {
            try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
                DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss"); // quick, will probably change later
                writer.write("-- FileProcessListener SESSION STARTED " + dateFormat.format(new Date()) + "\r\n");

                while (!stopped) {
                    try {
                        String logEntry;
                        while ((logEntry = messageQueue.take()) != null) {
                            try {
                                writer.write(logEntry);
                                writer.write(System.lineSeparator());
                                writer.flush();
                            } catch (IOException e) {
                                // We weren't able to flush the log entry to the disk. Wait a moment and then try again.
                                Thread.sleep(250);
                                messageQueue.addFirst(logEntry);
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().isInterrupted();
                    }
                }

                for (String s : messageQueue) {
                    writer.write(s);
                    writer.write(System.lineSeparator());
                }

                writer.write("-- FileProcessListener SESSION ENDED " + dateFormat.format(new Date()) + "\r\n");
            } catch (IOException e) {
                log.error("Unable to write to log " + file, e);
                stopped = true;
            }
        }, "PlayPen Logger - " + file.getName());
    }

    @Override
    public void onProcessAttach(XProcess proc) {
    }

    @Override
    public void onProcessDetach(XProcess proc) {
    }

    @Override
    public void onProcessOutput(XProcess proc, String out) {
        if (stopped) return;
        messageQueue.add(out);
    }

    @Override
    public void onProcessInput(XProcess proc, String in) {
        if (stopped) return;
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss"); // quick, will probably change later
        Date date = new Date();
        messageQueue.add(dateFormat.format(date) + " - INPUT: " + in);
    }

    @Override
    public void onProcessEnd(XProcess proc) {
        stopped = true;
        logWriter.interrupt();
    }
}
