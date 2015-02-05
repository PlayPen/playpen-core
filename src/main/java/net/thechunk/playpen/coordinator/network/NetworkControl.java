package net.thechunk.playpen.coordinator.network;

import lombok.Getter;
import net.thechunk.playpen.ConfigException;
import net.thechunk.playpen.Initialization;
import net.thechunk.playpen.coordinator.network.web.AuthenticationRoutes;
import net.thechunk.playpen.coordinator.network.web.coordinator.PackageRoutes;
import net.thechunk.playpen.coordinator.network.web.coordinator.UtilityRoutes;
import net.thechunk.playpen.p3.PackageManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import spark.Spark;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class NetworkControl {
    private static final Logger logger = LogManager.getLogger(NetworkControl.class);

    @Getter
    private static NetworkControl instance = null;

    @Getter
    private NetworkConfig config = null;

    @Getter
    private KeyStore keyStore = null;

    @Getter
    private PackageManager packageManager = null;

    @Getter
    private ScheduledExecutorService scheduler = null;

    private Map<String, CoordinatorInfo> activeCoordinators = new ConcurrentHashMap<>();

    public NetworkControl() {
        instance = this;
    }

    public void run() {
        try {
            config = NetworkConfig.readConfig();
        }
        catch(ConfigException e) {
            logger.fatal("Unable to read network configuration file", e);
            return;
        }

        scheduler = Executors.newScheduledThreadPool(config.getThreads());

        try {
            keyStore = new KeyStore();
        }
        catch(ConfigException e) {
            logger.fatal("Unable to instantiate keystore", e);
            return;
        }

        packageManager = new PackageManager();
        Initialization.packageManager(packageManager);

        Spark.ipAddress(config.getIp());
        Spark.port(config.getPort());

        AuthenticationRoutes.build();
        UtilityRoutes.build();
        PackageRoutes.build();
    }
}
