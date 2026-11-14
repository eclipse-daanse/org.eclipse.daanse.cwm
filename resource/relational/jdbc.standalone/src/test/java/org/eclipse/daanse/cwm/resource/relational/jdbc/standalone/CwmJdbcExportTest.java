/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.resource.relational.jdbc.standalone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selected.LeftOut;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalNames;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlStatement;
import org.eclipse.daanse.cwm.resource.relational.ddl.render.CwmDdlScripts.Kind;
import org.eclipse.daanse.cwm.resource.relational.diff.api.CatalogDiffer;
import org.eclipse.daanse.cwm.resource.relational.diff.api.DiffSettings;
import org.eclipse.daanse.cwm.resource.relational.diff.api.Tolerance;
import org.eclipse.daanse.cwm.resource.relational.jdbc.standalone.CwmJdbcExport.Exported;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.JdbcToCwmConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A model written into H2 and DuckDB, in the process, and read back is the model that was written;
 * an export runs again and again with {@code dropFirst}, honours its selection and names the
 * statement a database rejects.
 */
class CwmJdbcExportTest {

    /** Everything but the roles: H2 keeps them, but its metadata do not report them. */
    private static final List<String> WITHOUT_ROLES = List.of("!kind:ResponsibleParty");

    @Test
    void aModelGoesIntoH2AndComesBackTheSame_againAndAgain() throws Exception {
        try (Connection c = Connections.connect("jdbc:h2:mem:export_roundtrip;DB_CLOSE_DELAY=-1", "sa", "")) {
            Catalog model = ShopModels.shop("", false);
            ExportSettings settings = ExportSettings.defaults().withDropFirst(true);

            Exported first = CwmJdbcExport.write(c, model, CwmJdbcExport.selection(WITHOUT_ROLES), settings);
            Exported again = CwmJdbcExport.write(c, model, CwmJdbcExport.selection(WITHOUT_ROLES), settings);

            assertThat(first.dialect()).as("the dialect of the database").isEqualTo("h2");
            assertThat(first.skipped()).as("an empty database holds nothing to drop").isNotEmpty()
                    .allMatch(s -> s.sql().startsWith("DROP") || s.sql().startsWith("ALTER TABLE"));
            assertThat(sql(first.executed())).noneMatch(s -> s.startsWith("DROP"));
            assertThat(again.skipped()).as("the second time everything is there to drop").isEmpty();
            assertThat(sql(again.executed())).anyMatch(s -> s.startsWith("DROP TABLE"))
                    .anyMatch(s -> s.startsWith("CREATE TABLE"));
            assertThat(c.getAutoCommit()).as("the auto-commit state is restored").isTrue();
            assertThat(c.isClosed()).as("the caller's connection stays open").isFalse();
            assertThat(model.eResource()).isNull();
            assertThat(Catalogs.schemas(model)).as("the model is not touched").hasSize(2);

            Catalog readBack = CwmJdbcImport.load(c, again.readBack());
            assertThat(CatalogDiffer.create().diff(again.selected().model(), readBack,
                    DiffSettings.defaults().withTolerances(Tolerance.roundTrip("h2"))).lines()).isEmpty();
        }
    }

    /**
     * The model gained a check without a name, a unique constraint, an index and a foreign key since
     * it was last exported. With {@code dropFirst} it goes in again: the
     * tables are there, but not what the model gained, so the statements that would drop it are
     * skipped, under the names they give it, and what comes back is the model; once more, and the
     * database holds everything to drop.
     */
    @Test
    void whatTheModelGainedSinceTheLastExportIsNotDropped() throws Exception {
        try (Connection c = Connections.connect("jdbc:h2:mem:export_gained;DB_CLOSE_DELAY=-1", "sa", "")) {
            Selection rules = CwmJdbcExport.selection(WITHOUT_ROLES);
            ExportSettings settings = ExportSettings.defaults().withDropFirst(true);
            Catalog model = ShopModels.shop("", false);
            CwmJdbcExport.write(c, model, rules, settings);

            List<String> gained = ShopModels.gain(model, "", false);
            Exported again = CwmJdbcExport.write(c, model, rules, settings);

            assertThat(again.skipped()).extracting(s -> RelationalNames.of(s.element()))
                    .containsExactlyInAnyOrderElementsOf(gained);
            assertThat(sql(again.skipped())).allMatch(s -> s.startsWith("ALTER TABLE") || s.startsWith("DROP INDEX"));
            assertThat(sql(again.executed())).anyMatch(s -> s.startsWith("DROP TABLE"));
            assertThat(CatalogDiffer.create().diff(again.selected().model(), CwmJdbcImport.load(c, again.readBack()),
                    DiffSettings.defaults().withTolerances(Tolerance.roundTrip("h2"))).lines()).isEmpty();
            assertThat(CwmJdbcExport.write(c, model, rules, settings).skipped()).as("now they are there").isEmpty();
        }
    }

