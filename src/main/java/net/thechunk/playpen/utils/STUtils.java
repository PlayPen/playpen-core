package net.thechunk.playpen.utils;

import net.thechunk.playpen.p3.P3Package;
import org.stringtemplate.v4.ST;

import java.util.HashMap;
import java.util.Map;

public class STUtils {

    public static void buildSTProperties(P3Package p3, Map<String, String> properties, ST template) {
        template.add("package-id", p3.getId());
        template.add("package-version", p3.getVersion());
        template.add("resources", p3.getResources());

        Map<String, String> finalProps = new HashMap<>();
        finalProps.putAll(p3.getStrings());
        if(properties != null) {
            finalProps.putAll(properties);
        }

        for(Map.Entry<String, String> entry : finalProps.entrySet()) {
            template.add(entry.getKey(), entry.getValue());
        }
    }

    private STUtils() {}
}
