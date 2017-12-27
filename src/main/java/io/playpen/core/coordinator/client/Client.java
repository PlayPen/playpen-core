package io.playpen.core.coordinator.client;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.playpen.core.Bootstrap;
import io.playpen.core.Initialization;
import io.playpen.core.coordinator.CoordinatorMode;
import io.playpen.core.coordinator.PlayPen;
import io.playpen.core.networking.TransactionInfo;
import io.playpen.core.networking.TransactionManager;
import io.playpen.core.networking.netty.AuthenticatedMessageInitializer;
import io.playpen.core.p3.P3Package;
import io.playpen.core.p3.PackageException;
import io.playpen.core.p3.PackageManager;
import io.playpen.core.plugin.PluginManager;
import io.playpen.core.protocol.Commands;
import io.playpen.core.protocol.Coordinator;
import io.playpen.core.protocol.P3;
import io.playpen.core.protocol.Protocol;
import io.playpen.core.utils.AbortableCountDownLatch;
import io.playpen.core.utils.AuthUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Level;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Pattern;

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

    private Commands.C_CoordinatorListResponse coordList = null;

    private AttachInputListenThread ailThread = null;

    private AbortableCountDownLatch latch = null;

    private int acks = 0;

    private Client() {
        super();
    }

    protected void printHelpText() {
        System.err.println("playpen cli <command> [arguments...]");
        System.err.println("Commands: list, provision, deprovision, shutdown, promote, generate-keypair, send, attach, " +
                "freeze, upload");
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
            scheduler = Executors.newScheduledThreadPool(1);

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

            if(ailThread != null && ailThread.isAlive()) {
                ailThread.setActive(false);
                ailThread.interrupt();
            }

            group.shutdownGracefully();
        }

        System.exit(0);
    }

    @Override
    public void onVMShutdown() {
        log.info("VM shutting down, stopping all tasks");

        if(clientMode == ClientMode.ATTACH) {
            sendDetachConsole();
        }

        if(scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }

        if(ailThread != null && ailThread.isAlive()) {
            ailThread.setActive(false);
            ailThread.interrupt();
        }

        if(channel != null) {
            channel.close().syncUninterruptibly();
        }
    }

    @Override
    public String getServerId() {
        return coordName;
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
        log.error("PlayPen client does not currently support the plugin system!");
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

        ByteString messageBytes = message.toByteString();
        byte[] encBytes = AuthUtils.encrypt(messageBytes.toByteArray(), getKey());
        String hash = AuthUtils.createHash(getKey(), encBytes);
        messageBytes = ByteString.copyFrom(encBytes);

        Protocol.AuthenticatedMessage auth = Protocol.AuthenticatedMessage.newBuilder()
                .setUuid(getUuid())
                .setVersion(Bootstrap.getProtocolVersion())
                .setHash(hash)
                .setPayload(messageBytes)
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
            System.err.println("Received an invalid hash on a message from the network coordinator.");
            System.err.println("This is likely due to us having an invalid UUID or secret key. Please check your local.json!");
            from.close();

            if (latch != null)
                latch.abort();

            return false;
        }

        ByteString payload = auth.getPayload();
        byte[] payloadBytes = AuthUtils.decrypt(payload.toByteArray(), key);
        payload = ByteString.copyFrom(payloadBytes);

        Protocol.Transaction transaction = null;
        try {
            transaction = Protocol.Transaction.parseFrom(payload);
        }
        catch(InvalidProtocolBufferException e) {
            log.error("Unable to read transaction from message", e);
            System.err.println("Received an unreadable message from the network coordinator.");
            System.err.println("This is likely due to us having an invalid UUID or secret key. Please check your local.json!");
            from.close();

            if (latch != null)
                latch.abort();

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

            case C_DETACH_CONSOLE:
                return processDetachConsole(info);

            case C_ACK:
                return processAck(command.getCAck(), info);
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

            case "send":
                runSendCommand(arguments);
                break;

            case "attach":
                runAttachCommand(arguments);
                break;

            case "freeze":
                runFreezeCommand(arguments);
                break;

            case "upload":
                runUploadCommand(arguments);
                break;

            default:
                printHelpText();
                channel.close();
                break;
        }
    }

    protected void runListCommand(String[] arguments) {
        if(arguments.length != 2) {
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
        if(arguments.length < 3) {
            System.err.println("provision <package-id> [properties...]");
            System.err.println("Provisions a server on the network.");
            System.err.println("The property 'version' will specify the version of the package (default: promoted)");
            System.err.println("The property 'coordinator' will specify which coordinator to provision on.");
            System.err.println("The property 'name' will specify the name of the server.");
            channel.close();
            return;
        }

        clientMode = ClientMode.PROVISION;

        String id = arguments[2];
        String version = "promoted";
        String coordinator = null;
        String serverName = null;
        Map<String, String> properties = new HashMap<>();

        for(int i = 3; i < arguments.length; i += 2) {
            if(i + 1 >= arguments.length) {
                System.err.println("Properties must be in the form <key> <value>");
                channel.close();
                return;
            }

            String key = arguments[i];
            String value = arguments[i+1];

            String lowerKey = key.trim().toLowerCase();
            switch(lowerKey) {
                case "version":
                    version = value;
                    break;

                case "coordinator":
                    coordinator = value;
                    break;

                case "name":
                    serverName = value;
                    break;

                default:
                    properties.put(key, value);
                    break;
            }
        }

        if(!sendProvision(id, version, coordinator, serverName, properties)) {
            log.error("Unable to send provision to network");
            System.err.println("Unable to send provision to network");
            channel.close();
            return;
        }

        System.out.println("Waiting for provision response...");
    }

    protected void runDeprovisionCommand(String[] arguments) {
        if(arguments.length != 4 && arguments.length != 5) {
            System.err.println("deprovision <coordinator> <server> [force=false]");
            System.err.println("Deprovisions a server from the network. Coordinator and server accept regex.");
            System.err.println("For safety, all regex will have ^ prepended and $ appended.");
            channel.close();
            return;
        }

        clientMode = ClientMode.DEPROVISION;

        Pattern coordPattern = Pattern.compile('^' + arguments[2] + '$');
        Pattern serverPattern = Pattern.compile('^' + arguments[3] + '$');
        boolean force = arguments.length == 5 && (arguments[4].trim().toLowerCase().equals("true"));

        System.out.println("Retrieving coordinator list...");
        if(!blockUntilCoordList()) {
            System.err.println("Operation cancelled!");
            channel.close();
            return;
        }

        if(force) {
            System.out.println("NOTE: forcing deprovision operation");
        }

        Map<String, List<String>> servers = getServersFromList(coordPattern, serverPattern);
        if(servers.isEmpty()) {
            System.err.println("No coordinators/servers match patterns given.");
            channel.close();
            return;
        }

        System.out.println("Sending deprovision operations...");
        int count = 0;
        for(Map.Entry<String, List<String>> entry : servers.entrySet()) {
            String coordId = entry.getKey();
            System.out.println("Coordinator " + coordId + ":");
            for(String serverId : entry.getValue()) {
                if(sendDeprovision(coordId, serverId, force)) {
                    System.out.println("\tSent deprovision of " + serverId);
                    count++;
                }
                else {
                    System.err.println("\tUnable to send deprovision of " + serverId);
                }
            }
        }

        System.out.println("Operation completed, waiting for ack...");
        latch = new AbortableCountDownLatch(count - acks);
        try {
            latch.await();
        }
        catch(InterruptedException e) {}
        channel.close();
    }

    protected void runShutdownCommand(String[] arguments) {
        if(arguments.length != 3) {
            System.err.println("shutdown <coordinator>");
            System.err.println("Shuts down a coordinator and any related servers.");
            channel.close();
            return;
        }

        clientMode = ClientMode.SHUTDOWN;

        String coordId = arguments[2];

        if(sendShutdown(coordId)) {
            System.out.println("Sent shutdown to network, waiting for ack...");
        }
        else {
            System.err.println("Unable to send shutdown to network");
            channel.close();
            return;
        }

        latch = new AbortableCountDownLatch(1 - acks);
        try {
            latch.await();
        }
        catch(InterruptedException e) {}
        channel.close();
    }

    protected void runPromoteCommand(String[] arguments) {
        if(arguments.length != 4) {
            System.err.println("promote <package-id> <package-version>");
            System.err.println("Promotes a package.");
            channel.close();
            return;
        }

        clientMode = ClientMode.PROMOTE;

        String id = arguments[2];
        String version = arguments[3];
        if(version.equalsIgnoreCase("promoted")) {
            System.err.println("Cannot promote a package of version 'promoted'");
            channel.close();
            return;
        }

        if(sendPromote(id, version)) {
            System.out.println("Sent promote to network, waiting for ack...");
        }
        else {
            System.err.println("Unable to send promote to network");
            channel.close();
            return;
        }

        latch = new AbortableCountDownLatch(1 - acks);
        try {
            latch.await();
        }
        catch(InterruptedException e) {}
        channel.close();
    }

    protected void runGenerateKeypairCommand(String[] arguments) {
        if(arguments.length != 2 && arguments.length != 3) {
            System.err.println("generate-keypair [keyname]");
            System.err.println("Generates a new coordinator keypair");
            channel.close();
            return;
        }

        clientMode = ClientMode.GENERATE_KEYPAIR;

        if(sendCreateCoordinator(arguments.length == 3 ? arguments[2] : null)) {
            System.out.println("Requesting generation of new keypair...");
            // don't close channel, just wait
        }
        else {
            System.err.println("Unable to send generation request to network");
            channel.close();
        }
    }

    protected void runSendCommand(String[] arguments) {
        if(arguments.length != 5) {
            System.err.println("send <coordinator> <server> <input>");
            System.err.println("Sends a command to the console of a server.");
            System.err.println("Coordinator and server accept regex.");
            System.err.println("For safety, all regex will have ^ prepended and $ appended.");
            channel.close();
            return;
        }

        clientMode = ClientMode.SEND_INPUT;

        Pattern coordPattern = Pattern.compile('^' + arguments[2] + '$');
        Pattern serverPattern = Pattern.compile('^' + arguments[3] + "$");
        String input = arguments[4] + '\n';

        System.out.println("Retrieving coordinator list...");
        if(!blockUntilCoordList()) {
            System.err.println("Operation cancelled!");
            channel.close();
            return;
        }

        Map<String, List<String>> servers = getServersFromList(coordPattern, serverPattern);
        if(servers.isEmpty()) {
            System.err.println("No coordinators/servers match patterns given.");
            channel.close();
            return;
        }

        System.out.println("Sending input operations...");
        int count = 0;
        for(Map.Entry<String, List<String>> entry : servers.entrySet()) {
            String coordId = entry.getKey();
            System.out.println("Coordinator " + coordId + ":");
            for(String serverId : entry.getValue()) {
                if(sendInput(coordId, serverId, input)) {
                    System.out.println("\tSent input to " + serverId);
                    count++;
                }
                else {
                    System.err.println("\tUnable to send input to " + serverId);
                }
            }
        }

        System.out.println("Operation completed, waiting for ack...");
        latch = new AbortableCountDownLatch(count - acks);
        try {
            latch.await();
        }
        catch(InterruptedException e) {}
        channel.close();
    }

    protected void runAttachCommand(String[] arguments) {
        if(arguments.length != 4) {
            System.err.println("attach <coordinator> <server>");
            System.err.println("Attaches to the console of the specified server");
            System.err.println("NOTE: Regex is not supported by this command.");
            channel.close();
            return;
        }

        clientMode = ClientMode.ATTACH;

        String coordId = arguments[2];
        String serverId = arguments[3];

        // hacky fix to prevent double-printing messages
        sendDetachConsole();

        if(!sendAttachConsole(coordId, serverId)) {
            System.err.println("Unable to send attach command. Exiting.");
            channel.close();
            return;
        }
        else {
            System.out.println("Attaching console...");
            ailThread = new AttachInputListenThread(coordId, serverId);
            ailThread.start();
        }
    }

    protected void runFreezeCommand(String[] arguments) {
        if(arguments.length != 4) {
            System.err.println("freeze <coordinator> <server>");
            System.err.println("Marks a server to be saved after it shuts down.");
            System.err.println("Coordinator and server accept regex.");
            System.err.println("For safety, all regex will have ^ prepended and $ appended.");
            channel.close();
            return;
        }

        clientMode = ClientMode.FREEZE;

        Pattern coordPattern = Pattern.compile('^' + arguments[2] + '$');
        Pattern serverPattern = Pattern.compile('^' + arguments[3] + "$");

        System.out.println("Retrieving coordinator list...");
        if(!blockUntilCoordList()) {
            System.err.println("Operation cancelled!");
            channel.close();
            return;
        }

        Map<String, List<String>> servers = getServersFromList(coordPattern, serverPattern);
        if(servers.isEmpty()) {
            System.err.println("No coordinators/servers match patterns given.");
            channel.close();
            return;
        }

        System.out.println("Sending freeze operations...");
        int count = 0;
        for(Map.Entry<String, List<String>> entry : servers.entrySet()) {
            String coordId = entry.getKey();
            System.out.println("Coordinator " + coordId + ":");
            for(String serverId : entry.getValue()) {
                if(sendFreezeServer(coordId, serverId)) {
                    System.out.println("\tSent freeze to " + serverId);
                    count++;
                }
                else {
                    System.err.println("\tUnable to send freeze to " + serverId);
                }
            }
        }

        System.out.println("Operation completed, waiting for ack...");
        latch = new AbortableCountDownLatch(count - acks);
        try {
            latch.await();
        }
        catch(InterruptedException e) {}
        channel.close();
    }

    protected void runUploadCommand(String[] arguments) {
        if(arguments.length < 3) {
            System.err.println("upload <package-path...>");
            System.err.println("Upload packages to the network, expiring the cache if needed.");
            channel.close();
            return;
        }

        clientMode = ClientMode.UPLOAD;

        int count = 0;
        for(int argN = 2; argN < arguments.length; ++argN) {
            String pathStr = arguments[argN];
            System.out.println("Attempting upload of " + pathStr);
            File p3File = new File(pathStr);
            if (!p3File.exists()) {
                System.err.println("Unknown file \"" + pathStr + "\"");
                continue;
            }

            if (!p3File.isFile()) {
                System.err.println("\"" + pathStr + "\" is not a file");
                continue;
            }

            PackageManager packageManager = new PackageManager();
            Initialization.packageManager(packageManager);

            P3Package p3;
            try {
                p3 = packageManager.readPackage(p3File);
            } catch (PackageException e) {
                System.err.println("Unable to read package:");
                e.printStackTrace(System.err);
                continue;
            }

            if (p3 == null) {
                System.err.println("Unable to read package");
                continue;
            }

            System.out.println("Sending package " + p3.getId() + " (" + p3.getVersion() + ") to network...");
            if (!sendPackage(p3)) {
                System.err.println("Unable to send package!");
                continue;
            }

            count++;
        }

        if(count == 0) {
            System.err.println("No successful uploads!");
            channel.close();
            return;
        }

        System.out.println("Operation completed, waiting for ack...");
        latch = new AbortableCountDownLatch(count - acks);
        try {
            latch.await();
        } catch (InterruptedException e) {
        }
        channel.close();
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

            case DEPROVISION:
            case SEND_INPUT:
            case FREEZE:
                coordList = response;
                return true;
        }

        return false;
    }

    protected boolean sendProvision(String id, String version, String coordinator, String serverName, Map<String, String> properties) {
        P3.P3Meta meta = P3.P3Meta.newBuilder()
                .setId(id)
                .setVersion(version)
                .build();

        Commands.C_Provision.Builder provisionBuilder = Commands.C_Provision.newBuilder()
                .setP3(meta);

        if(coordinator != null) {
            provisionBuilder.setCoordinator(coordinator);
        }

        if(serverName != null) {
            provisionBuilder.setServerName(serverName);
        }

        for(Map.Entry<String, String> prop : properties.entrySet()) {
            provisionBuilder.addProperties(Coordinator.Property.newBuilder().setName(prop.getKey()).setValue(prop.getValue()).build());
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

    protected boolean sendCreateCoordinator(String keyName) {
        Commands.C_CreateCoordinator.Builder create = Commands.C_CreateCoordinator.newBuilder();
        if (keyName != null)
            create.setKeyName(keyName);

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.C_CREATE_COORDINATOR)
                .setCCreateCoordinator(create.build())
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

    protected boolean sendInput(String coordId, String serverId, String input) {
        Commands.C_SendInput protoInput = Commands.C_SendInput.newBuilder()
                .setCoordinatorId(coordId)
                .setServerId(serverId)
                .setInput(input)
                .build();

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.C_SEND_INPUT)
                .setCSendInput(protoInput)
                .build();

        TransactionInfo info = TransactionManager.get().begin();

        Protocol.Transaction message = TransactionManager.get()
                .build(info.getId(), Protocol.Transaction.Mode.SINGLE, command);
        if(message == null) {
            log.error("Unable to build message for send input");
            TransactionManager.get().cancel(info.getId());
            return false;
        }

        log.info("Sending C_SEND_INPUT to network coordinator");
        return TransactionManager.get().send(info.getId(), message, null);
    }

    protected boolean sendAttachConsole(String coordId, String serverId) {
        Commands.C_AttachConsole attach = Commands.C_AttachConsole.newBuilder()
                .setCoordinatorId(coordId)
                .setServerId(serverId)
                .build();

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.C_ATTACH_CONSOLE)
                .setCAttachConsole(attach)
                .build();

        TransactionInfo info = TransactionManager.get().begin();

        Protocol.Transaction message = TransactionManager.get()
                .build(info.getId(), Protocol.Transaction.Mode.CREATE, command);
        if(message == null) {
            log.error("Unable to build message for C_ATTACH_CONSOLE");
            TransactionManager.get().cancel(info.getId());
            return false;
        }

        log.info("Sending C_ATTACH_CONSOLE to network coordinator");
        return TransactionManager.get().send(info.getId(), message, null);
    }

    protected boolean processConsoleMessage(Commands.C_ConsoleMessage message, TransactionInfo info) {
        switch(clientMode) {
            case ATTACH:
                System.out.println(message.getValue());
                return true;
        }

        return false;
    }

    protected boolean processDetachConsole(TransactionInfo info) {
        switch(clientMode) {
            case ATTACH:
                System.out.println("Console detached!");
                channel.close();
                return true;
        }

        return false;
    }

    protected boolean sendDetachConsole() {
        Commands.C_DetachConsole detach = Commands.C_DetachConsole.newBuilder()
                .build();

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.C_DETACH_CONSOLE)
                .setCDetachConsole(detach)
                .build();

        TransactionInfo info = TransactionManager.get().begin();

        Protocol.Transaction message = TransactionManager.get()
                .build(info.getId(), Protocol.Transaction.Mode.SINGLE, command);
        if(message == null) {
            log.error("Unable to build message for C_DETACH_CONSOLE");
            TransactionManager.get().cancel(info.getId());
            return false;
        }

        log.info("Sending C_DETACH_CONSOLE to network coordinator");
        return TransactionManager.get().send(info.getId(), message, null);
    }

    protected boolean sendFreezeServer(String coordId, String serverId) {
        Commands.C_FreezeServer freeze = Commands.C_FreezeServer.newBuilder()
                .setCoordinatorId(coordId)
                .setServerId(serverId)
                .build();

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.C_FREEZE_SERVER)
                .setCFreezeServer(freeze)
                .build();

        TransactionInfo info = TransactionManager.get().begin();

        Protocol.Transaction message = TransactionManager.get()
                .build(info.getId(), Protocol.Transaction.Mode.SINGLE, command);
        if(message == null) {
            log.error("Unable to build message for C_FREEZE_SERVER");
            TransactionManager.get().cancel(info.getId());
            return false;
        }

        log.info("Sending C_FREEZE_SERVER to network coordinator");
        return TransactionManager.get().send(info.getId(), message, null);
    }

    protected boolean sendPackage(P3Package p3)
    {
        if(!p3.isResolved()) {
            log.error("Cannot pass an unresolved package to sendPackage");
            return false;
        }

        P3.P3Meta meta = P3.P3Meta.newBuilder()
                .setId(p3.getId())
                .setVersion(p3.getVersion())
                .build();

        try {
            p3.calculateChecksum();
        }
        catch (PackageException e) {
            log.log(Level.ERROR, "Unable to calculate package checksum", e);
            return false;
        }

        File packageFile = new File(p3.getLocalPath());
        long fileLength = packageFile.length();
        if (fileLength / 1024 / 1024 > 100) {
            System.out.println("Sending chunked package " + p3.getId() + " at " + p3.getVersion());
            System.out.println("Checksum: " + p3.getChecksum());

            TransactionInfo info = TransactionManager.get().begin();

            Commands.BaseCommand noop = Commands.BaseCommand.newBuilder()
                    .setType(Commands.BaseCommand.CommandType.NOOP)
                    .build();

            Protocol.Transaction noopMessage = TransactionManager.get()
                    .build(info.getId(), Protocol.Transaction.Mode.CREATE, noop);
            if (noopMessage == null) {
                System.out.println("Unable to build transaction for split package response");
                return false;
            }

            if (!TransactionManager.get().send(info.getId(), noopMessage, null)) {
                System.out.println("Unable to send transaction for split package response");
                return false;
            }

            try (FileInputStream in = new FileInputStream(packageFile)) {
                byte[] packageBytes = new byte[1048576];
                int chunkLen = 0;
                int chunkId = 0;
                while ((chunkLen = in.read(packageBytes)) != -1) {
                    P3.SplitPackageData data = P3.SplitPackageData.newBuilder()
                            .setMeta(meta)
                            .setEndOfFile(false)
                            .setChunkId(chunkId)
                            .setData(ByteString.copyFrom(packageBytes, 0, chunkLen))
                            .build();

                    Commands.C_UploadSplitPackage response = Commands.C_UploadSplitPackage.newBuilder()
                            .setData(data)
                            .build();

                    Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                            .setType(Commands.BaseCommand.CommandType.C_UPLOAD_SPLIT_PACKAGE)
                            .setCUploadSplitPackage(response)
                            .build();

                    Protocol.Transaction message = TransactionManager.get()
                            .build(info.getId(), Protocol.Transaction.Mode.CONTINUE, command);
                    if (message == null) {
                        System.out.println("Unable to build transaction for split package response");
                        return false;
                    }

                    if (!TransactionManager.get().send(info.getId(), message, null)) {
                        System.out.println("Unable to send transaction for split package response");
                        return false;
                    }

                    ++chunkId;
                }

                P3.SplitPackageData data = P3.SplitPackageData.newBuilder()
                        .setMeta(meta)
                        .setEndOfFile(true)
                        .setChecksum(p3.getChecksum())
                        .setChunkCount(chunkId)
                        .build();

                Commands.C_UploadSplitPackage response = Commands.C_UploadSplitPackage.newBuilder()
                        .setData(data)
                        .build();

                Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                        .setType(Commands.BaseCommand.CommandType.C_UPLOAD_SPLIT_PACKAGE)
                        .setCUploadSplitPackage(response)
                        .build();

                Protocol.Transaction message = TransactionManager.get()
                        .build(info.getId(), Protocol.Transaction.Mode.COMPLETE, command);
                if (message == null) {
                    System.out.println("Unable to build transaction for split package response");
                    return false;
                }

                System.out.println("Finishing split package response (" + chunkId + " chunks)");
                System.out.println("Checksum: " + p3.getChecksum());

                return TransactionManager.get().send(info.getId(), message, null);
            } catch (IOException e) {
                log.error("Unable to read package data", e);
                return false;
            }
        }
        else {
            ByteString packageData;
            try (InputStream stream = Files.newInputStream(Paths.get(p3.getLocalPath()))) {
                packageData = ByteString.readFrom(stream);
            }
            catch(IOException e) {
                log.fatal("Unable to read package file", e);
                return false;
            }

            try {
                p3.calculateChecksum();
            } catch (PackageException e) {
                log.error("Unable to calculate checksum on package", e);
                return false;
            }

            Commands.C_UploadPackage upload = Commands.C_UploadPackage.newBuilder()
                    .setData(P3.PackageData.newBuilder()
                            .setMeta(P3.P3Meta.newBuilder().setId(p3.getId()).setVersion(p3.getVersion()))
                            .setChecksum(p3.getChecksum())
                            .setData(packageData))
                    .build();

            Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                    .setType(Commands.BaseCommand.CommandType.C_UPLOAD_PACKAGE)
                    .setCUploadPackage(upload)
                    .build();

            TransactionInfo info = TransactionManager.get().begin();

            Protocol.Transaction message = TransactionManager.get()
                    .build(info.getId(), Protocol.Transaction.Mode.SINGLE, command);
            if(message == null) {
                log.error("Unable to build message for C_UPLOAD_PACKAGE");
                TransactionManager.get().cancel(info.getId());
                return false;
            }

            return TransactionManager.get().send(info.getId(), message, null);
        }
    }

    protected boolean processAck(Commands.C_Ack ack, TransactionInfo info) {
        if(clientMode == ClientMode.ATTACH)
            return true;

        System.out.println("ACK: " + ack.getResult());

        log.info("ACK: " + ack.getResult());

        acks++;
        if(latch == null)
            return false;

        latch.countDown();
        return true;
    }

    protected boolean blockUntilCoordList() {
        if(!sendListRequest())
            return false;

        try {
            do {
                Thread.sleep(1000);
            }
            while(coordList == null && channel.isActive());
        }
        catch(InterruptedException e) {
            return false;
        }

        return channel.isActive();
    }

    protected Map<String, List<String>> getServersFromList(Pattern coordPattern, Pattern serverPattern) {
        Map<String, List<String>> result = new HashMap<>();
        for(Coordinator.LocalCoordinator coord : coordList.getCoordinatorsList()) {
            if(coordPattern.matcher(coord.getUuid()).matches() ||
                    (coord.hasName() && coordPattern.matcher(coord.getName()).matches())) {
                List<String> servers = new ArrayList<>();
                for(Coordinator.Server server : coord.getServersList()) {
                    if(serverPattern.matcher(server.getUuid()).matches() ||
                            (server.hasName() && serverPattern.matcher(server.getName()).matches())) {
                        servers.add(server.getUuid());
                    }
                }

                if(!servers.isEmpty())
                    result.put(coord.getUuid(), servers);
            }
        }

        return result;
    }

    private static class AttachInputListenThread extends Thread {
        private String coordId;
        private String serverId;
        @Setter
        @Getter
        private boolean active = true;

        public AttachInputListenThread(String c, String s) {
            coordId = c;
            serverId = s;
        }

        @Override
        public void run() {
            while(active) {
                String input = System.console().readLine() + '\n';
                Client.get().sendInput(coordId, serverId, input);
            }
        }
    }
}
