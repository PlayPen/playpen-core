package net.thechunk.playpen.p3.provision;

import net.thechunk.playpen.Bootstrap;
import net.thechunk.playpen.p3.IProvisioningStep;
import net.thechunk.playpen.p3.PackageContext;
import net.thechunk.playpen.utils.JSONUtils;
import net.thechunk.playpen.utils.STUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.stringtemplate.v4.ST;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public class StringTemplateStep implements IProvisioningStep {
    private static final Logger logger = LogManager.getLogger(StringTemplateStep.class);

    @Override
    public String getStepId() {
        return "string-template";
    }

    @Override
    public boolean runStep(PackageContext ctx, JSONObject config) {
        JSONArray jsonFiles = JSONUtils.safeGetArray(config, "files");
        if(jsonFiles == null) {
            logger.error("'files' not defined as an array");
            return false;
        }

        File[] files = new File[jsonFiles.length()];
        for(int i = 0; i < jsonFiles.length(); ++i) {
            String fileName = JSONUtils.safeGetString(jsonFiles, i);
            if(fileName == null) {
                logger.error("Unable to read files entry #" + i);
                return false;
            }

            File file = Paths.get(ctx.getDestination().getPath(), fileName).toFile();
            if(!file.exists()) {
                logger.error("File does not exist: " + file.getPath());
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
                logger.error("Unable to read file " + file.getPath(), e);
                return false;
            }

            logger.info("Rendering " + file.getPath());

            ST template = new ST(fileContents);

            STUtils.buildSTProperties(ctx.getP3(), template);

            String rendered = template.render();

            FileOutputStream output = null;
            try {
                output = new FileOutputStream(file, false);
                output.write(rendered.getBytes());
            }
            catch(IOException e) {
                logger.error("Unable to write file " + file.getPath(), e);
                return false;
            }
            finally {
                try {
                    if (output != null)
                        output.close();
                }
                catch(IOException e) {}
            }
        }

        return true;
    }
}
