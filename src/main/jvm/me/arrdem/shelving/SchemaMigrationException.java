package me.arrdem.shelving;

import me.arrdem.shelving.Exception;

/**
 * An exception for when Shelving schema migrations fail.
 */
public class SchemaMigrationException extends Exception {
    public static Iterable<String> problems = null;
    
    public SchemaMigrationException() {
        super();
    }

    public SchemaMigrationException(String message) {
        super(message);
    }

    public SchemaMigrationException(String message, Throwable cause) {
        super(message, cause);
    }

    public SchemaMigrationException(String message, Iterable<String> problems) {
        super(message);
        this.problems = problems;
    }

    public String toString() {
        if (problems == null) {
            return super.toString();
        } else {
            return super.toString() + problems.toString();
        }
    }
}
