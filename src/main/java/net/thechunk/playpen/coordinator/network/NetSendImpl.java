package net.thechunk.playpen.coordinator.network;

import lombok.extern.log4j.Log4j2;
import net.thechunk.playpen.coordinator.PlayPen;
import net.thechunk.playpen.p3.P3Package;
import net.thechunk.playpen.protocol.Commands;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

@Log4j2
public abstract class NetSendImpl extends PlayPen {
    public boolean sendPackage(String target, String id, String version) {
        P3Package p3 = getPackageManager().resolve(id, version);
        if(p3 == null) {
            log.error("Unable to send unresolved package " + id + " at " + version);
            return false;
        }

        return sendPackage(target, p3);
    }

    public boolean sendPackage(String target, P3Package p3) {
        throw new NotImplementedException();
    }
}
