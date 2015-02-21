package net.thechunk.playpen.coordinator.network;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.thechunk.playpen.Bootstrap;
import net.thechunk.playpen.Initialization;
import net.thechunk.playpen.coordinator.CoordinatorMode;
import net.thechunk.playpen.coordinator.PlayPen;
import net.thechunk.playpen.networking.TransactionInfo;
import net.thechunk.playpen.networking.TransactionManager;
import net.thechunk.playpen.networking.netty.AuthenticatedMessageInitializer;
import net.thechunk.playpen.p3.P3Package;
import net.thechunk.playpen.p3.PackageManager;
import net.thechunk.playpen.plugin.EventManager;
import net.thechunk.playpen.plugin.PluginManager;
import net.thechunk.playpen.protocol.Commands;
import net.thechunk.playpen.protocol.Coordinator;
import net.thechunk.playpen.protocol.P3;
import net.thechunk.playpen.protocol.Protocol;
import net.thechunk.playpen.utils.AuthUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Log4j2
public class Network extends PlayPen {

    public static Network get() {
        if(PlayPen.get() == null) {
            new Network();
        }

        return (Network)PlayPen.get();
    }

    @Getter
    private Map<String, LocalCoordinator> coordinators = new ConcurrentHashMap<>();

    private PackageManager packageManager = null;

    private ScheduledExecutorService scheduler = null;

    private Map<String, String> consoles = new ConcurrentHashMap<>();

    private PluginManager pluginManager = null;

    private EventManager<INetworkListener> eventManager = null;

    private Network() {
        super();

        packageManager = new PackageManager();
        Initialization.packageManager(packageManager);

        eventManager = new EventManager<>();
        pluginManager = new PluginManager();
    }

    public boolean run() {
        log.info("Reading network configuration");
        String configStr;
        try {
            configStr = new String(Files.readAllBytes(Paths.get(Bootstrap.getHomeDir().getPath(), "network.json")));
        }
        catch(IOException e) {
            log.fatal("Unable to read configuration file.", e);
            return false;
        }

        InetAddress ip = null;
        int port = 0;

        try {
            JSONObject config = new JSONObject(configStr);
            String ipStr = config.getString("ip");
            ip = InetAddress.getByName(ipStr);
            port = config.getInt("port");
        }
        catch(Exception e) {
            log.fatal("Unable to read configuration file.", e);
            return false;
        }

        log.info("Reading keystore configuration");
        try {
            configStr = new String(Files.readAllBytes(Paths.get(Bootstrap.getHomeDir().getPath(), "keystore.json")));
        }
        catch(IOException e) {
            log.fatal("Unable to read keystore configuration", e);
            return false;
        }

        try {
            JSONObject keystore = new JSONObject(configStr);
            JSONArray coordKeys = keystore.getJSONArray("coordinators");
            for(int i = 0; i < coordKeys.length(); ++i) {
                JSONObject obj = coordKeys.getJSONObject(i);

                LocalCoordinator coord = new LocalCoordinator();
                coord.setEnabled(false);
                coord.setUuid(obj.getString("uuid"));
                coord.setKey(obj.getString("key"));
                coordinators.put(coord.getUuid(), coord);

                log.info("Loaded coordinator keypair " + coord.getUuid());
            }
        }
        catch(JSONException e) {
            log.fatal("Unable to read keystore configuration", e);
            return false;
        }

        log.info(coordinators.size() + " coordinator keypairs registered");

        if(!pluginManager.loadPlugins()) {
            log.fatal("Unable to initialize plugin manager");
            return false;
        }

        log.info("Starting network coordinator");
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            scheduler = Executors.newScheduledThreadPool(4);

            ServerBootstrap b = new ServerBootstrap();
            b.group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new AuthenticatedMessageInitializer())
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture f = b.bind(ip, port).await();

            if(!f.isSuccess()) {
                log.error("Unable to bind on " + ip + " port " + port);
                return false; // no retrying
            }

            log.info("Listening on " + ip + " port " + port);

            eventManager.callEvent(INetworkListener::onNetworkStartup);

