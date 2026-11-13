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
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.TaggedValues;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndexColumn;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.DeferrabilityType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalRules;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlGenerator;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.support.DialectProfile;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.support.SqlGenAssertions;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.support.SqlGenFixture;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.jdbc.api.DatabaseService;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.eclipse.daanse.sql.jdbc.api.schema.ImportedKey;
import org.eclipse.daanse.sql.jdbc.impl.DatabaseServiceImpl;
import org.eclipse.daanse.sql.model.schema.TableReference;
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
            try {
                executeAll(c, profile.create(dialect, f.schema));

                profile.prepareForMetadata(c);
                MetaInfo info = profile.snapshot(DB_SERVICE, c, dialect);
                SqlGenAssertions.assertTableExists(info.structureInfo(), profile.schemaName(), "CUSTOMERS",
                        TableReference.TYPE_TABLE);
                SqlGenAssertions.assertTableExists(info.structureInfo(), profile.schemaName(), "ORDERS",
                        TableReference.TYPE_TABLE);
                SqlGenAssertions.assertTableExists(info.structureInfo(), profile.schemaName(), "CUSTOMER_ORDERS",
                        TableReference.TYPE_VIEW);
                SqlGenAssertions.assertKeysConstraintsAndIndex(info, profile.schemaName());
            } finally {
                profile.cleanup(c, f.schema, dialect);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void create_with_table_and_column_comments(DialectProfile profile) throws Exception {
        ActiveDatabase db = activateOrSkip(profile);
        try (Connection c = db.dataSource().getConnection()) {
            Dialect dialect = db.dialect();
            SqlGenFixture f = SqlGenFixture.build(profile.schemaName(), dialect);
            SqlGenFixture.comment(f.schema, f.customers, "all customers");
            SqlGenFixture.comment(f.schema, f.customers.getFeature().stream().filter(x -> "NAME".equals(x.getName())).findFirst().orElseThrow(), "it's the name");
            try {
                executeAll(c, profile.create(dialect, f.schema));
            } finally {
                profile.cleanup(c, f.schema, dialect);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("triggerDialects")
    void each_trigger_has_its_own_procedure_also_where_two_share_a_body(DialectProfile profile) throws Exception {
        ActiveDatabase db = activateOrSkip(profile);
        try (Connection c = db.dataSource().getConnection()) {
            Dialect dialect = db.dialect();
            SqlGenFixture f = SqlGenFixture.build(profile.schemaName(), dialect);
            f.attachTrigger(f.customers, "trg_audit_cust", profile.triggerBody(), profile.triggerTiming(),
                    profile.triggerEvent());
            f.attachTrigger(f.orders, "trg_audit_ord", profile.triggerBody(), profile.triggerTiming(),
                    profile.triggerEvent());
            try {
                List<String> ddl = profile.create(dialect, f.schema);
                assertThat(ddl.stream().filter(s -> s.startsWith(profile.triggerProcPrefix())).count())
                        .as("a procedure/function of its own for each trigger, so a drop of one leaves the other")
                        .isEqualTo(2);
                assertThat(ddl.stream().filter(s -> s.startsWith("CREATE TRIGGER")).count())
                        .as("two CREATE TRIGGER statements").isEqualTo(2);
                if (profile.triggerCallSnippet() != null) {
                    assertThat(ddl).anyMatch(
                            s -> s.startsWith("CREATE TRIGGER") && s.contains(profile.triggerCallSnippet()));
                }
                // Lands on a real engine.
                executeAll(c, ddl);
            } finally {
                profile.cleanup(c, f.schema, dialect);
            }
        }
    }

    /**
     * Where the dialect writes materialized views, a view the model says is one is created as one, and
     * the drop script drops it as one.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void aMaterializedViewIsCreatedAndDroppedAsOneWhereTheDatabaseHasThem(DialectProfile profile) throws Exception {
        ActiveDatabase db = activateOrSkip(profile);
        Dialect dialect = db.dialect();
        Assumptions.assumeTrue(dialect.ddlGenerator().supportsMaterializedViews(),
                profile + " has no materialized views");
        try (Connection c = db.dataSource().getConnection()) {
            SqlGenFixture f = SqlGenFixture.build(profile.schemaName(), dialect);
            TaggedValues.set(f.view, RelationalRules.MATERIALIZED_TAG, "true");
            DdlGenerator generator = new DdlGeneratorFactoryImpl().create(dialect, profile.settings());
            try {
                executeAll(c, generator.createSchema(f.schema));
                profile.prepareForMetadata(c);
                assertThat(materializedViews(profile, dialect, c)).containsExactly("CUSTOMER_ORDERS");

                executeAll(c, generator.dropSchema(f.schema));
                assertThat(materializedViews(profile, dialect, c)).isEmpty();
            } finally {
                profile.cleanup(c, f.schema, dialect);
            }
        }
    }

    /**
     * Where the dialect has partial indexes, an index over part of the rows is created with its
     * filter, and the database reports the filter.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void anIndexOverPartOfTheRowsKeepsItsFilterWhereTheDatabaseHasThem(DialectProfile profile) throws Exception {
        ActiveDatabase db = activateOrSkip(profile);
        Dialect dialect = db.dialect();
        Assumptions.assumeTrue(dialect.ddlGenerator().supportsFilteredIndexes(), profile + " has no partial indexes");
        try (Connection c = db.dataSource().getConnection()) {
            SqlGenFixture f = SqlGenFixture.build(profile.schemaName(), dialect);
            f.nameIndex.setFilterCondition(dialect.quoteIdentifier("NAME") + " IS NOT NULL");
            try {
                executeAll(c, new DdlGeneratorFactoryImpl().create(dialect, profile.settings().withStrict(true))
                        .createSchema(f.schema));
                profile.prepareForMetadata(c);
                assertThat(profile.snapshot(DB_SERVICE, c, dialect).indexInfos().stream()
                        .flatMap(ii -> ii.indexInfoItems().stream())
                        .filter(item -> item.indexName().orElse("").equals("IDX_CUSTOMERS_NAME"))
                        .map(item -> item.filterCondition().orElse("")))
                        .as("the filter of the index").isNotEmpty().allMatch(filter -> filter.contains("NAME"));
            } finally {
                profile.cleanup(c, f.schema, dialect);
            }
        }
    }

    /**
     * Where the dialect keeps the order of an index column, a descending column is created
     * {@code DESC}, and the database reports it descending.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void aDescendingIndexColumnKeepsItsOrderWhereTheDatabaseKeepsIt(DialectProfile profile) throws Exception {
        ActiveDatabase db = activateOrSkip(profile);
        Dialect dialect = db.dialect();
        Assumptions.assumeTrue(dialect.ddlGenerator().supportsDescendingIndexColumns(),
                profile + " keeps no order in an index");
        try (Connection c = db.dataSource().getConnection()) {
            SqlGenFixture f = SqlGenFixture.build(profile.schemaName(), dialect);
            ((SQLIndexColumn) f.nameIndex.getIndexedFeature().get(0)).setIsAscending(false);
            try {
                executeAll(c, new DdlGeneratorFactoryImpl().create(dialect, profile.settings().withStrict(true))
                        .createSchema(f.schema));
                profile.prepareForMetadata(c);
                assertThat(profile.snapshot(DB_SERVICE, c, dialect).indexInfos().stream()
                        .flatMap(ii -> ii.indexInfoItems().stream())
                        .filter(item -> item.indexName().orElse("").equals("IDX_CUSTOMERS_NAME"))
                        .map(item -> item.ascending().orElse(true)))
                        .as("the order of the index column").containsExactly(false);
            } finally {
                profile.cleanup(c, f.schema, dialect);
            }
        }
    }

    /**
     * Where the dialect has deferrable foreign keys, a deferrable key is created so, and the database
     * reports it deferred.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void aDeferrableForeignKeyStaysDeferrableWhereTheDatabaseHasThem(DialectProfile profile) throws Exception {
        ActiveDatabase db = activateOrSkip(profile);
        Dialect dialect = db.dialect();
        Assumptions.assumeTrue(dialect.ddlGenerator().supportsDeferrableForeignKeys(),
                profile + " checks every key at once");
        try (Connection c = db.dataSource().getConnection()) {
            SqlGenFixture f = SqlGenFixture.build(profile.schemaName(), dialect);
            f.ordersFk.setDeferrability(DeferrabilityType.INITIALLY_DEFERRED);
            try {
                executeAll(c, new DdlGeneratorFactoryImpl().create(dialect, profile.settings().withStrict(true))
                        .createSchema(f.schema));
                profile.prepareForMetadata(c);
                assertThat(profile.snapshot(DB_SERVICE, c, dialect).structureInfo().importedKeys().stream()
                        .filter(key -> "FK_ORDERS_CUSTOMERS".equals(key.name())).map(key -> key.deferrability()))
                        .as("the deferrability of the key").containsOnly(ImportedKey.Deferrability.INITIALLY_DEFERRED);
            } finally {
                profile.cleanup(c, f.schema, dialect);
            }
        }
    }

    /**
     * Where the dialect writes routines, a function in its language is created, and the drop script
     * drops it by its signature.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void aFunctionIsCreatedAndDroppedWhereTheDialectWritesItsLanguage(DialectProfile profile) throws Exception {
        Assumptions.assumeTrue(profile.routineLanguage() != null, profile + " writes no routines");
        ActiveDatabase db = activateOrSkip(profile);
        Dialect dialect = db.dialect();
        try (Connection c = db.dataSource().getConnection()) {
            SqlGenFixture f = SqlGenFixture.build(profile.schemaName(), dialect);
            f.attachFunction("fn_double", profile.routineParameter(), profile.routineLanguage(),
                    profile.routineBody());
            DdlGenerator generator = new DdlGeneratorFactoryImpl().create(dialect,
                    profile.settings().withStrict(true));
            try {
                executeAll(c, generator.createSchema(f.schema));
                profile.prepareForMetadata(c);
                assertThat(functions(profile, dialect, c)).contains("fn_double");

                executeAll(c, generator.dropSchema(f.schema));
                assertThat(functions(profile, dialect, c)).doesNotContain("fn_double");
            } finally {
                profile.cleanup(c, f.schema, dialect);
            }
        }
    }

    /** The names of the functions the metadata report. */
    private static List<String> functions(DialectProfile profile, Dialect dialect, Connection c) throws Exception {
        return profile.snapshot(DB_SERVICE, c, dialect).structureInfo().functions().stream()
                .map(fn -> fn.reference().name()).toList();
    }

    /** The names of the materialized views of the test's schema. */
    private static List<String> materializedViews(DialectProfile profile, Dialect dialect, Connection c)
            throws Exception {
        return profile.snapshot(DB_SERVICE, c, dialect).structureInfo().materializedViews().stream()
                .map(mv -> mv.view()).filter(v -> v.schema().map(s -> s.name()).orElse("")
                        .equalsIgnoreCase(profile.schemaName()))
                .map(TableReference::name).toList();
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
