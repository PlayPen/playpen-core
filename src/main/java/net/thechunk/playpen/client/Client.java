package net.thechunk.playpen.client;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.thechunk.playpen.Bootstrap;
import net.thechunk.playpen.coordinator.PlayPen;
import net.thechunk.playpen.networking.TransactionInfo;
import net.thechunk.playpen.networking.TransactionManager;
import net.thechunk.playpen.networking.netty.AuthenticatedMessageInitializer;
import net.thechunk.playpen.p3.PackageManager;
import net.thechunk.playpen.protocol.Commands;
import net.thechunk.playpen.protocol.P3;
import net.thechunk.playpen.protocol.Protocol;
import net.thechunk.playpen.utils.AuthUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Client is basically a "light" version of a local coordinator. It implements a local coordinator that
 * can never be enabled, and doesn't accept anything but client commands.
 */
@Log4j2
public class Client extends PlayPen {
    public static Client get() {
        if(PlayPen.get() == null) {
            new Client();
        }

        return (Client)PlayPen.get();
    }

    private ScheduledExecutorService scheduler = null;

    @Getter
    private String coordName;

    @Getter
    private String uuid;

    @Getter
    private String key;

    @Getter
    private Channel channel = null;

    @Getter
    private ClientMode clientMode = ClientMode.NONE;

    private Client() {
        super();
    }

    protected void printHelpText() {
        System.err.println("playpen cli <list/provision/deprovision> [arguments...]");
    }

    public void run(String[] arguments) {
        if(arguments.length < 2) {
            printHelpText();
            return;
        }

        log.info("Starting PlayPen Client");

        log.info("Reading local configuration");
        String configStr;
        try {
            configStr = new String(Files.readAllBytes(Paths.get(Bootstrap.getHomeDir().getPath(), "local.json")));
        }
        catch(IOException e) {
            log.fatal("Unable to read configuration file", e);
            return;
        }

        InetAddress coordIp = null;
        int coordPort = 0;

        try {
            JSONObject config = new JSONObject(configStr);
            coordName = config.getString("name");
            uuid = config.getString("uuid");
            key = config.getString("key");
            coordIp = InetAddress.getByName(config.getString("coord-ip"));
            coordPort = config.getInt("coord-port");
        }
        catch(Exception e) {
            log.fatal("Unable to read configuration file", e);
            return;
        }

        if(uuid == null || uuid.isEmpty() || key == null || key.isEmpty()) {
            log.fatal("No UUID or secret key specified in local.json");
            return;
        }

        if(coordName == null) {
            log.warn("No coordinator name specified in local.json"); // not fatal
        }

        log.info("Starting client " + uuid);
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            scheduler = Executors.newScheduledThreadPool(4);

            io.netty.bootstrap.Bootstrap b = new io.netty.bootstrap.Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new AuthenticatedMessageInitializer());

            ChannelFuture f = b.connect(coordIp, coordPort).await();

            if(!f.isSuccess()) {
                log.error("Unable to connect to network coordinator at " + coordIp + " port " + coordPort);
                System.err.println("Unable to connect to network coordinator at " + coordIp + " port " + coordPort);
                return;
            }

            channel = f.channel();

            log.info("Connected to network coordinator at " + coordIp + " port " + coordPort);

            runCommand(arguments);

