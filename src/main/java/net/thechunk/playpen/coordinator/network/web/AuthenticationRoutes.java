package net.thechunk.playpen.coordinator.network.web;

import net.thechunk.playpen.coordinator.network.CoordinatorInfo;
import net.thechunk.playpen.coordinator.network.KeyStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import spark.route.HttpMethod;

import static spark.Spark.*;

public class AuthenticationRoutes {
    private static final Logger logger = LogManager.getLogger(AuthenticationRoutes.class);

    public static void build() {
        before("/net/*", (request, response) -> {
            String uuid = request.queryParams("uuid");
            String hash = request.headers("Authorization");
            String payload = null;

            if(request.requestMethod().equalsIgnoreCase("get")) {
                payload = request.pathInfo() + request.ip();
            }
            else {
                payload = request.body();
            }

            if(KeyStore.getInstance().authCoordinator(uuid, payload, hash) == null) {
                logger.warn("Bad authentication from " + request.ip() + " to endpoint " + request.pathInfo());
                halt(403, "Invalid authentication");
            }

            logger.info("Authenticated " + request.ip() + " for " + request.pathInfo());
        });

        // TODO: client auth
    }

    private AuthenticationRoutes() {}
}
