/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.resource.relational.diff.internal;

import org.eclipse.daanse.cwm.resource.relational.diff.api.ChangePlanner;
import org.eclipse.daanse.cwm.resource.relational.diff.api.SchemaDiff;
import org.eclipse.daanse.cwm.resource.relational.diff.api.TableDiff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.daanse.cwm.resource.relational.ddl.internal.support.SqlGenAssertions.executeAll;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.support.RenameFixtures;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.support.DialectProfile;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * One rename "source" — the differ folds a (drop NAME, add FULL_NAME) pair into
 * a single rename, so a seeded row survives the migration — run across every
 * {@link DialectProfile}. Replaces the per-dialect {@code CwmDiffRename*Test}
 * classes. Asserts the dialect-agnostic outcome (rename detected + data
 * preserved) rather than each dialect's rename SQL form.
 */
class DiffRenameParameterizedTest {

    static Stream<DialectProfile> dialects() {
        return Stream.of(DialectProfile.values());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void column_rename_preserves_data(DialectProfile profile) throws Exception {
        ActiveDatabase db = activateOrSkip(profile);
        try (Connection c = db.dataSource().getConnection()) {
            Dialect dialect = db.dialect();
            String schemaName = profile.schemaName();
            Schema oldSchema = RenameFixtures.build(schemaName, "NAME");
            Schema newSchema = RenameFixtures.build(schemaName, "FULL_NAME");

            String custQ = dialect.quoteIdentifier(schemaName, "CUSTOMERS").toString();
            String idQ = dialect.quoteIdentifier("ID").toString();
            String nameQ = dialect.quoteIdentifier("NAME").toString();
            String fullQ = dialect.quoteIdentifier("FULL_NAME").toString();
            try {
                executeAll(c, profile.create(dialect, oldSchema));

                // Seed a row under the OLD column name.
                try (PreparedStatement ps = c
                        .prepareStatement("INSERT INTO " + custQ + " (" + idQ + ", " + nameQ + ") VALUES (?, ?)")) {
                    ps.setInt(1, 1);
                    ps.setString(2, "Alice");
                    ps.executeUpdate();
                }

                // The differ must classify this as a rename, not drop + add.
                SchemaDiff diff = new SchemaDifferImpl().diff(oldSchema, newSchema);
                assertThat(diff.tablesChanged()).hasSize(1);
                TableDiff td = diff.tablesChanged().get(0);
                assertThat(td.columnsRenamed()).hasSize(1);
                assertThat(td.columnsRenamed().get(0).oldColumn().getName()).isEqualTo("NAME");
                assertThat(td.columnsRenamed().get(0).newColumn().getName()).isEqualTo("FULL_NAME");
                assertThat(td.columnsAdded()).isEmpty();
                assertThat(td.columnsDropped()).isEmpty();

                List<String> migration = new MigrationEmitterImpl(new DdlGeneratorFactoryImpl()).emit(ChangePlanner.create().plan(diff), dialect);
                executeAll(c, migration);

                // Data survived — the row is now visible under FULL_NAME.
                try (Statement s = c.createStatement();
                        ResultSet rs = s.executeQuery(
                                "SELECT " + fullQ + " FROM " + custQ + " WHERE " + idQ + " = 1")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isEqualTo("Alice");
                }
            } finally {
                profile.cleanup(c, newSchema, dialect);
            }
        }
    }

    private static ActiveDatabase activateOrSkip(DialectProfile profile) {
        try {
            return profile.activate();
        } catch (RuntimeException e) {
            Assumptions.assumeTrue(false, "Database '" + profile + "' unavailable (no Docker?): " + e.getMessage());
            throw new AssertionError("unreachable");
        }
    }
}
