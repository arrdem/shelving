package me.arrdem.shelving;

/**
 * Exception representing the result of referencing a missing spec.
 */
public class MissingSpecException extends Exception {
    public MissingSpecException() {
        super();
    }

    public MissingSpecException(String message) {
        super(message);
    }

    public MissingSpecException(String message, Throwable cause) {
        super(message, cause);
    }
}
