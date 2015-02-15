package net.thechunk.playpen.p3.step;

import lombok.extern.log4j.Log4j2;
import net.thechunk.playpen.p3.IPackageStep;
import net.thechunk.playpen.p3.PackageContext;
import org.json.JSONObject;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

@Log4j2
public class ExecuteStep implements IPackageStep {
    @Override
    public String getStepId() {
        return "execute";
    }

    @Override
    public boolean runStep(PackageContext ctx, JSONObject config) {
        throw new NotImplementedException();
    }
}
