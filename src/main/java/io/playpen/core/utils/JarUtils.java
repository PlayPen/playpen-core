package io.playpen.core.utils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;

public final class JarUtils {

    public static void exportResource(Class fromClass, String resourceName, String exportPath) throws IOException, URISyntaxException {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = fromClass.getResourceAsStream(resourceName);
            if(in == null) {
                throw new IOException("Cannot get resource \"" + resourceName + "\" from jar.");
            }

            int readBytes;
            byte[] buffer = new byte[4096];
            out = new FileOutputStream(exportPath);
            while((readBytes = in.read(buffer)) > 0) {
                out.write(buffer, 0, readBytes);
            }
        }
        finally {
            if(in != null)
                in.close();

            if(out != null)
                out.close();
        }
    }

    private JarUtils() {}
}
