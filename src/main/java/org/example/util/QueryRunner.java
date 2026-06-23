package org.example.util;

import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class QueryRunner {
    private static final System.Logger LOG = System.getLogger(QueryRunner.class.getName());

    // Cap on concurrent validation sessions so we never exhaust the connection pool.
    private static final int MAX_PARALLELISM = 8;

    private final ConnectionResolver resolver = new ConnectionResolver();

    /*
     * Sequential mutation/work: ONE session, ONE transaction, each step attempted once.
     */
    public final void run(
            DbSource source,
            QuerySteps... steps
    ) {
        try (Session session = resolver.resolveConnection(source)) {
            Transaction transaction = null;

            try {
                transaction = session.beginTransaction();
                for (QuerySteps step : steps) {
                    step.execute(session);
                }

                transaction.commit();

            } catch (Throwable failure) {
                safeRollback(transaction);
                LOG.log(System.Logger.Level.ERROR,
                        "run() rolled back: " + failure.getMessage(), failure);
                throw rethrow(
                        "Step execution failed; transaction rolled back",
                        failure
                );
            }
        }
    }

    public final void await(
            DbSource source,
            RetryPolicy policy,
            QuerySteps condition
    ) {
        policy.execute(() -> {
            runReadOnce(source, condition);
            return null;
        });
    }

    /*
     * Parallel validation - the one place we run steps concurrently. A Hibernate Session is
     * NOT thread-safe, so each validation gets its OWN fresh read-only session and is retried
     * independently per the policy. All failures are aggregated, so a single run reveals every
     * broken expectation rather than just the first.
     */
    public final void validateInParallel(
            DbSource source,
            RetryPolicy policy,
            QuerySteps... validations
    ) {
        if (validations.length == 0) {
            return;
        }

        int parallelism = Math.min(validations.length, MAX_PARALLELISM);
        List<Throwable> failures = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(parallelism)) {
            List<Future<Optional<Throwable>>> results = new ArrayList<>();

            for (QuerySteps validation : validations) {
                results.add(executor.submit(() -> {
                    try {
                        policy.execute(() -> {
                            runReadOnce(source, validation);
                            return null;
                        });

                        return Optional.empty();

                    } catch (Throwable failure) {
                        return Optional.of(failure);
                    }
                }));
            }

            for (Future<Optional<Throwable>> result : results) {
                try {
                    result.get().ifPresent(failures::add);
                } catch (Exception taskFailure) {
                    failures.add(taskFailure);
                }
            }
        }

        if (!failures.isEmpty()) {
            throw new AggregatedValidationException(failures);
        }
    }

    /*
     * Runs a single read step on a fresh, read-only session and always rolls back
     */
    private void runReadOnce(
            DbSource source,
            QuerySteps step
    ) throws Exception {
        try (Session session = resolver.resolveConnection(source)) {
            session.setDefaultReadOnly(true);
            Transaction transaction = session.beginTransaction();

            try {
                step.execute(session);

            } finally {
                safeRollback(transaction);
            }
        }
    }

    private static RuntimeException rethrow(
            String message,
            Throwable failure
    ) {
        if (failure instanceof Error error) {
            throw error;
        }

        return new QueryExecutionException(
                message,
                failure
        );
    }

    private static void safeRollback(
            Transaction transaction
    ) {
        try {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }

        } catch (Exception rollbackFailure) {
            LOG.log(System.Logger.Level.WARNING,
                    "Rollback failed: " + rollbackFailure.getMessage(), rollbackFailure);
        }
    }
}