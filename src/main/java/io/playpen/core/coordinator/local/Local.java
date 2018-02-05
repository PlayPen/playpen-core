package io.playpen.core.coordinator.local;

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
import io.playpen.core.p3.ExecutionType;
import io.playpen.core.p3.P3Package;
import io.playpen.core.p3.PackageException;
import io.playpen.core.p3.PackageManager;
import io.playpen.core.p3.resolver.LocalRepositoryResolver;
import io.playpen.core.plugin.PluginManager;
import io.playpen.core.protocol.Commands;
import io.playpen.core.protocol.Coordinator;
import io.playpen.core.protocol.P3;
import io.playpen.core.protocol.Protocol;
import io.playpen.core.utils.AuthUtils;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
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

    private Map<String, Coordinator.Server> provisioningServers = new ConcurrentHashMap<>();

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
    private Map<String, String> localStrings = new ConcurrentHashMap<>();

    @Getter
    private boolean enabled = true;

    @Getter
    private boolean useNameForLogs = true;

    @Getter
    private Channel channel = null;

    private boolean shuttingDown = false;

    private Map<String, ConsoleMessageListener> consoles = new ConcurrentHashMap<>();

    private Map<P3Package.P3PackageInfo, CountDownLatch> downloadMap = new ConcurrentHashMap<>();

    private final Object chunkLock = new Object();
    private Map<P3Package.P3PackageInfo, Semaphore> packageChunkLocks = new ConcurrentHashMap<>();

    private Map<String, String> checksumMap = new ConcurrentHashMap<>();
    private Map<String, CountDownLatch> checksumLatches = new ConcurrentHashMap<>();

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
        localStrings.clear();

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
            useNameForLogs = config.getBoolean("use-name-for-logs");

            JSONObject res = config.getJSONObject("resources");
            for(String key : res.keySet()) {
                resources.put(key, res.getInt(key));
            }

            JSONArray attr = config.getJSONArray("attributes");
            for(int i = 0; i < attr.length(); ++i) {
                attributes.add(attr.getString(i));
            }

            JSONObject strings = config.optJSONObject("strings");
            if (strings != null) {
                for (String key : strings.keySet()) {
                    localStrings.put(key, strings.getString(key));
                }
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

        if(channel != null) {
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
                File dest = Paths.get(Bootstrap.getHomeDir().getPath(), "frozen", (useNameForLogs ? server.getSafeName() : server.getUuid())).toFile();
                if (dest.exists())
                    FileUtils.deleteDirectory(dest);
                FileUtils.copyDirectory(new File(server.getLocalPath()), dest);

                FileUtils.copyFile(Paths.get(Bootstrap.getHomeDir().getPath(), "server-logs",
                        (useNameForLogs ? server.getName() : server.getUuid()) + ".log").toFile(),
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

            case SPLIT_PACKAGE_RESPONSE:
                return processSplitPackageResponse(command.getSplitPackageResponse(), info);

            case PACKAGE_CHECKSUM_RESPONSE:
                return processPackageChecksumResponse(command.getChecksumResponse(), info);

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
        server.setLocalPath(destination.toString());

        server.getProperties().putAll(localStrings);
        server.getProperties().putAll(properties);

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

        if(packageManager.execute(ExecutionType.EXECUTE, server.getP3(), new File(server.getLocalPath()), server.getProperties(), server)) {
            log.info("Server " + server.getUuid() + " execution completed successfully");
            return true;
        }
        else {
            log.error("Server " + server.getUuid() + " execution did not complete successfully");
            return false;
        }
    }

    public boolean detachConsole(String consoleId) {
        ConsoleMessageListener listener = consoles.get(consoleId);
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

        for (Coordinator.Server localServer : provisioningServers.values()) {
            Coordinator.Server.Builder server = Coordinator.Server.newBuilder()
                    .setUuid(localServer.getUuid())
                    .setP3(localServer.getP3())
                    .setActive(false)
                    .addAllProperties(localServer.getPropertiesList());
            if (localServer.getName() != null)
                server.setName(localServer.getName());

            syncBuilder.addServers(server.build());
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

        provisioningServers.put(server.getUuid(), server);

        scheduler.schedule(() -> {
            Local.get().checkPackageForProvision(tid, id, version, uuid, properties, name);
        }, 2, TimeUnit.SECONDS);

        return true;
    }

    protected boolean sendProvisionResponse(String tid, boolean ok) {
        TransactionInfo info = TransactionManager.get().getTransaction(tid);
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

        Path tmpDest = Paths.get(
                Bootstrap.getHomeDir().getPath(),
                "temp",
                UUID.randomUUID() + ".p3");
        Path trueDest = Paths.get(
                Bootstrap.getHomeDir().getPath(),
                "cache", "packages",
                response.getData().getMeta().getId() + "_" + response.getData().getMeta().getVersion() + ".p3");

        log.info("Writing received package " + response.getData().getMeta().getId() + " at " + response.getData().getMeta().getVersion() + " to temp");

        try (OutputStream output = Files.newOutputStream(tmpDest, StandardOpenOption.CREATE_NEW)) {
            response.getData().getData().writeTo(output);
        }
        catch(IOException e) {
            log.error("Unable to write package to " + tmpDest, e);
            return false;
        }

        // checksum
        String checksum = null;
        try {
            checksum = AuthUtils.createPackageChecksum(tmpDest.toString());
        } catch (IOException e) {
            log.error("Unable to generate checksum from downloaded package at " + tmpDest, e);
            return false;
        }

        if (!checksum.equals(response.getData().getChecksum())) {
            log.error("Checksum mismatch! Expected: " + response.getData().getChecksum() + ", got: " + checksum);
            return false;
        }


        log.info("Moving package " + response.getData().getMeta().getId() +  " at " + response.getData().getMeta().getVersion() + " to cache");

        try {
            Files.move(tmpDest, trueDest);
        }
        catch(IOException e) {
            log.error("Cannot move package to " + trueDest, e);
            return false;
        }

        P3Package.P3PackageInfo p3info = new P3Package.P3PackageInfo();
        p3info.setId(response.getData().getMeta().getId());
        p3info.setVersion(response.getData().getMeta().getVersion());
        CountDownLatch latch = downloadMap.get(p3info);
        if(latch != null) {
            latch.countDown();
        }

        return true;
    }

    protected boolean processSplitPackageResponse(Commands.SplitPackageResponse response, TransactionInfo info) {
        if(!response.getOk()) {
            log.error("Received non-ok package response");
            return false;
        }

        Path trueDest = Paths.get(
                Bootstrap.getHomeDir().getPath(),
                "cache", "packages",
                response.getData().getMeta().getId() + "_" + response.getData().getMeta().getVersion() + ".p3");

        P3.SplitPackageData data = response.getData();
        P3Package.P3PackageInfo p3info = new P3Package.P3PackageInfo();
        p3info.setId(data.getMeta().getId());
        p3info.setVersion(data.getMeta().getVersion());

        if (data.getEndOfFile()) {
            log.info("Received end of file for package " + data.getMeta().getId() + " (" + data.getMeta().getVersion() + ")");

            synchronized(chunkLock) {
                if (!packageChunkLocks.containsKey(p3info)) {
                    packageChunkLocks.put(p3info, new Semaphore(0));
                }
            }

            Thread thread = new Thread(() -> {
                try {
                    if (!packageChunkLocks.get(p3info).tryAcquire(data.getChunkCount(), 340, TimeUnit.SECONDS)) {
                        log.error("Timed out waiting for chunk download to finish");
                        return;
                    }
                } catch (InterruptedException e) {
                    log.error("Interrupted while waiting for chunk download to finish", e);
                    return;
                }
                finally {
                    packageChunkLocks.remove(p3info);
                }

                // merge all chunks
                log.info("Merging chunks...");

                Path tmpDest = Paths.get(
                        Bootstrap.getHomeDir().getPath(),
                        "temp",
                        UUID.randomUUID() + ".p3");
                try (FileChannel channel = FileChannel.open(tmpDest, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)) {
                    for (int i = 0; i < data.getChunkCount(); ++i) {
                        Path oldFile = Paths.get(
                                Bootstrap.getHomeDir().getPath(),
                                "temp",
                                "split-" + i + "-" + info.getId() + ".p3"
                        );

                        try (FileChannel chunkChannel = FileChannel.open(oldFile, StandardOpenOption.READ,
                                StandardOpenOption.DELETE_ON_CLOSE)) {
                            long currentFilePos = 0;
                            long sz = chunkChannel.size();
                            while (currentFilePos < sz) {
                                long remain = sz - currentFilePos;
                                long bytesCopied = chunkChannel.transferTo(currentFilePos, remain, channel);

                                if (bytesCopied == 0) {
                                    break;
                                }

                                currentFilePos += bytesCopied;
                            }
                        }
                    }
                }
                catch(IOException e) {
                    log.error("Unable to write package chunks to " + tmpDest, e);
                    return;
                }

                log.info("Merge finished.");

                // checksum
                String checksum = null;
                try {
                    checksum = AuthUtils.createPackageChecksum(tmpDest.toString());
                } catch (IOException e) {
                    log.error("Unable to generate checksum from downloaded package at " + tmpDest, e);
                    return;
                }

                if (!checksum.equals(data.getChecksum())) {
                    log.error("Checksum mismatch! Expected: " + data.getChecksum() + ", got: " + checksum);
                    return;
                }

                log.info("Moving package " + data.getMeta().getId() +  " at " + data.getMeta().getVersion() + " to cache");

                try {
                    Files.move(tmpDest, trueDest);
                }
                catch(IOException e) {
                    log.error("Cannot move package to " + trueDest, e);
                    return;
                }

                log.info("Finished moving package, unlocking download latch.");
                CountDownLatch latch = downloadMap.get(p3info);
                if(latch != null) {
                    latch.countDown();
                }
            });

            thread.start();

            return true;
        }

        Path tmpDest = Paths.get(
            Bootstrap.getHomeDir().getPath(),
            "temp",
            "split-" + data.getChunkId() + "-" + info.getId() + ".p3");

        log.info("Received chunk #" + response.getData().getChunkId());

        // append data
        try (OutputStream output = Files.newOutputStream(tmpDest, StandardOpenOption.CREATE_NEW)) {
            response.getData().getData().writeTo(output);
        }
        catch(IOException e) {
            log.error("Unable to write package chunk to " + tmpDest, e);
            return false;
        }

        synchronized(chunkLock) {
            if (!packageChunkLocks.containsKey(p3info)) {
                packageChunkLocks.put(p3info, new Semaphore(0));
            }
        }

        packageChunkLocks.get(p3info).release();

        return true;
    }

    protected boolean sendPackageChecksumRequest(String tid, String id, String version) {
        P3.P3Meta meta = P3.P3Meta.newBuilder()
                .setId(id)
                .setVersion(version)
                .build();

        Commands.PackageChecksumRequest request = Commands.PackageChecksumRequest.newBuilder()
                .setP3(meta)
                .build();

        Commands.BaseCommand command = Commands.BaseCommand.newBuilder()
                .setType(Commands.BaseCommand.CommandType.PACKAGE_CHECKSUM_REQUEST)
                .setChecksumRequest(request)
                .build();

        TransactionInfo info = TransactionManager.get().getTransaction(tid);

        Protocol.Transaction message = TransactionManager.get()
                .build(info.getId(), Protocol.Transaction.Mode.CREATE, command);
        if(message == null) {
            log.error("Unable to build message for package checksum request");
            TransactionManager.get().cancel(info.getId());
            return false;
        }

        log.info("Sending package checksum request of " + id + " at " + version);
        return TransactionManager.get().send(info.getId(), message, null);
    }

    protected boolean processPackageChecksumResponse(Commands.PackageChecksumResponse command, TransactionInfo info) {
        CountDownLatch latch = checksumLatches.get(info.getId());
        if (latch == null) {
            log.warn("No checksum transaction matches " + info.getId() + ", ignoring.");
            return false; // we didn't ask for this checksum, or it expired
        }

        if (!command.getOk()) {
            log.error("Received bad package checksum response.");
            latch.countDown(); // bad package
            return true;
        }

        log.info("Received package checksum: " + command.getChecksum());
        checksumMap.put(info.getId(), command.getChecksum());
        latch.countDown();
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

        Queue<String> lastLines = server.getProcess().getLastLines();
        for (String line : lastLines) {
            sendConsoleMessage(message.getConsoleId(), line);
        }

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

    protected void expireCache(String id, String version) {
        log.info("Expiring cache for " + id + " (" + version + ")");

        P3Package p3 = getPackageManager().resolve(id, version, false);

        P3Package.P3PackageInfo p3info = new P3Package.P3PackageInfo();
        p3info.setId(p3.getId());
        p3info.setVersion(p3.getVersion());

        File file = new File(p3.getLocalPath());
        file.delete();
        getPackageManager().getPackageCache().remove(p3info);
    }

    protected void checkPackageForProvision(final String tid, final String id, final String version, final String uuid,
                                            final Map<String, String> properties, final String name) {
        Thread thread = new Thread(() -> {
            try {
                TransactionInfo info = TransactionManager.get().getTransaction(tid);
                if (info == null) {
                    log.error("Cannot download package for provision with an invalid transaction id " + tid);
                    return;
                }

                P3Package p3 = packageManager.resolve(id, version);

                if (p3 == null) {
                    sendProvisionResponse(tid, false);
                    return;
                }

                // TODO: Clean up checksum stuff. Right now we check checksums even if the package was just downloaded, which
                // is a waste of time.

                Set<P3Package.P3PackageInfo> checked = new HashSet<>();
                Queue<P3Package> toCheck = new ArrayDeque<>();
                toCheck.add(p3);
                while (toCheck.peek() != null) {
                    P3Package checkOriginal = toCheck.poll();
                    P3Package check = checkOriginal;
                    P3Package.P3PackageInfo p3Info = new P3Package.P3PackageInfo();
                    p3Info.setId(check.getId());
                    p3Info.setVersion(check.getVersion());
                    if (checked.contains(p3Info))
                        continue;

                    checked.add(p3Info);

                    if (!check.isResolved())
                        check = packageManager.resolve(check.getId(), check.getVersion());

                    if (check == null) {
                        log.error("Unable to resolve package " + checkOriginal.getId() + " at " + checkOriginal.getVersion() + ", failing provision.");
                        sendProvisionResponse(tid, false);
                        return;
                    }

                    String newChecksum = requestChecksumForPackage(check.getId(), check.getVersion());

                    try {
                        check.calculateChecksum();
                    } catch (PackageException e) {
                        log.error("Unable to calculate local package checksum");
                        sendProvisionResponse(tid, false);
                        return;
                    }

                    if (newChecksum != null && !Objects.equals(newChecksum, check.getChecksum())) {
                        // need a new version of the package
                        log.info("Package " + check.getId() + " at " + check.getVersion() + " has a checksum mismatch, expiring cache and resolving again.");
                        log.info("Expected: " + check.getChecksum() + ", got: " + newChecksum);
                        expireCache(check.getId(), check.getVersion());
                        check = packageManager.resolve(check.getId(), check.getVersion());
                        if (check == null) {
                            sendProvisionResponse(tid, false);
                            return;
                        }
                    }

                    if (newChecksum == null)
                        log.warn("null checksum received, moving on");

                    toCheck.addAll(check.getDependencies());
                }


                if (provision(p3, uuid, properties, name)) {
                    sendProvisionResponse(tid, true);
                } else {
                    sendProvisionResponse(tid, false);
                }
            }
            finally {
                provisioningServers.remove(uuid);
            }
        });
        thread.start();
    }

    protected String requestChecksumForPackage(String id, String version) {
        log.info("Waiting for checksum for " + id + " at " + version);

        P3Package.P3PackageInfo p3info = new P3Package.P3PackageInfo();
        p3info.setId(id);
        p3info.setVersion(version);

        CountDownLatch latch = new CountDownLatch(1);
        TransactionInfo info = TransactionManager.get().begin();
        checksumLatches.put(info.getId(), latch);

        if (!Local.get().sendPackageChecksumRequest(info.getId(), id, version)) {
            log.error("Unable to send package checksum request for " + id + " at " + version);
            checksumLatches.remove(info.getId());
            return null;
        }

        try {
            if (!latch.await(15, TimeUnit.SECONDS))
                log.warn("Checksum latch timeout.");
        }
        catch(InterruptedException e) {
            log.error("Interrupted while waiting for package checksum");
            checksumMap.remove(info.getId());
            return null;
        }
        finally {
            checksumLatches.remove(info.getId());
        }

        return checksumMap.remove(info.getId());
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
                latch = Local.get().downloadMap.get(p3info);

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

            log.info("Waiting up to 240 seconds for package download");

            try {
                if (!latch.await(240, TimeUnit.SECONDS)) {
                    log.warn("Timeout on download latch");
                }
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
