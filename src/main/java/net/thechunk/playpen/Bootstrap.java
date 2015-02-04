package net.thechunk.playpen;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Bootstrap {

    private static Logger logger = Logger.getLogger(Bootstrap.class.getName());

    public static void main(String[] args) {
        boolean didCopyResources = false;

        try {
            File f = new File("keys.json");
            if (!f.exists()) {
                JarUtils.exportResource(Bootstrap.class, "/keys.json", "keys.json");
                didCopyResources = true;
            }

            f = new File("local.json");
            if(!f.exists()) {
                JarUtils.exportResource(Bootstrap.class, "/local.json", "local.json");
                didCopyResources = true;
            }

            f = new File("network.json");
            if(!f.exists()) {
                JarUtils.exportResource(Bootstrap.class, "/network.json", "network.json");
                didCopyResources = true;
            }

            if(new File("cache/packages").mkdirs())
                didCopyResources = true;

            if(new File("packages").mkdirs())
                didCopyResources = true;
        }
        catch(Exception e) {
            logger.log(Level.SEVERE, "Unable to copy default configuration resources", e);
            return;
        }

        if(didCopyResources) {
            logger.info("It looks like you were missing some resource files, so I've copied some defaults for you! " +
                    "I'll give you a chance to edit them. Bye!");
            return;
        }

        if(args.length < 1) {
            logger.severe("Missing mode parameter (either local, network, or p3)");
            return;
        }

        switch(args[0].toLowerCase()) {
            case "local":

                break;

            case "network":

                break;

            case "p3":
                P3Tool.execute(args);
                break;

            default:
                logger.severe("Unknown mode. Should be either local, network, or p3.");
                return;
        }
    }

}
