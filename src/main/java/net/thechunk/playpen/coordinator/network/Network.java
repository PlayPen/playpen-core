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
import lombok.extern.log4j.Log4j2;
import net.thechunk.playpen.Bootstrap;
import net.thechunk.playpen.Initialization;
import net.thechunk.playpen.coordinator.PlayPen;
import net.thechunk.playpen.networking.TransactionInfo;
import net.thechunk.playpen.networking.TransactionManager;
import net.thechunk.playpen.networking.netty.AuthenticatedMessageInitializer;
import net.thechunk.playpen.p3.P3Package;
import net.thechunk.playpen.p3.PackageManager;
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

    private Map<String, LocalCoordinator> coordinators = new ConcurrentHashMap<>();

    private PackageManager packageManager = null;

    private ScheduledExecutorService scheduler = null;

    private Network() {
        super();
        packageManager = new PackageManager();
        Initialization.packageManager(packageManager);
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
            f.channel().closeFuture().sync();
        }
        catch(InterruptedException e) {
            log.warn("Operation interrupted!", e);
            return false;
        }
        finally {
            scheduler.shutdownNow();
            scheduler = null;

            group.shutdownGracefully();
        }

        return true;
    }

    public LocalCoordinator getCoordinator(String id) {
        return coordinators.getOrDefault(id, null);
    }

    @Override
    public String getServerId() {
        return "net";
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


            case C_GET_COORDINATOR_LIST:
                return c_processGetCoordinatorList(info, from);
        }
    }

    @Override
    public void onVMShutdown() {
        log.info("VM shutting down");

        scheduler.shutdownNow();

        for(LocalCoordinator coord : coordinators.values()) {
            if(coord.getChannel() != null && coord.getChannel().isOpen()) {
                coord.getChannel().close().syncUninterruptibly();
            }
        }
    }

    public LocalCoordinator createCoordinator() {
        LocalCoordinator coord = new LocalCoordinator();
        coord.setEnabled(false);
        coord.setUuid(UUID.randomUUID().toString());
        coord.setKey(UUID.randomUUID().toString());
        coordinators.put(coord.getUuid(), coord);

        log.info("Generated new coordinator keypair " + coord.getUuid());

        saveKeystore();
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
    public synchronized boolean provision(P3Package p3, String serverName, Map<String, String> properties) {
        LocalCoordinator coord = selectCoordinator(p3);
        if(coord == null) {
            log.error("Unable to select a coordinator for a provisioning operation");
            return false;
        }

        return provision(p3, serverName, properties, coord.getUuid());
    }

    public boolean provision(P3Package p3, String serverName, Map<String, String> properties, String target) {
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
        
        return true;
    }

    protected synchronized boolean sendProvision(String target, P3Package p3, String name, Map<String, String> properties) {
        if(!p3.isResolved()) {
            log.error("Cannot pass an unresolved package to sendProvision");
            return false;
        }

        LocalCoordinator coord = getCoordinator(target);
        if(coord == null) {
            log.error("Unknown coordinator " + target + " for sendProvision");
            return false;
        }

        if(!coord.isEnabled()) {
            log.error("Coordinator " + target + " is not enabled for sendProvision");
            return false;
        }

        Server server = coord.createServer(p3, name, properties);
        if(server == null) {
            log.error("Unable to register server locally before sending for sendProvision");
            return false;
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
            return false;
        }

        log.info("Sending provision of " + p3.getId() + " at " + p3.getVersion() + " to " + coord.getUuid() + ", creating server " + server.getUuid());

        return TransactionManager.get().send(info.getId(), message, target);
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
        Server server = coord.getServers().getOrDefault(serverId, null);
        if(server == null) {
            log.error("Unknown server " + serverId + " on PROVISION_RESPONSE");
            return false;
        }

        if(command.getOk()) {
            server.setActive(true);
            log.info("Server " + serverId + " on " + coord.getUuid() + " has been activated (provision response)");
            return true;
        }
        else {
            coord.getServers().remove(serverId);
            log.warn("Server " + serverId + " on " + coord.getUuid() + " failed to activate (provision response)");
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

        Server server = coord.getServers().getOrDefault(serverId, null);
        if(server == null) {
            log.error("Cannot send DEPROVISION on invalid server " + serverId + " on coordinator " + target);
            return false;
        }

        server.setActive(false);

        Commands.Deprovision deprovision = Commands.Deprovision.newBuilder()
                .setUuid(serverId)
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

        log.info("Deprovisioning " + serverId + " on coordinator " + target);

        return TransactionManager.get().send(info.getId(), message, target);
    }

    protected boolean processServerShutdown(Commands.ServerShutdown command, TransactionInfo info, String from) {
        LocalCoordinator coord = getCoordinator(from);
        if(coord == null) {
            log.error("Cannot process SERVER_SHUTDOWN on invalid coordinator " + from);
            return false;
        }

        Server server = coord.getServers().getOrDefault(command.getUuid(), null);
        if(server == null) {
            log.error("Cannot process SERVER_SHUTDOWN on invalid server " + command.getUuid() + " on coordinator " + from);
            return false;
        }

        server.setActive(false);
        coord.getServers().remove(server.getUuid());
        log.info("Server " + server.getUuid() + " shutdown on " + coord.getUuid());

        return true;
    }

    protected boolean sendShutdown(String target) {
        LocalCoordinator coord = getCoordinator(target);
        if(coord == null) {
            log.error("Cannot send SHUTDOWN on invalid coordinator " + target);
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

        return TransactionManager.get().send(info.getId(), message, target);
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
            if(!coord.isEnabled())
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
}
