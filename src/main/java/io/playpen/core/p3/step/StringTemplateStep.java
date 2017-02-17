package io.playpen.core.p3.step;

import io.playpen.core.p3.IPackageStep;
import io.playpen.core.p3.P3Package;
import io.playpen.core.p3.PackageContext;
import io.playpen.core.utils.STUtils;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONObject;
import org.stringtemplate.v4.AutoIndentWriter;
import org.stringtemplate.v4.ST;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

@Log4j2
public class StringTemplateStep implements IPackageStep {
    @Override
    public String getStepId() {
        return "string-template";
    }

    @Override
    public boolean runStep(P3Package p3, PackageContext ctx, JSONObject config) {
        JSONArray jsonFiles = config.optJSONArray("files");
        if(jsonFiles == null) {
            log.error("'files' not defined as an array");
            return false;
        }

        File[] files = new File[jsonFiles.length()];
        for(int i = 0; i < jsonFiles.length(); ++i) {
            String fileName = jsonFiles.optString(i);
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
            String fileContents;
            try {
                fileContents = new String(Files.readAllBytes(file.toPath()));
            }
            catch(IOException e) {
                log.error("Unable to read file " + file.getPath(), e);
                return false;
            }

            log.info("Rendering " + file.getPath());

            ST template = new ST(fileContents);

            STUtils.buildSTProperties(p3, ctx, template);

            try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardOpenOption.TRUNCATE_EXISTING)) {
                template.write(new AutoIndentWriter(writer), Locale.US);
            }
            catch(IOException e) {
                log.error("Unable to write file " + file.getPath(), e);
                return false;
            }
        }

        return true;
    }
}
