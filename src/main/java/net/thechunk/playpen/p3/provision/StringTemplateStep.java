package net.thechunk.playpen.p3.provision;

import lombok.extern.log4j.Log4j2;
import net.thechunk.playpen.p3.IProvisioningStep;
import net.thechunk.playpen.p3.PackageContext;
import net.thechunk.playpen.utils.JSONUtils;
import net.thechunk.playpen.utils.STUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.stringtemplate.v4.ST;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Log4j2
public class StringTemplateStep implements IProvisioningStep {
    @Override
    public String getStepId() {
        return "string-template";
    }

    @Override
    public boolean runStep(PackageContext ctx, JSONObject config) {
        JSONArray jsonFiles = JSONUtils.safeGetArray(config, "files");
        if(jsonFiles == null) {
            log.error("'files' not defined as an array");
            return false;
        }

        File[] files = new File[jsonFiles.length()];
        for(int i = 0; i < jsonFiles.length(); ++i) {
            String fileName = JSONUtils.safeGetString(jsonFiles, i);
            if(fileName == null) {
                log.error("Unable to read files entry #" + i);
                return false;
            }

            File file = Paths.get(ctx.getDestination().getPath(), fileName).toFile();
            if(!file.exists()) {
                log.error("File does not exist: " + file.getPath());
                return false;
            }

            files[i] = file;
        }

        for(File file : files) {
            String fileContents = null;
            try {
                fileContents = new String(Files.readAllBytes(file.toPath()));
            }
            catch(IOException e) {
                log.error("Unable to read file " + file.getPath(), e);
                return false;
            }

            log.info("Rendering " + file.getPath());

            ST template = new ST(fileContents);

            STUtils.buildSTProperties(ctx.getP3(), template);

            String rendered = template.render();

            try (FileOutputStream output = new FileOutputStream(file, false)) {
                output.write(rendered.getBytes());
            }
            catch(IOException e) {
                log.error("Unable to write file " + file.getPath(), e);
                return false;
            }
        }

        return true;
    }
}
