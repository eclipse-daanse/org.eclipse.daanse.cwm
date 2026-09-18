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
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.Feature;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.support.DialectProfile;
import org.eclipse.daanse.cwm.resource.relational.diff.api.ChangeOp;
import org.eclipse.daanse.cwm.resource.relational.diff.api.ChangePlanner;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.support.TriggerFixtures;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Adding and removing a trigger through the migration, live on every
 * {@link DialectProfile} that supports triggers: the migration adding the
 * trigger must run and the trigger must fire on insert; the migration removing
 * it must run as well.
 */
class DiffTriggerParameterizedTest {

    static Stream<DialectProfile> dialects() {
        return Stream.of(DialectProfile.values());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void trigger_add_and_drop_apply(DialectProfile profile) throws Exception {
        Assumptions.assumeTrue(profile.supportsTriggers(), profile + " has no trigger support in the fixtures");
        ActiveDatabase db = activateOrSkip(profile);
        try (Connection c = db.dataSource().getConnection()) {
            Dialect dialect = db.dialect();
            String schemaName = profile.schemaName();
            Schema without = TriggerFixtures.build(schemaName, null, profile.triggerTiming(), profile.triggerEvent());
            Schema with = TriggerFixtures.build(schemaName, profile.triggerBody(), profile.triggerTiming(),
                    profile.triggerEvent());
            Set<Feature> features = profile.nonTriggerFeatures();
            MigrationEmitterImpl emitter = new MigrationEmitterImpl(new DdlGeneratorFactoryImpl());
            try {
                executeAll(c, new DdlGeneratorFactoryImpl().create(dialect).createSchema(without, features));

                List<ChangeOp> add = ChangePlanner.create().plan(new SchemaDifferImpl().diff(without, with));
                assertThat(add).singleElement().isInstanceOf(ChangeOp.CreateTrigger.class);
                executeAll(c, emitter.emit(add, dialect));

                // the trigger fires: the insert succeeds with the trigger in place
                try (Statement s = c.createStatement()) {
                    s.executeUpdate("INSERT INTO " + dialect.quoteIdentifier(schemaName, "CUSTOMERS") + " ("
                            + dialect.quoteIdentifier("ID") + ", " + dialect.quoteIdentifier("NAME")
                            + ") VALUES (1, 'a')");
                }

                List<ChangeOp> drop = ChangePlanner.create().plan(new SchemaDifferImpl().diff(with, without));
                assertThat(drop).singleElement().isInstanceOf(ChangeOp.DropTrigger.class);
                executeAll(c, emitter.emit(drop, dialect));
            } finally {
                profile.cleanup(c, with, dialect, profile.allFeatures());
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
