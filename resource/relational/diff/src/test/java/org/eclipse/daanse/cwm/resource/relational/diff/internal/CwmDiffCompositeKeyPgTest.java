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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;

import org.eclipse.daanse.cwm.resource.relational.diff.internal.support.MigrationVerifier;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.support.MigrationVerifier.ImportedKeyFacts;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ReferentialRuleType;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.eclipse.daanse.sql.jdbc.api.DatabaseService;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.StructureInfo;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.daanse.sql.jdbc.impl.DatabaseServiceImpl;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.db.postgresql.PostgreSqlDialect;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Composite primary key + composite foreign key end-to-end:
 * <ul>
 *   <li>{@code ORDER_LINES} has a 2-column PK ({@code ORDER_ID, LINE_NO}).</li>
 *   <li>{@code ORDER_LINE_NOTES} has a 2-column FK referencing the PK above.</li>
 * </ul>
 * Verifies create-from-CWM works (composite key shape lands faithfully in PG),
 * then a diff that widens a column on the FK side applies cleanly while the
 * composite PK/FK linkage stays intact.
 *
 * <p>PK mutation on a table with inbound FKs is intentionally <em>not</em>
 * exercised here — that requires proper dependency analysis in the emitter
 * (drop dependent FKs, drop+rebuild PK, recreate FKs) which is tracked as a
 * separate work item.
 */
@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
class CwmDiffCompositeKeyPgTest {

    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;
    private static final DatabaseService DB_SERVICE = new DatabaseServiceImpl();

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> CONTAINER = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("rt").withUsername("rt").withPassword("rt");

    private Connection connection;
    private Dialect dialect;

    @BeforeAll
    void setUp() throws Exception {
        connection = DriverManager.getConnection(
                CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword());
        dialect = new PostgreSqlDialect();
    }

