package net.thechunk.playpen.coordinator.local;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.channel.Channel;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.thechunk.playpen.Initialization;
import net.thechunk.playpen.coordinator.PlayPen;
import net.thechunk.playpen.networking.TransactionInfo;
import net.thechunk.playpen.networking.TransactionManager;
import net.thechunk.playpen.p3.PackageManager;
import net.thechunk.playpen.protocol.Commands;
import net.thechunk.playpen.protocol.Protocol;
import net.thechunk.playpen.utils.AuthUtils;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Log4j2
public class Local extends PlayPen {
    public static Local get() {
        if(PlayPen.get() == null) {
            new Local();
        }

        return (Local)PlayPen.get();
    }

    private Map<String, Server> servers = new ConcurrentHashMap<>();

    private PackageManager packageManager = null;

    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    @Getter
    private String serverName;

    @Getter
    private String uuid;

    @Getter
    private String key;

    @Getter
    private boolean enabled = true;

    @Getter
    private Channel channel = null;

    private Local() {
        super();
        packageManager = new PackageManager();
        Initialization.packageManager(packageManager);
    }

    public boolean run() {
        throw new NotImplementedException();
    }

    public Server getServer(String id) {
        return servers.getOrDefault(id, null);
    }

    @Override
    public String getServerId() {
        return serverName;
    }

    @Override
    public PackageManager getPackageManager() {
        return packageManager;
    }

    @Override
    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    @Override
    public boolean send(Protocol.Transaction message, String target) {
        if(channel == null || !channel.isActive()) {
            log.error("Unable to send transaction " + message.getId() + " as the channel is invalid.");
            return false;
        }

        if(!message.isInitialized()) {
            log.error("Transaction is not initialized (protobuf)");
            return false;
        }

        byte[] messageBytes = message.toByteArray();
        Protocol.AuthenticatedMessage auth = Protocol.AuthenticatedMessage.newBuilder()
                .setUuid(uuid)
                .setHash(AuthUtils.createHash(key, messageBytes))
                .setPayload(ByteString.copyFrom(messageBytes))
                .build();

        if(!auth.isInitialized()) {
            log.error("Message is not initialized (protobuf)");
            return false;
        }

        channel.writeAndFlush(auth);
        return true;
    }

    @Override
    public boolean receive(Protocol.AuthenticatedMessage auth, Channel from) {
        if(!auth.getUuid().equals(uuid) || !AuthUtils.validateHash(auth, key)) {
            log.error("Invalid hash on message");
            return false;
        }

        Protocol.Transaction transaction = null;
        try {
            transaction = Protocol.Transaction.parseFrom(auth.getPayload());
        }
        catch(InvalidProtocolBufferException e) {
            log.error("Unable to read transaction from message", e);
            return false;
        }

        TransactionManager.get().receive(transaction, null);
        return true;
    }

    @Override
    public boolean process(Commands.BaseCommand command, TransactionInfo info, String from) {
        throw new NotImplementedException(); // TODO
    }

    protected boolean sendSync() {
        Commands.Sync.Builder syncBuilder = Commands.Sync.newBuilder()
                .setEnabled(enabled);

        if(serverName != null)
            syncBuilder.setName(serverName);

        throw new NotImplementedException(); // TODO
    }
}
