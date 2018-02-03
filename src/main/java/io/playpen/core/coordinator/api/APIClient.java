package io.playpen.core.coordinator.api;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.playpen.core.coordinator.CoordinatorMode;
import io.playpen.core.coordinator.PlayPen;
import io.playpen.core.networking.TransactionInfo;
import io.playpen.core.networking.TransactionManager;
import io.playpen.core.networking.netty.AuthenticatedMessageInitializer;
import io.playpen.core.p3.PackageManager;
import io.playpen.core.plugin.PluginManager;
import io.playpen.core.protocol.Commands;
import io.playpen.core.protocol.Protocol;
import io.playpen.core.utils.AuthUtils;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Log4j2
public abstract class APIClient extends PlayPen {
    private ScheduledExecutorService scheduler = null;

    @Getter
    private Channel channel = null;

    protected APIClient() {
        super();
    }

    public boolean start() {
        log.info("Starting client " + getUUID());
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            scheduler = Executors.newScheduledThreadPool(1);

            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new AuthenticatedMessageInitializer());

            ChannelFuture f = b.connect(getNetworkIP(), getNetworkPort()).await();

            if (!f.isSuccess()) {
                log.error("Unable to connect to network coordinator at " + getNetworkIP() + " port " + getNetworkPort());
                return false;
            }

            channel = f.channel();

            log.info("Connected to network coordinator at " + getNetworkIP() + " port " + getNetworkPort());
        } catch (InterruptedException e) {
            log.warn("Operation interrupted!", e);
            return false;
        }

        return true;
    }

    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }

        if (channel != null) {
            channel.close().syncUninterruptibly();
        }
    }

    @Override
    public void onVMShutdown() {
        log.info("VM shutting down, stopping all tasks");

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }

        if (channel != null) {
            channel.close().syncUninterruptibly();
        }
    }

    @Override
    public String getServerId() {
        return getName();
    }

    @Override
    public CoordinatorMode getCoordinatorMode() {
        return CoordinatorMode.CLIENT;
    }

    @Override
    public PackageManager getPackageManager() {
        return null;
    }

    @Override
    public PluginManager getPluginManager() {
        log.error("PlayPen API client does not currently support the plugin system!");
        return null;
    }

    @Override
    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    public boolean isConnected() {
        return channel != null && channel.isActive();
    }

    public abstract String getName();

    public abstract String getUUID();

    public abstract String getKey();

    public abstract InetAddress getNetworkIP();

    public abstract int getNetworkPort();

    @Override
    public boolean send(Protocol.Transaction message, String target) {
        if (channel == null || !channel.isActive()) {
            log.error("Unable to send transaction " + message.getId() + " as the channel is invalid.");
            return false;
        }

        if (!message.isInitialized()) {
            log.error("Transaction is not initialized (protobuf)");
            return false;
        }

        ByteString messageBytes = message.toByteString();
        byte[] encBytes = AuthUtils.encrypt(messageBytes.toByteArray(), getKey());
        String hash = AuthUtils.createHash(getKey(), encBytes);
        messageBytes = ByteString.copyFrom(encBytes);

        Protocol.AuthenticatedMessage auth = Protocol.AuthenticatedMessage.newBuilder()
                .setUuid(getUUID())
                .setVersion(io.playpen.core.Bootstrap.getProtocolVersion())
                .setHash(hash)
                .setPayload(messageBytes)
                .build();

        if (!auth.isInitialized()) {
            log.error("Message is not initialized (protobuf)");
            return false;
        }

        channel.writeAndFlush(auth);
        return true;
    }

    @Override
    public boolean receive(Protocol.AuthenticatedMessage auth, Channel from) {
        if (!auth.getUuid().equalsIgnoreCase(getUUID()) || !AuthUtils.validateHash(auth, getKey())) {
            log.error("Invalid hash on message");
            return false;
        }

        ByteString payload = auth.getPayload();
        byte[] payloadBytes = AuthUtils.decrypt(payload.toByteArray(), getKey());
        payload = ByteString.copyFrom(payloadBytes);

        Protocol.Transaction transaction = null;
        try {
            transaction = Protocol.Transaction.parseFrom(payload);
        } catch (InvalidProtocolBufferException e) {
            log.error("Unable to read transaction from message", e);
            return false;
        }

        TransactionManager.get().receive(transaction, null);
        return true;
    }

    @Override
    public boolean process(Commands.BaseCommand command, TransactionInfo info, String from) {
        switch(command.getType()) {
            default:
                log.error("Client cannot process command " + command.getType());
                return false;

            case C_COORDINATOR_LIST_RESPONSE:
                return processListResponse(command.getCCoordinatorListResponse(), info);

            case C_PROVISION_RESPONSE:
                return processProvisionResponse(command.getCProvisionResponse(), info);

            case C_COORDINATOR_CREATED:
                return processCoordinatorCreated(command.getCCoordinatorCreated(), info);

            case C_CONSOLE_MESSAGE:
                return processConsoleMessage(command.getCConsoleMessage(), info);

            case C_CONSOLE_ATTACHED:
                return processConsoleAttached(command.getCConsoleAttached(), info);

            case C_DETACH_CONSOLE:
                return processDetachConsole(command.getCConsoleDetached(), info);

            case C_ACK:
                return processAck(command.getCAck(), info);

            case C_PACKAGE_LIST:
                return processPackageList(command.getCPackageList(), info);

            case C_ACCESS_DENIED:
                return processAccessDenied(command.getCAccessDenied(), info);

            case PACKAGE_RESPONSE:
                return processPackageResponse(command.getPackageResponse(), info);
        }
    }

    public abstract boolean processProvisionResponse(Commands.C_ProvisionResponse response, TransactionInfo info);
    public abstract boolean processCoordinatorCreated(Commands.C_CoordinatorCreated response, TransactionInfo info);
    public abstract boolean processConsoleMessage(Commands.C_ConsoleMessage message, TransactionInfo info);
    public abstract boolean processDetachConsole(Commands.C_ConsoleDetached message, TransactionInfo info);
    public abstract boolean processConsoleAttached(Commands.C_ConsoleAttached message, TransactionInfo info);
    public abstract boolean processListResponse(Commands.C_CoordinatorListResponse message, TransactionInfo info);
    public abstract boolean processAck(Commands.C_Ack message, TransactionInfo info);
    public abstract boolean processPackageList(Commands.C_PackageList message, TransactionInfo info);
    public abstract boolean processAccessDenied(Commands.C_AccessDenied message, TransactionInfo info);
    public abstract boolean processPackageResponse(Commands.PackageResponse response, TransactionInfo info);
}
