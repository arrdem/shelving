package me.arrdem.shelving;

/**
 * Base Exception for Shelving.
 */
public class Exception extends java.lang.Exception {
    public Exception() {
        super();
    }

    public Exception(String message) {
        super(message);
    }

    public Exception(String message, Throwable cause) {
        super(message, cause);
    }
}
