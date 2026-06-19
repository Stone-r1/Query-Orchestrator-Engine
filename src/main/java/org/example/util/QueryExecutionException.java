package org.example.util;

/*
 * Wraps any failure that escapes a QueryRunner operation after the transaction has been rolled back.
 */
public class QueryExecutionException extends RuntimeException {
    public QueryExecutionException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}

