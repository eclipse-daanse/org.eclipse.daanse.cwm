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

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Description;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.Feature;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.JdbcToCwmConfig;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.CwmLoader;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.internal.CwmLoaderImpl;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ColumnSets;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.jdbc.datasource.testkit.h2.H2DatabaseProvider;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.jdbc.api.DatabaseService;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.eclipse.daanse.sql.jdbc.impl.DatabaseServiceImpl;
import org.eclipse.daanse.sql.jdbc.metadata.H2MetadataProvider;
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
        Schema fixture = RoundTripFixture.build(schemaName, RoundTripFixture.Options.standard());

        // 1. Emit DDL (no triggers — H2 triggers need Java class bodies) and run it.
        List<String> ddl = new DdlGeneratorFactoryImpl().create(dialect).createSchema(fixture,
                EnumSet.complementOf(EnumSet.of(Feature.TRIGGER)));
        RoundTripFixture.executeAll(connection, ddl);

        // 1a. Out-of-band: table and column comments — the loader turns JDBC
        // REMARKS into CWM business-information Descriptions.
        try (Statement s = connection.createStatement()) {
            s.execute("COMMENT ON TABLE \"" + schemaName + "\".\"CUSTOMERS\" IS 'Customer master data'");
            s.execute("COMMENT ON COLUMN \"" + schemaName + "\".\"CUSTOMERS\".\"EMAIL\" IS 'Unique customer email'");
        }

        try {
            // 2. Snapshot via DatabaseService. H2's bulk-metadata snapshot is
            // structural — tables, columns and views — so this is a lighter
            // round-trip than the PG/Oracle tests (which add PK/UC/FK/CHECK/index
            // via dialect-specific catalogs). We don't scope the connection; the
            // loader filters by schema name.
            MetaInfo info = DB_SERVICE.createMetaInfo(connection, new H2MetadataProvider());

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
            assertThat(RoundTripFixture.jdbcType(custEmail)).isEqualTo(Types.VARCHAR);
            Column custId = ColumnSets.findColumn(customers, "ID").orElseThrow();
            assertThat(custId.getIsNullable()).isEqualTo(NullableType.COLUMN_NO_NULLS);
            assertThat(RoundTripFixture.jdbcType(custId)).isEqualTo(Types.INTEGER);
            Column custName = ColumnSets.findColumn(customers, "NAME").orElseThrow();
            assertThat(custName.getIsNullable()).isEqualTo(NullableType.COLUMN_NULLABLE);

            // STATUS column carries a DEFAULT — Column.initialValue is populated.
            Column custStatus = ColumnSets.findColumn(customers, "STATUS").orElseThrow();
            assertThat(custStatus.getInitialValue()).isNotNull();
            assertThat(custStatus.getInitialValue().getBody()).contains("NEW");

            // JDBC REMARKS land as typed CWM Descriptions on the elements.
            assertThat(Descriptions.find(customers, CwmLoader.DESCRIPTION_TYPE_JDBC_REMARKS)).get().extracting(Description::getBody).isEqualTo("Customer master data");
            assertThat(Descriptions.find(custEmail, CwmLoader.DESCRIPTION_TYPE_JDBC_REMARKS)).get().extracting(Description::getBody).isEqualTo("Unique customer email");

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
            RoundTripFixture.executeAll(connection, reDdl);
            MetaInfo reInfo = DB_SERVICE.createMetaInfo(connection, new H2MetadataProvider());
            assertThat(reInfo.structureInfo().tables().stream().map(td -> td.table())
                    .filter(t -> reSchemaName.equals(
                            t.schema().map(org.eclipse.daanse.sql.model.schema.SchemaReference::name).orElse(null)))
                    .map(org.eclipse.daanse.sql.model.schema.TableReference::name)).contains("CUSTOMERS", "ORDERS");
        } finally {
            RoundTripFixture.executeIgnoring(connection,
                    "DROP SCHEMA IF EXISTS \"" + schemaName + "\" CASCADE",
                    "DROP SCHEMA IF EXISTS \"" + schemaName + "_RE\" CASCADE");
        }
    }
}
