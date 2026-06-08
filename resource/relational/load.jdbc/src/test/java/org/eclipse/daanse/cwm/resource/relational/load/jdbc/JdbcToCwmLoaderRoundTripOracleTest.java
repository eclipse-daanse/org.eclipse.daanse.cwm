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
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ConditionTimingType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.EventManipulationType;
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
import org.eclipse.daanse.jdbc.datasource.testkit.oracle.OracleDatabaseProvider;
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
 * Oracle round-trip — same shape as the PG test. Also verifies that a
 * {@code BEFORE INSERT OR UPDATE} trigger lands as a single CWM
 * {@link org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger}: CWM 1.1
 * has a single-valued {@link EventManipulationType}, so the loader keeps the
 * primary event (INSERT) and preserves the PL/SQL body in
 * {@code actionStatement}. Use two CWM triggers if you need multiple events
 * structurally.
 *
 * <p>
 * Image is ~3 GB and takes ~20–30 s on first run.
 */
@TestInstance(Lifecycle.PER_CLASS)
class JdbcToCwmLoaderRoundTripOracleTest {

    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;
    private static final CoreFactory CF = CoreFactory.eINSTANCE;
    private static final DatatypesFactory DF = DatatypesFactory.eINSTANCE;
    private static final DatabaseService DB_SERVICE = new DatabaseServiceImpl();

    private Connection connection;
    private Dialect dialect;

