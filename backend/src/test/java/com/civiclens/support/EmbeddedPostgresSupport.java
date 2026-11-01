package com.civiclens.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * One real PostgreSQL 17 server per test JVM (zonky embedded binaries). Used instead of Testcontainers because
 * it needs no Docker; the SQL dialect, constraints, locking and Flyway migrations are the real PostgreSQL ones.
 */
public final class EmbeddedPostgresSupport {

    private static EmbeddedPostgres postgres;

    private EmbeddedPostgresSupport() {
    }

    public static synchronized String jdbcUrl() {
        if (postgres == null) {
            try {
                postgres = EmbeddedPostgres.builder().start();
            } catch (IOException e) {
                throw new UncheckedIOException("Could not start embedded PostgreSQL", e);
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    postgres.close();
                } catch (IOException ignored) {
                    // best effort
                }
            }));
        }
        return postgres.getJdbcUrl("postgres", "postgres");
    }
}
