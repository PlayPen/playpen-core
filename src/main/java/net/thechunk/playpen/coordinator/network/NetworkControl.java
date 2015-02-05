package net.thechunk.playpen.coordinator.network;

import lombok.Getter;
import net.thechunk.playpen.ConfigException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import spark.Spark;

public class NetworkControl {
    private static final Logger logger = LogManager.getLogger(NetworkControl.class);

    @Getter
    private static NetworkControl instance = null;

    @Getter
    private NetworkConfig config = null;

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
    }
}
