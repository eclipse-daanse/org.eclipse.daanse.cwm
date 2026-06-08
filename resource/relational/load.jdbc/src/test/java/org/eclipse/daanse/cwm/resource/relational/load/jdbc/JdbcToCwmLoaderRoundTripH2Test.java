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
import org.eclipse.daanse.cwm.resource.relational.ddl.api.Feature;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.JdbcToCwmConfig;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.internal.CwmLoaderImpl;
import org.eclipse.daanse.cwm.util.resource.relational.Catalogs;
import org.eclipse.daanse.cwm.util.resource.relational.ColumnSets;
import org.eclipse.daanse.cwm.util.resource.relational.Schemas;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.jdbc.datasource.testkit.h2.H2DatabaseProvider;
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
 * H2 round-trip: fixture → DDL → execute on in-process H2 → snapshot via
 * {@link DatabaseService} → load via
 * {@link org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.CwmLoader} →
 * assert against the fixture.
 *
 * <p>H2 runs in-process (no Docker), so this is the cheap, always-runnable
 * counterpart to the Postgres/Oracle container tests. It covers the same
 * structural shape minus the DB-native extras those tests exercise (jsonb,
 * PL/pgSQL triggers).
 */
@TestInstance(Lifecycle.PER_CLASS)
class JdbcToCwmLoaderRoundTripH2Test {

    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;
    private static final CoreFactory CF = CoreFactory.eINSTANCE;
    private static final DatatypesFactory DF = DatatypesFactory.eINSTANCE;
    private static final DatabaseService DB_SERVICE = new DatabaseServiceImpl();

    private Connection connection;
    private Dialect dialect;

    @BeforeAll
    void setUp() throws Exception {
        ActiveDatabase dbInit = new H2DatabaseProvider().activate();
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
        String schemaName = "RT_H2";
        Schema fixture = buildFixture(schemaName);

        // 1. Emit DDL (no triggers — H2 triggers need Java class bodies) and run it.
        List<String> ddl = new DdlGeneratorFactoryImpl().create(dialect).createSchema(fixture,
                EnumSet.complementOf(EnumSet.of(Feature.TRIGGER)));
        executeAll(ddl);

        try {
            // 2. Snapshot via DatabaseService. H2's bulk-metadata snapshot is
            // structural — tables, columns and views — so this is a lighter
            // round-trip than the PG/Oracle tests (which add PK/UC/FK/CHECK/index
            // via dialect-specific catalogs). We don't scope the connection; the
            // loader filters by schema name.
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

            // Columns: names, JDBC types and nullability round-trip.
            List<Column> custCols = ColumnSets.columns(customers);
            assertThat(custCols).extracting(Column::getName).containsExactlyInAnyOrder("ID", "EMAIL", "NAME", "STATUS");
            Column custEmail = ColumnSets.findColumn(customers, "EMAIL").orElseThrow();
            assertThat(custEmail.getIsNullable()).isEqualTo(NullableType.COLUMN_NO_NULLS);
            assertThat(jdbcType(custEmail)).isEqualTo(Types.VARCHAR);
            Column custId = ColumnSets.findColumn(customers, "ID").orElseThrow();
            assertThat(custId.getIsNullable()).isEqualTo(NullableType.COLUMN_NO_NULLS);
            assertThat(jdbcType(custId)).isEqualTo(Types.INTEGER);
            Column custName = ColumnSets.findColumn(customers, "NAME").orElseThrow();
            assertThat(custName.getIsNullable()).isEqualTo(NullableType.COLUMN_NULLABLE);

            // STATUS column carries a DEFAULT — Column.initialValue is populated.
            Column custStatus = ColumnSets.findColumn(customers, "STATUS").orElseThrow();
            assertThat(custStatus.getInitialValue()).isNotNull();
            assertThat(custStatus.getInitialValue().getBody()).contains("NEW");

            // View round-trips as a relation (H2's snapshot doesn't expose the
            // view body, so we only assert the view itself was loaded).
            View view = Schemas.findView(loaded, "CUSTOMER_ORDERS").orElseThrow();
            assertThat(view.getName()).isEqualTo("CUSTOMER_ORDERS");

            // 5. Re-emit DDL from the loaded catalog against a second schema — proves
            // the loader produced a usable model.
            String reSchemaName = schemaName + "_RE";
            loaded.setName(reSchemaName);
            // The loaded view has no body (H2 doesn't expose it), so re-emit only
            // tables — enough to prove the loaded model is structurally usable.
            List<String> reDdl = new DdlGeneratorFactoryImpl().create(dialect).createSchema(loaded,
                    EnumSet.complementOf(EnumSet.of(Feature.TRIGGER, Feature.VIEW)));
            executeAll(reDdl);
            MetaInfo reInfo = DB_SERVICE.createMetaInfo(connection, dialect);
            assertThat(reInfo.structureInfo().tables().stream().map(td -> td.table())
                    .filter(t -> reSchemaName.equals(
                            t.schema().map(org.eclipse.daanse.jdbc.db.api.schema.SchemaReference::name).orElse(null)))
                    .map(org.eclipse.daanse.jdbc.db.api.schema.TableReference::name)).contains("CUSTOMERS", "ORDERS");
        } finally {
            try (Statement s = connection.createStatement()) {
                s.execute("DROP SCHEMA IF EXISTS \"" + schemaName + "\" CASCADE");
                s.execute("DROP SCHEMA IF EXISTS \"" + schemaName + "_RE\" CASCADE");
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
        customers.getFeature().add(cId);
        customers.getFeature().add(cEmail);
        customers.getFeature().add(cName);
        customers.getFeature().add(cStatus);
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
