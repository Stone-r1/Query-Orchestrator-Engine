package org.example.util;

import org.hibernate.Session;

public class ConnectionResolver extends HibernateSessionResolver {
    public Session resolveConnection(
            DbSource source
    ) {
        return switch (source) {
            case MY_DB -> openSession();
        };
    }
}