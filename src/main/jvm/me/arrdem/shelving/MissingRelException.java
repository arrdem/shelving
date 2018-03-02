package me.arrdem.shelving;

/**
 * Exception representing the result of referencing a missing relation.
 */
public class MissingRelException extends Exception {
    public MissingRelException() {
        super();
    }

    public MissingRelException(String message) {
        super(message);
    }

    public MissingRelException(String message, Throwable cause) {
        super(message, cause);
    }
}
