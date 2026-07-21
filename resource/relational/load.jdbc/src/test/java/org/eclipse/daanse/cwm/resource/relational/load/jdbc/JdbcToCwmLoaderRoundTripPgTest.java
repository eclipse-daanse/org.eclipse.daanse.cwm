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
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ConditionTimingType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.DeferrabilityType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.EventManipulationType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ProcedureType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ReferentialRuleType;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.Feature;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.CwmLoader;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.JdbcToCwmConfig;
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
import org.eclipse.daanse.jdbc.datasource.testkit.postgresql.PostgresDatabaseProvider;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.jdbc.api.DatabaseService;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.eclipse.daanse.sql.jdbc.impl.DatabaseServiceImpl;
import org.eclipse.daanse.sql.jdbc.metadata.PostgreSqlMetadataProvider;
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
        Schema fixture = RoundTripFixture.build(schemaName, RoundTripFixture.Options.pg());

        // 1. Emit DDL and execute on Postgres.
        List<String> ddl = new DdlGeneratorFactoryImpl().create(dialect).createSchema(fixture,
                EnumSet.complementOf(EnumSet.of(Feature.TRIGGER)));
        RoundTripFixture.executeAll(connection, ddl);

        // 1a. Out-of-band: install a PL/pgSQL function + trigger so the loader
        // has something to extract from pg_proc.prosrc. The CWM serializer
        // can't yet emit the function-then-trigger pair on its own. Also add
        // table/column comments — the loader turns JDBC REMARKS into CWM
        // business-information Descriptions.
        try (Statement s = connection.createStatement()) {
            s.execute("CREATE OR REPLACE FUNCTION \"" + schemaName + "\".trg_audit_fn() "
                    + "RETURNS trigger LANGUAGE plpgsql AS $$ " + "BEGIN RAISE NOTICE 'audit'; RETURN NEW; END; $$");
            s.execute("CREATE TRIGGER trg_audit BEFORE INSERT ON \"" + schemaName + "\".\"CUSTOMERS\" FOR EACH ROW "
                    + "EXECUTE FUNCTION \"" + schemaName + "\".trg_audit_fn()");
            s.execute("COMMENT ON TABLE \"" + schemaName + "\".\"CUSTOMERS\" IS 'Customer master data'");
            s.execute("COMMENT ON COLUMN \"" + schemaName + "\".\"CUSTOMERS\".\"EMAIL\" IS 'Unique customer email'");
            // Multi-event trigger with a WHEN guard — the loader splits it into
            // one CWM Trigger per event and keeps the guard as actionCondition.
            s.execute("CREATE OR REPLACE FUNCTION \"" + schemaName + "\".trg_status_fn() "
                    + "RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RETURN NEW; END; $$");
            s.execute("CREATE TRIGGER trg_status BEFORE INSERT OR UPDATE ON \"" + schemaName
                    + "\".\"CUSTOMERS\" FOR EACH ROW WHEN (NEW.\"STATUS\" IS NOT NULL) "
                    + "EXECUTE FUNCTION \"" + schemaName + "\".trg_status_fn()");
            // Partial index — pgjdbc exposes the WHERE clause as FILTER_CONDITION.
            s.execute("CREATE INDEX \"IDX_CUSTOMERS_STATUS_ACTIVE\" ON \"" + schemaName
                    + "\".\"CUSTOMERS\" (\"STATUS\") WHERE \"STATUS\" <> 'NEW'");
            // Materialized view — no CWM 1.1 class; loads as read-only View
            // carrying the "materialized" tag.
            s.execute("CREATE MATERIALIZED VIEW \"" + schemaName + "\".\"MV_CUSTOMER_EMAILS\" AS "
                    + "SELECT \"EMAIL\" FROM \"" + schemaName + "\".\"CUSTOMERS\"");
            // Routines — load as CWM Procedure (type function/procedure).
            s.execute("CREATE FUNCTION \"" + schemaName + "\".fn_add(a integer, b integer) "
                    + "RETURNS integer LANGUAGE sql AS 'SELECT a + b'");
            s.execute("CREATE PROCEDURE \"" + schemaName + "\".prc_noop() LANGUAGE sql AS 'SELECT 1'");
        }

        try {
            // 2. Snapshot via DatabaseService — pass the dialect as MetadataProvider so
            // the snapshot includes UNIQUE/CHECK constraints (PG-specific catalogs).
            // PG's bulk-metadata queries scope to connection.getSchema() (defaults
            // to "public") so point it at the test schema first.
            connection.setSchema(schemaName);
            MetaInfo info = DB_SERVICE.createMetaInfo(connection, new PostgreSqlMetadataProvider());

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
            assertThat(RoundTripFixture.jdbcType(custEmail)).isEqualTo(Types.VARCHAR);
            Column custId = ColumnSets.findColumn(customers, "ID").orElseThrow();
            assertThat(custId.getIsNullable()).isEqualTo(NullableType.COLUMN_NO_NULLS);
            assertThat(RoundTripFixture.jdbcType(custId)).isEqualTo(Types.INTEGER);

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

            // JDBC REMARKS land as typed CWM Descriptions on the elements.
            assertThat(Descriptions.find(customers, CwmLoader.DESCRIPTION_TYPE_JDBC_REMARKS)).get().extracting(Description::getBody).isEqualTo("Customer master data");
            assertThat(Descriptions.find(custEmail, CwmLoader.DESCRIPTION_TYPE_JDBC_REMARKS)).get().extracting(Description::getBody).isEqualTo("Unique customer email");

            // Primary keys
            assertThat(PrimaryKeys.columns(Tables.findPrimaryKey(customers).orElseThrow())).extracting(Column::getName)
                    .containsExactly("ID");
            assertThat(PrimaryKeys.columns(Tables.findPrimaryKey(orders).orElseThrow())).extracting(Column::getName)
                    .containsExactly("ID");

            // Unique constraints (excluding PK)
            List<UniqueConstraint> custUcs = Tables.uniqueConstraints(customers).stream()
                    .filter(uc -> !(uc instanceof PrimaryKey)).toList();
            assertThat(custUcs).hasSize(1);
            UniqueConstraint custUc = custUcs.get(0);
            assertThat(custUc.getName()).isEqualTo("UC_CUSTOMERS_EMAIL");
            assertThat(UniqueConstraints.columns(custUc)).extracting(Column::getName).containsExactly("EMAIL");

            // Foreign keys
            List<ForeignKey> ordFks = Tables.foreignKeys(orders);
            assertThat(ordFks).hasSize(1);
            ForeignKey ordFk = ordFks.get(0);
            assertThat(ordFk.getName()).isEqualTo("FK_ORDERS_CUSTOMERS");
            assertThat(ForeignKeys.columns(ordFk)).extracting(Column::getName).containsExactly("CUSTOMER_ID");
            Optional<Table> targetTable = ForeignKeys.targetTable(ordFk);
            assertThat(targetTable).isPresent();
            assertThat(targetTable.get().getName()).isEqualTo("CUSTOMERS");
            assertThat(ordFk.getDeleteRule()).isEqualTo(ReferentialRuleType.IMPORTED_KEY_CASCADE);
            assertThat(ordFk.getDeferrability()).isEqualTo(DeferrabilityType.NOT_DEFERRABLE);

            // Index
            SQLIndex idx = loaded.getOwnedElement().stream().filter(SQLIndex.class::isInstance)
                    .map(SQLIndex.class::cast).filter(i -> "IDX_CUSTOMERS_NAME".equals(i.getName())).findFirst()
                    .orElseThrow();
            assertThat(idx.getSpannedClass()).isEqualTo(customers);
            assertThat(idx.getIndexedFeature()).hasSize(1);
            assertThat(idx.getIndexedFeature().get(0).getFeature().getName()).isEqualTo("NAME");
            assertThat(idx.getIndexedFeature().get(0).isIsAscending())
                    .as("pgjdbc reports btree columns as ascending").isTrue();

            // Partial index round-trips its WHERE clause as filterCondition.
            SQLIndex partialIdx = loaded.getOwnedElement().stream().filter(SQLIndex.class::isInstance)
                    .map(SQLIndex.class::cast).filter(i -> "IDX_CUSTOMERS_STATUS_ACTIVE".equals(i.getName()))
                    .findFirst().orElseThrow();
            assertThat(partialIdx.getFilterCondition()).containsIgnoringCase("STATUS");

            // A NON-unique index on the PK column survives loading — the loader
            // only skips the PK's own unique backing index.
            SQLIndex pkDupIdx = loaded.getOwnedElement().stream().filter(SQLIndex.class::isInstance)
                    .map(SQLIndex.class::cast).filter(i -> "IDX_CUSTOMERS_ID_EXTRA".equals(i.getName())).findFirst()
                    .orElseThrow();
            assertThat(pkDupIdx.isIsUnique()).isFalse();
            assertThat(pkDupIdx.getIndexedFeature()).hasSize(1);
            assertThat(pkDupIdx.getIndexedFeature().get(0).getFeature().getName()).isEqualTo("ID");

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
            assertThat(loadedTrg.getConditionTiming()).isEqualTo(ConditionTimingType.BEFORE);
            assertThat(loadedTrg.getEventManipulation()).isEqualTo(EventManipulationType.INSERT);

            // Multi-event trigger: split into one CWM Trigger per event, WHEN
            // guard preserved as actionCondition.
            assertThat(customers.getTrigger().stream().map(t -> t.getName()).filter(n -> n.startsWith("trg_status")))
                    .containsExactlyInAnyOrder("trg_status_INSERT", "trg_status_UPDATE");
            org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger statusInsert = customers.getTrigger().stream()
                    .filter(t -> "trg_status_INSERT".equals(t.getName())).findFirst().orElseThrow();
            assertThat(statusInsert.getEventManipulation()).isEqualTo(EventManipulationType.INSERT);
            assertThat(statusInsert.getActionCondition()).isNotNull();
            assertThat(statusInsert.getActionCondition().getBody()).containsIgnoringCase("STATUS");

            // Materialized view: View with isReadOnly=true, "materialized" tag
            // and the defining query.
            View mv = Schemas.findView(loaded, "MV_CUSTOMER_EMAILS").orElseThrow();
            assertThat(mv.isIsReadOnly()).isTrue();
            assertThat(mv.getTaggedValue()).anyMatch(tv -> CwmLoader.TAG_MATERIALIZED.equals(tv.getTag()));
            assertThat(mv.getQueryExpression()).isNotNull();
            assertThat(mv.getQueryExpression().getBody()).containsIgnoringCase("EMAIL");

            // Routines: CWM Procedure with type function/procedure and body.
            List<Procedure> routines = loaded.getOwnedElement().stream().filter(Procedure.class::isInstance)
                    .map(Procedure.class::cast).toList();
            assertThat(routines).extracting(Procedure::getName).contains("fn_add", "prc_noop");
            Procedure fnAdd = routines.stream().filter(p -> "fn_add".equals(p.getName())).findFirst().orElseThrow();
            assertThat(fnAdd.getType()).isEqualTo(ProcedureType.FUNCTION);
            assertThat(fnAdd.getBody()).isNotNull();
            assertThat(fnAdd.getBody().getBody()).contains("a + b");
            Procedure prcNoop = routines.stream().filter(p -> "prc_noop".equals(p.getName())).findFirst().orElseThrow();
            assertThat(prcNoop.getType()).isEqualTo(ProcedureType.PROCEDURE);

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
            RoundTripFixture.executeAll(connection, reDdl);
            // Re-snapshot via the daanse API and assert the re-emitted schema
            // exposes the same shape — no raw JDBC metadata reads.
            connection.setSchema(reSchemaName);
            MetaInfo reInfo = DB_SERVICE.createMetaInfo(connection, new PostgreSqlMetadataProvider());
            assertThat(reInfo.structureInfo().tables().stream().map(td -> td.table())
                    .filter(t -> reSchemaName.equals(
                            t.schema().map(org.eclipse.daanse.sql.model.schema.SchemaReference::name).orElse(null)))
                    .map(org.eclipse.daanse.sql.model.schema.TableReference::name)).contains("CUSTOMERS", "ORDERS");
            assertThat(reInfo.structureInfo().checkConstraints().stream()
                    .filter(c -> "CUSTOMERS".equals(c.table().name()) && reSchemaName.equals(c.table().schema()
                            .map(org.eclipse.daanse.sql.model.schema.SchemaReference::name).orElse(null)))
                    .map(org.eclipse.daanse.sql.jdbc.api.schema.CheckConstraint::name))
                    .contains("CK_CUSTOMERS_EMAIL_LEN");
        } finally {
            RoundTripFixture.executeIgnoring(connection,
                    "DROP SCHEMA IF EXISTS \"" + schemaName + "\" CASCADE",
                    "DROP SCHEMA IF EXISTS \"" + schemaName + "_re\" CASCADE");
        }
    }
}
