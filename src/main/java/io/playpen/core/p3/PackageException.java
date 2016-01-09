package io.playpen.core.p3;

public class PackageException extends Exception {
    public PackageException() {
        super();
    }

    public PackageException(String message) {
        super(message);
    }

    public PackageException(String message, Throwable inner) {
        super(message, inner);
    }
}
