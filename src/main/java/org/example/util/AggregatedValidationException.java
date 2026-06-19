package org.example.util;

import java.util.List;
import java.util.stream.Collectors;

/*
 * Raised by parallel validation when one or more validations fail. Instead of reporting only
 * the first failure, it collects every failure so a single run reveals all broken expectations.
 */
public class AggregatedValidationException extends RuntimeException {
    private final transient List<Throwable> failures;

    public AggregatedValidationException(
            List<Throwable> failures
    ) {
        super(buildMessage(failures));
        this.failures = failures;
        failures.forEach(this::addSuppressed);
    }

    public List<Throwable> failures() {
        return failures;
    }

    private static String buildMessage(
            List<Throwable> failures
    ) {
        return failures.size() + " validation(s) failed:\n"
                + failures.stream()
                .map(failure -> "  - " + failure.getMessage())
                .collect(Collectors.joining("\n"));
    }
}

