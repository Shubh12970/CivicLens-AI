package com.civiclens.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.io.File;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Developer convenience for machines without Docker: starts a local PostgreSQL 17 on 127.0.0.1 and creates the
 * {@code civiclens} database and role. Data persists in target/local-pg until {@code mvnw clean}.
 *
 * <pre>
 *   $env:LOCAL_DB_PASSWORD = "choose-a-local-password"
 *   .\mvnw.cmd -q test-compile exec:java@local-db
 * </pre>
 * Then run the app with DB_PASSWORD set to the same value. Stop with Ctrl+C.
 */
public final class LocalPostgresLauncher {

    private LocalPostgresLauncher() {
    }

    public static void main(String[] args) throws Exception {
        String password = System.getenv("LOCAL_DB_PASSWORD");
        if (password == null || password.isBlank() || password.contains("'")) {
            System.err.println("Set LOCAL_DB_PASSWORD (no single quotes) before starting the local database.");
            System.exit(1);
        }
        int port = Integer.parseInt(System.getenv().getOrDefault("LOCAL_DB_PORT", "5432"));
        File dataDir = new File("target/local-pg");
        boolean fresh = !new File(dataDir, "PG_VERSION").exists();
        EmbeddedPostgres pg = EmbeddedPostgres.builder()
                .setPort(port)
                .setDataDirectory(dataDir)
                .setCleanDataDirectory(false)
                .start();
        try (Connection c = pg.getPostgresDatabase().getConnection(); Statement s = c.createStatement()) {
            var rs = s.executeQuery("SELECT 1 FROM pg_roles WHERE rolname = 'civiclens'");
            if (!rs.next()) {
                s.execute("CREATE ROLE civiclens LOGIN PASSWORD '" + password + "'");
            } else {
                s.execute("ALTER ROLE civiclens PASSWORD '" + password + "'");
            }
            var db = s.executeQuery("SELECT 1 FROM pg_database WHERE datname = 'civiclens'");
            if (!db.next()) {
                s.execute("CREATE DATABASE civiclens OWNER civiclens");
            }
        }
        System.out.println("Local PostgreSQL running (" + (fresh ? "new" : "existing") + " data in target/local-pg)");
        System.out.println("DB_URL=jdbc:postgresql://localhost:" + port + "/civiclens  DB_USERNAME=civiclens");
        System.out.println("Press Ctrl+C to stop.");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                pg.close();
            } catch (Exception ignored) {
                // best effort
            }
        }));
        Thread.currentThread().join();
    }
}
