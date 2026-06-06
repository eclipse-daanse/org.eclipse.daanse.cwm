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
import java.sql.JDBCType;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.sql.DataSource;

import org.eclipse.daanse.cwm.testkit.api.dbcheck.DatabaseCellCheck;
import org.eclipse.daanse.cwm.testkit.api.dbcheck.DatabaseCheckSuite;
import org.eclipse.daanse.cwm.testkit.api.dbcheck.DatabaseColumnCheck;
import org.eclipse.daanse.cwm.testkit.api.dbcheck.DatabaseQueryCheck;
import org.eclipse.daanse.cwm.testkit.api.dbcheck.DatabaseSchemaCheck;
import org.eclipse.daanse.cwm.testkit.api.dbcheck.DatabaseTableCheck;
import org.eclipse.daanse.jdbc.db.api.DatabaseService;
import org.eclipse.daanse.jdbc.db.api.meta.MetaInfo;
import org.eclipse.daanse.jdbc.db.api.schema.ColumnDefinition;
import org.eclipse.daanse.jdbc.db.api.schema.TableDefinition;
import org.eclipse.daanse.jdbc.db.impl.DatabaseServiceImpl;
import org.junit.jupiter.api.DynamicTest;

/**
 * Runs a {@link DatabaseCheckSuite} against a live {@code DataSource} and
 * returns one JUnit {@link DynamicTest} per assertion.
 */
public final class DatabaseCheckExecutor {

    private DatabaseCheckExecutor() {
    }

    /**
     * Builds the dynamic tests for {@code suite} against {@code dataSource};
     * {@code breadcrumb} prefixes each test name. Returns an empty list when
     * {@code suite} is {@code null}.
     */
    public static List<DynamicTest> execute(String breadcrumb, DataSource dataSource, DatabaseCheckSuite suite) {
        if (suite == null) {
            return List.of();
        }
        DatabaseService databaseService = new DatabaseServiceImpl();
        MetaInfo meta;
        try {
            meta = databaseService.createMetaInfo(dataSource);
        } catch (Exception e) {
            return List.of(DynamicTest.dynamicTest(breadcrumb + " » db-check setup", () -> {
                throw new AssertionError("Failed to introspect DataSource", e);
            }));
        }
        List<DynamicTest> tests = new ArrayList<>();
        String head = breadcrumb + " » " + safe(suite.name());
        for (DatabaseSchemaCheck schemaCheck : suite.schemaChecks()) {
            String schemaHead = head + " » schema=" + safe(schemaCheck.schemaName());
            checkSchema(schemaHead, meta, schemaCheck, tests);
        }
        for (DatabaseQueryCheck queryCheck : suite.queryChecks()) {
            checkQuery(head + " » query=" + safe(queryCheck.name()), dataSource, queryCheck, tests);
        }
        return tests;
    }

