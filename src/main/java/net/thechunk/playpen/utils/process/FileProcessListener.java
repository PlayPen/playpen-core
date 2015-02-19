package net.thechunk.playpen.utils.process;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FileProcessListener implements IProcessListener {
    private BufferedWriter writer = null;

    public FileProcessListener(File file) throws IOException {
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss"); // quick, will probably change later
        Date date = new Date();

        writer = Files.newBufferedWriter(file.toPath(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
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
