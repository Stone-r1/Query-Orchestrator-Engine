package org.example.util;

import org.hibernate.Session;

import static java.lang.Thread.sleep;

/*TODO
 * Instead of making per run() session pool, make per scenario session pool
 * one scenario -> one session | may be n sessions for n databases
 */
public class QueryRunner {
    private final ConnectionResolver resolver = new ConnectionResolver();

    public final void run(
            DbSource source,
            QuerySteps... steps
    ) {
        try (Session session = resolver.resolveConnection(source)) {
            try {
                session.beginTransaction();
                for (QuerySteps step : steps) {
                    sleep(1000); // change to this proper retry policy
                    step.execute(session);
                }

                session.getTransaction().commit();
            } catch (Exception e) {
                session.getTransaction().rollback();
                System.out.println(e.getMessage());
            }
        }
    }
}