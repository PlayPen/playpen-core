package io.playpen.core;

public class ConfigException extends Exception {
    public ConfigException() {
        super();
    }

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable inner) {
        super(message, inner);
    }
}
