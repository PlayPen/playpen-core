package net.thechunk.playpen;

import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Bootstrap {

    private static Logger logger = LogManager.getLogger(Bootstrap.class);

    @Getter
    private static File homeDir;

    public static void main(String[] args) {
        boolean didCopyResources = false;

        try {
            homeDir = new File(Bootstrap.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getParentFile();
        }
        catch(URISyntaxException e) {}

        try {
            File f = Paths.get(homeDir.getPath(), "logging.xml").toFile();
            if(!f.exists()) {
                JarUtils.exportResource(Bootstrap.class, "/logging.xml", f.getPath());
                didCopyResources = true;
            }

            f = Paths.get(homeDir.getPath(), "keys.json").toFile();
            if (!f.exists()) {
                JarUtils.exportResource(Bootstrap.class, "/keys.json", f.getPath());
                didCopyResources = true;
            }

            f = Paths.get(homeDir.getPath(), "packages.json").toFile();
            if(!f.exists()) {
                JarUtils.exportResource(Bootstrap.class, "/packages.json", f.getPath());
                didCopyResources = true;
            }

            f = Paths.get(homeDir.getPath(), "local.json").toFile();
            if(!f.exists()) {
                JarUtils.exportResource(Bootstrap.class, "/local.json", f.getPath());
                didCopyResources = true;
            }

            f = Paths.get(homeDir.getPath(), "network.json").toFile();
            if(!f.exists()) {
                JarUtils.exportResource(Bootstrap.class, "/network.json", f.getPath());
                didCopyResources = true;
            }

            f = Paths.get(homeDir.getPath(), "playpen.bat").toFile();
            if(!f.exists()) {
                JarUtils.exportResource(Bootstrap.class, "/playpen.bat", f.getPath());
                didCopyResources = true;
            }

            f = Paths.get(homeDir.getPath(), "playpen.sh").toFile();
            if(!f.exists()) {
                JarUtils.exportResource(Bootstrap.class, "/playpen.sh", f.getPath());
                didCopyResources = true;
            }

            if(Paths.get(homeDir.getPath(), "cache/packages").toFile().mkdirs())
                didCopyResources = true;

            if(Paths.get(homeDir.getPath(), "packages").toFile().mkdirs())
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