    /**
     * DuckDB refuses a foreign key into another schema, so it gets one schema of the shop: the
     * selection is honoured, and the result says what it left out and why.
     */
    @Test
    void aSelectionIsHonouredAndReported_DuckDb(@TempDir Path dir) throws Exception {
        try (Connection c = Connections.connect("jdbc:duckdb:" + dir.resolve("shop.duckdb"), null, null)) {
            Catalog model = ShopModels.shop("", false);
            ExportSettings settings = ExportSettings.defaults().withDropFirst(true);

            CwmJdbcExport.write(c, model, CwmJdbcExport.selection(List.of("schema:sales")), settings);
            Exported again = CwmJdbcExport.write(c, model, CwmJdbcExport.selection(List.of("schema:sales")),
                    settings);

            assertThat(again.dialect()).isEqualTo("duckdb");
            assertThat(CwmJdbcExport.ddlInTransactions(c.getMetaData())).as("DuckDB runs DDL in transactions")
                    .isTrue();
            assertThat(again.selected().leftOut()).extracting(LeftOut::path).contains("shop.stock",
                    "shop.sales.orders.fk_orders_product", "shop.reader", "shop.writer");
            assertThat(Catalogs.schemas(again.selected().model())).extracting(Schema::getName)
                    .containsExactly("sales");
            assertThat(schemas(c)).contains("sales").doesNotContain("stock");
            assertThat(again.skipped()).isEmpty();

            Catalog readBack = CwmJdbcImport.load(c, again.readBack());
            assertThat(CatalogDiffer.create().diff(again.selected().model(), readBack,
                    DiffSettings.defaults().withTolerances(Tolerance.roundTrip("duckdb"))).lines()).isEmpty();
        }
    }

    /**
     * H2 commits every DDL statement, as its metadata say: the statements before the one it rejects
     * remain, and the failure says so, naming the statement, its place and its element.
     */
    @Test
    void aRejectedStatementIsNamed_andOnH2WhatRanBeforeRemains() throws Exception {
        try (Connection c = Connections.connect("jdbc:h2:mem:export_failure;DB_CLOSE_DELAY=-1", "sa", "")) {
            Catalog model = ShopModels.shop("broken_", false);
            CheckConstraint check = Tables.checkConstraints(Schemas
                    .findTable(Catalogs.findSchema(model, "broken_sales").orElseThrow(), "orders").orElseThrow())
                    .get(0);
            check.getBody().setBody("\"qty\" >>> 0");

            assertThat(CwmJdbcExport.ddlInTransactions(c.getMetaData())).as("H2 commits DDL at once").isFalse();
            assertThatThrownBy(() -> CwmJdbcExport.write(c, model, CwmJdbcExport.selection(WITHOUT_ROLES),
                    ExportSettings.defaults()))
                    .isInstanceOfSatisfying(StatementFailedException.class, e -> {
                        assertThat(e.script()).isEqualTo(Kind.CREATE);
                        assertThat(e.statement().element()).as("the check of the cut copy")
                                .isInstanceOf(CheckConstraint.class).isNotSameAs(check);
                        assertThat(e.element()).isEqualTo("CheckConstraint shop.broken_sales.orders.ck_orders_qty");
                        assertThat(e.statement().sql()).contains("ck_orders_qty").contains(">>>");
                        assertThat(e.executed()).hasSize(e.position() - 1);
                        assertThat(e.rolledBack()).isFalse();
                        assertThat(e.getCause()).isInstanceOf(SQLException.class);
                        assertThat(e.getSQLState()).isEqualTo(((SQLException) e.getCause()).getSQLState());
                        assertThat(e.getMessage()).startsWith("statement " + e.position()
                                + " of the CREATE script, for CheckConstraint shop.broken_sales.orders.ck_orders_qty"
                                + ", failed: ALTER TABLE").contains("not rolled back, the " + e.executed().size()
                                + " statements before it remain");
                    });
            assertThat(tables(c, "broken_sales")).as("the tables created before remain")
                    .containsExactlyInAnyOrder("customer", "orders");
            assertThat(c.getAutoCommit()).isTrue();
        }
    }

