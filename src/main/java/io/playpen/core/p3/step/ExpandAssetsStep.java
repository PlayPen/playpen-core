package io.playpen.core.p3.step;

import io.playpen.core.Bootstrap;
import io.playpen.core.p3.IPackageStep;
import io.playpen.core.p3.P3Package;
import io.playpen.core.p3.PackageContext;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.zeroturnaround.zip.ZipException;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.nio.file.Paths;

@Log4j2
public class ExpandAssetsStep implements IPackageStep {
    @Override
    public String getStepId() {
        return "expand-assets";
    }

    @Override
    public boolean runStep(P3Package p3, PackageContext ctx, JSONObject config) {
        File path = Paths.get(Bootstrap.getHomeDir().getPath(), "assets", p3.getId(), p3.getVersion()).toFile();
        if(path.exists() && path.isDirectory()) {
            log.info("Not expanding asset package (already exists)");
            return true;
        }

        log.info("Expanding asset package to " + path.getPath());
        try {
            ZipUtil.unpack(new File(p3.getLocalPath()), path);
        }
        catch(ZipException e) {
            log.error("Unable to expand package", e);
            return false;
        }

        return true;
    }
}