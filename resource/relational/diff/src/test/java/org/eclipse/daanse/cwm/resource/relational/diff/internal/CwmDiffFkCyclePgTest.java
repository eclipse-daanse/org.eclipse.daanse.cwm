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
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.eclipse.daanse.sql.jdbc.api.DatabaseService;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
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
 * Foreign-key cycle: {@code USERS.PRIMARY_ROLE_ID → ROLES.ID} and
 * {@code ROLES.OWNER_USER_ID → USERS.ID}. Verifies that
 * {@link CwmDdlGenerator} emits tables before FKs (so neither side blocks
 * the other), and that {@link org.eclipse.daanse.cwm.resource.relational.diff.api.MigrationEmitter} drops dependent FKs before
 * dropping a table on either side of the cycle (via {@code CASCADE}).
 */
@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
class CwmDiffFkCyclePgTest {

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
    void fk_cycle_creates_and_drops_cleanly() throws Exception {
        String schemaName = "rt_cycle";
        Schema oldSchema = build(schemaName, /* dropOneSide */ false);
        Schema newSchema = build(schemaName, /* dropOneSide */ true);

        try {
            // Create both sides of the cycle.
            executeAll(new DdlGeneratorFactoryImpl().create(dialect).createSchema(oldSchema));

            MetaInfo before = DB_SERVICE.createMetaInfo(connection, new org.eclipse.daanse.sql.jdbc.metadata.PostgreSqlMetadataProvider());
            ImportedKeyFacts userFk = MigrationVerifier
                    .foreignKeysOf(before.structureInfo(), schemaName, "USERS").get(0);
            assertThat(userFk.referencedTable()).isEqualTo("ROLES");
            ImportedKeyFacts roleFk = MigrationVerifier
                    .foreignKeysOf(before.structureInfo(), schemaName, "ROLES").get(0);
            assertThat(roleFk.referencedTable()).isEqualTo("USERS");

            // Migration: drop ROLES (along with the cycle FKs). Cascade on drop is
            // required because USERS still has the FK to ROLES.
            SchemaDiff diff = new SchemaDifferImpl().diff(oldSchema, newSchema);
            List<String> migration = new MigrationEmitterImpl(new DdlGeneratorFactoryImpl())
                    .emit(ChangePlanner.create().plan(diff), dialect, DdlSettings.defaults().withCascade(true));
            executeAll(migration);

            MetaInfo after = DB_SERVICE.createMetaInfo(connection, new org.eclipse.daanse.sql.jdbc.metadata.PostgreSqlMetadataProvider());
            assertThat(MigrationVerifier
                    .tableNames(after.structureInfo(), schemaName,
                            org.eclipse.daanse.sql.model.schema.TableReference.TYPE_TABLE))
                    .containsExactly("USERS")
                    .doesNotContain("ROLES");
        } finally {
            try (Statement s = connection.createStatement()) {
                s.execute("DROP SCHEMA IF EXISTS \"" + schemaName + "\" CASCADE");
            } catch (SQLException ignored) {
                // already dropped
            }
        }
    }

    /** Build USERS ↔ ROLES with a cycle. {@code dropOneSide} drops ROLES. */
    private static Schema build(String schemaName, boolean dropOneSide) {
        Schema schema = RF.createSchema();
        schema.setName(schemaName);

        // USERS
        Table users = RF.createTable();
        users.setName("USERS");
        Column uId = col("ID", type("INTEGER", Types.INTEGER), true);
        Column uPrimaryRoleId = col("PRIMARY_ROLE_ID", type("INTEGER", Types.INTEGER), false);
        users.getFeature().add(uId);
        users.getFeature().add(uPrimaryRoleId);
        schema.getOwnedElement().add(users);
        PrimaryKey usersPk = RF.createPrimaryKey();
        usersPk.setName("PK_USERS");
        usersPk.getFeature().add(uId);
        users.getOwnedElement().add(usersPk);

        if (dropOneSide) {
            // No ROLES, no FKs. USERS keeps PRIMARY_ROLE_ID dangling.
            return schema;
        }

        // ROLES
        Table roles = RF.createTable();
        roles.setName("ROLES");
        Column rId = col("ID", type("INTEGER", Types.INTEGER), true);
        Column rOwnerUserId = col("OWNER_USER_ID", type("INTEGER", Types.INTEGER), false);
        roles.getFeature().add(rId);
        roles.getFeature().add(rOwnerUserId);
        schema.getOwnedElement().add(roles);
        PrimaryKey rolesPk = RF.createPrimaryKey();
        rolesPk.setName("PK_ROLES");
        rolesPk.getFeature().add(rId);
        roles.getOwnedElement().add(rolesPk);

        // USERS.PRIMARY_ROLE_ID → ROLES.ID
        ForeignKey usersFk = RF.createForeignKey();
        usersFk.setName("FK_USERS_PRIMARY_ROLE");
        usersFk.getFeature().add(uPrimaryRoleId);
        usersFk.setUniqueKey(rolesPk);
        users.getOwnedElement().add(usersFk);
        usersFk.setDeleteRule(ReferentialRuleType.IMPORTED_KEY_NO_ACTION);
        usersFk.setUpdateRule(ReferentialRuleType.IMPORTED_KEY_NO_ACTION);

        // ROLES.OWNER_USER_ID → USERS.ID
        ForeignKey rolesFk = RF.createForeignKey();
        rolesFk.setName("FK_ROLES_OWNER_USER");
        rolesFk.getFeature().add(rOwnerUserId);
        rolesFk.setUniqueKey(usersPk);
        roles.getOwnedElement().add(rolesFk);
        rolesFk.setDeleteRule(ReferentialRuleType.IMPORTED_KEY_NO_ACTION);
        rolesFk.setUpdateRule(ReferentialRuleType.IMPORTED_KEY_NO_ACTION);

        return schema;
    }

    private static SQLSimpleType type(String name, int jdbc) {
        SQLSimpleType t = RF.createSQLSimpleType();
        t.setName(name);
        t.setTypeNumber(jdbc);
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
