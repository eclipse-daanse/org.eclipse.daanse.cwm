/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.resource.relational.ddl.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.daanse.cwm.resource.relational.ddl.internal.support.SqlGenAssertions.executeAll;

import java.sql.Connection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.resource.relational.ddl.api.Feature;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.support.SqlGenAssertions;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.support.SqlGenFixture;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.support.DialectProfile;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.jdbc.db.api.DatabaseService;
import org.eclipse.daanse.jdbc.db.api.meta.MetaInfo;
import org.eclipse.daanse.jdbc.db.api.schema.TableReference;
import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.impl.DatabaseServiceImpl;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * One round-trip "source" (build the CUSTOMERS/ORDERS/view fixture, emit DDL,
 * execute, snapshot, assert) run across every {@link DialectProfile}. Replaces
 * the five per-dialect {@code RoundTripTest} classes; each dialect's quirks
 * (schema scoping, feature subset, trigger emit form) live in the profile.
 */
class RoundTripParameterizedTest {

    private static final DatabaseService DB_SERVICE = new DatabaseServiceImpl();

    static Stream<DialectProfile> dialects() {
        return Stream.of(DialectProfile.values());
    }

    static Stream<DialectProfile> triggerDialects() {
        return Stream.of(DialectProfile.values()).filter(DialectProfile::supportsTriggers);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void create_snapshot_drop(DialectProfile profile) throws Exception {
        ActiveDatabase db = activateOrSkip(profile);
        try (Connection c = db.dataSource().getConnection()) {
            Dialect dialect = db.dialect();
            SqlGenFixture f = SqlGenFixture.build(profile.schemaName(), dialect);
            Set<Feature> features = profile.nonTriggerFeatures();
            try {
                executeAll(c, new DdlGeneratorFactoryImpl().create(dialect).createSchema(f.schema, features));

                profile.prepareForMetadata(c);
                MetaInfo info = profile.snapshot(DB_SERVICE, c, dialect);
                SqlGenAssertions.assertTableExists(info.structureInfo(), profile.schemaName(), "CUSTOMERS",
                        TableReference.TYPE_TABLE);
                SqlGenAssertions.assertTableExists(info.structureInfo(), profile.schemaName(), "ORDERS",
                        TableReference.TYPE_TABLE);
                SqlGenAssertions.assertTableExists(info.structureInfo(), profile.schemaName(), "CUSTOMER_ORDERS",
                        TableReference.TYPE_VIEW);
            } finally {
                profile.cleanup(c, f.schema, dialect, features);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("triggerDialects")
    void shared_trigger_body_emits_one_procedure_for_two_triggers(DialectProfile profile) throws Exception {
        ActiveDatabase db = activateOrSkip(profile);
        try (Connection c = db.dataSource().getConnection()) {
            Dialect dialect = db.dialect();
            SqlGenFixture f = SqlGenFixture.build(profile.schemaName(), dialect);
            f.attachTrigger(f.customers, "trg_audit_cust", profile.triggerBody(), profile.triggerTiming(),
                    profile.triggerEvent());
            f.attachTrigger(f.orders, "trg_audit_ord", profile.triggerBody(), profile.triggerTiming(),
                    profile.triggerEvent());
            Set<Feature> features = profile.allFeatures();
            try {
                List<String> ddl = new DdlGeneratorFactoryImpl().create(dialect).createSchema(f.schema, features);
                assertThat(ddl.stream().filter(s -> s.startsWith(profile.triggerProcPrefix())).count())
                        .as("one shared procedure/function for two triggers with the same body").isEqualTo(1);
                assertThat(ddl.stream().filter(s -> s.startsWith("CREATE TRIGGER")).count())
                        .as("two CREATE TRIGGER statements").isEqualTo(2);
                if (profile.triggerCallSnippet() != null) {
                    assertThat(ddl).anyMatch(
                            s -> s.startsWith("CREATE TRIGGER") && s.contains(profile.triggerCallSnippet()));
                }
                // Lands on a real engine.
                executeAll(c, ddl);
            } finally {
                profile.cleanup(c, f.schema, dialect, features);
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
