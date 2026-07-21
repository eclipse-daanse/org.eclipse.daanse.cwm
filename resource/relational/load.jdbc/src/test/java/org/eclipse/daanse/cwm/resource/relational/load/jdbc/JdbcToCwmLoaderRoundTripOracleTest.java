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
import java.sql.Statement;
import java.sql.Types;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Description;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ConditionTimingType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.EventManipulationType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ReferentialRuleType;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.Feature;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.JdbcToCwmConfig;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.CwmLoader;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.internal.CwmLoaderImpl;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ColumnSets;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ForeignKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.PrimaryKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.UniqueConstraints;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.jdbc.datasource.testkit.oracle.OracleDatabaseProvider;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.jdbc.api.DatabaseService;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.eclipse.daanse.sql.jdbc.impl.DatabaseServiceImpl;
import org.eclipse.daanse.sql.jdbc.metadata.OracleMetadataProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

/**
 * Oracle round-trip — same shape as the PG test. Also verifies that a
 * {@code BEFORE INSERT OR UPDATE} trigger is split into one CWM
 * {@link org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger} per
 * event: CWM 1.1 has a single-valued {@link EventManipulationType}, so the
 * loader emits suffixed triggers ({@code _INSERT}, {@code _UPDATE}) that share
 * the PL/SQL body in {@code actionStatement}.
 *
 * <p>
 * Oracle's JDBC driver only reports table/column comments (REMARKS) when
 * {@code remarksReporting} is enabled — the test switches it on via
 * {@code OracleConnection.setRemarksReporting(true)}.
 *
 * <p>
 * Image is ~3 GB and takes ~20–30 s on first run.
 */
@TestInstance(Lifecycle.PER_CLASS)
class JdbcToCwmLoaderRoundTripOracleTest {

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
        Schema fixture = RoundTripFixture.build(schemaName, RoundTripFixture.Options.oracle());

        // Skip Feature.SCHEMA — Oracle schemas are users, not CREATE SCHEMA targets.
        List<String> ddl = new DdlGeneratorFactoryImpl().create(dialect).createSchema(fixture,
                EnumSet.complementOf(EnumSet.of(Feature.TRIGGER, Feature.SCHEMA)));
        RoundTripFixture.executeAll(connection, ddl);

        // Install a multi-event trigger out-of-band — the CWM serializer only
        // emits single-event triggers, but we want to test the loader side.
        // Also add table/column comments — the loader turns JDBC REMARKS into
        // CWM business-information Descriptions.
        try (Statement s = connection.createStatement()) {
            s.execute(
                    "CREATE OR REPLACE TRIGGER \"" + schemaName + "\".\"TRG_AUDIT\" " + "BEFORE INSERT OR UPDATE ON \""
                            + schemaName + "\".\"CUSTOMERS\" " + "FOR EACH ROW BEGIN NULL; END;");
            s.execute("COMMENT ON TABLE \"" + schemaName + "\".\"CUSTOMERS\" IS 'Customer master data'");
            s.execute("COMMENT ON COLUMN \"" + schemaName + "\".\"CUSTOMERS\".\"EMAIL\" IS 'Unique customer email'");
        }

