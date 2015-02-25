package net.thechunk.playpen.p3.step;

import lombok.extern.log4j.Log4j2;
import net.thechunk.playpen.Bootstrap;
import net.thechunk.playpen.p3.IPackageStep;
import net.thechunk.playpen.p3.P3Package;
import net.thechunk.playpen.p3.PackageContext;
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