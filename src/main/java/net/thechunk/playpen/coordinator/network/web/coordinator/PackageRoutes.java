package net.thechunk.playpen.coordinator.network.web.coordinator;

import net.thechunk.playpen.coordinator.network.NetworkControl;
import net.thechunk.playpen.p3.P3Package;
import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static spark.Spark.*;

public class PackageRoutes {
    private static final Logger logger = LogManager.getLogger(PackageRoutes.class);

    public static void build() {
        get("/net/package/dl/:id/:version", (request, response) -> {
            String id = request.params("id");
            String version = request.params("version");
            logger.info("Preparing package " + id + " at " + version + " for " + request.ip());
            P3Package p3 = NetworkControl.getInstance().getPackageManager().resolve(id, version);
            if(p3 == null) {
                logger.error("Unknown package " + id + " at " + version + " for " + request.ip());
                halt(404, "unknown package");
            }

            File file = new File(p3.getLocalPath());
            if(!file.exists() || !file.isFile()) {
                logger.error("Couldn't find package file " + id + " at " + version + " for " + request.ip());
                halt(404, "package file no longer exists");
            }

            String packageContents = null;
            try {
                packageContents = Base64.encodeBase64String(Files.readAllBytes(file.toPath()));
            }
            catch(IOException e) {
                logger.error("Couldn't read package", e);
                halt(404, "unable to read package");
            }

            response.header("Content-Description", "File Transfer");
            response.header("Content-Type", "application/octet-stream");
            response.header("Content-Disposition", "attachment; filename=" + id + "_" + version + ".p64");
            response.header("Content-Transfer-Encoding", "binary");
            response.header("Expires", "0");
            response.header("Cache-Control", "must-revalidate");
            response.header("Pragma", "public");
            response.header("Content-Length", String.valueOf(packageContents.length()));

            return packageContents;
        });
    }

    private PackageRoutes() {}
}
