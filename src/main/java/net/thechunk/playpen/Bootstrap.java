package net.thechunk.playpen;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

public class Bootstrap {

    private static Logger logger = LogManager.getLogger(Bootstrap.class);

    public static void main(String[] args) {
        boolean didCopyResources = false;

        try {
            File f = new File("keys.json");
            if (!f.exists()) {
                JarUtils.exportResource(Bootstrap.class, "/keys.json", "keys.json");
                didCopyResources = true;
            }

            f = new File("packages.json");
            if(!f.exists()) {
                JarUtils.exportResource(Bootstrap.class, "/packages.json", "packages.json");
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

            f = new File("playpen.bat");
            if(!f.exists()) {
                JarUtils.exportResource(Bootstrap.class, "/playpen.bat", "playpen.bat");
                didCopyResources = true;
            }

            f = new File("playpen.sh");
            if(!f.exists()) {
                JarUtils.exportResource(Bootstrap.class, "/playpen.sh", "playpen.sh");
                didCopyResources = true;
            }

            if(new File("cache/packages").mkdirs())
                didCopyResources = true;

            if(new File("packages").mkdirs())
                didCopyResources = true;

            if(new File("logs").mkdirs())
                didCopyResources = true;
        }
        catch(Exception e) {
            System.err.println("Unable to copy default resources");
            e.printStackTrace(System.err);
            return;
        }

        if(didCopyResources) {
            System.err.println("It looks like you were missing some resource files, so I've copied some defaults for you! " +
                    "I'll give you a chance to edit them. Bye!");
            return;
        }

        if(args.length < 1) {
            System.err.println("playpen <local/network/p3> [arguments...]");
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
                System.err.println("playpen <local/network/p3> [arguments...]");
                return;
        }
    }

}
