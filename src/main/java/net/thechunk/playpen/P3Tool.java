package net.thechunk.playpen;

import net.thechunk.playpen.p3.*;

import java.io.File;
import java.util.Map;
import java.util.logging.Level;

public class P3Tool {

    public static void execute(String[] args) {
        if(args.length < 2) {
            System.err.println("playpen p3 <inspect> [arguments...]");
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
            System.err.println("playpen p3 inspect <file>");
            return;
        }

        PackageManager pm = new PackageManager();
        Initialization.packageManager(pm);

        File p3File = new File(args[2]);
        if(!p3File.exists() || !p3File.isFile()) {
            System.err.println("Package doesn't exist or isn't a file!");
            return;
        }

        P3Package p3 = null;

        try {
            p3 = pm.readPackage(p3File);
        }
        catch(PackageException e) {
            System.err.println("Unable to read package");
            e.printStackTrace(System.err);
            return;
        }

        System.out.println("=== Package ===");
        System.out.println("Id: " + p3.getId());
        System.out.println("Version: " + p3.getVersion());

        if(p3.getParent() != null) {
            System.out.println("Parent id: " + p3.getParent().getId());
            System.out.println("Parent version: " + p3.getParent().getVersion());
            System.out.println("-- Note: Validation does not resolve parent packages");
        }
        else {
            System.out.println("Parent: None");
        }

        if(p3.getResources().size() == 0) {
            System.err.println("-- Package doesn't take any resources");
        }
        else {
            for (Map.Entry<String, Double> resource : p3.getResources().entrySet()) {
                System.out.println("Resource: " + resource.getKey() + " = " + resource.getValue());
            }
        }

        if(p3.getAttributes().size() == 0) {
            System.err.println("-- Package doesn't require any attributes");
        }
        else {
            for(String attr : p3.getAttributes()) {
                System.out.println("Requires: " + attr);
            }
        }

        for(Map.Entry<String, String> str : p3.getStrings().entrySet()) {
            System.out.println("String: " + str.getKey() + " = " + str.getValue());
        }

        if(p3.getProvisioningSteps().size() == 0) {
            System.err.println("-- Package doesn't define any provisioning steps");
        }
        else {
            for(P3Package.ProvisioningStepConfig config : p3.getProvisioningSteps()) {
                System.out.println("Provisioning step: " + config.getStep().getStepId());
            }
        }

        if(p3.getExecutionSteps().size() == 0) {
            System.err.println("-- Package doesn't define any execution steps");
        }
        else {
            for(String execute : p3.getExecutionSteps()) {
                System.out.println("Execution step: " + execute);
            }
        }

        System.out.println("=== End Package ===");
    }

    private P3Tool() {}
}
