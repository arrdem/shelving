package me.arrdem;

import me.arrdem.Exception;

/**
 * An exception for when operations have not been implemented.
 *
 * Parallel to UnsupportedOperationException, but for required operations.
 */
public class UnimplementedOperationException extends Exception {
    public UnimplementedOperationException() {
        super();
    }

    public UnimplementedOperationException(String message) {
        super(message);
    }

    public UnimplementedOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
