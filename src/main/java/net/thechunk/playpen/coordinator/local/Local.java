package net.thechunk.playpen.coordinator.local;

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
import net.thechunk.playpen.Initialization;
import net.thechunk.playpen.coordinator.CoordinatorMode;
import net.thechunk.playpen.coordinator.PlayPen;
import net.thechunk.playpen.networking.TransactionInfo;
import net.thechunk.playpen.networking.TransactionManager;
import net.thechunk.playpen.networking.netty.AuthenticatedMessageInitializer;
import net.thechunk.playpen.p3.ExecutionType;
import net.thechunk.playpen.p3.P3Package;
import net.thechunk.playpen.p3.PackageManager;
import net.thechunk.playpen.p3.resolver.LocalRepositoryResolver;
import net.thechunk.playpen.plugin.PluginManager;
import net.thechunk.playpen.protocol.Commands;
import net.thechunk.playpen.protocol.Coordinator;
import net.thechunk.playpen.protocol.P3;
import net.thechunk.playpen.protocol.Protocol;
import net.thechunk.playpen.utils.AuthUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

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

    private ScheduledExecutorService scheduler = null;

    @Getter
    private String coordName;

    @Getter
    private String uuid;

    @Getter
    private String key;

    @Getter
    private Map<String, Integer> resources = new ConcurrentHashMap<>();

    @Getter
    private Set<String> attributes = new ConcurrentSkipListSet<>();

    @Getter
    private boolean enabled = true;

    @Getter
    private Channel channel = null;

    private boolean shuttingDown = false;

    private Map<String, ConsoleMessageListener> consoles = new ConcurrentHashMap<>();

    private Map<P3Package.P3PackageInfo, CountDownLatch> downloadMap = new ConcurrentHashMap<>();

    private Local() {
        super();
        packageManager = new PackageManager();
        Initialization.packageManager(packageManager);
        packageManager.setFallbackResolver(new PackageDownloadResolver());

        log.info("Removing old server installations");
        try {
            File serversDir = Paths.get(Bootstrap.getHomeDir().toString(), "servers").toFile();
            FileUtils.deleteDirectory(serversDir);
            serversDir.mkdirs();
        }
        catch(IOException e) {
            log.warn("Unable to remove old server installations", e);
        }

        log.info("Clearing package cache");
        try {
            File cacheDir = Paths.get(Bootstrap.getHomeDir().toString(), "cache", "packages").toFile();
            FileUtils.deleteDirectory(cacheDir);
            cacheDir.mkdirs();
        }
        catch(IOException e) {
            log.warn("Unable to clear package cache", e);
        }

        log.info("Clearing assets");
        try {
            File assetsDir = Paths.get(Bootstrap.getHomeDir().toString(), "assets").toFile();
            FileUtils.deleteDirectory(assetsDir);
            assetsDir.mkdirs();
        }
        catch(IOException e) {
            log.warn("Unable to clear assets", e);
        }

        log.info("Clearing temporary files");
        try {
            File tempDir = Paths.get(Bootstrap.getHomeDir().toString(), "temp").toFile();
            FileUtils.deleteDirectory(tempDir);
            tempDir.mkdirs();
        }
        catch(IOException e) {
            log.warn("Unable to clear temporary files", e);
        }
    }

    public boolean run() {
        enabled = true;
        resources.clear();
        attributes.clear();

        log.info("Reading local configuration");
        String configStr;
        try {
            configStr = new String(Files.readAllBytes(Paths.get(Bootstrap.getHomeDir().getPath(), "local.json")));
        }
        catch(IOException e) {
            log.fatal("Unable to read configuration file", e);
            return false;
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

            JSONObject res = config.getJSONObject("resources");
            for(String key : res.keySet()) {
                resources.put(key, res.getInt(key));
            }

            JSONArray attr = config.getJSONArray("attributes");
            for(int i = 0; i < attr.length(); ++i) {
                attributes.add(attr.getString(i));
            }
        }
        catch(Exception e) {
            log.fatal("Unable to read configuration file", e);
            return false;
        }

        if(uuid == null || uuid.isEmpty() || key == null || key.isEmpty()) {
            log.fatal("No UUID or secret key specified in local.json");
            return false;
        }

        if(coordName == null) {
            log.warn("No coordinator name specified in local.json"); // not fatal
        }

        log.info("Starting local coordinator " + uuid);
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
                return true; // let's retry!
            }

            channel = f.channel();

            log.info("Connected to network coordinator at " + coordIp + " port " + coordPort);

            sync();

            log.info("Scheduling SYNC for every 90 seconds");
            scheduler.scheduleAtFixedRate(() -> Local.get().sync(), 90, 90, TimeUnit.SECONDS);

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

        return !shuttingDown;
    }

    @Override
    public void onVMShutdown() {
        log.info("VM shutting down, shutting down all servers (force)");

        if(scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }

        for(Server server : servers.values()) {
            shutdownServer(server.getUuid(), true, false);
        }

        if(channel != null && channel.isOpen()) {
            channel.close().syncUninterruptibly();
        }
    }

    public Map<String, Integer> getAvailableResources() {
        Map<String, Integer> used = new HashMap<>();
        for(Map.Entry<String, Integer> entry : resources.entrySet()) {
            Integer value = entry.getValue();
            for(Server server : servers.values()) {
                value -= server.getP3().getResources().getOrDefault(entry.getKey(), 0);
            }

            used.put(entry.getKey(), value);
        }

        return used;
    }

    public boolean canProvisionPackage(P3Package p3) {
        for(String attr : p3.getAttributes()) {
            if(!attributes.contains(attr)) {
                log.warn("Missing attribute " + attr + " for " + p3.getId() + " at " + p3.getVersion());
                return false;
            }
        }

        Map<String, Integer> resources = getAvailableResources();
        for(Map.Entry<String, Integer> entry : p3.getResources().entrySet()) {
            if(!resources.containsKey(entry.getKey())) {
                log.warn("Missing resource " + entry.getKey() + " for " + p3.getId() + " at " + p3.getVersion());
                return false;
            }

            if(resources.get(entry.getKey()) - entry.getValue() < 0) {
                log.warn("Not enough of resource " + entry.getKey() + " for " + p3.getId() + " at " + p3.getVersion());
                return false;
            }
        }

        return true;
    }

    public Server getServer(String idOrName) {
        if(servers.containsKey(idOrName))
            return servers.get(idOrName);

        for(Server server : servers.values()) {
            if(server.getName() != null && server.getName().equals(idOrName))
                return server;
        }

        return null;
    }

    public void notifyServerShutdown(String id) {
        Server server = getServer(id);
        if(server == null) {
            log.error("Unable to notify for server shutdown (invalid id: " + id + ")");
            return;
        }

        log.info("LC notify server shutdown");

        if(!sendServerShutdown(id)) {
            log.error("Unable to notify network coordinator of server shutdown");
        }

        if(server.isFreezeOnShutdown()) {
            log.info("Server " + id + " is freezing...");
            try {
                File dest = Paths.get(Bootstrap.getHomeDir().getPath(), "frozen", server.getUuid()).toFile();
                FileUtils.copyDirectory(new File(server.getLocalPath()), dest);

                FileUtils.copyFile(Paths.get(Bootstrap.getHomeDir().getPath(), "server-logs", server.getUuid() + ".log").toFile(),
                        Paths.get(dest.getPath(), "playpen_server.log").toFile());
            }
            catch(IOException e) {
                log.error("Unable to freeze server " + id, e);
            }
        }

        log.info("Deleting server " + id + " from disk");

        try {
            FileUtils.deleteDirectory(new File(server.getLocalPath()));
        }
        catch(IOException e) {
            log.warn("Unable to remove server " + id + " from disk, ignoring.");
        }

        servers.remove(id);
    }

    public void shutdownCoordinator() {
        log.info("Shutting down coordinator");
        shuttingDown = true;

        enabled = false;
        sendSync();

        for(Server server : servers.values()) {
            shutdownServer(server.getUuid(), false, false);
        }

        if(channel != null && channel.isOpen()) {
            channel.close();
        }
    }

    public void shutdownServer(String id) {
        shutdownServer(id, false, true);
    }

    public void shutdownServer(String id, boolean force) {
        shutdownServer(id, force, true);
    }

    public void shutdownServer(String id, boolean force, boolean notify) {
        log.info("Shutting down server " + id + " (force: " + (force ? "yes" : "no") + ")");
        Server server = getServer(id);
        if(force) {
            if(server.getProcess() != null && server.getProcess().isRunning()) {
                server.getProcess().stop();
            }
        }
        else {
            if(!packageManager.execute(ExecutionType.SHUTDOWN, server.getP3(), new File(server.getLocalPath()), server.getProperties(), server)) {
                log.info("Unable to run normal shutdown process on server " + id + ", forcing");
                if(server.getProcess() != null && server.getProcess().isRunning()) {
                    server.getProcess().stop();
                }
            }
        }

        if(notify) {
            notifyServerShutdown(id);
        }
    }

    @Override
    public String getServerId() {
        return coordName;
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
    public PluginManager getPluginManager() {
        log.error("PlayPen local does not currently support the plugin system!");
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
        byte[] encBytes = AuthUtils.encrypt(messageBytes.toByteArray(), key);
        messageBytes = ByteString.copyFrom(encBytes);

        Protocol.AuthenticatedMessage auth = Protocol.AuthenticatedMessage.newBuilder()
                .setUuid(uuid)
                .setHash(AuthUtils.createHash(key, messageBytes.toByteArray()))
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
            return false;
        }

        TransactionManager.get().receive(transaction, null);
        return true;
    }

    @Override
    public boolean process(Commands.BaseCommand command, TransactionInfo info, String from) {
        switch(command.getType()) {
            default:
                log.error("Local coordinator cannot process command " + command.getType());
                return false;

            case PROVISION:
                return processProvision(command.getProvision(), info);

            case PACKAGE_RESPONSE:
                return processPackageResponse(command.getPackageResponse(), info);

            case DEPROVISION:
                return processDeprovision(command.getDeprovision(), info);

            case SHUTDOWN:
                return processShutdown(info);

            case SEND_INPUT:
                return processSendInput(command.getSendInput(), info);

            case ATTACH_CONSOLE:
                return processAttachConsole(command.getAttachConsole(), info);

            case DETACH_CONSOLE:
                return processDetachConsole(command.getDetachConsole(), info);

            case FREEZE_SERVER:
                return processFreezeServer(command.getFreezeServer(), info);

            case EXPIRE_CACHE:
                return processExpireCache(command.getExpireCache(), info);
        }
    }

    public boolean sync() {
        return sendSync();
    }

    public boolean provision(P3Package p3, String uuid, Map<String, String> properties) {
        return provision(p3, uuid, properties, null);
    }

    public boolean provision(P3Package p3, String uuid, Map<String, String> properties, String name) {
        if(!p3.isResolved()) {
            log.error("Cannot provision unresolved package " + p3.getId() + " at " + p3.getVersion());
            return false;
        }

        if(!canProvisionPackage(p3)) {
            log.error("Cannot provision package " + p3.getId() + " at " + p3.getVersion());
            return false;
        }

        File destination = Paths.get(Bootstrap.getHomeDir().getPath(), "servers", uuid).toFile();
        if(destination.exists()) {
            log.error("Cannot provision into directory that already exists! " + destination.toString());
            return false;
        }

        destination.mkdirs();

        Server server = new Server();
        server.setP3(p3);
        server.setUuid(uuid);
        server.setName(name);
        server.getProperties().putAll(properties);
        server.setLocalPath(destination.toString());

        if(properties.containsKey("frozen") && "true".equalsIgnoreCase(properties.get("frozen"))) {
            server.setFreezeOnShutdown(true);
            log.info("Server " + server.getName() + " will be frozen on shutdown (props)");
        }

        if(!packageManager.execute(ExecutionType.PROVISION, p3, destination, server.getProperties(), server)) {
            log.error("Unable to provision server " + uuid + " (package manager failed provision operation)");
            try {
                FileUtils.deleteDirectory(destination);
            }
            catch(IOException e) {
                log.error("Unable to clean up directory " + destination.toString());
            }

            return false;
        }

        try {
            String schema = new String(Files.readAllBytes(Paths.get(server.getLocalPath(), "package.json")));
            p3 = Local.get().getPackageManager().readSchema(schema);
        }
        catch(Exception e) {
            log.error("Encountered exception while loading P3 from server's local directory, using packaged instead of provisioned schema", e);
        }

        server.setP3(p3);

        servers.put(server.getUuid(), server);

        log.info("Provisioned server " + uuid + ", executing!");
        new ServerExecutionThread(server).start();
        return true;
    }

    public boolean detachConsole(String consoleId) {
        ConsoleMessageListener listener = consoles.getOrDefault(consoleId, null);
        if(listener == null) {
            log.error("Cannot detach unknown console " + consoleId);
            return false;
        }
        else {
            log.info("Detaching console " + consoleId);
            consoles.remove(consoleId);
            listener.remove();
            sendDetachConsole(consoleId);
            return true;
        }
    }

    protected boolean sendSync() {
        Commands.Sync.Builder syncBuilder = Commands.Sync.newBuilder()
                .setEnabled(enabled);

        if(coordName != null)
            syncBuilder.setName(coordName);

        for(Map.Entry<String, Integer> entry : resources.entrySet()) {
            Coordinator.Resource resource = Coordinator.Resource.newBuilder()
                    .setName(entry.getKey())
                    .setValue(entry.getValue())
                    .build();

            syncBuilder.addResources(resource);
        }

        syncBuilder.addAllAttributes(attributes);

        for(Server server : servers.values()) {
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
                Coordinator.Property prop = Coordinator.Property.newBuilder()
                        .setName(entry.getKey())
                        .setValue(entry.getValue())
                        .build();

                serverBuilder.addProperties(prop);
            }

            syncBuilder.addServers(serverBuilder.build());
        }

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

    protected boolean processProvision(Commands.Provision command, TransactionInfo info) {
        Coordinator.Server server = command.getServer();

        if(servers.containsKey(server.getUuid())) {
            log.error("PROVISION contained existing server uuid");
            sendProvisionResponse(info.getId(), false);
            return false;
        }

        final Map<String, String> properties = new HashMap<>();
        for(Coordinator.Property prop : server.getPropertiesList()) {
            properties.put(prop.getName(), prop.getValue());
        }

        final String id = server.getP3().getId();
        final String version = server.getP3().getVersion();
        final String uuid = server.getUuid();
        final String name = server.hasName() ? server.getName() : null;
        final String tid = info.getId();
        scheduler.schedule(() -> Local.get().checkPackageForProvision(tid, id, version, uuid, properties, name), 2, TimeUnit.SECONDS);

        return true;
    }

    protected boolean sendProvisionResponse(String tid, boolean ok) {
        TransactionInfo info = TransactionManager.get().getInfo(tid);
        if(info == null) {
            log.error("Unknown transaction " + tid + ", unable to send provision response");
            return false;
        }

        Commands.ProvisionResponse response = Commands.ProvisionResponse.newBuilder()
                .setOk(ok)
                .build();

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.PROVISION_RESPONSE)
                .setProvisionResponse(response)
                .build();

        Protocol.Transaction message = TransactionManager.get()
                .build(info.getId(), Protocol.Transaction.Mode.COMPLETE, command);
        if(message == null) {
            log.error("Unable to build message for provision response");
            return false;
        }

        log.info("Sending provision response (" + (ok ? "ok" : "not ok") + ")");

        return TransactionManager.get().send(info.getId(), message, null);
    }

    protected boolean sendPackageRequest(String tid, String id, String version) {
        P3.P3Meta meta = P3.P3Meta.newBuilder()
                .setId(id)
                .setVersion(version)
                .build();

        Commands.PackageRequest request = Commands.PackageRequest.newBuilder()
                .setP3(meta)
                .build();

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.PACKAGE_REQUEST)
                .setPackageRequest(request)
                .build();

        TransactionInfo info = TransactionManager.get().begin();

        Protocol.Transaction message = TransactionManager.get()
                .build(info.getId(), Protocol.Transaction.Mode.CREATE, command);
        if(message == null) {
            log.error("Unable to build message for package request");
            TransactionManager.get().cancel(info.getId());
            return false;
        }

        log.info("Sending package request of " + id + " at " + version);
        return TransactionManager.get().send(info.getId(), message, null);
    }

    protected boolean processPackageResponse(Commands.PackageResponse response, TransactionInfo info) {
        if(!response.getOk()) {
            log.error("Received non-ok package response");
            return false;
        }

        File tmpDest = Paths.get(
                Bootstrap.getHomeDir().getPath(),
                "temp",
                UUID.randomUUID() + ".p3").toFile();
        File trueDest = Paths.get(
                Bootstrap.getHomeDir().getPath(),
                "cache", "packages",
                response.getData().getMeta().getId() + "_" + response.getData().getMeta().getVersion() + ".p3").toFile();

        if(tmpDest.exists()) {
            log.error("Cannot write package to existing file " + tmpDest);
            return false;
        }

        log.info("Writing received package " + response.getData().getMeta().getId() + " at " + response.getData().getMeta().getVersion() + " to temp");

        try (FileOutputStream output = new FileOutputStream(tmpDest)) {
            IOUtils.write(response.getData().getData().toByteArray(), output);
        }
        catch(IOException e) {
            log.error("Unable to write package to " + tmpDest, e);
            return false;
        }

        if(trueDest.exists()) {
            log.error("Cannot move package to existing file " + trueDest);
            return false;
        }

        log.info("Moving package " + response.getData().getMeta().getId() +  " at " + response.getData().getMeta().getVersion() + " to cache");

        try {
            Files.move(tmpDest.toPath(), trueDest.toPath());
        }
        catch(IOException e) {
            log.error("Cannot move package to " + trueDest, e);
            return false;
        }

        P3Package.P3PackageInfo p3info = new P3Package.P3PackageInfo();
        p3info.setId(response.getData().getMeta().getId());
        p3info.setVersion(response.getData().getMeta().getVersion());
        CountDownLatch latch = downloadMap.getOrDefault(p3info, null);
        if(latch != null) {
            latch.countDown();
        }

        return true;
    }

    protected boolean processDeprovision(Commands.Deprovision command, TransactionInfo info) {
        Server server = getServer(command.getUuid());
        if(server == null) {
            log.error("Unknown server for DEPROVISION " + command.getUuid());
            return false;
        }

        shutdownServer(command.getUuid(), command.getForce(), false); // do not notify, since the server stopping will
                                                                      // do that anyway.
        return true;
    }

    protected boolean sendServerShutdown(String id) {
        if(shuttingDown)
            return true;

        Server server = getServer(id);
        if(server == null) {
            log.error("Cannot send SERVER_SHUTDOWN with invalid server id " + id);
            return false;
        }

        Commands.ServerShutdown shutdown = Commands.ServerShutdown.newBuilder()
                .setUuid(id)
                .build();

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.SERVER_SHUTDOWN)
                .setServerShutdown(shutdown)
                .build();

        TransactionInfo info = TransactionManager.get().begin();
        Protocol.Transaction message = TransactionManager.get()
                .build(info.getId(), Protocol.Transaction.Mode.SINGLE, command);
        if(message == null) {
            log.error("Unable to build transaction for SERVER_SHUTDOWN");
            TransactionManager.get().cancel(info.getId());
            return false;
        }

        log.info("Sending server shutdown notice to network coordinator");

        return TransactionManager.get().send(info.getId(), message, null);
    }

    protected boolean processShutdown(TransactionInfo info) {
        log.info("SHUTDOWN received, closing everything");
        shutdownCoordinator();
        return true;
    }

    protected boolean processSendInput(Commands.SendInput protoInput, TransactionInfo info) {
        Server server = getServer(protoInput.getId());
        if(server == null) {
            log.error("Cannot send input to invalid server " + protoInput.getId());
            return false;
        }

        if(server.getProcess() == null || !server.getProcess().isRunning()) {
            log.warn("Cannot send input to server " + server.getUuid() + " (process not running)");
            return false;
        }

        log.info("Sending input to server " + server.getUuid());
        server.getProcess().sendInput(protoInput.getInput());

        return true;
    }

    protected boolean processAttachConsole(Commands.AttachConsole message, TransactionInfo info) {
        Server server = getServer(message.getServerId());
        if(server == null) {
            log.error("Cannot ATTACH_CONSOLE to unknown server " + message.getServerId());
            sendDetachConsole(message.getConsoleId());
            return false;
        }

        if(consoles.containsKey(message.getConsoleId())) {
            log.warn("Received ATTACH_CONSOLE with existing console id, ignoring");
            return false;
        }

        if(server.getProcess() == null || !server.getProcess().isRunning()) {
            log.warn("Server " + server.getUuid() + " has no listenable process");
            sendDetachConsole(message.getConsoleId());
            return false;
        }

        log.info("Attaching console " + message.getConsoleId() + " to server " + server.getUuid());
        ConsoleMessageListener listener = new ConsoleMessageListener(message.getConsoleId());
        consoles.put(message.getConsoleId(), listener);
        server.getProcess().addListener(listener);

        return true;
    }

    public boolean sendConsoleMessage(String consoleId, String consoleMessage) { // yes, that's public
        if(!consoles.containsKey(consoleId)) {
            log.error("Cannot send CONSOLE_MESSAGE with invalid console id " + consoleId);
            return false;
        }

        Commands.ConsoleMessage cm = Commands.ConsoleMessage.newBuilder()
                .setConsoleId(consoleId)
                .setValue(consoleMessage)
                .build();

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.CONSOLE_MESSAGE)
                .setConsoleMessage(cm)
                .build();

        TransactionInfo info = TransactionManager.get().begin();

        Protocol.Transaction message = TransactionManager.get()
                .build(info.getId(), Protocol.Transaction.Mode.SINGLE, command);
        if(message == null) {
            log.error("Unable to create transaction for CONSOLE_MESSAGE");
            TransactionManager.get().cancel(info.getId());
            return false;
        }

        return TransactionManager.get().send(info.getId(), message, null);
    }

    protected boolean sendDetachConsole(String consoleId) {
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
            log.error("Unable to create transaction for DETACH_CONSOLE");
            TransactionManager.get().cancel(info.getId());
            return false;
        }

        log.info("Sending DETACH_CONSOLE for " + consoleId);

        return TransactionManager.get().send(info.getId(), message, null);
    }

    protected boolean processDetachConsole(Commands.DetachConsole message, TransactionInfo info) {
        return detachConsole(message.getConsoleId());
    }

    protected boolean processFreezeServer(Commands.FreezeServer message, TransactionInfo info) {
        Server server = getServer(message.getUuid());
        if(server == null) {
            log.error("Cannot freeze unknown server " + message.getUuid());
            return false;
        }

        server.setFreezeOnShutdown(true);
        log.info("Server " + server.getName() + " will have its files frozen on shutdown");
        return true;
    }

    protected boolean processExpireCache(Commands.ExpireCache message, TransactionInfo info) {
        log.info("Expiring cache for " + message.getP3().getId() + " (" + message.getP3().getVersion() + ")");

        P3Package p3 = getPackageManager().resolve(message.getP3().getId(), message.getP3().getVersion(), false);
        if(p3 == null)
            return true;

        P3Package.P3PackageInfo p3info = new P3Package.P3PackageInfo();
        p3info.setId(p3.getId());
        p3info.setVersion(p3.getVersion());

        File file = new File(p3.getLocalPath());
        file.delete();
        getPackageManager().getPackageCache().remove(p3info);

        return true;
    }

    protected void checkPackageForProvision(String tid, String id, String version, String uuid, Map<String, String> properties, String name) {
        TransactionInfo info = TransactionManager.get().getInfo(tid);
        if(info == null) {
            log.error("Cannot download package for provision with an invalid transaction id " + tid);
            return;
        }

        P3Package p3 = packageManager.resolve(id, version);

        if(p3 == null) {
            sendProvisionResponse(tid, false);
            return;
        }

        if(provision(p3, uuid, properties, name)) {
            sendProvisionResponse(tid, true);
        }
        else {
            sendProvisionResponse(tid, false);
        }
    }

    @Log4j2
    private static class PackageDownloadResolver extends LocalRepositoryResolver {
        private final Object downloadLock = new Object();

        public PackageDownloadResolver() {
            super(Paths.get(Bootstrap.getHomeDir().getPath(), "cache", "packages").toFile());
        }

        @Override
        public P3Package resolvePackage(PackageManager pm, String id, String version) {
            log.info("Attempting package download for " + id + " at " + version);

            P3Package.P3PackageInfo p3info = new P3Package.P3PackageInfo();
            p3info.setId(id);
            p3info.setVersion(version);

            CountDownLatch latch = null;
            synchronized(downloadLock) {
                latch = Local.get().downloadMap.getOrDefault(p3info, null);

                if (latch == null) {
                    TransactionInfo info = TransactionManager.get().begin();

                    if (!Local.get().sendPackageRequest(info.getId(), id, version)) {
                        log.error("Unable to send package request for " + id + " at " + version + "");
                        return null;
                    }

                    latch = new CountDownLatch(1);
                    Local.get().downloadMap.put(p3info, latch);
                }
            }

            log.info("Waiting up to 60 seconds for package download");

            try {
                latch.await(60, TimeUnit.SECONDS);
            }
            catch(InterruptedException e) {
                log.error("Interrupted while waiting for package download");
                return null;
            }
            finally {
                Local.get().downloadMap.remove(p3info);
            }

            P3Package p3 = super.resolvePackage(pm, id, version);

            if(p3 == null) {
                log.error("Unable to download package " + id + " (" + version + ")");
                return null;
            }

            return p3;
        }
    }
}
