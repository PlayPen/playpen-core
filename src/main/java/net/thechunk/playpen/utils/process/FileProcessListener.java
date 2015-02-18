package net.thechunk.playpen.utils.process;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

public class FileProcessListener implements IProcessListener {
    private BufferedWriter writer = null;

    public FileProcessListener(File file) throws IOException {
        writer = Files.newBufferedWriter(file.toPath(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        writer.write("-- FileProcessListener SESSION STARTED\r\n");
        writer.flush();
    }

    @Override
    public void onProcessOutput(XProcess proc, String out) {
        try {
            writer.write(out + "\r\n");
            writer.flush();
        }
        catch(IOException e) {}
    }

    @Override
    public void onProcessEnd(XProcess proc) {
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
