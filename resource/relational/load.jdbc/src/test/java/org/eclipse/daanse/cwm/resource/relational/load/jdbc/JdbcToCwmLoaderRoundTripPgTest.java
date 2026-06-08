/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.resource.relational.load.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.DatatypesFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.QueryExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.BooleanExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndexColumn;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ReferentialRuleType;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.JdbcToCwmConfig;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.internal.CwmLoaderImpl;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.Feature;
import org.eclipse.daanse.cwm.util.resource.relational.Catalogs;
import org.eclipse.daanse.cwm.util.resource.relational.ColumnSets;
import org.eclipse.daanse.cwm.util.resource.relational.ForeignKeys;
import org.eclipse.daanse.cwm.util.resource.relational.PrimaryKeys;
import org.eclipse.daanse.cwm.util.resource.relational.Schemas;
import org.eclipse.daanse.cwm.util.resource.relational.Tables;
import org.eclipse.daanse.cwm.util.resource.relational.UniqueConstraints;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.jdbc.datasource.testkit.postgresql.PostgresDatabaseProvider;
import org.eclipse.daanse.jdbc.db.api.DatabaseService;
import org.eclipse.daanse.jdbc.db.api.meta.MetaInfo;
import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.impl.DatabaseServiceImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

/**
 * PG round-trip: fixture → DDL via {@link org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlGenerator} → execute on
 * Testcontainers PG → snapshot via {@link DatabaseService} → load via
 * {@link org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.CwmLoader} → assert against the fixture.
 */
@TestInstance(Lifecycle.PER_CLASS)
class JdbcToCwmLoaderRoundTripPgTest {

    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;
    private static final CoreFactory CF = CoreFactory.eINSTANCE;
    private static final DatatypesFactory DF = DatatypesFactory.eINSTANCE;
    private static final DatabaseService DB_SERVICE = new DatabaseServiceImpl();

    private Connection connection;
    private Dialect dialect;

    @BeforeAll
    void setUp() throws Exception {
        ActiveDatabase dbInit = new PostgresDatabaseProvider().activate();
        connection = dbInit.dataSource().getConnection();
        dialect = dbInit.dialect();
    }

