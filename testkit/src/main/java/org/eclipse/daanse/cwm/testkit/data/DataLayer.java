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
package org.eclipse.daanse.cwm.testkit.data;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.eclipse.daanse.cwm.data.source.csv.CsvRecordPublisher;
import org.eclipse.daanse.cwm.model.cwm.resource.record.Field;
import org.eclipse.daanse.cwm.model.cwm.resource.record.RecordDef;
import org.eclipse.daanse.cwm.model.cwm.resource.record.RecordFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.record.RecordFile;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.testkit.DbTiming;
import org.eclipse.daanse.cwm.testkit.api.DataSupplier;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ColumnSets;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.cwm.data.api.RawRecord;
import org.eclipse.daanse.cwm.data.api.FieldMapping;
import org.eclipse.daanse.cwm.data.source.csv.record.FieldMappingR;
import org.eclipse.daanse.cwm.data.sink.jdbc.DatabaseRecordSink;

/**
 * Loads a {@link DataSupplier}'s CSV resources, then its programmatic rows,
 * into tables already created from the CWM {@link Schema}.
 *
 * <p>
 * CSVs are header-only; column types come from the CWM table. CSV headers must
 * match the table column names (case-insensitive).
 */
public final class DataLayer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataLayer.class);

    private static final RecordFactory REC = RecordFactory.eINSTANCE;

    /**
     * Rows per {@code executeBatch}. One table is one transaction regardless;
     * this only decides how often the accumulated rows are sent.
     */
    private static final int DEFAULT_BATCH = 10_000;

    /**
     * Ceiling per table. It has to hold the largest table a dataset ships, on the
     * slowest driver in use, so it is generous by design.
     */
    private static final int CSV_LOAD_TIMEOUT_SECONDS = 600;


    private DataLayer() {
    }

    /**
     * Loads {@code data} into the tables of {@code cwmSchema}: CSVs first, then
     * {@link DataSupplier#load}. No-op when {@code data} is {@code null}.
     */
    public static void apply(DataSource dataSource, Dialect dialect, Schema cwmSchema, DataSupplier data)
            throws Exception {
        if (data == null) {
            return;
        }
        Map<String, URL> csv = data.csvResources();
        if (csv != null && !csv.isEmpty()) {
            Path tempDir = Files.createTempDirectory("daanse-testkit-data-");
            try {
                // Two passes. The first reads the CWM model and stages the files, on one
                // thread, so that nothing touches EMF once the loaders are running. The
                // second only writes, and may do so table by table in parallel.
                List<TableLoad> loads = new ArrayList<>();
                for (Map.Entry<String, URL> e : csv.entrySet()) {
                    Table table = lookupTable(cwmSchema, e.getKey());
                    if (table == null) {
                        // The CSV is published but the CWM Schema has no table by that
                        // name — the catalog may use a view, or the schema may not
                        // describe this table yet. Loading is skipped, but not quietly:
                        // a dataset that silently arrives with fewer tables than it
                        // ships looks exactly like a dataset that loaded correctly.
                        LOGGER.warn("no table \"{}\" in the schema — its CSV is not loaded", e.getKey());
                        continue;
                    }
                    // Staged after the lookup, so a skipped CSV costs no copy.
                    Path target = tempDir.resolve(e.getKey() + ".csv");
                    try (InputStream in = e.getValue().openStream()) {
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    loads.add(plan(cwmSchema, table, target, data.analyzeAfterTable(e.getKey())));
                }
                long t = System.nanoTime();
                runLoads(dataSource, dialect, loads);
                DbTiming.log(dialect, "csv-load", t, "tables:" + loads.size());
            } finally {
                deleteTree(tempDir);
            }
        }
        try (Connection conn = dataSource.getConnection()) {
            data.load(conn, dialect);
        }
    }

    /**
     * Gathers optimizer statistics for the whole database, if
     * {@link DataSupplier#analyzeAfterAll()} asks for it and the dialect has
     * such a command. Call it <em>after</em> the indexes exist, not from inside
     * the load: Derby's and PostgreSQL's statistics describe index
     * cardinalities, so gathering them on an unindexed table tells the planner
     * nothing.
     *
     * <p>
     * Worth the time it takes: without statistics a planner has nothing to go on,
     * and queries that would be quick become slow enough to reach a timeout. Only
     * the dialects that have such a statement do anything; the rest is a no-op.
     *
     */
    public static void analyze(DataSource dataSource, Dialect dialect, DataSupplier data) {
        if (data == null || !data.analyzeAfterAll()) {
            return;
        }
        List<String> sqls = analyzeSqls(dialect);
        if (sqls.isEmpty()) {
            return;
        }
        long t = System.nanoTime();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String sql : sqls) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            // Statistics are an optimization, never a precondition.
            LOGGER.warn("db={} ANALYZE failed", DbTiming.dbId(dialect), e);
            return;
        }
        DbTiming.log(dialect, "analyze", t, null);
    }

    /** The statement that gathers statistics, empty where the dialect has none. */
    private static List<String> analyzeSqls(Dialect dialect) {
        if (dialect == null) {
            return List.of();
        }
        return dialect.ddlGenerator().analyzeSchema().map(List::of).orElseGet(List::of);
    }


    /**
     * Gathers statistics for one table, if the dataset asked for it and the
     * dialect has such a statement.
     */
    private static void analyzeTable(DataSource ds, Dialect dialect, TableReference table, boolean wanted) {
        if (!wanted || dialect == null) {
            return;
        }
        java.util.Optional<String> sql = dialect.ddlGenerator().analyzeTable(table);
        if (sql.isEmpty()) {
            return;
        }
        long t = System.nanoTime();
        try (Connection connection = ds.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql.get());
        } catch (SQLException e) {
            LOGGER.warn("db={} ANALYZE of {} failed", DbTiming.dbId(dialect), table.name(), e);
            return;
        }
        DbTiming.log(dialect, "analyze-table", t, "table:" + table.name());
    }


    /**
     * One table's load, with everything read out of the CWM model already, so the
     * loader itself touches nothing shared.
     */
    private record TableLoad(String tableName, Path csvPath, TableReference tableRef, List<FieldMapping> mappings,
            List<JDBCType> types, RecordFile recordFile, RecordDef recordDef, boolean analyzeAfterLoad) {
    }

    /**
     * Loads the tables, in parallel where the database allows it.
     *
     * <p>
     * Each table has its own connection and its own transaction, and the schema
     * declares no foreign keys, so the order is free. What limits the gain is the
     * largest table: it stays on the critical path however many threads run.
     *
     * <p>
     * A dialect that says it takes one writer at a time gets one loader.
     */
    private static void runLoads(DataSource dataSource, Dialect dialect, List<TableLoad> loads) throws Exception {
        int concurrent = Math.min(loadThreads(dialect), loads.size());
        if (concurrent <= 1 || loads.size() <= 1) {
            for (TableLoad load : loads) {
                runLoad(dataSource, dialect, load);
            }
            return;
        }
        // A virtual thread per table, throttled by a permit count. The threads are
        // free, the concurrency is not: how many loads may run at once is the one
        // number that matters, and it stays explicit instead of being whatever the
        // scheduler happens to allow. It also travels: against a database in a
        // container the work is I/O-bound and the permit count can be raised at no
        // cost, while an embedded engine reached over JNI pins its carrier anyway.
        // Tables start in the order they were supplied - the dataset's own
        // foreign-key order. Sorting by size first or last makes no reliable
        // difference: with a few slots the bound is the total work, not the
        // longest single table.
        Semaphore permits = new Semaphore(concurrent);
        List<Future<?>> pending = new ArrayList<>(loads.size());
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (TableLoad load : loads) {
                pending.add(executor.submit(() -> {
                    permits.acquire();
                    try {
                        runLoad(dataSource, dialect, load);
                        return null;
                    } finally {
                        permits.release();
                    }
                }));
            }
            // close() awaits every task, including the ones after a failure - which
            // is what makes the failure list below complete.
        }
        List<String> failures = new ArrayList<>();
        Throwable first = null;
        for (int i = 0; i < pending.size(); i++) {
            try {
                pending.get(i).get();
            } catch (ExecutionException e) {
                // Collect them all: one failing table used to hide the others, and
                // which tables failed together is usually the diagnosis.
                failures.add(loads.get(i).tableName() + ": " + describe(e.getCause()));
                if (first == null) {
                    first = e.getCause();
                }
            }
        }
        if (!failures.isEmpty()) {
            // The first failure goes on as the cause. Without it the message named
            // the tables and swallowed the reason - "Error completing database
            // import" three times over, with the SQLException that actually says
            // what went wrong nowhere to be seen.
            throw new IllegalStateException(
                    "CSV load failed for " + failures.size() + " of " + loads.size() + " tables: " + failures, first);
        }
    }

    /**
     * A throwable and the chain under it, on one line. A wrapper alone says
     * nothing: the sink's "Error completing database import" is a lid, and what
     * matters is the {@code SQLException} beneath it.
     */
    private static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null && sb.length() < 400; c = c.getCause()) {
            if (sb.length() > 0) {
                sb.append(" <- ");
            }
            sb.append(c.getClass().getSimpleName()).append(": ").append(c.getMessage());
        }
        return sb.toString();
    }

    private static int loadThreads(Dialect dialect) {
        if (dialect != null && !dialect.supportsParallelLoading()) {
            return 1;
        }
        // Four, not one per core. Parallel loading buys wall-clock with CPU, and
        // past four the CPU it costs outweighs the time it saves - a caller that
        // shares the machine with a test suite wants the CPU.
        return 4;
    }

    private static Table lookupTable(Schema schema, String name) {
        // Strict case match — table name in the CSV resource key must match
        // the CWM Table's name exactly.
        for (Table t : Schemas.tables(schema)) {
            if (name.equals(t.getName())) {
                return t;
            }
        }
        return null;
    }

    /** Reads everything the load needs out of the CWM model. Single-threaded. */
    private static TableLoad plan(Schema schema, Table table, Path csvPath, boolean analyzeAfterLoad) {
        RecordFile recordFile = REC.createRecordFile();
        recordFile.setIsSelfDescribing(true);
        recordFile.setSkipRecords(0);

        RecordDef recordDef = REC.createRecordDef();
        recordDef.setFieldDelimiter(",");
        recordDef.setTextDelimiter("\"");
        recordDef.setIsFixedWidth(false);

        List<FieldMapping> mappings = new ArrayList<>();
        List<JDBCType> types = new ArrayList<>();
        for (Column c : ColumnSets.columns(table)) {
            Field f = REC.createField();
            f.setName(c.getName());
            recordDef.getFeature().add(f);
            mappings.add(new FieldMappingR(c.getName(), c.getName(), Optional.empty()));
            types.add(jdbcTypeOf(c));
        }

        String schemaName = schema.getName();
        TableReference tableRef = new TableReference(
                (schemaName == null || schemaName.isBlank()) ? Optional.empty()
                        : Optional.of(new SchemaReference(Optional.empty(), schemaName)),
                table.getName(), TableReference.TYPE_TABLE);

        return new TableLoad(table.getName(), csvPath, tableRef, mappings, types, recordFile, recordDef,
                analyzeAfterLoad);
    }

    private static void runLoad(DataSource ds, Dialect dialect, TableLoad load) throws Exception {
        DatabaseRecordSink sink = new DatabaseRecordSink(ds, dialect, load.tableRef(), load.mappings(), load.types(),
                DEFAULT_BATCH);
        CsvRecordPublisher publisher = new CsvRecordPublisher(load.csvPath(), load.recordFile(), load.recordDef());

        // Parsing and writing alternate on this one thread. Splitting them across
        // two with a queue between gains nothing on one table and is 15 % worse
        // on four: handing a row across a thread costs about what parsing it
        // costs. Concurrency belongs between tables, not inside one.
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] error = { null };
        publisher.subscribe(new Flow.Subscriber<RawRecord>() {
            @Override
            public void onSubscribe(Flow.Subscription s) {
                sink.onSubscribe(s);
            }

            @Override
            public void onNext(RawRecord r) {
                sink.onNext(r);
            }

            @Override
            public void onError(Throwable t) {
                error[0] = t;
                sink.onError(t);
                latch.countDown();
            }

            @Override
            public void onComplete() {
                sink.onComplete();
                latch.countDown();
            }
        });
        if (!latch.await(CSV_LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("CSV load for " + load.tableName() + " timed out");
        }
        if (error[0] != null) {
            throw new RuntimeException("CSV load failed for " + load.tableName(), error[0]);
        }
        verifyRowCount(ds, dialect, load, sink.rowCount());
        // Still inside this table's permit, so it competes with the other loads
        // rather than waiting for all of them to finish.
        analyzeTable(ds, dialect, load.tableRef(), load.analyzeAfterLoad());
    }

    /**
     * Reads back what the load claims to have written. The sink counts the rows
     * it bound, not the rows the database stored, so without this a table can be
     * reported as loaded while it holds nothing — which is exactly how four
     * tables once went missing without a single failing test.
     */
    private static void verifyRowCount(DataSource ds, Dialect dialect, TableLoad load, long expected)
            throws SQLException {
        String sql = "SELECT count(*) FROM " + dialect.ddlGenerator().qualified(load.tableRef());
        long actual;
        try (Connection connection = ds.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(sql)) {
            actual = rs.next() ? rs.getLong(1) : -1;
        }
        if (actual != expected) {
            throw new IllegalStateException(load.tableName() + ": loaded " + expected
                    + " rows but the table holds " + actual);
        }
    }

    private static JDBCType jdbcTypeOf(Column c) {
        if (c.getType() instanceof org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLDataType sdt) {
            long n = sdt.getTypeNumber();
            if (n != 0) {
                try {
                    return JDBCType.valueOf((int) n);
                } catch (IllegalArgumentException ignore) {
                    // fall through
                }
            }
        }
        return JDBCType.VARCHAR;
    }

    private static void deleteTree(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }
}
