package org.example.util;

import org.hibernate.Session;

import java.sql.SQLException;


@FunctionalInterface
public interface ResultQuery<T> {
    T execute(Session session) throws SQLException;
}
