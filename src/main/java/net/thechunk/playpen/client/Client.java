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
        System.err.println("playpen cli <command> [arguments...]");
        System.err.println("Commands: list, provision, deprovision, shutdown, promote, generate-keypair");
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

            case C_COORDINATOR_CREATED:
                return processCoordinatorCreated(command.getCCoordinatorCreated(), info);
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

            case "shutdown":
                runShutdownCommand(arguments);
                break;

            case "promote":
                runPromoteCommand(arguments);
                break;

            case "generate-keypair":
                runGenerateKeypairCommand(arguments);
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
            System.err.println("Retrieves and displays the list of all active coordinators, their configurations, and active servers.");
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
            System.err.println("Provisions a server on the network.");
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
            System.err.println("Deprovisions a server from the network.");
            channel.close();
            return;
        }

        clientMode = ClientMode.DEPROVISION;

        String coordId = arguments[2];
        String serverId = arguments[3];
        boolean force = arguments.length == 5 ? (arguments[4].trim().toLowerCase().equals("true") ? true : false) : false; // dat nested ternary

        if(force) {
            System.out.println("NOTE: forcing deprovision operation");
        }

        if(sendDeprovision(coordId, serverId, force)) {
            System.out.println("Sent deprovision to network");
            channel.close();
        }
        else {
            System.err.println("Unable to send deprovision to network");
            channel.close();
        }
    }

    protected void runShutdownCommand(String[] arguments) {
        if(arguments.length != 3) {
            printHelpText();
            System.err.println("shutdown <coordinator-uuid>");
            System.err.println("Shuts down a coordinator and any related servers.");
            channel.close();
            return;
        }

        clientMode = ClientMode.SHUTDOWN;

        String coordId = arguments[2];

        if(sendShutdown(coordId)) {
            System.out.println("Sent shutdown to network");
            channel.close();
        }
        else {
            System.err.println("Unable to send shutdown to network");
            channel.close();
        }
    }

    protected void runPromoteCommand(String[] arguments) {
        if(arguments.length != 4) {
            printHelpText();
            System.err.println("promote <package-id> <package-version>");
            System.err.println("Promotes a package.");
            channel.close();
            return;
        }

        clientMode = ClientMode.PROMOTE;

        String id = arguments[2];
        String version = arguments[3];
        if(version.equals("promoted")) {
            System.err.println("Cannot promote a package of version 'promoted'");
            channel.close();
            return;
        }

        if(sendPromote(id, version)) {
            System.out.println("Sent promote to network");
            channel.close();
        }
        else {
            System.err.println("Unable to send promote to network");
            channel.close();
        }
    }

    protected void runGenerateKeypairCommand(String[] arguments) {
        if(arguments.length != 2) {
            printHelpText();
            System.err.println("generate-keypair");
            System.err.println("Generates a new coordinator keypair");
            channel.close();
            return;
        }

        clientMode = ClientMode.GENERATE_KEYPAIR;

        if(sendCreateCoordinator()) {
            System.out.println("Requesting generation of new keypair...");
            // don't close channel, just wait
        }
        else {
            System.err.println("Unable to send generation request to network");
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
                return true;
        }

        return false;
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
                return true;
        }

        return false;
    }

    protected boolean sendDeprovision(String coordId, String serverId, boolean force) {
        Commands.C_Deprovision deprovision = Commands.C_Deprovision.newBuilder()
                .setCoordinatorId(coordId)
                .setServerId(serverId)
                .setForce(force)
                .build();

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.C_DEPROVISION)
                .setCDeprovision(deprovision)
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

    protected boolean sendShutdown(String coordId) {
        Commands.C_Shutdown shutdown = Commands.C_Shutdown.newBuilder()
                .setUuid(coordId)
                .build();

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.C_SHUTDOWN)
                .setCShutdown(shutdown)
                .build();

        TransactionInfo info = TransactionManager.get().begin();

        Protocol.Transaction message = TransactionManager.get()
                .build(info.getId(), Protocol.Transaction.Mode.SINGLE, command);
        if(message == null) {
            log.error("Unable to build message for shutdown");
            TransactionManager.get().cancel(info.getId());
            return false;
        }

        log.info("Sending C_SHUTDOWN to network coordinator");
        return TransactionManager.get().send(info.getId(), message, null);
    }

    protected boolean sendPromote(String id, String version) {
        Commands.C_Promote promote = Commands.C_Promote.newBuilder()
                .setP3(P3.P3Meta.newBuilder().setId(id).setVersion(version).build())
                .build();

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.C_PROMOTE)
                .setCPromote(promote)
                .build();

        TransactionInfo info = TransactionManager.get().begin();

        Protocol.Transaction message = TransactionManager.get()
                .build(info.getId(), Protocol.Transaction.Mode.SINGLE, command);
        if(message == null) {
            log.error("Unable to build message for promote");
            TransactionManager.get().cancel(info.getId());
            return false;
        }

        log.info("Sending C_PROMOTE to network coordinator");
        return TransactionManager.get().send(info.getId(), message, null);
    }

    protected boolean sendCreateCoordinator() {
        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.C_CREATE_COORDINATOR)
                .build();

        TransactionInfo info = TransactionManager.get().begin();

        Protocol.Transaction message = TransactionManager.get()
                .build(info.getId(), Protocol.Transaction.Mode.CREATE, command);
        if(message == null) {
            log.error("Unable to build message for create coordinator");
            TransactionManager.get().cancel(info.getId());
            return false;
        }

        log.info("Sending C_CREATE_COORDINATOR to network coordinator");
        return TransactionManager.get().send(info.getId(), message, null);
    }

    protected boolean processCoordinatorCreated(Commands.C_CoordinatorCreated response, TransactionInfo info) {
        switch(clientMode) {
            case GENERATE_KEYPAIR:
                log.info("Received C_COORDINATOR_CREATED uuid = " + response.getUuid() + ", key = " + response.getKey());
                System.out.println("Keypair generation successful:");
                System.out.println("UUID: " + response.getUuid());
                System.out.println("Key: " + response.getKey());
                channel.close();
                return true;
        }

        return false;
    }
}
