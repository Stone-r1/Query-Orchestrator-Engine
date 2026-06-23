package org.example.util;

import org.hibernate.Session;

import java.sql.SQLException;


@FunctionalInterface
public interface QuerySteps {
    void execute(
            Session session
    ) throws SQLException;

    /*
     * Executes a query that returns a value and stores it into the given Ref.
     * The Ref can then be read by subsequent steps in the same run().
     */
    static <T> QuerySteps selectMode(
            Ref<T> referenceWrapper,
            ResultQuery<T> query
    ) {
        return session -> referenceWrapper.set(query.execute(session));
    }

    /*
     * Groups multiple steps under a named validation, insertion and deletion contexts.
     */
    static QuerySteps generalMode(
            QuerySteps... steps
    ) {
        return session -> {
            for (QuerySteps step : steps) {
                step.execute(session);
            }
        };
    }
}

