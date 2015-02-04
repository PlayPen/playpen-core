package net.thechunk.playpen;

import net.thechunk.playpen.p3.*;

import java.io.File;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class P3Tool {

    private static final Logger logger = Logger.getLogger(P3Tool.class.getName());

    public static void execute(String[] args) {
        if(args.length < 2) {
            logger.severe("P3Tool requires a command: inspect");
            return;
        }

        switch(args[1].toLowerCase()) {
            case "inspect":
                inspect(args);
                break;
        }
    }

    private static void inspect(String[] args) {
        if(args.length != 3) {
            logger.severe("P3Tool inspect takes 1 argument (package)");
            return;
        }

        PackageManager pm = new PackageManager();
        Initialization.packageManager(pm);

        File p3File = new File(args[2]);
        if(!p3File.exists() || !p3File.isFile()) {
            logger.severe("Package doesn't exist or isn't a file!");
            return;
        }

        P3Package p3 = null;

        try {
            p3 = pm.readPackage(p3File);
        }
        catch(PackageException e) {
            logger.log(Level.SEVERE, "Unable to read package", e);
            return;
        }

        logger.info("Package:");
        logger.info("Id:" + p3.getId());
        logger.info("Version: " + p3.getVersion());

        if(p3.getParent() != null) {
            logger.info("Parent id:" + p3.getParent().getId());
            logger.info("Parent version: " + p3.getVersion());
            logger.info("Note: Validation does not resolve parent packages");
        }
        else {
            logger.info("Parent: None");
        }

        if(p3.getResources().size() == 0) {
            logger.warning("Package doesn't take any resources");
        }
        else {
            for (Map.Entry<String, Double> resource : p3.getResources().entrySet()) {
                logger.info("Resource: " + resource.getKey() + " = " + resource.getValue());
            }
        }

        if(p3.getAttributes().size() == 0) {
            logger.warning("Package doesn't require any attributes");
        }
        else {
            for(String attr : p3.getAttributes()) {
                logger.info("Requires: " + attr);
            }
        }

        for(Map.Entry<String, String> str : p3.getStrings().entrySet()) {
            logger.info("String: " + str.getKey() + " = " + str.getValue());
        }

        if(p3.getProvisioningSteps().size() == 0) {
            logger.warning("Package doesn't define any provisioning steps");
        }
        else {
            for(P3Package.ProvisioningStepConfig config : p3.getProvisioningSteps()) {
                logger.info("Provisioning step: " + config.getStep().getStepId());
            }
        }

        if(p3.getExecutionSteps().size() == 0) {
            logger.warning("Package doesn't define any execution steps");
        }
        else {
            for(String execute : p3.getExecutionSteps()) {
                logger.info("Execution step: " + execute);
            }
        }

        logger.info("Package inspection completed!");
    }

    private P3Tool() {}
}
