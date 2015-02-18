package net.thechunk.playpen;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.thechunk.playpen.client.Client;
import net.thechunk.playpen.coordinator.PlayPen;
import net.thechunk.playpen.coordinator.VMShutdownThread;
import net.thechunk.playpen.coordinator.local.Local;
import net.thechunk.playpen.coordinator.network.Network;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Paths;

@Log4j2
public class Bootstrap {
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

            f = Paths.get(homeDir.getPath(), "keystore.json").toFile();
            if (!f.exists()) {
                JarUtils.exportResource(Bootstrap.class, "/keystore.json", f.getPath());
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

            if(Paths.get(homeDir.getPath(), "cache", "packages").toFile().mkdirs())
                didCopyResources = true;

            if(Paths.get(homeDir.getPath(), "packages").toFile().mkdirs())
                didCopyResources = true;

            if(Paths.get(homeDir.getPath(), "plugins").toFile().mkdirs())
                didCopyResources = true;

            if(Paths.get(homeDir.getPath(), "servers").toFile().mkdirs())
                didCopyResources = true;

            if(Paths.get(homeDir.getPath(), "temp").toFile().mkdir())
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
            System.err.println("playpen <local/network/p3/cli> [arguments...]");
            return;
        }

        switch(args[0].toLowerCase()) {
            case "local":
                runLocalCoordinator();
                break;

            case "network":
                runNetworkCoordinator();
                break;

            case "p3":
                P3Tool.run(args);
                break;

            case "cli":
                runClient(args);
                break;

            default:
                System.err.println("playpen <local/network/p3> [arguments...]");
                return;
        }
    }

    private static void runLocalCoordinator() {
        log.info("Bootstrap starting local coordinator (autorestart enabled)");

        Runtime.getRuntime().addShutdownHook(new VMShutdownThread());

        try {
            while(true) {
                //PlayPen.reset(); // DO NOT RESET PLAYPEN! If we get disconnected from the network, we don't want to
                                   // shutdown any servers that are running. We just want to reconnect to the network.
                if(!Local.get().run())
                    break;

                log.info("Waiting 10 seconds before restarting...");
                Thread.sleep(1000L * 10L);
            }
        }
        catch(Exception e) {
            log.fatal("Caught exception at bootstrap level while running local coordinator", e);
            return;
        }

        log.info("Ending local coordinator session");
    }

    private static void runNetworkCoordinator() {
        log.info("Bootstrap starting network coordinator (autorestart enabled)");

        Runtime.getRuntime().addShutdownHook(new VMShutdownThread());

        try {
            while (true) {
                PlayPen.reset();
                if(!Network.get().run())
                    break;

                log.info("Waiting 10 seconds before restarting...");
                Thread.sleep(1000L * 10L);
            }
        }
        catch(Exception e) {
            log.fatal("Caught exception at bootstrap level while running network coordinator", e);
            return;
        }

        log.info("Ending network coordinator session");
    }

    private static void runClient(String[] arguments) {
        log.info("Bootstrap starting client");

        Runtime.getRuntime().addShutdownHook(new VMShutdownThread());

        try {
            Client.get().run(arguments);
        }
        catch(Exception e) {
            log.fatal("Caught exception at bootstrap level while running client", e);
            return;
        }

        log.info("Ending client session");
    }
}
