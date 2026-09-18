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
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.support.DialectProfile;
import org.eclipse.daanse.cwm.resource.relational.diff.api.ChangeOp;
import org.eclipse.daanse.cwm.resource.relational.diff.api.ChangePlanner;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.support.ViewFixtures;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A view that gains a column is replaced in place, live on every
 * {@link DialectProfile}: the migration must run and the view must return
 * the new column afterwards.
 */
class DiffViewReplaceParameterizedTest {

    static Stream<DialectProfile> dialects() {
        return Stream.of(DialectProfile.values());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void view_with_an_appended_column_is_replaced(DialectProfile profile) throws Exception {
        ActiveDatabase db = activateOrSkip(profile);
        try (Connection c = db.dataSource().getConnection()) {
            Dialect dialect = db.dialect();
            String schemaName = profile.schemaName();
            Schema before = ViewFixtures.build(schemaName, dialect, List.of("ID", "EMAIL"));
            Schema after = ViewFixtures.build(schemaName, dialect, List.of("ID", "EMAIL", "NAME"));
            try {
                executeAll(c, new DdlGeneratorFactoryImpl().create(dialect).createSchema(before,
                        profile.nonTriggerFeatures()));

                List<ChangeOp> ops = ChangePlanner.create().plan(new SchemaDifferImpl().diff(before, after));
                assertThat(ops).singleElement().isInstanceOf(ChangeOp.ReplaceView.class);
                executeAll(c, new MigrationEmitterImpl(new DdlGeneratorFactoryImpl()).emit(ops, dialect));

                try (Statement s = c.createStatement();
                        ResultSet rs = s.executeQuery("SELECT * FROM "
                                + dialect.quoteIdentifier(schemaName, "CUSTOMER_V"))) {
                    assertThat(rs.getMetaData().getColumnCount()).isEqualTo(3);
                }
            } finally {
                profile.cleanup(c, after, dialect, profile.allFeatures());
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