            f.channel().closeFuture().sync();
        }
        catch(InterruptedException e) {
            log.warn("Operation interrupted!", e);
            return;
        }
        finally {
            scheduler.shutdownNow();
            scheduler = null;

            group.shutdownGracefully();
        }

        return;
    }

    @Override
    public void onVMShutdown() {
        log.info("VM shutting down, stopping all tasks");

        if(scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }

        if(channel != null && channel.isOpen()) {
            channel.close().syncUninterruptibly();
        }
    }

    @Override
    public String getServerId() {
        return coordName;
    }

    @Override
    public PackageManager getPackageManager() {
        return null;
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
        switch(command.getType()) {
            default:
                log.error("Client cannot process command " + command.getType());
                return false;

            case C_COORDINATOR_LIST_RESPONSE:
                return processListResponse(command.getCCoordinatorListResponse(), info);

            case C_PROVISION_RESPONSE:
                return processProvisionResponse(command.getCProvisionResponse(), info);
        }
    }

    protected void runCommand(String[] arguments) {
        if(arguments.length < 2) {
            printHelpText();
            channel.close();
            return;
        }


        if(!sendSync()) {
            log.error("Unable to SYNC");
            channel.close();
            return;
        }

        switch(arguments[1].toLowerCase()) {
            case "list":
                runListCommand(arguments);
                break;

            case "provision":
                runProvisionCommand(arguments);
                break;

            case "deprovision":
                runDeprovisionCommand(arguments);
                break;

            default:
                printHelpText();
                channel.close();
                break;
        }
    }

    protected void runListCommand(String[] arguments) {
        if(arguments.length != 2) {
            printHelpText();
            System.err.println("list");
            channel.close();
            return;
        }

        clientMode = ClientMode.LIST;
        if(!sendListRequest()) {
            log.error("Unable to send list request to coordinator");
            System.err.println("Unable to send list request to coordinator");
            channel.close();
            return;
        }

        System.out.println("Retrieving coordinator list...");
    }

    protected void runProvisionCommand(String[] arguments) {
        if(arguments.length != 3 && arguments.length != 4 && arguments.length != 5) {
            printHelpText();
            System.err.println("provision <package-id> [version] [coordinator-uuid]");
            System.err.println("If version is unspecified, 'promoted' will be used.");
            System.err.println("If coordinator-uuid is unspecified, the network will choose a coordinator.");
            channel.close();
            return;
        }

        clientMode = ClientMode.PROVISION;

        String id = arguments[2];
        String version = arguments.length >= 4 ? arguments[3] : "promoted";
        String coordinator = arguments.length == 5 ? arguments[4] : null;

        if(!sendProvision(id, version, coordinator)) {
            log.error("Unable to send provision to coordinator");
            System.err.println("Unable to send provision to coordinator");
            channel.close();
            return;
        }

        System.out.println("Waiting for provision response...");
    }

    protected void runDeprovisionCommand(String[] arguments) {
        if(arguments.length != 4 && arguments.length != 5) {
            printHelpText();
            System.err.println("deprovision <coordinator-uuid> <server-uuid> [force=false]");
            channel.close();
            return;
        }

        clientMode = ClientMode.SHUTDOWN_SERVER;

        String coordId = arguments[2];
        String serverId = arguments[3];
        boolean force = arguments.length == 5 ? (arguments[4].trim().toLowerCase().equals("true") ? true : false) : false; // dat nested ternary

        if(sendDeprovision(coordId, serverId, force)) {
            System.out.println("Sent deprovision to network");
            channel.close();
        }
        else {
            System.err.println("Unable to send deprovision to network");
            channel.close();
        }
    }

    protected boolean sendSync() {
        Commands.Sync.Builder syncBuilder = Commands.Sync.newBuilder()
                .setEnabled(false);

        if(coordName != null)
            syncBuilder.setName(coordName);

        Commands.Sync sync = syncBuilder.build();

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.SYNC)
                .setSync(sync)
                .build();

        TransactionInfo info = TransactionManager.get().begin();

        Protocol.Transaction message = TransactionManager.get()
                .build(info.getId(), Protocol.Transaction.Mode.SINGLE, command);
        if(message == null) {
            log.error("Unable to build message for sync");
            TransactionManager.get().cancel(info.getId());
            return false;
        }

        log.info("Sending SYNC to network coordinator");
        return TransactionManager.get().send(info.getId(), message, null);
    }

    protected boolean sendListRequest() {
        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.C_GET_COORDINATOR_LIST)
                .build();

        TransactionInfo info = TransactionManager.get().begin();

        Protocol.Transaction message = TransactionManager.get()
                .build(info.getId(), Protocol.Transaction.Mode.CREATE, command);
        if(message == null) {
            log.error("Unable to build message for coordinator list");
            TransactionManager.get().cancel(info.getId());
            return false;
        }

        log.info("Sending C_GET_COORDINATOR_LIST to network coordinator");
        return TransactionManager.get().send(info.getId(), message, null);
    }

    protected boolean processListResponse(Commands.C_CoordinatorListResponse response, TransactionInfo info) {
        log.info("Received C_COORDINATOR_LIST_RESPONSE");

        switch(clientMode) {
            case LIST:
                System.out.println(response.toString());
                channel.close();
                break;
        }

        return true;
    }

    protected boolean sendProvision(String id, String version, String coordinator) {
        P3.P3Meta meta = P3.P3Meta.newBuilder()
                .setId(id)
                .setVersion(version)
                .build();

        Commands.C_Provision.Builder provisionBuilder = Commands.C_Provision.newBuilder()
                .setP3(meta);
        if(coordinator != null) {
            provisionBuilder.setCoordinator(coordinator);
        }

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.C_PROVISION)
                .setCProvision(provisionBuilder.build())
                .build();

        TransactionInfo info = TransactionManager.get().begin();

        Protocol.Transaction message = TransactionManager.get()
                .build(info.getId(), Protocol.Transaction.Mode.CREATE, command);
        if(message == null) {
            log.error("Unable to build message for provision");
            TransactionManager.get().cancel(info.getId());
            return false;
        }

        log.info("Sending C_PROVISION to network coordinator");
        return TransactionManager.get().send(info.getId(), message, null);
    }

    protected boolean processProvisionResponse(Commands.C_ProvisionResponse response, TransactionInfo info) {
        switch(clientMode) {
            case PROVISION:
                log.info("Provision response: " + (response.getOk() ? "ok" : "not ok"));
                if(response.getOk()) {
                    System.out.println("Provision operation succeeded");
                    System.out.println("Coordinator: " + response.getCoordinatorId());
                    System.out.println("Server: " + response.getServerId());
                }
                else {
                    System.err.println("Provision operation unsuccessful");
                }
                channel.close();
                break;
        }

        return true;
    }

    protected boolean sendDeprovision(String coordId, String serverId, boolean force) {
        Commands.C_Deprovision shutdown = Commands.C_Deprovision.newBuilder()
                .setCoordinatorId(coordId)
                .setServerId(serverId)
                .setForce(force)
                .build();

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.C_DEPROVISION)
                .setCDeprovision(shutdown)
                .build();

        TransactionInfo info = TransactionManager.get().begin();

        Protocol.Transaction message = TransactionManager.get()
                .build(info.getId(), Protocol.Transaction.Mode.SINGLE, command);
        if(message == null) {
            log.error("Unable to build message for deprovision");
            TransactionManager.get().cancel(info.getId());
            return false;
        }

        log.info("Sending C_DEPROVISION to network coordinator");
        return TransactionManager.get().send(info.getId(), message, null);
    }
}
