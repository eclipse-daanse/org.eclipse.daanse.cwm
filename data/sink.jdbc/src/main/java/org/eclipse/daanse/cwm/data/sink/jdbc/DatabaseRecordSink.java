/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.data.sink.jdbc;

import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Flow;

import javax.sql.DataSource;

import org.eclipse.daanse.cwm.data.api.FieldMapping;
import org.eclipse.daanse.cwm.data.api.RawRecord;
import org.eclipse.daanse.cwm.data.api.RecordSink;
import org.eclipse.daanse.sql.model.schema.ColumnDefinition;
import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.model.schema.ColumnReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnDefinitionRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnMetaDataRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes {@link RawRecord} items to a database table via PreparedStatement with
 * batch support. Maps fields to columns using {@link FieldMapping} definitions.
 */
public class DatabaseRecordSink implements RecordSink<RawRecord> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseRecordSink.class);

    private final DataSource dataSource;
    private final Dialect dialect;
    private final TableReference targetTable;
    private final List<FieldMapping> fieldMappings;
    private final List<JDBCType> jdbcTypes;
    private final int batchSize;
    private final boolean boundedDemand;

    private Flow.Subscription subscription;
    private Connection connection;
    private PreparedStatement preparedStatement;
    private long batchCount;
    private long rowsAtLastExecute;
    private Throwable failure;
    private boolean closedReported;

    /** Whether this database has transactions at all; ClickHouse has none. */
    private boolean transactional;

    // Resolved once in onSubscribe: everything the per-row loop needs, in arrays
    // indexed by column, so nothing is looked up or unwrapped per value.
    private String[] sourceFieldNames;
    private JDBCType[] columnTypes;
    private java.util.function.Function<String, Object>[] converters;

    /**
     * Columns the model calls boolean but this database stores as a number.
     * Resolved once, from the dialect, because it holds for the whole table.
     */
    private boolean[] booleanAsNumber;

    /**
     * Position of each of our columns in the incoming records, resolved from the
     * first one. Null until then.
     */
    private int[] sourceIndexes;

    /**
     * Rows per INSERT statement. A load costs what its <em>statements</em> cost:
     * a driver may implement {@code executeBatch()} as a loop over
     * {@code execute()} — DuckDB's does — so writing N tuples per statement
     * divides the bind and plan cycles by N.
     */
    private static final int ROWS_PER_INSERT = 200;

    /**
     * Bound-parameter ceiling per statement. PostgreSQL refuses more than 65535
     * and SQL Server more than 2100; staying below both costs nothing, because the
     * gain flattens out long before either.
     */
    private static final int MAX_PARAMETERS = 2_000;

    /** How many tuples the block statement carries; 1 means there is none. */
    private int tuplesPerStatement;
    /** Rows already bound into the block statement, 0..tuplesPerStatement-1. */
    private int boundRows;
    /** Carries {@link #tuplesPerStatement} tuples; null when that is 1. */
    private PreparedStatement blockStatement;

    public DatabaseRecordSink(DataSource dataSource, Dialect dialect, TableReference targetTable,
            List<FieldMapping> fieldMappings, List<JDBCType> jdbcTypes, int batchSize) {
        this(dataSource, dialect, targetTable, fieldMappings, jdbcTypes, batchSize, false);
    }

    /**
     * @param boundedDemand request records in windows of {@code batchSize}
     *                      instead of unbounded — batch size, demand window and
     *                      execution interval become one knob. Requires a
     *                      reentrancy-safe upstream.
     */
    public DatabaseRecordSink(DataSource dataSource, Dialect dialect, TableReference targetTable,
            List<FieldMapping> fieldMappings, List<JDBCType> jdbcTypes, int batchSize, boolean boundedDemand) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.targetTable = targetTable;
        this.fieldMappings = fieldMappings;
        this.jdbcTypes = jdbcTypes;
        this.batchSize = batchSize;
        this.boundedDemand = boundedDemand;
    }

    // --- observation hooks (no-ops here; an observing subclass adds telemetry) --

    /** Called once after connection and statements are prepared. */
    protected void onSinkOpened(String insertSql, Connection openedConnection) {
    }

    /** Called after each JDBC batch execution with the rows it carried. */
    protected void onBatchExecuted(long rows, long nanos) {
    }

    /** Called after the final commit (transactional databases only). */
    protected void onCommitted(long nanos) {
    }

    /** Called exactly once when the sink is done, successful or not. */
    protected void onSinkClosed(Throwable terminalFailure, long writtenRows) {
    }

    /**
     * How many rows this sink bound and sent, valid once {@link #onComplete()}
     * has returned. It is what the sink believes it wrote — comparing it with
     * {@code SELECT count(*)} is what catches a table that accepted every
     * statement and stored nothing.
     */
    public long rowCount() {
        return batchCount;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        try {
            connection = dataSource.getConnection();
            // ClickHouse has no transactions and answers setAutoCommit(false)
            // with a SQLFeatureNotSupportedException. Every statement stands on
            // its own there; there is nothing to open and nothing to commit.
            transactional = dialect.supportsTransactions();
            if (transactional) {
                connection.setAutoCommit(false);
            }

            List<ColumnDefinition> columns = fieldMappings.stream().map(m -> {
                ColumnReference ref = new ColumnReference(java.util.Optional.empty(), m.targetFeatureName());
                ColumnMetaData meta = new ColumnMetaDataRecord(java.sql.JDBCType.OTHER, "OTHER",
                        java.util.OptionalInt.empty(), java.util.OptionalInt.empty(), java.util.OptionalInt.empty(),
                        ColumnMetaData.Nullability.UNKNOWN, java.util.OptionalInt.empty(), java.util.Optional.empty(),
                        java.util.Optional.empty(), ColumnMetaData.AutoIncrement.UNKNOWN,
                        ColumnMetaData.GeneratedColumn.UNKNOWN);
                return (ColumnDefinition) new ColumnDefinitionRecord(ref, meta);
            }).toList();
            // The single-row statement always exists: it takes the tail of the file,
            // the rows that do not fill a whole block.
            String insertSql = dialect.ddlGenerator().insertInto(targetTable, columns);
            preparedStatement = connection.prepareStatement(insertSql);

            tuplesPerStatement = Math.min(tuplesPerStatement(columns.size()),
                    dialect.ddlGenerator().maxInsertRows());
            if (tuplesPerStatement > 1) {
                blockStatement = connection
                        .prepareStatement(dialect.ddlGenerator().insertInto(targetTable, columns, tuplesPerStatement));
            }
            batchCount = 0;
            boundRows = 0;
            resolveColumns();
            onSinkOpened(insertSql, connection);

            subscription.request(boundedDemand ? Math.max(batchSize, tuplesPerStatement) : Long.MAX_VALUE);
        } catch (SQLException e) {
            failure = e;
            reportClosed();
            throw new JdbcSinkException("Failed to initialize database sink", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void resolveColumns() {
        int count = fieldMappings.size();
        sourceFieldNames = new String[count];
        columnTypes = new JDBCType[count];
        converters = new java.util.function.Function[count];
        booleanAsNumber = new boolean[count];
        // The model states BOOLEAN because that is the logical type; what the
        // column actually is was decided by the dialect when the table was
        // created, and most of them chose a number.
        boolean numericBooleans = !"BOOLEAN".equalsIgnoreCase(dialect.ddlGenerator().booleanTypeName());
        for (int i = 0; i < count; i++) {
            FieldMapping mapping = fieldMappings.get(i);
            sourceFieldNames[i] = mapping.sourceFieldName();
            columnTypes[i] = i < jdbcTypes.size() ? jdbcTypes.get(i) : JDBCType.VARCHAR;
            converters[i] = mapping.converter().orElse(null);
            booleanAsNumber[i] = numericBooleans
                    && (columnTypes[i] == JDBCType.BOOLEAN || columnTypes[i] == JDBCType.BIT);
        }
    }

    /**
     * How many tuples one statement carries, capped so the parameter count stays
     * within what every driver accepts.
     */
    private static int tuplesPerStatement(int columnCount) {
        if (ROWS_PER_INSERT <= 1 || columnCount <= 0) {
            return 1;
        }
        return Math.max(1, Math.min(ROWS_PER_INSERT, MAX_PARAMETERS / columnCount));
    }

    /**
     * Looks up each source field name once, against the record's own field-name
     * list. A name the source does not carry gets -1, which reads back as null —
     * the same result the map lookup gave.
     */
    private void resolveSourceIndexes(RawRecord item) {
        java.util.List<String> names = item.fieldNames();
        int[] indexes = new int[sourceFieldNames.length];
        for (int i = 0; i < sourceFieldNames.length; i++) {
            indexes[i] = names.indexOf(sourceFieldNames[i]);
        }
        sourceIndexes = indexes;
    }

    @Override
    public void onNext(RawRecord item) {
        try {
            if (tuplesPerStatement > 1) {
                // Held rather than bound straight away: a statement with N tuples can
                // only be executed with all N bound, and the file's last block is
                // shorter than that. What is held is at most N records.
                block.add(item);
                if (block.size() == tuplesPerStatement) {
                    flushBlock();
                }
                return;
            }

            bindRow(preparedStatement, 0, item);
            preparedStatement.addBatch();
            batchCount++;
            if (batchCount % batchSize == 0) {
                executeBatch();
            }
        } catch (SQLException e) {
            failure = e;
            subscription.cancel();
            closeResources();
            reportClosed();
            throw new JdbcSinkException("Error writing record at line " + item.lineNumber(), e);
        }
    }

    /** Binds and queues a full block. */
    private void flushBlock() throws SQLException {
        for (int row = 0; row < block.size(); row++) {
            bindRow(blockStatement, row * sourceFieldNames.length, block.get(row));
        }
        // No clearParameters: addBatch has taken the values, and every parameter is
        // bound again on the next block.
        blockStatement.addBatch();
        batchCount += block.size();
        block.clear();
        if (batchCount % batchSize < tuplesPerStatement) {
            timedExecute(blockStatement);
        }
    }

    /** Executes a batch, reports it to the hook and re-requests when bounded. */
    private void timedExecute(PreparedStatement statement) throws SQLException {
        long start = System.nanoTime();
        try {
            statement.executeBatch();
        } catch (SQLException e) {
            onBatchExecuted(0, System.nanoTime() - start);
            throw e;
        }
        long rows = batchCount - rowsAtLastExecute;
        rowsAtLastExecute = batchCount;
        if (rows > 0) {
            onBatchExecuted(rows, System.nanoTime() - start);
            if (boundedDemand) {
                subscription.request(rows);
            }
        }
    }

    /** The rows waiting for their block to fill. */
    private final List<RawRecord> block = new java.util.ArrayList<>();

    /** Binds one record into the parameters starting at {@code base}. */
    private void bindRow(PreparedStatement statement, int base, RawRecord item) throws SQLException {
        // Where each of our columns sits in the record. The record's field names
        // are the source's, one list for the whole file, so this holds from the
        // first row to the last and is resolved once.
        if (sourceIndexes == null) {
            resolveSourceIndexes(item);
        }
        for (int i = 0; i < sourceFieldNames.length; i++) {
            String rawValue = item.field(sourceIndexes[i]);
            int index = base + i + 1;
            if (booleanAsNumber[i] && converters[i] == null) {
                TypeConverter.setBooleanAsNumber(statement, index, rawValue);
            } else if (converters[i] == null) {
                TypeConverter.setTypedValue(statement, index, columnTypes[i], rawValue);
            } else {
                Object converted = rawValue == null ? null : converters[i].apply(rawValue);
                if (converted == null) {
                    statement.setNull(index, columnTypes[i].getVendorTypeNumber());
                } else {
                    statement.setObject(index, converted, columnTypes[i].getVendorTypeNumber());
                }
            }
        }
    }

    @Override
    public void onError(Throwable throwable) {
        LOGGER.error("Error in database ETL pipeline", throwable);
        failure = throwable;
        try {
            if (connection != null && transactional) {
                connection.rollback();
            }
        } catch (SQLException e) {
            LOGGER.warn("Error rolling back transaction", e);
        }
        closeResources();
        reportClosed();
    }

    @Override
    public void onComplete() {
        try {
            if (blockStatement != null) {
                timedExecute(blockStatement);
                // The tail — fewer rows than the block statement has tuples — goes in
                // one at a time through the single-row statement.
                for (RawRecord record : block) {
                    bindRow(preparedStatement, 0, record);
                    preparedStatement.addBatch();
                }
                batchCount += block.size();
                block.clear();
                timedExecute(preparedStatement);
            } else if (batchCount % batchSize != 0) {
                executeBatch();
            }
            if (transactional) {
                long start = System.nanoTime();
                connection.commit();
                connection.setAutoCommit(true);
                onCommitted(System.nanoTime() - start);
            }
            LOGGER.debug("Database import completed for table {}", targetTable.name());
        } catch (SQLException e) {
            failure = e;
            throw new JdbcSinkException("Error completing database import", e);
        } finally {
            closeResources();
            reportClosed();
        }
    }

    /**
     * Sends the accumulated rows. It does <em>not</em> commit: one table is one
     * transaction, committed in {@link #onComplete()}. Committing per batch made
     * the {@code setAutoCommit(false)} above pointless and turned a load into as
     * many durable transactions as it had batches.
     */
    private void executeBatch() throws SQLException {
        timedExecute(preparedStatement);
    }

    private void reportClosed() {
        if (!closedReported) {
            closedReported = true;
            onSinkClosed(failure, batchCount);
        }
    }

    private void closeResources() {
        try {
            if (blockStatement != null) {
                blockStatement.close();
            }
        } catch (SQLException e) {
            LOGGER.warn("Error closing the multi-row PreparedStatement", e);
        }
        try {
            if (preparedStatement != null) {
                preparedStatement.close();
            }
        } catch (SQLException e) {
            LOGGER.warn("Error closing PreparedStatement", e);
        }
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            LOGGER.warn("Error closing Connection", e);
        }
    }
}