            f.channel().closeFuture().sync();
        }
        catch(InterruptedException e) {
            log.warn("Operation interrupted!", e);
            return false;
        }
        finally {
            eventManager.callEvent(INetworkListener::onNetworkShutdown);

            scheduler.shutdownNow();
            scheduler = null;

            group.shutdownGracefully();

            pluginManager.stopPlugins();
        }

        return true;
    }

    public LocalCoordinator getCoordinator(String idOrName) {
        if(coordinators.containsKey(idOrName))
            return coordinators.get(idOrName);

        for(LocalCoordinator coord : coordinators.values()) {
            if(coord.getName() != null && coord.getName().equals(idOrName))
                return coord;
        }

        return null;
    }

    @Override
    public String getServerId() {
        return "net";
    }

    @Override
    public CoordinatorMode getCoordinatorMode() {
        return CoordinatorMode.NETWORK;
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
    public PluginManager getPluginManager() {
        return pluginManager;
    }

    public EventManager<INetworkListener> getEventManager() {
        return eventManager;
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
            log.error("Closing connection from " + from + " due to bad hash");
            from.close();
            return false;
        }

        local.setChannel(from);

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

    @Override
    public boolean process(Commands.BaseCommand command, TransactionInfo info, String from) {
        switch(command.getType()) {
            default:
                log.error("Network coordinator cannot process command " + command.getType());
                return false;

            case SYNC:
                return processSync(command.getSync(), info, from);

            case PROVISION_RESPONSE:
                return processProvisionResponse(command.getProvisionResponse(), info, from);

            case PACKAGE_REQUEST:
                return processPackageRequest(command.getPackageRequest(), info, from);

            case SERVER_SHUTDOWN:
                return processServerShutdown(command.getServerShutdown(), info, from);

            case CONSOLE_MESSAGE:
                return processConsoleMessage(command.getConsoleMessage(), info, from);

            case DETACH_CONSOLE:
                return processDetachConsole(command.getDetachConsole(), info, from);


            case C_GET_COORDINATOR_LIST:
                return c_processGetCoordinatorList(info, from);

            case C_PROVISION:
                return c_processProvision(command.getCProvision(), info, from);

            case C_DEPROVISION:
                return c_processDeprovision(command.getCDeprovision(), info, from);

            case C_SHUTDOWN:
                return c_processShutdown(command.getCShutdown(), info, from);

            case C_PROMOTE:
                return c_processPromote(command.getCPromote(), info, from);

            case C_CREATE_COORDINATOR:
                return c_processCreateCoordinator(info, from);

            case C_SEND_INPUT:
                return c_processSendInput(command.getCSendInput(), info, from);

            case C_ATTACH_CONSOLE:
                return c_processAttachConsole(command.getCAttachConsole(), info, from);

            case C_DETACH_CONSOLE:
                return c_processDetachConsole(info, from);
        }
    }

    @Override
    public void onVMShutdown() {
        log.info("VM shutting down");

        eventManager.callEvent(INetworkListener::onNetworkShutdown);

        scheduler.shutdownNow();

        for(LocalCoordinator coord : coordinators.values()) {
            if(coord.getChannel() != null && coord.getChannel().isOpen()) {
                coord.getChannel().close().syncUninterruptibly();
            }
        }

        pluginManager.stopPlugins();
    }

    public LocalCoordinator createCoordinator() {
        LocalCoordinator coord = new LocalCoordinator();
        coord.setEnabled(false);
        coord.setUuid(UUID.randomUUID().toString());
        coord.setKey(UUID.randomUUID().toString());
        coordinators.put(coord.getUuid(), coord);

        log.info("Generated new coordinator keypair " + coord.getUuid());

        saveKeystore();

        eventManager.callEvent(l -> l.onCoordinatorCreated(coord));

        return coord;
    }

    public void saveKeystore() {
        log.info("Saving keystore...");
        try {
            Path path = Paths.get(Bootstrap.getHomeDir().getPath(), "keystore.json");
            JSONObject config = new JSONObject(new String(Files.readAllBytes(path)));

            JSONArray coords = new JSONArray();
            for(LocalCoordinator coord : coordinators.values()) {
                JSONObject obj = new JSONObject();
                obj.put("uuid", coord.getUuid());
                obj.put("key", coord.getKey());

                coords.put(obj);
            }

            config.put("coordinators", coords);
            String json = config.toString(2);

            try (FileOutputStream output = new FileOutputStream(path.toFile())) {
                IOUtils.write(json, output);
            }
        }
        catch(JSONException e) {
            log.error("Unable to save keystore", e);
        }
        catch(IOException e) {
            log.error("Unable to save keystore", e);
        }

        log.info("Saved keystore");
    }

    /**
     * Selects a coordinator to use for provisioning. This will only return an active coordinator
     * with the lowest normalized resource usage.
     */
    public LocalCoordinator selectCoordinator(P3Package p3) {
        LocalCoordinator best = null;
        double bestNRU = Double.MAX_VALUE;
        for(LocalCoordinator coord : coordinators.values()) {
            if(!coord.isEnabled() || coord.getChannel() == null || !coord.getChannel().isActive())
                continue;

            double nru = coord.getNormalizedResourceUsage();
            if(nru < bestNRU && coord.canProvisionPackage(p3)) {
                best = coord;
                bestNRU = nru;
            }
        }

        return best;
    }

    /**
     * Synchronized due to the use of selectCoordinator
     */
    public synchronized ProvisionResult provision(P3Package p3, String serverName, Map<String, String> properties) {
        LocalCoordinator coord = selectCoordinator(p3);
        if(coord == null) {
            log.error("Unable to select a coordinator for a provisioning operation");
            return null;
        }

        return provision(p3, serverName, properties, coord.getUuid());
    }

    public ProvisionResult provision(P3Package p3, String serverName, Map<String, String> properties, String target) {
        log.info("Provision requested for " + p3.getId() + " at " + p3.getVersion() + " on coordinator " + target + " with server name " + serverName);
        return sendProvision(target, p3, serverName, properties);
    }

    public boolean deprovision(String target, String serverId) {
        return deprovision(target, serverId, false);
    }

    public boolean deprovision(String target, String serverId, boolean force) {
        return sendDeprovision(target, serverId, force);
    }

    public boolean shutdownCoordinator(String target) {
        return sendShutdown(target);
    }

    protected boolean processSync(Commands.Sync command, TransactionInfo info, String from) {
        LocalCoordinator coord = getCoordinator(from);
        if(coord == null) {
            log.error("Can't process SYNC on invalid coordinator " + from);
            return false;
        }

        coord.setEnabled(false); // protection so we don't start any new tasks
                                 // while syncing this coordinator

        if(command.hasName()) {
            coord.setName(command.getName());
        }
        else {
            coord.setName(coord.getUuid());
        }

        coord.getResources().clear();
        for(Coordinator.Resource resource : command.getResourcesList()) {
            coord.getResources().put(resource.getName(), resource.getValue());
        }

        coord.getAttributes().clear();
        for(String attr : command.getAttributesList()) {
            coord.getAttributes().add(attr);
        }

        coord.getServers().clear();
        for(Coordinator.Server cmdServer : command.getServersList()) {
            Server server = new Server();
            server.setActive(true);
            server.setUuid(cmdServer.getUuid());
            server.setName(cmdServer.hasName() ? cmdServer.getName() : server.getUuid());
            server.setP3(packageManager.resolve(cmdServer.getP3().getId(), cmdServer.getP3().getVersion()));
            if(server.getP3() == null) {
                log.warn("Unknown P3 " + cmdServer.getP3().getId() + " at " +
                    cmdServer.getP3().getVersion() + " for server " + server.getName());
            }

            for(Coordinator.Property prop : cmdServer.getPropertiesList()) {
                server.getProperties().put(prop.getName(), prop.getValue());
            }

            coord.getServers().put(server.getUuid(), server);
        }

        coord.setEnabled(command.getEnabled());
        log.info("Synchronized " + coord.getUuid() + " with " + coord.getServers().size()
                + " servers (" + (coord.isEnabled() ? "enabled" : "not enabled") + ")");
        log.debug(coord.getUuid() + " has " + coord.getResources().size() + " resources and " + coord.getAttributes().size() + " attributes");

        eventManager.callEvent(l -> l.onCoordinatorSync(coord));
        
        return true;
    }

    protected synchronized ProvisionResult sendProvision(String target, P3Package p3, String name, Map<String, String> properties) {
        if(!p3.isResolved()) {
            log.error("Cannot pass an unresolved package to sendProvision");
            return null;
        }

        LocalCoordinator coord = getCoordinator(target);
        if(coord == null) {
            log.error("Unknown coordinator " + target + " for sendProvision");
            return null;
        }

        if(!coord.isEnabled()) {
            log.error("Coordinator " + target + " is not enabled for sendProvision");
            return null;
        }

        Server server = coord.createServer(p3, name, properties);
        if(server == null) {
            log.error("Unable to register server locally before sending for sendProvision");
            return null;
        }

        P3.P3Meta meta = P3.P3Meta.newBuilder()
                .setId(p3.getId())
                .setVersion(p3.getVersion())
                .build();

        Coordinator.Server.Builder serverBuilder = Coordinator.Server.newBuilder()
                .setP3(meta)
                .setUuid(server.getUuid());
        if(name != null)
            serverBuilder.setName(name);

        if(properties != null) {
            for(Map.Entry<String, String> entry : properties.entrySet()) {
                Coordinator.Property prop = Coordinator.Property.newBuilder()
                        .setName(entry.getKey())
                        .setValue(entry.getValue())
                        .build();

                serverBuilder.addProperties(prop);
            }
        }

        Commands.Provision provision = Commands.Provision.newBuilder()
                .setServer(serverBuilder.build())
                .build();

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.PROVISION)
                .setProvision(provision)
                .build();

        TransactionInfo info = TransactionManager.get().begin();

        Protocol.Transaction message = TransactionManager.get()
                .build(info.getId(), Protocol.Transaction.Mode.CREATE, command);
        if(message == null) {
            log.error("Unable to build message for provision");
            TransactionManager.get().cancel(info.getId());
            return null;
        }

        log.info("Sending provision of " + p3.getId() + " at " + p3.getVersion() + " to " + coord.getUuid() + ", creating server " + server.getUuid());

        if(!TransactionManager.get().send(info.getId(), message, target))
            return null;

        ProvisionResult result = new ProvisionResult();
        result.setCoordinator(target);
        result.setServer(server.getUuid());

        eventManager.callEvent(l -> l.onRequestProvision(coord, server));

        return result;
    }

    protected boolean processProvisionResponse(Commands.ProvisionResponse command, TransactionInfo info, String from) {
        LocalCoordinator coord = getCoordinator(from);
        if(coord == null) {
            log.error("Cannot process PROVISION_RESPONSE on invalid coordinator " + from);
            return false;
        }

        Commands.BaseCommand previous = info.getTransaction().getPayload();
        if(previous == null || previous.getType() != Commands.BaseCommand.CommandType.PROVISION) {
            log.error("PROVISION_RESPONSE expects transaction to have previously contained a PROVISION command");
            return false;
        }

        String serverId = previous.getProvision().getServer().getUuid();
        Server server = coord.getServer(serverId);
        if(server == null) {
            log.error("Unknown server " + serverId + " on PROVISION_RESPONSE");
            return false;
        }

        if(command.getOk()) {
            server.setActive(true);
            log.info("Server " + server.getUuid() + " on " + coord.getUuid() + " has been activated (provision response)");

            eventManager.callEvent(l -> l.onProvisionResponse(coord, server, true));

            return true;
        }
        else {
            coord.getServers().remove(server.getUuid());
            log.warn("Server " + server.getUuid() + " on " + coord.getUuid() + " failed to activate (provision response)");

            eventManager.callEvent(l -> l.onProvisionResponse(coord, server, false));

            return false;
        }
    }

    protected boolean processPackageRequest(Commands.PackageRequest command, TransactionInfo info, String from) {
        LocalCoordinator coord = getCoordinator(from);
        if(coord == null) {
            log.error("Cannot process PACKAGE_REQUEST on invalid coordinator " + from);
            return false;
        }

        String id = command.getP3().getId();
        String version = command.getP3().getVersion();
        log.info("Package " + id + " at " + version + " requested by " + from);

        P3Package p3 = packageManager.resolve(id, version);
        if(p3 == null) {
            log.error("Unable to resolve package " + id + " at " + version + " for " + from);
            return sendPackageResponseFailure(from, info.getId());
        }

        return sendPackageResponse(from, info.getId(), p3);
    }

    protected boolean sendPackageResponseFailure(String target, String tid) {
        TransactionInfo info = TransactionManager.get().getInfo(tid);
        if(info == null) {
            log.error("Unknown transaction " + tid + ", unable to send package");
            return false;
        }

        Commands.PackageResponse response = Commands.PackageResponse.newBuilder()
                .setOk(false)
                .build();

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.PACKAGE_RESPONSE)
                .setPackageResponse(response)
                .build();

        Protocol.Transaction message = TransactionManager.get()
                .build(info.getId(), Protocol.Transaction.Mode.COMPLETE, command);
        if(message == null) {
            log.error("Unable to build transaction for package response (failure)");
            return false;
        }

        return TransactionManager.get().send(info.getId(), message, target);
    }

    protected boolean sendPackageResponse(String target, String tid, P3Package p3) {
        if(!p3.isResolved()) {
            log.error("Cannot pass an unresolved package to sendPackage");
            return false;
        }

        TransactionInfo info = TransactionManager.get().getInfo(tid);
        if(info == null) {
            log.error("Unknown transaction " + tid + ", unable to send package");
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
                .setPackageResponse(response)
                .build();

        Protocol.Transaction message = TransactionManager.get()
                .build(info.getId(), Protocol.Transaction.Mode.COMPLETE, command);
        if(message == null) {
            log.error("Unable to build transaction for package response");
            return false;
        }

        log.info("Sending package " + p3.getId() + " at " + p3.getVersion() + " to " + target);

        return TransactionManager.get().send(info.getId(), message, target);
    }

    protected boolean sendDeprovision(String target, String serverId, boolean force) {
        LocalCoordinator coord = getCoordinator(target);
        if(coord == null) {
            log.error("Cannot send DEPROVISION on invalid coordinator " + target);
            return false;
        }

        Server server = coord.getServer(serverId);
        if(server == null) {
            log.error("Cannot send DEPROVISION on invalid server " + serverId + " on coordinator " + target);
            return false;
        }

        server.setActive(false);

        Commands.Deprovision deprovision = Commands.Deprovision.newBuilder()
                .setUuid(server.getUuid())
                .setForce(force)
                .build();

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.DEPROVISION)
                .setDeprovision(deprovision)
                .build();

        TransactionInfo info = TransactionManager.get().begin();
        Protocol.Transaction message = TransactionManager.get()
                .build(info.getId(), Protocol.Transaction.Mode.SINGLE, command);
        if(message == null) {
            log.error("Unable to build transaction for DEPROVISION");
            TransactionManager.get().cancel(info.getId());
            return false;
        }

        log.info("Deprovisioning " + server.getUuid() + " on coordinator " + target);

        eventManager.callEvent(l -> l.onRequestDeprovision(coord, server));

        return TransactionManager.get().send(info.getId(), message, target);
    }

    protected boolean processServerShutdown(Commands.ServerShutdown command, TransactionInfo info, String from) {
        LocalCoordinator coord = getCoordinator(from);
        if(coord == null) {
            log.error("Cannot process SERVER_SHUTDOWN on invalid coordinator " + from);
            return false;
        }

        Server server = coord.getServer(command.getUuid());
        if(server == null) {
            log.error("Cannot process SERVER_SHUTDOWN on invalid server " + command.getUuid() + " on coordinator " + coord.getUuid());
            return false;
        }

        server.setActive(false);
        coord.getServers().remove(server.getUuid());
        log.info("Server " + server.getUuid() + " shutdown on " + coord.getUuid());

        eventManager.callEvent(l -> l.onServerShutdown(coord, server));

        return true;
    }

    protected boolean sendShutdown(String target) {
        LocalCoordinator coord = getCoordinator(target);
        if(coord == null) {
            log.error("Cannot send SHUTDOWN to invalid coordinator " + target);
            return false;
        }

        coord.setEnabled(false);

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.SHUTDOWN)
                .build();

        TransactionInfo info = TransactionManager.get().begin();
        Protocol.Transaction message = TransactionManager.get()
                .build(info.getId(), Protocol.Transaction.Mode.SINGLE, command);
        if(message == null) {
            log.error("Unable to build transaction for SHUTDOWN");
            TransactionManager.get().cancel(info.getId());
            return false;
        }

        log.info("Shutting down coordinator " + target);

        eventManager.callEvent(l -> l.onRequestShutdown(coord));

        return TransactionManager.get().send(info.getId(), message, coord.getUuid());
    }

    public boolean sendInput(String target, String serverId, String input) {
        LocalCoordinator coord = getCoordinator(target);
        if(coord == null) {
            log.error("Cannot send SEND_INPUT to invalid coordinator " + target);
            return false;
        }

        Server server = coord.getServer(serverId);
        if(server == null) {
            log.error("Cannot send SEND_INPUT to invalid server " + serverId + " on coordinator " + coord.getUuid());
            return false;
        }

        Commands.SendInput protoInput = Commands.SendInput.newBuilder()
                .setId(server.getUuid())
                .setInput(input)
                .build();

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.SEND_INPUT)
                .setSendInput(protoInput)
                .build();

        TransactionInfo info = TransactionManager.get().begin();
        Protocol.Transaction message = TransactionManager.get()
                .build(info.getId(), Protocol.Transaction.Mode.SINGLE, command);
        if(message == null) {
            log.error("Unable to build transaction for SEND_INPUT");
            TransactionManager.get().cancel(info.getId());
            return false;
        }

        log.info("Sending input to server " + serverId + " on coordinator " + coord.getUuid());

        return TransactionManager.get().send(info.getId(), message, coord.getUuid());
    }

    protected boolean sendAttachConsole(String target, String serverId, String consoleId) {
        LocalCoordinator coord = getCoordinator(target);
        if(coord == null) {
            log.error("Cannot send ATTACH_CONSOLE to invalid coordinator " + target);
            return false;
        }

        Server server = coord.getServer(serverId);
        if(server == null) {
            log.error("Cannot send ATTACH_CONSOLE to invalid server " + serverId + " on " + target);
            return false;
        }

        if(!consoles.containsKey(consoleId)) {
            log.error("Cannot send ATTACH_CONSOLE with invalid console id " + consoleId);
            return false;
        }

        Commands.AttachConsole attach = Commands.AttachConsole.newBuilder()
                .setServerId(serverId)
                .setConsoleId(consoleId)
                .build();

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.ATTACH_CONSOLE)
                .setAttachConsole(attach)
                .build();

        TransactionInfo info = TransactionManager.get().begin();

        Protocol.Transaction message = TransactionManager.get()
                .build(info.getId(), Protocol.Transaction.Mode.SINGLE, command);
        if(message == null) {
            log.error("Unable to build transaction for ATTACH_CONSOLE");
            TransactionManager.get().cancel(info.getId());
            return false;
        }

        return TransactionManager.get().send(info.getId(), message, coord.getUuid());
    }

    protected boolean processConsoleMessage(Commands.ConsoleMessage message, TransactionInfo info, String from) {
        String targetId = consoles.getOrDefault(message.getConsoleId(), null);
        if(targetId == null) {
            log.error("CONSOLE_MESSAGE received with invalid console id");
            sendDetachConsole(from, message.getConsoleId());
            return false;
        }

        LocalCoordinator target = getCoordinator(targetId);
        if(target == null || target.getChannel() == null || !target.getChannel().isActive()) {
            log.warn("CONSOLE_MESSAGE received but attached coordinator isn't valid. Sending detach.");
            sendDetachConsole(from, message.getConsoleId());
            consoles.remove(message.getConsoleId());
            return false;
        }

        return c_sendConsoleMessage(target.getUuid(), message.getValue());
    }

    protected boolean sendDetachConsole(String target, String consoleId) {
        LocalCoordinator coord = getCoordinator(target);
        if(coord == null) {
            log.error("Cannot send DETACH_CONSOLE to invalid coordinator " + target);
            return false;
        }

        Commands.DetachConsole detach = Commands.DetachConsole.newBuilder()
                .setConsoleId(consoleId)
                .build();

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.DETACH_CONSOLE)
                .setDetachConsole(detach)
                .build();

        TransactionInfo info = TransactionManager.get().begin();

        Protocol.Transaction message = TransactionManager.get()
                .build(info.getId(), Protocol.Transaction.Mode.SINGLE, command);
        if(message == null) {
            log.error("Unable to build transaction for DETACH_CONSOLE");
            TransactionManager.get().cancel(info.getId());
            return false;
        }

        log.info("Sending DETACH_CONSOLE for " + consoleId);

        return TransactionManager.get().send(info.getId(), message, coord.getUuid());
    }

    protected boolean processDetachConsole(Commands.DetachConsole message, TransactionInfo info, String from) {
        String targetId = consoles.getOrDefault(message.getConsoleId(), null);
        if(targetId == null) {
            log.error("DETACH_CONSOLE received with invalid console id");
            return false;
        }

        LocalCoordinator target = getCoordinator(targetId);
        consoles.remove(message.getConsoleId());

        log.info("Detaching from console " + message.getConsoleId());
        if(target == null || target.getChannel() == null || !target.getChannel().isActive()) {
            return true;
        }

        log.info("Detaching client " + target.getUuid() + " from console");

        return c_sendDetachConsole(target.getUuid());
    }

    protected boolean c_processGetCoordinatorList(TransactionInfo info, String from) {
        log.info(from + " requested active coordinator list");
        return c_sendCoordinatorListResponse(from, info.getId());
    }

    protected boolean c_sendCoordinatorListResponse(String target, String tid) {Commands.C_CoordinatorListResponse.Builder responseBuilder = Commands.C_CoordinatorListResponse.newBuilder();
        TransactionInfo info = TransactionManager.get().getInfo(tid);
        if(info == null) {
            log.error("Unable to send C_COORDINATOR_LIST_RESPONSE with invalid transaction " + tid);
            return false;
        }

        for(LocalCoordinator coord : coordinators.values()) {
            if(!coord.isEnabled() || coord.getChannel() == null || !coord.getChannel().isActive())
                continue;

            Coordinator.LocalCoordinator.Builder coordBuilder = Coordinator.LocalCoordinator.newBuilder()
                    .setUuid(coord.getUuid())
                    .setEnabled(coord.isEnabled())
                    .addAllAttributes(coord.getAttributes());

            if(coord.getName() != null)
                coordBuilder.setName(coord.getName());

            for(Map.Entry<String, Integer> entry : coord.getResources().entrySet()) {
                coordBuilder.addResources(Coordinator.Resource.newBuilder().setName(entry.getKey()).setValue(entry.getValue()).build());
            }

            for(Server server : coord.getServers().values()) {
                if(!server.isActive())
                    continue;

                P3.P3Meta meta = P3.P3Meta.newBuilder()
                        .setId(server.getP3().getId())
                        .setVersion(server.getP3().getVersion())
                        .build();

                Coordinator.Server.Builder serverBuilder = Coordinator.Server.newBuilder()
                        .setP3(meta)
                        .setUuid(server.getUuid());

                if(server.getName() != null)
                    serverBuilder.setName(server.getName());

                for(Map.Entry<String, String> entry : server.getProperties().entrySet()) {
                    serverBuilder.addProperties(Coordinator.Property.newBuilder().setName(entry.getKey()).setValue(entry.getValue()).build());
                }

                coordBuilder.addServers(serverBuilder.build());
            }

            responseBuilder.addCoordinators(coordBuilder.build());
        }

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.C_COORDINATOR_LIST_RESPONSE)
                .setCCoordinatorListResponse(responseBuilder.build())
                .build();

        Protocol.Transaction message = TransactionManager.get()
                .build(tid, Protocol.Transaction.Mode.COMPLETE, command);
        if(message == null) {
            log.error("Unable to build transaction for coordinator list response");
            return false;
        }

        log.info("Sending active coordinator list to " + target);

        return TransactionManager.get().send(tid, message, target);
    }

    protected boolean c_processProvision(Commands.C_Provision request, TransactionInfo info, String from) {
        log.info("Attempting provision operation (C_PROVISION)");
        P3Package p3 = packageManager.resolve(request.getP3().getId(), request.getP3().getVersion());
        if(p3 == null) {
            log.error("Unknown package for C_PROVISION " + request.getP3().getId() + " at " + request.getP3().getVersion());
            c_sendProvisionResponseFailure(from, info.getId());
            return false;
        }

        log.info("Attempting provision at client's request of " + p3.getId() + " at " + p3.getVersion());

        Map<String, String> properties = new HashMap<>();
        for(Coordinator.Property prop : request.getPropertiesList()) {
            properties.put(prop.getName(), prop.getValue());
        }

        ProvisionResult result;
        if(request.hasCoordinator()) {
            result = provision(p3, request.hasServerName() ? request.getServerName() : null, properties, request.getCoordinator());
        }
        else {
            result = provision(p3, request.hasServerName() ? request.getServerName() : null, properties);
        }

        if(result != null) {
            log.info("Provision request succeeded");
            return c_sendProvisionResponse(from, info.getId(), result.getCoordinator(), result.getServer());
        }
        else {
            log.error("Provision request failed");
            c_sendProvisionResponseFailure(from, info.getId());
            return false;
        }
    }

    protected boolean c_sendProvisionResponseFailure(String target, String tid) {
        TransactionInfo info = TransactionManager.get().getInfo(tid);
        if(info == null) {
            log.error("Cannot send C_PROVISION_RESPONSE with invalid transaction " + tid);
            return false;
        }

        Commands.C_ProvisionResponse response = Commands.C_ProvisionResponse.newBuilder()
                .setOk(false)
                .build();

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.C_PROVISION_RESPONSE)
                .setCProvisionResponse(response)
                .build();

        Protocol.Transaction message = TransactionManager.get()
                .build(tid, Protocol.Transaction.Mode.COMPLETE, command);
        if(message == null) {
            log.error("Unable to build transaction for client provision response (failure)");
            return false;
        }

        log.info("Sending C_PROVISION_RESPONSE (failure)");

        return TransactionManager.get().send(tid, message, target);
    }

    protected boolean c_sendProvisionResponse(String target, String tid, String coordinator, String server) {
        TransactionInfo info = TransactionManager.get().getInfo(tid);
        if(info == null) {
            log.error("Cannot send C_PROVISION_RESPONSE with invalid transaction " + tid);
            return false;
        }

        Commands.C_ProvisionResponse response = Commands.C_ProvisionResponse.newBuilder()
                .setOk(true)
                .setCoordinatorId(coordinator)
                .setServerId(server)
                .build();

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.C_PROVISION_RESPONSE)
                .setCProvisionResponse(response)
                .build();

        Protocol.Transaction message = TransactionManager.get()
                .build(tid, Protocol.Transaction.Mode.COMPLETE, command);
        if(message == null) {
            log.error("Unable to build transaction for client provision response");
            return false;
        }

        log.info("Sending C_PROVISION_RESPONSE");

        return TransactionManager.get().send(tid, message, target);
    }

    protected boolean c_processDeprovision(Commands.C_Deprovision depro, TransactionInfo info, String from) {
        log.info("Attempting deprovision of " + depro.getServerId() + " on " + depro.getCoordinatorId() + " on behalf of client " + from);
        if(deprovision(depro.getCoordinatorId(), depro.getServerId(), depro.getForce())) {
            return true;
        }
        else {
            log.error("Unable to deprovision " + depro.getServerId() + " on " + depro.getCoordinatorId() + " on behalf of client " + from);
            return false;
        }
    }

    protected boolean c_processShutdown(Commands.C_Shutdown shutdown, TransactionInfo info, String from) {
        log.info("Attempting shutdown of coordinator " + shutdown.getUuid() + " on behalf of client " + from);
        if(shutdownCoordinator(shutdown.getUuid())) {
            return true;
        }
        else {
            log.error("Unable to shutdown coordinator " + shutdown.getUuid() + " on behalf of client " + from);
            return false;
        }
    }

    protected boolean c_processPromote(Commands.C_Promote promote, TransactionInfo info, String from) {
        log.info("Attemping promotion of package " + promote.getP3().getId() + " at " + promote.getP3().getVersion() + " on behalf of client " + from);
        P3Package p3 = packageManager.resolve(promote.getP3().getId(), promote.getP3().getVersion());
        if(p3 == null) {
            log.error("Unable to resolve package " + promote.getP3().getId() + " at " + promote.getP3().getVersion() + " for promotion");
            return false;
        }

        return packageManager.promote(p3);
    }

    protected boolean c_processCreateCoordinator(TransactionInfo info, String from) {
        log.info("Creating coordinator on behalf of client " + from);
        LocalCoordinator coord = createCoordinator();
        if(coord == null) {
            log.error("Unable to create coordinator");
            return false;
        }

        Commands.C_CoordinatorCreated response = Commands.C_CoordinatorCreated.newBuilder()
                .setUuid(coord.getUuid())
                .setKey(coord.getKey())
                .build();

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.C_COORDINATOR_CREATED)
                .setCCoordinatorCreated(response)
                .build();

        Protocol.Transaction message = TransactionManager.get()
                .build(info.getId(), Protocol.Transaction.Mode.COMPLETE, command);
        if(message == null) {
            log.error("Unable to create message for coordinator created");
            return false;
        }

        log.info("Sending C_COORDINATOR_CREATED");

        return TransactionManager.get().send(info.getId(), message, from);
    }

    protected boolean c_processSendInput(Commands.C_SendInput protoInput, TransactionInfo info, String from) {
        log.info("Sending input to " + protoInput.getServerId() + " on coordinator " + protoInput.getCoordinatorId() + " on behalf of client " + from);
        return sendInput(protoInput.getCoordinatorId(), protoInput.getServerId(), protoInput.getInput());
    }

    protected boolean c_processAttachConsole(Commands.C_AttachConsole message, TransactionInfo info, String from) {
        LocalCoordinator coord = getCoordinator(message.getCoordinatorId());
        if(coord == null) {
            log.error("Unable to process C_ATTACH_CONSOLE with invalid target " + message.getCoordinatorId());
            c_sendDetachConsole(from);
            return false;
        }

        Server server = coord.getServer(message.getServerId());
        if(server == null) {
            log.error("Unable to process C_ATTACH_CONSOLE with invalid server " + message.getServerId() + " on " + message.getCoordinatorId());
            c_sendDetachConsole(from);
            return false;
        }

        String consoleId = UUID.randomUUID().toString();
        while(consoles.containsKey(consoleId))
            consoleId = UUID.randomUUID().toString();

        consoles.put(consoleId, from);
        if(!sendAttachConsole(coord.getUuid(), server.getUuid(), consoleId)) {
            consoles.remove(consoleId);
            c_sendDetachConsole(from);
            return false;
        }

        return true;
    }

    protected boolean c_sendConsoleMessage(String target, String consoleMessage) {
        LocalCoordinator coord = getCoordinator(target);
        if(coord == null) {
            log.error("Cannot send C_CONSOLE_MESSAGE to invalid coordinator " + target);
            return false;
        }

        Commands.C_ConsoleMessage cm = Commands.C_ConsoleMessage.newBuilder()
                .setValue(consoleMessage)
                .build();

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.C_CONSOLE_MESSAGE)
                .setCConsoleMessage(cm)
                .build();

        TransactionInfo info = TransactionManager.get().begin();

        Protocol.Transaction message = TransactionManager.get()
                .build(info.getId(), Protocol.Transaction.Mode.SINGLE, command);
        if(message == null) {
            log.error("Unable to create transaction for C_CONSOLE_MESSAGE");
            TransactionManager.get().cancel(info.getId());
            return false;
        }

        return TransactionManager.get().send(info.getId(), message, coord.getUuid());
    }

    protected boolean c_sendDetachConsole(String target) {
        LocalCoordinator coord = getCoordinator(target);
        if(coord == null) {
            log.error("Cannot send C_DETACH_CONSOLE to invalid coordinator " + target);
            return false;
        }

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.C_DETACH_CONSOLE)
                .build();

        TransactionInfo info = TransactionManager.get().begin();

        Protocol.Transaction message = TransactionManager.get()
                .build(info.getId(), Protocol.Transaction.Mode.SINGLE, command);
        if(message == null) {
            log.error("Unable to create transaction for C_DETACH_CONSOLE");
            TransactionManager.get().cancel(info.getId());
            return false;
        }

        return TransactionManager.get().send(info.getId(), message, coord.getUuid());
    }

    protected boolean c_processDetachConsole(TransactionInfo info, String from) {
        log.info("Detaching " + from + " from all consoles");
        Iterator<Map.Entry<String, String>> itr = consoles.entrySet().iterator();
        while(itr.hasNext()) {
            Map.Entry<String, String> entry = itr.next();
            if(from.equals(entry.getValue()))
                itr.remove();

        }

        return true;
    }
}