    @BeforeAll
    void setUp() throws Exception {
        ActiveDatabase dbInit = new OracleDatabaseProvider().activate();
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
        // Oracle: schema == user. The container's user comes back as upper-case
        // in the metadata catalogs, so build the fixture under that name.
        // OracleDatabaseProvider creates the container with username "rt"; Oracle uppercases stored names.
        String schemaName = "RT";
        Schema fixture = buildFixture(schemaName);

        // Skip Feature.SCHEMA — Oracle schemas are users, not CREATE SCHEMA targets.
        List<String> ddl = new DdlGeneratorFactoryImpl().create(dialect).createSchema(fixture,
                EnumSet.complementOf(EnumSet.of(Feature.TRIGGER, Feature.SCHEMA)));
        executeAll(ddl);

        // Install a multi-event trigger out-of-band — the CWM serializer only
        // emits single-event triggers, but we want to test the loader side.
        try (Statement s = connection.createStatement()) {
            s.execute(
                    "CREATE OR REPLACE TRIGGER \"" + schemaName + "\".\"TRG_AUDIT\" " + "BEFORE INSERT OR UPDATE ON \""
                            + schemaName + "\".\"CUSTOMERS\" " + "FOR EACH ROW BEGIN NULL; END;");
        }

        try {
            MetaInfo info = DB_SERVICE.createMetaInfo(connection, dialect);
            Catalog catalog = new CwmLoaderImpl().load(info,
                    JdbcToCwmConfig.builder().schemas(schemaName).catalogName("RT").build());

            assertThat(catalog.getName()).isEqualTo("RT");
            List<Schema> schemas = Catalogs.schemas(catalog);
            assertThat(schemas).extracting(Schema::getName).contains(schemaName);
            Schema loaded = schemas.stream().filter(sc -> schemaName.equals(sc.getName())).findFirst().orElseThrow();

            List<Table> tables = Schemas.tables(loaded);
            assertThat(tables).extracting(Table::getName).contains("CUSTOMERS", "ORDERS");

            Table customers = tables.stream().filter(t -> t.getName().equals("CUSTOMERS")).findFirst().orElseThrow();
            Table orders = tables.stream().filter(t -> t.getName().equals("ORDERS")).findFirst().orElseThrow();

            List<Column> custCols = ColumnSets.columns(customers);
            assertThat(custCols).extracting(Column::getName).containsExactlyInAnyOrder("ID", "EMAIL", "NAME", "STATUS");
            Column custEmail = ColumnSets.findColumn(customers, "EMAIL").orElseThrow();
            assertThat(custEmail.getIsNullable()).isEqualTo(NullableType.COLUMN_NO_NULLS);
            assertThat(jdbcType(custEmail)).isEqualTo(Types.VARCHAR);
            Column custId = ColumnSets.findColumn(customers, "ID").orElseThrow();
            assertThat(custId.getIsNullable()).isEqualTo(NullableType.COLUMN_NO_NULLS);
            // Oracle INTEGER is a synonym for NUMBER(38,0); accept any of the
            // numeric JDBC codes so the test survives across Oracle versions.
            assertThat(jdbcType(custId)).isIn(Types.INTEGER, Types.NUMERIC, Types.DECIMAL);

            Column custStatus = ColumnSets.findColumn(customers, "STATUS").orElseThrow();
            assertThat(custStatus.getInitialValue()).isNotNull();
            assertThat(custStatus.getInitialValue().getBody()).contains("NEW");

            assertThat(PrimaryKeys.columns(Tables.findPrimaryKey(customers).orElseThrow())).extracting(Column::getName)
                    .containsExactly("ID");
            assertThat(PrimaryKeys.columns(Tables.findPrimaryKey(orders).orElseThrow())).extracting(Column::getName)
                    .containsExactly("ID");

            List<UniqueConstraint> custUcs = Tables.findUniqueConstraints(customers).stream()
                    .filter(uc -> !(uc instanceof PrimaryKey)).toList();
            assertThat(custUcs).hasSize(1);
            UniqueConstraint custUc = custUcs.get(0);
            assertThat(custUc.getName()).isEqualTo("UC_CUSTOMERS_EMAIL");
            assertThat(UniqueConstraints.columns(custUc)).extracting(Column::getName).containsExactly("EMAIL");

            List<ForeignKey> ordFks = Tables.findForeignKeys(orders);
            assertThat(ordFks).hasSize(1);
            ForeignKey ordFk = ordFks.get(0);
            assertThat(ordFk.getName()).isEqualTo("FK_ORDERS_CUSTOMERS");
            assertThat(ForeignKeys.columns(ordFk)).extracting(Column::getName).containsExactly("CUSTOMER_ID");
            Optional<Table> targetTable = ForeignKeys.targetTable(ordFk);
            assertThat(targetTable).isPresent();
            assertThat(targetTable.get().getName()).isEqualTo("CUSTOMERS");
            assertThat(ordFk.getDeleteRule()).isEqualTo(ReferentialRuleType.IMPORTED_KEY_CASCADE);

            SQLIndex idx = loaded.getOwnedElement().stream().filter(SQLIndex.class::isInstance)
                    .map(SQLIndex.class::cast).filter(i -> "IDX_CUSTOMERS_NAME".equals(i.getName())).findFirst()
                    .orElseThrow();
            assertThat(idx.getSpannedClass()).isEqualTo(customers);
            assertThat(idx.getIndexedFeature()).hasSize(1);
            assertThat(idx.getIndexedFeature().get(0).getFeature().getName()).isEqualTo("NAME");

            View view = Schemas.findView(loaded, "CUSTOMER_ORDERS").orElseThrow();
            assertThat(view.getQueryExpression()).isNotNull();
            assertThat(view.getQueryExpression().getBody()).isNotBlank();

            // BEFORE INSERT OR UPDATE → one CWM Trigger, eventManipulation = INSERT.
            List<org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger> triggersForCustomers = customers
                    .getTrigger().stream().filter(t -> "TRG_AUDIT".equalsIgnoreCase(t.getName())).toList();
            assertThat(triggersForCustomers).as("multi-event Oracle trigger maps to a single CWM Trigger").hasSize(1);
            org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger loadedTrg = triggersForCustomers.get(0);
            assertThat(loadedTrg.getConditionTiming()).isEqualTo(ConditionTimingType.BEFORE);
            assertThat(loadedTrg.getEventManipulation()).isEqualTo(EventManipulationType.INSERT);
            assertThat(loadedTrg.getActionStatement()).isNotNull();
            assertThat(loadedTrg.getActionStatement().getBody()).contains("BEGIN").contains("END");

            List<CheckConstraint> custChecks = customers.getOwnedElement().stream()
                    .filter(CheckConstraint.class::isInstance).map(CheckConstraint.class::cast).toList();
            assertThat(custChecks).extracting(CheckConstraint::getName).contains("CK_CUSTOMERS_ID_POS");
        } finally {
            for (String stmt : List.of("DROP TRIGGER \"" + schemaName + "\".\"TRG_AUDIT\"",
                    "DROP VIEW \"CUSTOMER_ORDERS\"", "DROP TABLE \"ORDERS\" CASCADE CONSTRAINTS",
                    "DROP TABLE \"CUSTOMERS\" CASCADE CONSTRAINTS")) {
                try (Statement s = connection.createStatement()) {
                    s.execute(stmt);
                } catch (SQLException ignored) {
                    // already gone
                }
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
        cc.setName("CK_CUSTOMERS_ID_POS");
        BooleanExpression be = CF.createBooleanExpression();
        be.setBody("\"ID\" > 0");
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
        // Oracle FKs don't accept ON UPDATE — the emitter drops the clause.
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
