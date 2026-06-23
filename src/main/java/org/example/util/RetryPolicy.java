package org.example.util;

import java.time.Duration;

/*
 * RetryPolicy is an immutable value object - build one with a factory and reuse it.
 */
public final class RetryPolicy {

    /*
     * The unit of work a policy drives. Returns a value and may throw, so it can wrap both reads and validations.
     */
    @FunctionalInterface
    public interface RetryableAction<T> {
        T run() throws Exception;
    }

    private final int maxAttempts;
    private final Duration delay;

    private RetryPolicy(int maxAttempts, Duration delay) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        this.maxAttempts = maxAttempts;
        this.delay = delay;
    }

    public static RetryPolicy of(
            int maxAttempts,
            Duration delay
    ) {
        return new RetryPolicy(maxAttempts, delay);
    }

    /*
     * Runs the action, retrying on ANY Throwable until it succeeds or attempts are exhausted.
     */
    public <T> T execute(
            RetryableAction<T> action
    ) {
        Throwable lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.run();

            } catch (Throwable failure) {
                lastFailure = failure;
                if (attempt < maxAttempts) {
                    sleep(delay);
                }
            }
        }

        throw propagate(lastFailure);
    }

    private static RuntimeException propagate(Throwable failure) {
        return switch (failure) {
            case RuntimeException runtime -> runtime;
            case Error error -> throw error;
            case null -> new QueryExecutionException(
                    "Retry exhausted with no captured failure", null);
            default -> new QueryExecutionException(
                    "Retry exhausted after failure", failure);
        };
    }

    private static void sleep(
            Duration duration
    ) {
        try {
            Thread.sleep(Math.max(0L, duration.toMillis()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Retry was interrupted while waiting",
                    interrupted
            );
        }
    }
}