        try {
            // Oracle only exposes comments as JDBC REMARKS when asked to.
            connection.unwrap(oracle.jdbc.OracleConnection.class).setRemarksReporting(true);
            MetaInfo info = DB_SERVICE.createMetaInfo(connection, new OracleMetadataProvider());
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
            assertThat(RoundTripFixture.jdbcType(custEmail)).isEqualTo(Types.VARCHAR);
            Column custId = ColumnSets.findColumn(customers, "ID").orElseThrow();
            assertThat(custId.getIsNullable()).isEqualTo(NullableType.COLUMN_NO_NULLS);
            // Oracle INTEGER is a synonym for NUMBER(38,0); accept any of the
            // numeric JDBC codes so the test survives across Oracle versions.
            assertThat(RoundTripFixture.jdbcType(custId)).isIn(Types.INTEGER, Types.NUMERIC, Types.DECIMAL);

            Column custStatus = ColumnSets.findColumn(customers, "STATUS").orElseThrow();
            assertThat(custStatus.getInitialValue()).isNotNull();
            assertThat(custStatus.getInitialValue().getBody()).contains("NEW");

            // JDBC REMARKS land as typed CWM Descriptions on the elements.
            assertThat(Descriptions.find(customers, CwmLoader.DESCRIPTION_TYPE_JDBC_REMARKS)).get().extracting(Description::getBody).isEqualTo("Customer master data");
            assertThat(Descriptions.find(custEmail, CwmLoader.DESCRIPTION_TYPE_JDBC_REMARKS)).get().extracting(Description::getBody).isEqualTo("Unique customer email");

            assertThat(PrimaryKeys.columns(Tables.findPrimaryKey(customers).orElseThrow())).extracting(Column::getName)
                    .containsExactly("ID");
            assertThat(PrimaryKeys.columns(Tables.findPrimaryKey(orders).orElseThrow())).extracting(Column::getName)
                    .containsExactly("ID");

            List<UniqueConstraint> custUcs = Tables.uniqueConstraints(customers).stream()
                    .filter(uc -> !(uc instanceof PrimaryKey)).toList();
            assertThat(custUcs).hasSize(1);
            UniqueConstraint custUc = custUcs.get(0);
            assertThat(custUc.getName()).isEqualTo("UC_CUSTOMERS_EMAIL");
            assertThat(UniqueConstraints.columns(custUc)).extracting(Column::getName).containsExactly("EMAIL");

            List<ForeignKey> ordFks = Tables.foreignKeys(orders);
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

            // BEFORE INSERT OR UPDATE → one CWM Trigger per event, suffixed.
            List<org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger> triggersForCustomers = customers
                    .getTrigger().stream().filter(t -> t.getName().toUpperCase().startsWith("TRG_AUDIT")).toList();
            assertThat(triggersForCustomers).as("multi-event Oracle trigger splits into one CWM Trigger per event")
                    .extracting(t -> t.getName()).containsExactlyInAnyOrder("TRG_AUDIT_INSERT", "TRG_AUDIT_UPDATE");
            org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger loadedTrg = triggersForCustomers.stream()
                    .filter(t -> "TRG_AUDIT_INSERT".equals(t.getName())).findFirst().orElseThrow();
            assertThat(loadedTrg.getConditionTiming()).isEqualTo(ConditionTimingType.BEFORE);
            assertThat(loadedTrg.getEventManipulation()).isEqualTo(EventManipulationType.INSERT);
            assertThat(loadedTrg.getActionStatement()).isNotNull();
            assertThat(loadedTrg.getActionStatement().getBody()).contains("BEGIN").contains("END");
            assertThat(triggersForCustomers.stream().filter(t -> "TRG_AUDIT_UPDATE".equals(t.getName())).findFirst()
                    .orElseThrow().getEventManipulation()).isEqualTo(EventManipulationType.UPDATE);

            List<CheckConstraint> custChecks = customers.getOwnedElement().stream()
                    .filter(CheckConstraint.class::isInstance).map(CheckConstraint.class::cast).toList();
            assertThat(custChecks).extracting(CheckConstraint::getName).contains("CK_CUSTOMERS_ID_POS");
        } finally {
            RoundTripFixture.executeIgnoring(connection,
                    "DROP TRIGGER \"" + schemaName + "\".\"TRG_AUDIT\"",
                    "DROP VIEW \"CUSTOMER_ORDERS\"",
                    "DROP TABLE \"ORDERS\" CASCADE CONSTRAINTS",
                    "DROP TABLE \"CUSTOMERS\" CASCADE CONSTRAINTS");
        }
    }
}
