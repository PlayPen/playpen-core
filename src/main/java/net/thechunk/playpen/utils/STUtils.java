package net.thechunk.playpen.utils;

import net.thechunk.playpen.Bootstrap;
import net.thechunk.playpen.p3.P3Package;
import net.thechunk.playpen.p3.PackageContext;
import org.stringtemplate.v4.ST;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class STUtils {

    public static void buildSTProperties(P3Package p3, PackageContext ctx, ST template) {
        template.add("package-id", p3.getId());
        template.add("package-version", p3.getVersion());
        template.add("resources", ctx.getResources());
        template.add("asset_path", Paths.get(Bootstrap.getHomeDir().getPath(), "assets").toFile().getAbsolutePath());

        for(Map.Entry<String, String> entry : ctx.getProperties().entrySet()) {
            template.add(entry.getKey(), entry.getValue());
        }
    }

    private STUtils() {}
}
