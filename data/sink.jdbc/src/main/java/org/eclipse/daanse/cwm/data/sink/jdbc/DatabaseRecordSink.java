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
import org.eclipse.daanse.jdbc.db.api.schema.ColumnDefinition;
import org.eclipse.daanse.jdbc.db.api.schema.ColumnMetaData;
import org.eclipse.daanse.jdbc.db.api.schema.ColumnReference;
import org.eclipse.daanse.jdbc.db.api.schema.TableReference;
import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.record.schema.ColumnDefinitionRecord;
import org.eclipse.daanse.jdbc.db.record.schema.ColumnMetaDataRecord;
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

    private Flow.Subscription subscription;
    private Connection connection;
    private PreparedStatement preparedStatement;
    private int batchCount;

    public DatabaseRecordSink(DataSource dataSource, Dialect dialect, TableReference targetTable,
            List<FieldMapping> fieldMappings, List<JDBCType> jdbcTypes, int batchSize) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.targetTable = targetTable;
        this.fieldMappings = fieldMappings;
        this.jdbcTypes = jdbcTypes;
        this.batchSize = batchSize;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);

            List<ColumnDefinition> columns = fieldMappings.stream().map(m -> {
                ColumnReference ref = new ColumnReference(java.util.Optional.empty(), m.targetFeatureName());
                ColumnMetaData meta = new ColumnMetaDataRecord(java.sql.JDBCType.OTHER, "OTHER",
                        java.util.OptionalInt.empty(), java.util.OptionalInt.empty(), java.util.OptionalInt.empty(),
                        ColumnMetaData.Nullability.UNKNOWN, java.util.OptionalInt.empty(), java.util.Optional.empty(),
                        java.util.Optional.empty(), ColumnMetaData.AutoIncrement.UNKNOWN,
                        ColumnMetaData.GeneratedColumn.UNKNOWN);
                return (ColumnDefinition) new ColumnDefinitionRecord(ref, meta);
            }).toList();
            String sql = dialect.ddlGenerator().insertInto(targetTable, columns);

            preparedStatement = connection.prepareStatement(sql);
            batchCount = 0;

            subscription.request(Long.MAX_VALUE);
        } catch (SQLException e) {
            throw new JdbcSinkException("Failed to initialize database sink", e);
        }
    }

    @Override
    public void onNext(RawRecord item) {
        try {
            int colIndex = 1;
            for (int i = 0; i < fieldMappings.size(); i++) {
                FieldMapping mapping = fieldMappings.get(i);
                String rawValue = item.fields().get(mapping.sourceFieldName());
                JDBCType jdbcType = i < jdbcTypes.size() ? jdbcTypes.get(i) : JDBCType.VARCHAR;

                if (mapping.converter().isPresent()) {
                    Object converted = rawValue == null ? null : mapping.converter().get().apply(rawValue);
                    if (converted == null) {
                        preparedStatement.setNull(colIndex++, jdbcType.getVendorTypeNumber());
                    } else {
                        preparedStatement.setObject(colIndex++, converted, jdbcType.getVendorTypeNumber());
                    }
                } else {
                    TypeConverter.setTypedValue(preparedStatement, colIndex++, jdbcType, rawValue);
                }
            }

            preparedStatement.addBatch();
            preparedStatement.clearParameters();
            batchCount++;

            if (batchCount % batchSize == 0) {
                executeBatch();
            }
        } catch (SQLException e) {
            subscription.cancel();
            closeResources();
            throw new JdbcSinkException("Error writing record at line " + item.lineNumber(), e);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        LOGGER.error("Error in database ETL pipeline", throwable);
        try {
            if (connection != null) {
                connection.rollback();
            }
        } catch (SQLException e) {
            LOGGER.warn("Error rolling back transaction", e);
        }
        closeResources();
    }

    @Override
    public void onComplete() {
        try {
            if (batchCount % batchSize != 0) {
                executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(true);
            LOGGER.debug("Database import completed for table {}", targetTable.name());
        } catch (SQLException e) {
            throw new JdbcSinkException("Error completing database import", e);
        } finally {
            closeResources();
        }
    }

    private void executeBatch() throws SQLException {
        long start = System.currentTimeMillis();
        preparedStatement.executeBatch();
        connection.commit();
        LOGGER.debug("Batch executed in {}ms", System.currentTimeMillis() - start);
    }

    private void closeResources() {
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
