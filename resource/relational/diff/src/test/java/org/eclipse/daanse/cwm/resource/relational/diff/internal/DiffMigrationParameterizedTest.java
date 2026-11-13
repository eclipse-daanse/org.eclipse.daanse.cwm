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

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.daanse.cwm.resource.relational.ddl.internal.support.SqlGenAssertions.executeAll;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.support.MigrationFixtures;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.support.MigrationVerifier;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.support.MigrationVerifier.ImportedKeyFacts;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.support.DialectProfile;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.sql.jdbc.api.DatabaseService;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.StructureInfo;
import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.jdbc.impl.DatabaseServiceImpl;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * One migration "source" — diff an old schema against a new one, emit and apply
 * the {@code ALTER}/{@code DROP}/{@code CREATE} migration, then assert the live
 * structure — run across every {@link DialectProfile}. Replaces the five
 * per-dialect {@code CwmDiffMigration*Test} classes.
 */
class DiffMigrationParameterizedTest {

    private static final DatabaseService DB_SERVICE = new DatabaseServiceImpl();

    /** The migration adds the foreign key of the new table with ALTER TABLE. */
    static Stream<DialectProfile> dialects() {
        return Stream.of(DialectProfile.values()).filter(DialectProfile::altersConstraints);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void diff_drives_migration_from_old_to_new_schema(DialectProfile profile) throws Exception {
        ActiveDatabase db = activateOrSkip(profile);
        try (Connection c = db.dataSource().getConnection()) {
            Dialect dialect = db.dialect();
            String schemaName = profile.schemaName();
            Schema oldSchema = MigrationFixtures.buildOld(schemaName, dialect);
            Schema newSchema = MigrationFixtures.buildNew(schemaName, dialect);
            try {
                executeAll(c, profile.create(dialect, oldSchema));

                SchemaDiff diff = new SchemaDifferImpl().diff(oldSchema, newSchema);
                assertThat(diff.isEmpty()).isFalse();
                List<String> migration = new MigrationEmitterImpl(new DdlGeneratorFactoryImpl()).emit(ChangePlanner.create().plan(diff), dialect);
                assertThat(migration).isNotEmpty();
                executeAll(c, migration);

                profile.prepareForMetadata(c);
                MetaInfo info = profile.snapshot(DB_SERVICE, c, dialect);
                StructureInfo si = info.structureInfo();

                // 'contains' rather than 'exactly': the MariaDB snapshot leaks a
                // handful of unscoped information_schema tables. The exact shape of
                // CUSTOMERS/AUDIT_LOG is pinned by the column/PK/FK assertions below.
                assertThat(MigrationVerifier.tableNames(si, schemaName, TableReference.TYPE_TABLE))
                        .contains("CUSTOMERS", "AUDIT_LOG");

                Map<String, ColumnMetaData> cust = MigrationVerifier.columnsOf(si, schemaName, "CUSTOMERS");
                assertThat(cust).containsOnlyKeys("ID", "EMAIL", "REGION");
                assertThat(cust.get("EMAIL").nullability()).isEqualTo(ColumnMetaData.Nullability.NULLABLE);
                assertThat(cust.get("EMAIL").columnSize()).hasValue(200);
                assertThat(cust.get("REGION").columnDefault()).hasValueSatisfying(d -> assertThat(d).contains("EU"));

                Map<String, ColumnMetaData> audit = MigrationVerifier.columnsOf(si, schemaName, "AUDIT_LOG");
                assertThat(audit).containsOnlyKeys("ID", "CUSTOMER_ID", "MESSAGE");
                assertThat(MigrationVerifier.primaryKeyColumns(si, schemaName, "AUDIT_LOG")).containsExactly("ID");
                ImportedKeyFacts auditFk = MigrationVerifier.foreignKeysOf(si, schemaName, "AUDIT_LOG").get(0);
                assertThat(auditFk.fkColumns()).containsExactly("CUSTOMER_ID");
                assertThat(auditFk.referencedTable()).isEqualTo("CUSTOMERS");
                assertThat(auditFk.referencedColumns()).containsExactly("ID");

                assertThat(MigrationVerifier.tableNames(si, schemaName, TableReference.TYPE_VIEW))
                        .contains("CUSTOMER_SUMMARY");

                assertThat(new SchemaDifferImpl().diff(newSchema, newSchema).isEmpty()).isTrue();
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
