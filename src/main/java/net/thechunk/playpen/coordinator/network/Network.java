package net.thechunk.playpen.coordinator.network;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.channel.Channel;
import lombok.extern.log4j.Log4j2;
import net.thechunk.playpen.coordinator.PlayPen;
import net.thechunk.playpen.networking.TransactionManager;
import net.thechunk.playpen.protocol.Protocol;
import net.thechunk.playpen.utils.AuthUtils;

import java.util.HashMap;
import java.util.Map;

@Log4j2
public class Network extends PlayPen {

    public static Network get() {
        if(PlayPen.get() == null) {
            new Network();
        }

        return (Network)PlayPen.get();
    }

    private Map<String, LocalCoordinator> coordinators = new HashMap<>();

    private Network() {
        super();
    }

    public LocalCoordinator getCoordinator(String id) {
        return coordinators.getOrDefault(id, null);
    }

    @Override
    public String getServerId() {
        return "net";
    }

    @Override
    public boolean send(Protocol.Transaction message, String target) {
        LocalCoordinator coord = getCoordinator(target);
        if(coord == null) {
            log.error("Unable to send transaction " + message.getId() + ": target " + target + " is invalid");
            return false;
        }

        if(coord.getChannel() == null || !coord.getChannel().isActive()) {
            log.error("Coordinator channel is null or inactive (" + target + ")");
            return false;
        }

        if(!message.isInitialized()) {
            log.error("Transaction is not initialized (protobuf)");
            return false;
        }

        byte[] messageBytes = message.toByteArray();
        Protocol.AuthenticatedMessage auth = Protocol.AuthenticatedMessage.newBuilder()
                .setUuid(coord.getUuid())
                .setHash(AuthUtils.createHash(coord.getKey(), messageBytes))
                .setPayload(ByteString.copyFrom(messageBytes))
                .build();

        if(!auth.isInitialized()) {
            log.error("Message is not initialized (protobuf)");
            return false;
        }

        coord.getChannel().writeAndFlush(auth);
        return true;
    }

    @Override
    public boolean receive(Protocol.AuthenticatedMessage auth, Channel from) {
        LocalCoordinator local = getCoordinator(auth.getUuid());
        if(local == null) {
            log.error("Unknown coordinator on receive (" + auth.getUuid() + ")");
            return false;
        }

        if(!AuthUtils.validateHash(auth, local.getKey())) {
            log.error("Invalid hash on message from " + auth.getUuid());
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

        TransactionManager.get().receive(transaction, local.getUuid());
        return true;
    }
}
