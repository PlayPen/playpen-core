package net.thechunk.playpen.networking;


import java.util.UUID;

public abstract class Network {
    private static Network instance = null;

    public static Network get() {
        return instance;
    }

    public Network() {
        instance = this;
    }

    public abstract String getServerId();

    public String generateId() {
        return getServerId() + "-" + UUID.randomUUID().toString();
    }
}
