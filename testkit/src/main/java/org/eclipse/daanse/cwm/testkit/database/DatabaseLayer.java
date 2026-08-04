/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena, Stefan Bischof - initial
 */
package org.eclipse.daanse.cwm.testkit.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import javax.sql.DataSource;

import org.eclipse.daanse.cwm.testkit.DbTiming;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlGeneratorFactory;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.Feature;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.sql.dialect.api.Dialect;

/**
 * Creates the tables (and keys, indexes, views) of a CWM {@link Schema} in a
 * JDBC {@link DataSource} using the dialect's DDL generator.
 */
public final class DatabaseLayer {

    private DatabaseLayer() {
    }

    /**
     * Creates the full schema (all features).
     */
    public static void apply(DataSource dataSource, Dialect dialect, Schema schema) throws SQLException {
        apply(dataSource, dialect, schema, Feature.ALL);
    }

    /**
     * Creates only the listed features, e.g. {@code SCHEMA, TABLE,
     * PRIMARY_KEY} to skip indexes or foreign keys.
     */
    public static void apply(DataSource dataSource, Dialect dialect, Schema schema, Set<Feature> features)
            throws SQLException {
        apply(dataSource, dialect, schema, features, DdlSettings.defaults());
    }

    /**
     * Creates the listed features with explicit {@link DdlSettings}, e.g. to drop
     * schema qualification for connection-scoped databases.
     */
    public static void apply(DataSource dataSource, Dialect dialect, Schema schema, Set<Feature> features,
            DdlSettings settings) throws SQLException {
        DdlGeneratorFactory factory = ServiceLoader.load(DdlGeneratorFactory.class).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No DdlGeneratorFactory on the classpath — add org.eclipse.daanse.cwm.resource.relational.ddl"));
        List<String> ddl = factory.create(dialect, settings).createSchema(schema, features);
        if (ddl.isEmpty()) {
            return;
        }
        long t = System.nanoTime();
        execute(dataSource, dialect, ddl);
        // Two calls are the normal shape - tables before the data, keys and
        // indexes after it - so the phase says which of the two this was.
        DbTiming.log(dialect, features.contains(Feature.TABLE) ? "ddl" : "index", t, "statements:" + ddl.size());
    }

    /**
     * Runs the generated DDL, statements of the same concurrent kind in
     * parallel. Each is a round trip the server spends mostly waiting, so
     * overlapping them shortens the schema build against a remote database and
     * costs nothing against an embedded one.
     *
     * <p>
     * Kinds never mix, so the generator's order survives — indexes still follow
     * the tables they are on.
     *
     * <p>
     * Within a concurrent run each statement gets its own connection: a JDBC
     * connection need not be thread-safe, and an embedded engine refuses a second
     * statement while one is pending on the same connection. Everything outside
     * such a run is sequential and shares one connection, so a schema does not
     * open a session per statement.
     */
    private static void execute(DataSource dataSource, Dialect dialect, List<String> ddl) throws SQLException {
        int concurrent = concurrentDdl(dialect);
        List<String> run = new ArrayList<>();
        String runKind = null;
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String sql : ddl) {
                String kind = concurrentKind(sql);
                if (kind != null) {
                    // Only statements of the same kind share a run, so the order
                    // between kinds survives — indexes still come after the
                    // tables they are on.
                    if (runKind != null && !runKind.equals(kind)) {
                        executeRun(dataSource, run, concurrent);
                        run.clear();
                    }
                    runKind = kind;
                    run.add(sql);
                    continue;
                }
                // The concurrent run finishes before the shared connection is
                // used again, so the two never overlap.
                executeRun(dataSource, run, concurrent);
                run.clear();
                runKind = null;
                statement.execute(sql);
            }
            executeRun(dataSource, run, concurrent);
        }
    }

    /**
     * The kind of a statement that may run beside its own kind, or null for one
     * that must wait its turn. Tables and indexes qualify: within either kind the
     * statements are independent. Indexes on one table do contend on the server,
     * but a schema spreads them over many.
     */
    private static String concurrentKind(String sql) {
        if (sql == null) {
            return null;
        }
        String s = sql.stripLeading();
        if (s.regionMatches(true, 0, "CREATE TABLE", 0, "CREATE TABLE".length())) {
            return "TABLE";
        }
        if (s.regionMatches(true, 0, "CREATE INDEX", 0, "CREATE INDEX".length())
                || s.regionMatches(true, 0, "CREATE UNIQUE INDEX", 0, "CREATE UNIQUE INDEX".length())) {
            return "INDEX";
        }
        return null;
    }

    private static void executeRun(DataSource dataSource, List<String> run, int concurrent) throws SQLException {
        if (run.isEmpty()) {
            return;
        }
        if (concurrent <= 1 || run.size() == 1) {
            for (String sql : run) {
                executeOne(dataSource, sql);
            }
            return;
        }
        Semaphore permits = new Semaphore(concurrent);
        List<String> statements = List.copyOf(run);
        List<Future<?>> pending = new ArrayList<>(statements.size());
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (String sql : statements) {
                pending.add(executor.submit(() -> {
                    permits.acquire();
                    try {
                        executeOne(dataSource, sql);
                        return null;
                    } finally {
                        permits.release();
                    }
                }));
            }
        }
        for (int i = 0; i < pending.size(); i++) {
            try {
                pending.get(i).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SQLException("interrupted while creating tables", e);
            } catch (ExecutionException e) {
                throw new SQLException("DDL failed: " + statements.get(i), e.getCause());
            }
        }
    }

    private static void executeOne(DataSource dataSource, String sql) throws SQLException {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    /** Statements of one kind that may run at once; a single-writer dialect gets one. */
    private static final int CONCURRENT_DDL = 4;

    /** How many statements of one kind may run at once. */
    private static int concurrentDdl(Dialect dialect) {
        if (dialect != null && !dialect.supportsParallelLoading()) {
            return 1;
        }
        return CONCURRENT_DDL;
    }
}
