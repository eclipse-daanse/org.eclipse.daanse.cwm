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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
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
import org.eclipse.daanse.cwm.testkit.api.DataSupplier;
import org.eclipse.daanse.cwm.util.resource.relational.ColumnSets;
import org.eclipse.daanse.cwm.util.resource.relational.Schemas;
import org.eclipse.daanse.jdbc.db.api.schema.SchemaReference;
import org.eclipse.daanse.jdbc.db.api.schema.TableReference;
import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
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

    private static final RecordFactory REC = RecordFactory.eINSTANCE;
    private static final int DEFAULT_BATCH = 100;
    private static final int CSV_LOAD_TIMEOUT_SECONDS = 60;

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
                for (Map.Entry<String, URL> e : csv.entrySet()) {
                    Path target = tempDir.resolve(e.getKey() + ".csv");
                    try (InputStream in = e.getValue().openStream()) {
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    Table table = lookupTable(cwmSchema, e.getKey());
                    if (table == null) {
                        throw new IllegalStateException("No CWM Table named '" + e.getKey() + "' in schema '"
                                + (cwmSchema.getName() == null ? "<default>" : cwmSchema.getName()) + "' for CSV "
                                + e.getValue());
                    }
                    loadCsv(dataSource, dialect, cwmSchema, table, target);
                }
            } finally {
                deleteTree(tempDir);
            }
        }
        try (Connection conn = dataSource.getConnection()) {
            data.load(conn, dialect);
        }
    }

    private static Table lookupTable(Schema schema, String name) {
        for (Table t : Schemas.tables(schema)) {
            if (name.equalsIgnoreCase(t.getName())) {
                return t;
            }
        }
        return null;
    }

    private static void loadCsv(DataSource ds, Dialect dialect, Schema schema, Table table, Path csvPath)
            throws Exception {
        RecordFile recordFile = REC.createRecordFile();
        recordFile.setIsSelfDescribing(true);
        recordFile.setSkipRecords(0);

        RecordDef recordDef = REC.createRecordDef();
        recordDef.setFieldDelimiter(",");
        recordDef.setTextDelimiter("\"");
        recordDef.setIsFixedWidth(false);
        for (Column c : ColumnSets.columns(table)) {
            Field f = REC.createField();
            f.setName(c.getName().toLowerCase());
            recordDef.getFeature().add(f);
        }

        String schemaName = schema.getName();
        TableReference tableRef = new TableReference(
                (schemaName == null || schemaName.isBlank()) ? Optional.empty()
                        : Optional.of(new SchemaReference(Optional.empty(), schemaName)),
                table.getName(), TableReference.TYPE_TABLE);

        List<FieldMapping> mappings = new ArrayList<>();
        List<JDBCType> types = new ArrayList<>();
        for (Column c : ColumnSets.columns(table)) {
            mappings.add(new FieldMappingR(c.getName().toLowerCase(), c.getName(), Optional.empty()));
            types.add(jdbcTypeOf(c));
        }

        DatabaseRecordSink sink = new DatabaseRecordSink(ds, dialect, tableRef, mappings, types, DEFAULT_BATCH);
        CsvRecordPublisher publisher = new CsvRecordPublisher(csvPath, recordFile, recordDef);

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
            throw new IllegalStateException("CSV load for " + table.getName() + " timed out");
        }
        if (error[0] != null) {
            throw new RuntimeException("CSV load failed for " + table.getName(), error[0]);
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