    @AfterAll
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed())
            connection.close();
    }

    @Test
    void jdbc_to_cwm_round_trip_matches_original_fixture() throws Exception {
        String schemaName = "rt_load";
        Schema fixture = buildFixture(schemaName);

        // 1. Emit DDL and execute on Postgres.
        List<String> ddl = new DdlGeneratorFactoryImpl().create(dialect).createSchema(fixture,
                EnumSet.complementOf(EnumSet.of(Feature.TRIGGER)));
        executeAll(ddl);

        // 1a. Out-of-band: install a PL/pgSQL function + trigger so the loader
        // has something to extract from pg_proc.prosrc. The CWM serializer
        // can't yet emit the function-then-trigger pair on its own.
        try (java.sql.Statement s = connection.createStatement()) {
            s.execute("CREATE OR REPLACE FUNCTION \"" + schemaName + "\".trg_audit_fn() "
                    + "RETURNS trigger LANGUAGE plpgsql AS $$ " + "BEGIN RAISE NOTICE 'audit'; RETURN NEW; END; $$");
            s.execute("CREATE TRIGGER trg_audit BEFORE INSERT ON \"" + schemaName + "\".\"CUSTOMERS\" FOR EACH ROW "
                    + "EXECUTE FUNCTION \"" + schemaName + "\".trg_audit_fn()");
        }

        try {
            // 2. Snapshot via DatabaseService — pass the dialect as MetadataProvider so
            // the snapshot includes UNIQUE/CHECK constraints (PG-specific catalogs).
            // PG's bulk-metadata queries scope to connection.getSchema() (defaults
            // to "public") so point it at the test schema first.
            connection.setSchema(schemaName);
            MetaInfo info = DB_SERVICE.createMetaInfo(connection, dialect);

            // 3. Load into a fresh CWM Catalog, scoped to the test schema.
            Catalog catalog = new CwmLoaderImpl().load(info,
                    JdbcToCwmConfig.builder().schemas(schemaName).catalogName("RT").build());

            // 4. Structural assertions vs the original fixture.
            assertThat(catalog.getName()).isEqualTo("RT");
            List<Schema> schemas = Catalogs.schemas(catalog);
            assertThat(schemas).hasSize(1);
            Schema loaded = schemas.get(0);
            assertThat(loaded.getName()).isEqualTo(schemaName);

            // Tables
            List<Table> tables = Schemas.tables(loaded);
            assertThat(tables).extracting(Table::getName).containsExactlyInAnyOrder("CUSTOMERS", "ORDERS");

            Table customers = tables.stream().filter(t -> t.getName().equals("CUSTOMERS")).findFirst().orElseThrow();
            Table orders = tables.stream().filter(t -> t.getName().equals("ORDERS")).findFirst().orElseThrow();

            // Columns
            List<Column> custCols = ColumnSets.columns(customers);
            assertThat(custCols).extracting(Column::getName).containsExactlyInAnyOrder("ID", "EMAIL", "NAME", "STATUS",
                    "META");
            Column custEmail = ColumnSets.findColumn(customers, "EMAIL").orElseThrow();
            assertThat(custEmail.getIsNullable()).isEqualTo(NullableType.COLUMN_NO_NULLS);
            assertThat(jdbcType(custEmail)).isEqualTo(Types.VARCHAR);
            Column custId = ColumnSets.findColumn(customers, "ID").orElseThrow();
            assertThat(custId.getIsNullable()).isEqualTo(NullableType.COLUMN_NO_NULLS);
            assertThat(jdbcType(custId)).isEqualTo(Types.INTEGER);

            // STATUS column carries a DEFAULT — Column.initialValue is populated by the
            // loader.
            Column custStatus = ColumnSets.findColumn(customers, "STATUS").orElseThrow();
            assertThat(custStatus.getInitialValue()).isNotNull();
            assertThat(custStatus.getInitialValue().getBody()).contains("NEW");

            // META column round-trips its native PG type name (jsonb) even though it
            // doesn't map to a JDBC code other than OTHER.
            Column custMeta = ColumnSets.findColumn(customers, "META").orElseThrow();
            assertThat(custMeta.getType()).isInstanceOf(SQLSimpleType.class);
            assertThat(((SQLSimpleType) custMeta.getType()).getName()).isEqualToIgnoringCase("jsonb");

            // Primary keys
            assertThat(PrimaryKeys.columns(Tables.findPrimaryKey(customers).orElseThrow())).extracting(Column::getName)
                    .containsExactly("ID");
            assertThat(PrimaryKeys.columns(Tables.findPrimaryKey(orders).orElseThrow())).extracting(Column::getName)
                    .containsExactly("ID");

            // Unique constraints (excluding PK)
            List<UniqueConstraint> custUcs = Tables.findUniqueConstraints(customers).stream()
                    .filter(uc -> !(uc instanceof PrimaryKey)).toList();
            assertThat(custUcs).hasSize(1);
            UniqueConstraint custUc = custUcs.get(0);
            assertThat(custUc.getName()).isEqualTo("UC_CUSTOMERS_EMAIL");
            assertThat(UniqueConstraints.columns(custUc)).extracting(Column::getName).containsExactly("EMAIL");

            // Foreign keys
            List<ForeignKey> ordFks = Tables.findForeignKeys(orders);
            assertThat(ordFks).hasSize(1);
            ForeignKey ordFk = ordFks.get(0);
            assertThat(ordFk.getName()).isEqualTo("FK_ORDERS_CUSTOMERS");
            assertThat(ForeignKeys.columns(ordFk)).extracting(Column::getName).containsExactly("CUSTOMER_ID");
            Optional<Table> targetTable = ForeignKeys.targetTable(ordFk);
            assertThat(targetTable).isPresent();
            assertThat(targetTable.get().getName()).isEqualTo("CUSTOMERS");
            assertThat(ordFk.getDeleteRule()).isEqualTo(ReferentialRuleType.IMPORTED_KEY_CASCADE);

            // Index
            SQLIndex idx = loaded.getOwnedElement().stream().filter(SQLIndex.class::isInstance)
                    .map(SQLIndex.class::cast).filter(i -> "IDX_CUSTOMERS_NAME".equals(i.getName())).findFirst()
                    .orElseThrow();
            assertThat(idx.getSpannedClass()).isEqualTo(customers);
            assertThat(idx.getIndexedFeature()).hasSize(1);
            assertThat(idx.getIndexedFeature().get(0).getFeature().getName()).isEqualTo("NAME");

            // View
            View view = Schemas.findView(loaded, "CUSTOMER_ORDERS").orElseThrow();
            assertThat(view.getQueryExpression()).isNotNull();
            assertThat(view.getQueryExpression().getBody()).isNotBlank();

            // Trigger — the loader pulled it via the PG MetadataProvider, with
            // body sourced from pg_proc.prosrc.
            assertThat(customers.getTrigger()).extracting(t -> t.getName()).contains("trg_audit");
            org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger loadedTrg = customers.getTrigger().stream()
                    .filter(t -> "trg_audit".equals(t.getName())).findFirst().orElseThrow();
            assertThat(loadedTrg.getActionStatement()).isNotNull();
            assertThat(loadedTrg.getActionStatement().getBody())
                    .as("loader stores the procedural source from pg_proc.prosrc").contains("audit")
                    .contains("RETURN NEW");
            assertThat(loadedTrg.getConditionTiming()).isEqualTo(
                    org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ConditionTimingType.BEFORE);
            assertThat(loadedTrg.getEventManipulation()).isEqualTo(
                    org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.EventManipulationType.INSERT);

            // CHECK constraint by name (PG normalizes the body, so we don't assert exact
            // text).
            List<CheckConstraint> custChecks = customers.getOwnedElement().stream()
                    .filter(CheckConstraint.class::isInstance).map(CheckConstraint.class::cast).toList();
            assertThat(custChecks).extracting(CheckConstraint::getName).contains("CK_CUSTOMERS_EMAIL_LEN");

            // 5. Re-emit DDL from the loaded catalog, and verify it runs against a
            // second schema cleanly — proves the loader produced a usable model.
            // PG normalises CHECK bodies (e.g. `LENGTH("EMAIL") > 3` becomes
            // `length((email)::text) > 3`); the rewritten form is still valid PG
            // SQL so it survives the second round-trip.
            String reSchemaName = schemaName + "_re";
            loaded.setName(reSchemaName);
            List<String> reDdl = new DdlGeneratorFactoryImpl().create(dialect).createSchema(loaded,
                    EnumSet.complementOf(EnumSet.of(Feature.TRIGGER)));
            executeAll(reDdl);
            // Re-snapshot via the daanse API and assert the re-emitted schema
            // exposes the same shape — no raw JDBC metadata reads.
            connection.setSchema(reSchemaName);
            MetaInfo reInfo = DB_SERVICE.createMetaInfo(connection, dialect);
            assertThat(reInfo.structureInfo().tables().stream().map(td -> td.table())
                    .filter(t -> reSchemaName.equals(
                            t.schema().map(org.eclipse.daanse.jdbc.db.api.schema.SchemaReference::name).orElse(null)))
                    .map(org.eclipse.daanse.jdbc.db.api.schema.TableReference::name)).contains("CUSTOMERS", "ORDERS");
            assertThat(reInfo.structureInfo().checkConstraints().stream()
                    .filter(c -> "CUSTOMERS".equals(c.table().name()) && reSchemaName.equals(c.table().schema()
                            .map(org.eclipse.daanse.jdbc.db.api.schema.SchemaReference::name).orElse(null)))
                    .map(org.eclipse.daanse.jdbc.db.api.schema.CheckConstraint::name))
                    .contains("CK_CUSTOMERS_EMAIL_LEN");
        } finally {
            try (Statement s = connection.createStatement()) {
                s.execute("DROP SCHEMA IF EXISTS \"" + schemaName + "\" CASCADE");
                s.execute("DROP SCHEMA IF EXISTS \"" + schemaName + "_re\" CASCADE");
            } catch (SQLException ignored) {
                // already dropped
            }
        }
    }

    // -------------------- fixture --------------------

    private static SQLSimpleType type(String name, int jdbc, long max, long prec, long scale) {
        SQLSimpleType t = RF.createSQLSimpleType();
        t.setName(name);
        t.setTypeNumber(jdbc);
        if (max > 0)
            t.setCharacterMaximumLength(max);
        if (prec > 0)
            t.setNumericPrecision(prec);
        if (scale > 0)
            t.setNumericScale(scale);
        return t;
    }

    private static Column col(String name, SQLSimpleType type, boolean notNull) {
        Column c = RF.createColumn();
        c.setName(name);
        c.setType(type);
        c.setIsNullable(notNull ? NullableType.COLUMN_NO_NULLS : NullableType.COLUMN_NULLABLE);
        return c;
    }

    private static Schema buildFixture(String schemaName) {
        Schema schema = RF.createSchema();
        schema.setName(schemaName);

        Table customers = RF.createTable();
        customers.setName("CUSTOMERS");
        Column cId = col("ID", type("INTEGER", Types.INTEGER, 0, 0, 0), true);
        Column cEmail = col("EMAIL", type("CHARACTER VARYING", Types.VARCHAR, 100, 0, 0), true);
        Column cName = col("NAME", type("CHARACTER VARYING", Types.VARCHAR, 50, 0, 0), false);
        // Defaulted column — verifies columnDefault round-trip.
        Column cStatus = col("STATUS", type("CHARACTER VARYING", Types.VARCHAR, 16, 0, 0), false);
        org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Expression statusDefault = CF.createExpression();
        statusDefault.setLanguage("SQL");
        statusDefault.setBody("'NEW'");
        cStatus.setInitialValue(statusDefault);
        // PG-native type — verifies typeName preservation through the round-trip.
        Column cMeta = col("META", type("jsonb", Types.OTHER, 0, 0, 0), false);
        customers.getFeature().add(cId);
        customers.getFeature().add(cEmail);
        customers.getFeature().add(cName);
        customers.getFeature().add(cStatus);
        customers.getFeature().add(cMeta);
        schema.getOwnedElement().add(customers);

        PrimaryKey customersPk = RF.createPrimaryKey();
        customersPk.setName("PK_CUSTOMERS");
        customersPk.getFeature().add(cId);
        cId.getUniqueKey().add(customersPk);

        UniqueConstraint uc = RF.createUniqueConstraint();
        uc.setName("UC_CUSTOMERS_EMAIL");
        uc.getFeature().add(cEmail);
        cEmail.getUniqueKey().add(uc);

        CheckConstraint cc = RF.createCheckConstraint();
        cc.setName("CK_CUSTOMERS_EMAIL_LEN");
        BooleanExpression be = CF.createBooleanExpression();
        be.setBody("LENGTH(\"EMAIL\") > 3");
        be.setLanguage("SQL");
        cc.setBody(be);
        customers.getOwnedElement().add(cc);

        SQLIndex idx = RF.createSQLIndex();
        idx.setName("IDX_CUSTOMERS_NAME");
        idx.setSpannedClass(customers);
        SQLIndexColumn ifc = RF.createSQLIndexColumn();
        ifc.setFeature(cName);
        idx.getIndexedFeature().add(ifc);
        schema.getOwnedElement().add(idx);

        Table orders = RF.createTable();
        orders.setName("ORDERS");
        Column oId = col("ID", type("INTEGER", Types.INTEGER, 0, 0, 0), true);
        Column oCustomerId = col("CUSTOMER_ID", type("INTEGER", Types.INTEGER, 0, 0, 0), true);
        Column oTotal = col("TOTAL", type("DECIMAL", Types.DECIMAL, 0, 10, 2), false);
        orders.getFeature().add(oId);
        orders.getFeature().add(oCustomerId);
        orders.getFeature().add(oTotal);
        schema.getOwnedElement().add(orders);

        PrimaryKey ordersPk = RF.createPrimaryKey();
        ordersPk.setName("PK_ORDERS");
        ordersPk.getFeature().add(oId);
        oId.getUniqueKey().add(ordersPk);

        ForeignKey fk = RF.createForeignKey();
        fk.setName("FK_ORDERS_CUSTOMERS");
        fk.getFeature().add(oCustomerId);
        fk.setUniqueKey(customersPk);
        oCustomerId.getKeyRelationship().add(fk);
        fk.setDeleteRule(ReferentialRuleType.IMPORTED_KEY_CASCADE);
        fk.setUpdateRule(ReferentialRuleType.IMPORTED_KEY_NO_ACTION);

        View view = RF.createView();
        view.setName("CUSTOMER_ORDERS");
        QueryExpression qe = DF.createQueryExpression();
        qe.setLanguage("SQL");
        qe.setBody("SELECT C.\"NAME\", O.\"TOTAL\" FROM \"" + schemaName + "\".\"CUSTOMERS\" C " + "JOIN \""
                + schemaName + "\".\"ORDERS\" O ON O.\"CUSTOMER_ID\" = C.\"ID\"");
        view.setQueryExpression(qe);
        schema.getOwnedElement().add(view);

        return schema;
    }

    private void executeAll(List<String> sql) throws SQLException {
        try (Statement s = connection.createStatement()) {
            for (String stmt : sql) {
                s.execute(stmt);
            }
        }
    }

    private static int jdbcType(Column col) {
        if (col.getType() instanceof SQLSimpleType s) {
            return (int) s.getTypeNumber();
        }
        return Types.OTHER;
    }
}
