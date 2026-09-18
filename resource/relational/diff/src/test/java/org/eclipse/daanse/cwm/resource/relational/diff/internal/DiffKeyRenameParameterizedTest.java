/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.resource.relational.diff.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.daanse.cwm.resource.relational.ddl.internal.support.SqlGenAssertions.executeAll;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.Feature;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.support.DialectProfile;
import org.eclipse.daanse.cwm.resource.relational.diff.api.ChangeOp;
import org.eclipse.daanse.cwm.resource.relational.diff.api.ChangePlanner;
import org.eclipse.daanse.cwm.resource.relational.diff.api.SchemaDiff;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.support.KeyRenameFixtures;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Index and constraint renames applied to a live database of every
 * {@link DialectProfile}: the migration must run, the index must carry its new
 * name afterwards, and no index is dropped and re-created where the dialect
 * can rename.
 */
class DiffKeyRenameParameterizedTest {

    static Stream<DialectProfile> dialects() {
        return Stream.of(DialectProfile.values());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void index_and_constraint_renames_apply(DialectProfile profile) throws Exception {
        ActiveDatabase db = activateOrSkip(profile);
        try (Connection c = db.dataSource().getConnection()) {
            Dialect dialect = db.dialect();
            String schemaName = profile.schemaName();
            String check = dialect.quoteIdentifier("ID") + " > 0";
            Schema oldSchema = KeyRenameFixtures.build(schemaName, "", check);
            Schema newSchema = KeyRenameFixtures.build(schemaName, "_V2", check);
            Set<Feature> features = profile.nonTriggerFeatures();
            try {
                executeAll(c, new DdlGeneratorFactoryImpl().create(dialect).createSchema(oldSchema, features));

                SchemaDiff diff = new SchemaDifferImpl().diff(oldSchema, newSchema);
                List<ChangeOp> ops = ChangePlanner.create().plan(diff);
                assertThat(ops).filteredOn(ChangeOp.RenameIndex.class::isInstance).hasSize(1);
                assertThat(ops).filteredOn(ChangeOp.RenameConstraint.class::isInstance).hasSize(4);
                executeAll(c, new MigrationEmitterImpl(new DdlGeneratorFactoryImpl()).emit(ops, dialect));

                List<String> indexes = indexNames(c, profile, "CUSTOMERS");
                assertThat(indexes).contains("IX_CUSTOMERS_NAME_V2").doesNotContain("IX_CUSTOMERS_NAME");
            } finally {
                profile.cleanup(c, newSchema, dialect, features);
            }
        }
    }

    private static List<String> indexNames(Connection c, DialectProfile profile, String table)
            throws SQLException {
        DatabaseMetaData md = c.getMetaData();
        boolean catalogScoped = profile == DialectProfile.MARIADB;
        String catalog = catalogScoped ? profile.schemaName() : null;
        String schema = catalogScoped ? null : profile.schemaName();
        List<String> out = new ArrayList<>();
        try (ResultSet rs = md.getIndexInfo(catalog, schema, table, false, false)) {
            while (rs.next()) {
                String name = rs.getString("INDEX_NAME");
                if (name != null) {
                    out.add(name);
                }
            }
        }
        return out;
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
