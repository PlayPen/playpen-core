package net.thechunk.playpen.p3.step;

import lombok.extern.log4j.Log4j2;
import net.thechunk.playpen.Bootstrap;
import net.thechunk.playpen.p3.ExecutionType;
import net.thechunk.playpen.p3.IPackageStep;
import net.thechunk.playpen.p3.P3Package;
import net.thechunk.playpen.p3.PackageContext;
import net.thechunk.playpen.utils.JSONUtils;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Log4j2
public class AssetPackageStep implements IPackageStep {
    @Override
    public String getStepId() {
        return "asset-package";
    }

    @Override
    public boolean runStep(PackageContext ctx, JSONObject config) {
        String id = config.getString("package-id");
        String version = config.getString("package-version");

        P3Package p3 = ctx.getPackageManager().resolve(id, version);
        if(p3 == null) {
            log.error("Unknown asset package " + id + " at " + version);
            return false;
        }

        File assetDir = Paths.get(Bootstrap.getHomeDir().getPath(), "assets", p3.getId() + "_" + p3.getVersion()).toFile();
        if(!assetDir.exists() || !assetDir.isDirectory()) {
            Map<String, String> props = new HashMap<>();
            props.put("mode", "asset");
            ctx.getPackageManager().execute(ExecutionType.PROVISION, p3, assetDir, props, null);
        }

        ctx.getProperties().put("asset_path", assetDir.getAbsolutePath());
        return true;
    }
}