    /**
     * A database holds more than the model written into it, here the schemas H2 keeps for itself.
     * Read back by the rules alone, which have no include rule, they come too and differ from the
     * model; read back as the export says, only the written schemas come, as written, and a table
     * that is there beyond the written ones shows.
     */
    @Test
    void whatWasWrittenIsReadBack_notTheDatabasesOwnSchemas() throws Exception {
        try (Connection c = Connections.connect("jdbc:h2:mem:export_readback;DB_CLOSE_DELAY=-1", "sa", "")) {
            Selection rules = CwmJdbcExport.selection(WITHOUT_ROLES);
            Exported done = CwmJdbcExport.write(c, ShopModels.shop("", false), rules, ExportSettings.defaults());
            Catalog written = done.selected().model();
            DiffSettings tolerances = DiffSettings.defaults().withTolerances(Tolerance.roundTrip(done.dialect()));

            Catalog byTheRules = CwmJdbcImport.load(c,
                    JdbcToCwmConfig.defaults().withCatalogName("shop").withSelection(rules));
            assertThat(CatalogDiffer.create().diff(written, byTheRules, tolerances).lines())
                    .containsExactly("schema shop.INFORMATION_SCHEMA: only right", "schema shop.PUBLIC: only right");

            JdbcToCwmConfig readBack = done.readBack();
            assertThat(readBack.catalogName()).isEqualTo("shop");
            assertThat(readBack.selection().excludes()).hasSize(2);
            Catalog read = CwmJdbcImport.load(c, readBack);
            assertThat(Catalogs.schemas(read)).extracting(Schema::getName).containsExactly("sales", "stock");
            assertThat(CatalogDiffer.create().diff(written, read, tolerances).lines()).isEmpty();

            try (Statement s = c.createStatement()) {
                s.execute("CREATE TABLE \"sales\".\"extra\" (\"id\" INTEGER)");
            }
            assertThat(CatalogDiffer.create().diff(written, CwmJdbcImport.load(c, readBack), tolerances).lines())
                    .containsExactly("table shop.sales.extra: only right");
        }
    }

    @Test
    void theDialectIsTheOneNamed_orTheDatabases() throws Exception {
        assertThat(ExportSettings.defaults().dialect()).isNull();
        assertThat(ExportSettings.defaults().withDialect(" PG ").dialect()).isEqualTo("postgresql");
        assertThatThrownBy(() -> ExportSettings.defaults().withDialect("db2"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("db2")
                .hasMessageContaining("postgresql, mysql");
        try (Connection c = Connections.connect("jdbc:h2:mem:export_dialect;DB_CLOSE_DELAY=-1", "sa", "")) {
            Exported asAnsi = CwmJdbcExport.write(c, ShopModels.shop("", false),
                    CwmJdbcExport.selection(List.of("schema:stock")),
                    ExportSettings.defaults().withDialect("ansi"));
            assertThat(asAnsi.dialect()).isEqualTo("ansi");
            assertThat(tables(c, "stock")).containsExactly("product");
        }
    }

    static List<String> sql(List<DdlStatement> statements) {
        return DdlStatement.sql(statements);
    }

    private static List<String> schemas(Connection c) throws SQLException {
        List<String> names = new ArrayList<>();
        try (ResultSet rs = c.getMetaData().getSchemas()) {
            while (rs.next()) {
                names.add(rs.getString("TABLE_SCHEM"));
            }
        }
        return names;
    }

    static List<String> tables(Connection c, String schema) throws SQLException {
        List<String> names = new ArrayList<>();
        try (ResultSet rs = c.getMetaData().getTables(null, schema, "%", new String[] { "TABLE" })) {
            while (rs.next()) {
                names.add(rs.getString("TABLE_NAME"));
            }
        }
        return names;
    }
}
