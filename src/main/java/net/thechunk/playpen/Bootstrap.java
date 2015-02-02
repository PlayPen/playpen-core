package net.thechunk.playpen;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Bootstrap {

    private static Logger logger = Logger.getLogger(Bootstrap.class.getName());

    public static void main(String[] args) {
        try {
            File f = new File("keys.json");
            if (!f.exists()) {
                JarUtils.exportResource(Bootstrap.class, "/keys.json", "keys.json");
            }

            f = new File("local.json");
            if(!f.exists()) {
                JarUtils.exportResource(Bootstrap.class, "/local.json", "local.json");
            }

            f = new File("network.json");
            if(!f.exists()) {
                JarUtils.exportResource(Bootstrap.class, "/network.json", "network.json");
            }
        }
        catch(Exception e) {
            logger.log(Level.SEVERE, "Unable to copy default configuration resources", e);
            return;
        }
    }

}
