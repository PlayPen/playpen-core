package io.playpen.core.utils;

import io.playpen.core.Bootstrap;
import io.playpen.core.p3.P3Package;
import io.playpen.core.p3.PackageContext;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.misc.Aggregate;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class STUtils {

    public static void buildSTProperties(P3Package p3, PackageContext ctx, ST template) {
        template.add("package-id", p3.getId());
        template.add("package-version", p3.getVersion());
        template.add("resources", ctx.getResources());
        template.add("asset_path", Paths.get(Bootstrap.getHomeDir().getPath(), "assets").toFile().getAbsolutePath());
        template.add("server_path", ctx.getDestination().getAbsolutePath());

        for(Map.Entry<String, String> entry : ctx.getProperties().entrySet()) {
            template.add(entry.getKey(), entry.getValue());
        }

        Map<String, String> versions = new HashMap<>();
        for (P3Package p3Package : ctx.getDependencyChain()) {
            versions.put(p3Package.getId(), p3Package.getVersion());
        }
        versions.put(p3.getId(), p3.getVersion());

        template.add("package_versions", versions);
    }

    private STUtils() {}
}
