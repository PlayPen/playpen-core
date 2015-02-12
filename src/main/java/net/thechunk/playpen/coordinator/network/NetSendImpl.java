package net.thechunk.playpen.coordinator.network;

import com.google.protobuf.ByteString;
import lombok.extern.log4j.Log4j2;
import net.thechunk.playpen.coordinator.PlayPen;
import net.thechunk.playpen.networking.TransactionInfo;
import net.thechunk.playpen.networking.TransactionManager;
import net.thechunk.playpen.p3.P3Package;
import net.thechunk.playpen.protocol.Commands;
import net.thechunk.playpen.protocol.P3;
import net.thechunk.playpen.protocol.Protocol;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Log4j2
public abstract class NetSendImpl extends PlayPen {
    protected NetSendImpl() {
        super();
    }

    public boolean sendPackage(String target, String transaction, String id, String version) {
        P3Package p3 = getPackageManager().resolve(id, version);
        if(p3 == null) {
            log.error("Unable to send unresolved package " + id + " at " + version);
            return false;
        }

        return sendPackage(target, transaction, p3);
    }

    public boolean sendPackage(String target, String transaction, P3Package p3) {
        if(!p3.isResolved()) {
            log.error("Cannot pass an unresolved package to sendPackage(target, p3)");
            return false;
        }

        TransactionInfo info = TransactionManager.get().getInfo(transaction);
        if(info == null) {
            log.error("Unknown transaction " + transaction + ", unable to send package");
            return false;
        }

        P3.P3Meta meta = P3.P3Meta.newBuilder()
                .setId(p3.getId())
                .setVersion(p3.getVersion())
                .build();

        byte[] packageBytes = null;
        try {
            packageBytes = Files.readAllBytes(Paths.get(p3.getLocalPath()));
        }
        catch(IOException e) {
            log.error("Unable to read package data", e);
            return false;
        }

        P3.PackageData data = P3.PackageData.newBuilder()
                .setMeta(meta)
                .setData(ByteString.copyFrom(packageBytes))
                .build();

        Commands.PackageResponse response = Commands.PackageResponse.newBuilder()
                .setOk(true)
                .setData(data)
                .build();

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.PACKAGE_RESPONSE)
                .setExtension(Commands.PackageResponse.command, response)
                .build();

        Protocol.Transaction message = TransactionManager.get()
                .build(info.getId(), Protocol.Transaction.Mode.COMPLETE, command);
        if(message == null) {
            log.error("Unable to build transaction for package response");
            return false;
        }

        return send(message, target);
    }
}
