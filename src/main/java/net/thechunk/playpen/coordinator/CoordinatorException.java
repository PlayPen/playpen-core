package net.thechunk.playpen.coordinator;

public class CoordinatorException extends Exception {
    public CoordinatorException() {
        super();
    }

    public CoordinatorException(String message) {
        super(message);
    }

    public CoordinatorException(String message, Throwable inner) {
        super(message, inner);
    }
}
