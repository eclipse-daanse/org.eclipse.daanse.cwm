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
import java.util.Locale;

import org.eclipse.daanse.sql.jdbc.api.meta.IndexInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.StructureInfo;
import org.eclipse.daanse.sql.model.schema.ColumnReference;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.jdbc.api.schema.TableDefinition;
import org.eclipse.daanse.sql.model.schema.TableReference;

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

    /**
     * The keys, constraints and index of the fixture, as the snapshot reports them: names compared
     * without regard to case, and constraint names not at all, as some databases rename them.
     */
    public static void assertKeysConstraintsAndIndex(MetaInfo info, String schema) {
        StructureInfo si = info.structureInfo();
        assertThat(si.primaryKeys()).as("primary keys").anySatisfy(pk -> {
            assertThat(is(pk.table(), schema, "CUSTOMERS")).isTrue();
            assertThat(names(pk.columns())).containsExactly("ID");
        }).anySatisfy(pk -> {
            assertThat(is(pk.table(), schema, "ORDERS")).isTrue();
            assertThat(names(pk.columns())).containsExactly("ID");
        });
        assertThat(si.importedKeys()).as("foreign keys").anySatisfy(fk -> {
            assertThat(is(fk.foreignKeyColumn(), schema, "ORDERS", "CUSTOMER_ID")).isTrue();
            assertThat(is(fk.primaryKeyColumn(), schema, "CUSTOMERS", "ID")).isTrue();
        });
        assertThat(si.uniqueConstraints()).as("unique constraints").anySatisfy(uc -> {
            assertThat(is(uc.table(), schema, "CUSTOMERS")).isTrue();
            assertThat(names(uc.columns())).containsExactly("EMAIL");
        });
        assertThat(si.checkConstraints()).as("checks").anySatisfy(ck -> {
            assertThat(is(ck.table(), schema, "CUSTOMERS")).isTrue();
            assertThat(ck.checkClause()).containsIgnoringCase("ID");
        });
        assertThat(info.indexInfos()).as("indexes").filteredOn(ii -> is(ii.tableReference(), schema, "CUSTOMERS"))
                .flatMap(IndexInfo::indexInfoItems)
                .filteredOn(item -> item.indexName().filter("IDX_CUSTOMERS_NAME"::equalsIgnoreCase).isPresent())
                .singleElement().satisfies(item -> assertThat(item.column()).map(ColumnReference::name)
                        .hasValueSatisfying(name -> assertThat(name).isEqualToIgnoringCase("NAME")));
    }

    private static boolean is(TableReference table, String schema, String name) {
        return table.name().equalsIgnoreCase(name) && matchesSchema(schema, table);
    }

    private static boolean is(ColumnReference column, String schema, String table, String name) {
        return column.name().equalsIgnoreCase(name) && column.table().filter(t -> is(t, schema, table)).isPresent();
    }

    private static List<String> names(List<ColumnReference> columns) {
        return columns.stream().map(c -> c.name().toUpperCase(Locale.ROOT)).toList();
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
