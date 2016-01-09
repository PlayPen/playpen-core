package io.playpen.core.coordinator.network;

import io.playpen.core.plugin.IEventListener;
import io.playpen.core.plugin.IPlugin;

public interface INetworkListener extends IEventListener<INetworkListener> {
    /**
     * Called right after netty startup.
     */
    void onNetworkStartup();

    /**
     * Called right before netty shutdown.
     */
    void onNetworkShutdown();

    /**
     * Called after a coordinator has been created with a new keypair.
     * @param coordinator
     */
    void onCoordinatorCreated(LocalCoordinator coordinator);

    /**
     * Called after a coordinator has synced.
     * @param coordinator
     */
    void onCoordinatorSync(LocalCoordinator coordinator);

    /**
     * Called when the network requests provisioning of a local coordinator.
     * @param coordinator
     * @param server
     */
    void onRequestProvision(LocalCoordinator coordinator, Server server);

    /**
     * Called when a local coordinator responds to a provision request.
     * @param coordinator
     * @param server
     * @param ok
     */
    void onProvisionResponse(LocalCoordinator coordinator, Server server, boolean ok);

    /**
     * Called when the network requests deprovisioning of a server.
     * @param coordinator
     * @param server
     */
    void onRequestDeprovision(LocalCoordinator coordinator, Server server);

    /**
     * Called when a local coordinator notifies the network of a server shutdown.
     * @param coordinator
     * @param server
     */
    void onServerShutdown(LocalCoordinator coordinator, Server server);

    /**
     * Called when the network requests shutdown of a local coordinator.
     * @param coordinator
     */
    void onRequestShutdown(LocalCoordinator coordinator);

    /**
     * Called when a plugin broadcasts a message to other plugins
     * @param id
     * @param args
     */
    void onPluginMessage(IPlugin plugin, String id, Object... args);
}
