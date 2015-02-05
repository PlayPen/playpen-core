package net.thechunk.playpen.coordinator.network.web.coordinator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static spark.Spark.*;

public class UtilityRoutes {

    private static final Logger logger = LogManager.getLogger(UtilityRoutes.class);

    public static void build() {
        get("/net/ping", (request, response) -> {
            logger.info(request.ip() + " test pinged!");
            return "pong";
        });
    }

    private UtilityRoutes() {}
}
