package net.thechunk.playpen.utils.process;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileProcessListener implements IProcessListener {
    private BufferedWriter writer = null;
    private final ExecutorService service;

    public FileProcessListener(File file) throws IOException {
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss"); // quick, will probably change later
        Date date = new Date();

        writer = Files.newBufferedWriter(file.toPath(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        service = Executors.newSingleThreadExecutor();
        writer.write("-- FileProcessListener SESSION STARTED " + dateFormat.format(date) + "\r\n");
        writer.flush();
    }

    @Override
    public void onProcessAttach(XProcess proc) {
    }

    @Override
    public void onProcessDetach(XProcess proc) {
    }

    @Override
    public void onProcessOutput(XProcess proc, String out) {
        if (!service.isShutdown()) {
            service.execute(() -> {
                try {
                    writer.write(out);
                    writer.write(System.lineSeparator());
                    writer.flush();
                } catch (IOException e) {
                }
            });
        }
    }

    @Override
    public void onProcessInput(XProcess proc, String in) {
        if (!service.isShutdown()) {
            service.execute(() -> {
                try {
                    writer.write(in);
                    writer.write(System.lineSeparator());
                    writer.flush();
                } catch (IOException e) {
                }
            });
        }
    }

    @Override
    public void onProcessEnd(XProcess proc) {
        service.shutdownNow();
        try {
            writer.write("-- FileProcessListener SESSION ENDED\r\n");
            writer.flush();
        }
        catch(IOException e) {}
        finally {
            try {
                writer.close();
            }
            catch(IOException e) {}
        }
    }
}
