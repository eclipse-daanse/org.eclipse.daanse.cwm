/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.resource.relational.ddl.internal.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.jdbc.db.api.meta.StructureInfo;
import org.eclipse.daanse.jdbc.db.api.schema.SchemaReference;
import org.eclipse.daanse.jdbc.db.api.schema.TableDefinition;
import org.eclipse.daanse.jdbc.db.api.schema.TableReference;

/** Shared assertions for per-dialect round-trip tests. */
public final class SqlGenAssertions {

    private SqlGenAssertions() {
    }

    public static List<String> tableNamesIn(StructureInfo si, String schema, String type) {
        List<String> out = new ArrayList<>();
        for (TableDefinition td : si.tables()) {
            TableReference tr = td.table();
            if (!matchesType(type, tr.type()))
                continue;
            if (!matchesSchema(schema, tr))
                continue;
            out.add(tr.name());
        }
        return out;
    }

    public static void executeAll(Connection connection, List<String> sql) throws SQLException {
        try (Statement s = connection.createStatement()) {
            for (String stmt : sql) {
                if (stmt == null || stmt.startsWith("--"))
                    continue;
                try {
                    s.execute(stmt);
                } catch (SQLException e) {
                    throw new SQLException("failed: " + stmt + " — " + e.getMessage(), e);
                }
            }
        }
    }

    public static void assertTableExists(StructureInfo si, String schema, String tableName, String type) {
        assertThat(tableNamesIn(si, schema, type)).as("tables in schema=%s of type=%s", schema, type)
                .contains(tableName);
    }

    private static boolean matchesType(String expected, String actual) {
        if (expected.equals(actual))
            return true;
        // H2 reports "BASE TABLE" instead of "TABLE".
        return TableReference.TYPE_TABLE.equals(expected) && "BASE TABLE".equals(actual);
    }

    private static boolean matchesSchema(String expected, TableReference tr) {
        // MariaDB/MySQL drivers don't always populate the schema field; accept
        // empty schema as a match when the caller scopes at the connection level.
        return tr.schema().map(SchemaReference::name).map(s -> s.equalsIgnoreCase(expected)).orElse(true);
    }
}