    @AfterAll
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) connection.close();
    }

    @Test
    void composite_pk_and_fk_round_trip() throws Exception {
        String schemaName = "rt_comp";
        Schema oldSchema = build(schemaName, 200);
        Schema newSchema = build(schemaName, 500); // widen NOTES.NOTE VARCHAR(200) -> (500)

        try {
            executeAll(new DdlGeneratorFactoryImpl().create(dialect).createSchema(oldSchema));

            // Composite PK + composite FK survive emit.
            MetaInfo before = DB_SERVICE.createMetaInfo(connection, new org.eclipse.daanse.sql.jdbc.metadata.PostgreSqlMetadataProvider());
            assertThat(MigrationVerifier.primaryKeyColumns(before.structureInfo(), schemaName, "ORDER_LINES"))
                    .containsExactly("ORDER_ID", "LINE_NO");
            ImportedKeyFacts beforeFk = MigrationVerifier.foreignKeysOf(
                    before.structureInfo(), schemaName, "ORDER_LINE_NOTES").get(0);
            assertThat(beforeFk.fkColumns()).containsExactly("ORDER_ID", "LINE_NO");
            assertThat(beforeFk.referencedColumns()).containsExactly("ORDER_ID", "LINE_NO");

            // Diff + apply a benign column change. PK/FK stay intact.
            SchemaDiff diff = new SchemaDifferImpl().diff(oldSchema, newSchema);
            assertThat(diff.tablesChanged()).hasSize(1);
            List<String> migration = new MigrationEmitterImpl(new DdlGeneratorFactoryImpl()).emit(ChangePlanner.create().plan(diff), dialect);
            executeAll(migration);

            MetaInfo after = DB_SERVICE.createMetaInfo(connection, new org.eclipse.daanse.sql.jdbc.metadata.PostgreSqlMetadataProvider());
            StructureInfo si = after.structureInfo();
            assertThat(MigrationVerifier.primaryKeyColumns(si, schemaName, "ORDER_LINES"))
                    .containsExactly("ORDER_ID", "LINE_NO");
            ImportedKeyFacts afterFk = MigrationVerifier.foreignKeysOf(si, schemaName, "ORDER_LINE_NOTES").get(0);
            assertThat(afterFk.fkColumns()).containsExactly("ORDER_ID", "LINE_NO");
            assertThat(afterFk.referencedColumns()).containsExactly("ORDER_ID", "LINE_NO");
            assertThat(MigrationVerifier.columnsOf(si, schemaName, "ORDER_LINE_NOTES").get("NOTE").columnSize())
                    .hasValue(500);
        } finally {
            try (Statement s = connection.createStatement()) {
                s.execute("DROP SCHEMA IF EXISTS \"" + schemaName + "\" CASCADE");
            } catch (SQLException ignored) {
                // already dropped
            }
        }
    }

    // fixture

    private static Schema build(String schemaName, int noteSize) {
        Schema schema = RF.createSchema();
        schema.setName(schemaName);

        // ORDER_LINES — PK (ORDER_ID, LINE_NO)
        Table orderLines = RF.createTable();
        orderLines.setName("ORDER_LINES");
        Column olOrderId = col("ORDER_ID", type("INTEGER", Types.INTEGER), true);
        Column olLineNo = col("LINE_NO", type("INTEGER", Types.INTEGER), true);
        Column olQty = col("QTY", type("INTEGER", Types.INTEGER), false);
        orderLines.getFeature().add(olOrderId);
        orderLines.getFeature().add(olLineNo);
        orderLines.getFeature().add(olQty);
        schema.getOwnedElement().add(orderLines);

        PrimaryKey olPk = RF.createPrimaryKey();
        olPk.setName("PK_ORDER_LINES");
        olPk.getFeature().add(olOrderId);
        olPk.getFeature().add(olLineNo);
        orderLines.getOwnedElement().add(olPk);

        // ORDER_LINE_NOTES — composite FK to ORDER_LINES (ORDER_ID, LINE_NO).
        Table notes = RF.createTable();
        notes.setName("ORDER_LINE_NOTES");
        Column nOrderId = col("ORDER_ID", type("INTEGER", Types.INTEGER), true);
        Column nLineNo = col("LINE_NO", type("INTEGER", Types.INTEGER), true);
        Column nText = col("NOTE", type("CHARACTER VARYING", Types.VARCHAR, noteSize), false);
        notes.getFeature().add(nOrderId);
        notes.getFeature().add(nLineNo);
        notes.getFeature().add(nText);
        schema.getOwnedElement().add(notes);

        ForeignKey notesFk = RF.createForeignKey();
        notesFk.setName("FK_NOTES_LINES");
        notesFk.getFeature().add(nOrderId);
        notesFk.getFeature().add(nLineNo);
        notesFk.setUniqueKey(olPk);
        notes.getOwnedElement().add(notesFk);
        notesFk.setDeleteRule(ReferentialRuleType.IMPORTED_KEY_CASCADE);
        notesFk.setUpdateRule(ReferentialRuleType.IMPORTED_KEY_NO_ACTION);

        return schema;
    }

    private static SQLSimpleType type(String name, int jdbc) {
        return type(name, jdbc, 0);
    }

    private static SQLSimpleType type(String name, int jdbc, long charMax) {
        SQLSimpleType t = RF.createSQLSimpleType();
        t.setName(name);
        t.setTypeNumber(jdbc);
        if (charMax > 0) t.setCharacterMaximumLength(charMax);
        return t;
    }

    private static Column col(String name, SQLSimpleType type, boolean notNull) {
        Column c = RF.createColumn();
        c.setName(name);
        c.setType(type);
        c.setIsNullable(notNull ? NullableType.COLUMN_NO_NULLS : NullableType.COLUMN_NULLABLE);
        return c;
    }

    private void executeAll(List<String> sql) throws SQLException {
        try (Statement s = connection.createStatement()) {
            for (String stmt : sql) {
                if (stmt == null || stmt.startsWith("--")) continue;
                try {
                    s.execute(stmt);
                } catch (SQLException e) {
                    throw new SQLException("failed: " + stmt + " — " + e.getMessage(), e);
                }
            }
        }
    }
}