    private static void checkQuery(String head, DataSource dataSource, DatabaseQueryCheck c, List<DynamicTest> tests) {
        List<List<Object>> rows;
        int colCount;
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(c.query())) {
            colCount = rs.getMetaData().getColumnCount();
            rows = new ArrayList<>();
            while (rs.next()) {
                List<Object> row = new ArrayList<>(colCount);
                for (int i = 1; i <= colCount; i++) {
                    row.add(rs.getObject(i));
                }
                rows.add(row);
            }
        } catch (Exception e) {
            tests.add(DynamicTest.dynamicTest(head + " execute", () -> {
                throw new AssertionError("query execution failed: " + e.getMessage(), e);
            }));
            return;
        }
        final int rowCount = rows.size();
        final int finalColCount = colCount;
        if (c.expectedRowCount() >= 0) {
            int expected = c.expectedRowCount();
            tests.add(DynamicTest.dynamicTest(head + " rowCount=" + expected, () -> {
                if (rowCount != expected) {
                    throw new AssertionError("expected " + expected + " rows but got " + rowCount);
                }
            }));
        }
        if (c.expectedColumnCount() >= 0) {
            int expected = c.expectedColumnCount();
            tests.add(DynamicTest.dynamicTest(head + " columnCount=" + expected, () -> {
                if (finalColCount != expected) {
                    throw new AssertionError("expected " + expected + " columns but got " + finalColCount);
                }
            }));
        }
        for (DatabaseCellCheck cell : c.cellChecks()) {
            tests.add(DynamicTest.dynamicTest(
                    head + " cell[" + cell.rowIndex() + "," + cell.columnIndex() + "]=" + cell.name(), () -> {
                        if (cell.rowIndex() < 0 || cell.rowIndex() >= rows.size()) {
                            throw new AssertionError(
                                    "row " + cell.rowIndex() + " out of bounds (have " + rows.size() + " rows)");
                        }
                        List<Object> row = rows.get(cell.rowIndex());
                        if (cell.columnIndex() < 0 || cell.columnIndex() >= row.size()) {
                            throw new AssertionError("column " + cell.columnIndex() + " out of bounds (have "
                                    + row.size() + " columns)");
                        }
                        Object actual = row.get(cell.columnIndex());
                        String actualStr = actual == null ? null : actual.toString();
                        if (!Objects.equals(cell.expectedValue(), actualStr)) {
                            throw new AssertionError("expected " + cell.expectedValue() + " but got " + actualStr);
                        }
                    }));
        }
    }

    private static void checkSchema(String head, MetaInfo meta, DatabaseSchemaCheck c, List<DynamicTest> tests) {
        boolean isDefault = c.schemaName() == null || c.schemaName().isBlank();
        if (!isDefault) {
            boolean present = meta.structureInfo().schemas().stream()
                    .anyMatch(s -> nameEquals(s.name(), c.schemaName()));
            if (c.expectAbsent()) {
                tests.add(DynamicTest.dynamicTest(head + " expectAbsent", () -> {
                    if (present) {
                        throw new AssertionError("schema " + c.schemaName() + " should be absent but is present");
                    }
                }));
                return;
            }
            tests.add(DynamicTest.dynamicTest(head + " present", () -> {
                if (!present) {
                    throw new AssertionError("schema " + c.schemaName() + " is absent");
                }
            }));
        }
        for (DatabaseTableCheck tableCheck : c.tableChecks()) {
            String tableHead = head + " » table=" + safe(tableCheck.tableName());
            checkTable(tableHead, meta, c.schemaName(), tableCheck, tests);
        }
    }

    private static void checkTable(String head, MetaInfo meta, String schemaName, DatabaseTableCheck c,
            List<DynamicTest> tests) {
        Optional<TableDefinition> found = findTable(meta, schemaName, c.tableName());
        if (c.expectAbsent()) {
            tests.add(DynamicTest.dynamicTest(head + " expectAbsent", () -> {
                if (found.isPresent()) {
                    throw new AssertionError("table " + c.tableName() + " should be absent but is present");
                }
            }));
            return;
        }
        tests.add(DynamicTest.dynamicTest(head + " present", () -> {
            if (found.isEmpty()) {
                throw new AssertionError("table " + c.tableName() + " is absent");
            }
        }));
        if (found.isEmpty()) {
            return;
        }
        for (DatabaseColumnCheck colCheck : c.columnChecks()) {
            String colHead = head + " » column=" + safe(colCheck.columnName());
            checkColumn(colHead, meta, found.get(), colCheck, tests);
        }
    }

    private static void checkColumn(String head, MetaInfo meta, TableDefinition table, DatabaseColumnCheck c,
            List<DynamicTest> tests) {
        String targetTableName = table.table().name();
        Optional<ColumnDefinition> col = meta.structureInfo().columns().stream()
                .filter(cd -> cd.column().table().map(t -> nameEquals(t.name(), targetTableName)).orElse(false))
                .filter(cd -> nameEquals(cd.column().name(), c.columnName())).findFirst();
        if (c.expectAbsent()) {
            tests.add(DynamicTest.dynamicTest(head + " expectAbsent", () -> {
                if (col.isPresent()) {
                    throw new AssertionError("column " + c.columnName() + " should be absent but is present");
                }
            }));
            return;
        }
        tests.add(DynamicTest.dynamicTest(head + " present", () -> {
            if (col.isEmpty()) {
                throw new AssertionError("column " + c.columnName() + " is absent");
            }
        }));
        if (col.isEmpty()) {
            return;
        }
        c.expectedType()
                .ifPresent(expectedType -> tests.add(DynamicTest.dynamicTest(head + " type=" + expectedType, () -> {
                    JDBCType actual = col.get().columnMetaData().dataType();
                    if (!actual.getName().equalsIgnoreCase(expectedType)) {
                        throw new AssertionError("column " + c.columnName() + " expected type " + expectedType
                                + " but found " + actual.getName());
                    }
                })));
        c.expectedNullable().ifPresent(
                expectedNullable -> tests.add(DynamicTest.dynamicTest(head + " nullable=" + expectedNullable, () -> {
                    boolean nullable = col.get().columnMetaData()
                            .nullability() == org.eclipse.daanse.jdbc.db.api.schema.ColumnMetaData.Nullability.NULLABLE;
                    if (nullable != expectedNullable) {
                        throw new AssertionError("column " + c.columnName() + " expected nullable=" + expectedNullable
                                + " but found nullable=" + nullable);
                    }
                })));
    }

    private static Optional<TableDefinition> findTable(MetaInfo meta, String schemaName, String tableName) {
        return meta.structureInfo().tables().stream().filter(td -> nameEquals(td.table().name(), tableName))
                .filter(td -> {
                    if (schemaName == null || schemaName.isBlank()) {
                        return true;
                    }
                    return td.table().schema().filter(s -> nameEquals(s.name(), schemaName)).isPresent();
                }).findFirst();
    }

    private static boolean nameEquals(String a, String b) {
        if (a == null) {
            return b == null || b.isBlank();
        }
        return a.equalsIgnoreCase(b);
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "<default>" : s;
    }
}
