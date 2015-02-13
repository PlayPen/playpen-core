package net.thechunk.playpen.utils;

import net.thechunk.playpen.p3.P3Package;
import org.stringtemplate.v4.ST;

import java.util.Map;

public class STUtils {

    public static void buildSTProperties(P3Package p3, Map<String, String> properties, ST template) {
        template.add("package-id", p3.getId());
        template.add("package-version", p3.getVersion());
        template.add("resources", p3.getResources());

        for(Map.Entry<String, String> entry : p3.getStrings().entrySet()) {
            template.add(entry.getKey(), entry.getValue());
        }

        if(properties != null) {
            for(Map.Entry<String, String> entry : properties.entrySet()) {
                template.add(entry.getKey(), entry.getValue());
            }
        }
    }

    private STUtils() {}
}
