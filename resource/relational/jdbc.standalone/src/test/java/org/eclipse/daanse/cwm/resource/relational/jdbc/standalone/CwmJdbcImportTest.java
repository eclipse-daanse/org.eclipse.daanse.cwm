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

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.eclipse.daanse.cwm.emf.standalone.CwmPackages;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ElementRule;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.UnmatchedRulesException;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeStereotypes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ColumnSets;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.resource.relational.load.jdbc.api.JdbcToCwmConfig;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.jdbc.datasource.testkit.postgresql.PostgresDatabaseProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Connect by URL with the driver of a given class loader, load, select by rules. */
class CwmJdbcImportTest {

    private static final String URL = "jdbc:h2:mem:standalone;DB_CLOSE_DELAY=-1";

    @Test
    void connectsByUrlAndLoadsWhatTheRulesSelect() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        try (Connection c = Connections.connect(URL, "sa", "", null, loader); Statement s = c.createStatement()) {
            s.execute("CREATE SCHEMA SHOP");
            s.execute("CREATE TABLE SHOP.CUSTOMER (ID BIGINT PRIMARY KEY, NAME VARCHAR(100) NOT NULL)");
            s.execute("CREATE TABLE SHOP.ORDERS (ID BIGINT PRIMARY KEY, CUSTOMER_ID BIGINT)");
            s.execute("CREATE TABLE SHOP.TMP_IMPORT (LINE VARCHAR(200))");

            Catalog catalog = CwmJdbcImport.load(c, config("schema:SHOP", "!table:TMP_*"));

            assertThat(catalog.getName()).isEqualTo("shop");
            List<Schema> schemas = Catalogs.schemas(catalog);
            assertThat(schemas).extracting(Schema::getName).containsExactly("SHOP");
            List<Table> tables = Schemas.tables(schemas.get(0));
            assertThat(tables).extracting(Table::getName).containsExactlyInAnyOrder("CUSTOMER", "ORDERS");
            Table customer = tables.stream().filter(t -> t.getName().equals("CUSTOMER")).findFirst().orElseThrow();
            assertThat(ColumnSets.columns(customer)).extracting(col -> col.getName()).containsExactly("ID", "NAME");
            Table orders = tables.stream().filter(t -> t.getName().equals("ORDERS")).findFirst().orElseThrow();
            var customerId = ColumnSets.findColumn(customer, "ID").orElseThrow().getType();
            assertThat(customerId.eContainer()).as("the SQL types are owned by the catalog").isSameAs(catalog);
            assertThat(ColumnSets.findColumn(orders, "ID").orElseThrow().getType()).as("one per distinct type")
                    .isSameAs(customerId);
        }
    }

    /**
     * A type is one per name, whatever sizes its columns have; the size is the column's. So a type's
     * id names it, and it stays the same when a column of another size comes along.
     */
    @Test
    void aTypeIsOneForAllItsSizesAndItsIdStays(@TempDir Path dir) throws Exception {
        String url = "jdbc:h2:mem:types;DB_CLOSE_DELAY=-1";
        JdbcToCwmConfig config = config("schema:SHOP");
        try (Connection c = Connections.connect(url, "sa", "", null, getClass().getClassLoader());
                Statement s = c.createStatement()) {
            s.execute("CREATE SCHEMA SHOP");
            s.execute("CREATE TABLE SHOP.PRODUCT (CODE VARCHAR(20), NAME VARCHAR(100), PRICE DECIMAL(10,2))");

            Catalog first = CwmJdbcImport.load(c, config);
            Table product = Schemas.tables(Catalogs.schemas(first).get(0)).get(0);
            var code = ColumnSets.findColumn(product, "CODE").orElseThrow();
            var name = ColumnSets.findColumn(product, "NAME").orElseThrow();
            var price = ColumnSets.findColumn(product, "PRICE").orElseThrow();
            assertThat(code.getType()).isSameAs(name.getType());
            assertThat(List.of(code.getLength(), name.getLength())).containsExactly(20L, 100L);
            assertThat(List.of(price.getPrecision(), price.getScale())).containsExactly(10L, 2L);
            var firstIds = typeIds(dir.resolve("first.xmi"), first);
            assertThat(firstIds).allSatisfy(id -> assertThat(id).doesNotMatch(".*_\\d+"));

            s.execute("CREATE TABLE SHOP.A_LABEL (TEXT VARCHAR(7), WEIGHT DECIMAL(5,0))");
            var secondIds = typeIds(dir.resolve("second.xmi"), CwmJdbcImport.load(c, config));
            assertThat(secondIds).isEqualTo(firstIds);
        }
    }

    /** The ids the catalog's SQL types are written with. */
    private static List<String> typeIds(Path file, Catalog catalog) {
        var resource = (org.eclipse.emf.ecore.xmi.XMIResource) CwmPackages.save(file, List.of(catalog));
        Catalog written = (Catalog) resource.getContents().get(0);
        return written.getOwnedElement().stream()
                .filter(org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType.class::isInstance)
                .map(resource::getID).sorted().toList();
    }

    @Test
    void loadsByUrlOrDataSourceAndNamesTheFileAfterTheCatalog(@TempDir Path dir) throws Exception {
        String url = "jdbc:h2:mem:byurl;DB_CLOSE_DELAY=-1";
        try (Connection c = Connections.connect(url, "sa", "", null, getClass().getClassLoader());
                Statement s = c.createStatement()) {
            s.execute("CREATE SCHEMA STOCK");
            s.execute("CREATE TABLE STOCK.ITEM (ID BIGINT PRIMARY KEY)");
        }

        JdbcToCwmConfig depot = config("schema:STOCK").withCatalogName("depot");
        Catalog catalog = CwmJdbcImport.load(url, "sa", "", null, getClass().getClassLoader(), depot);
        Path file = dir.resolve(CwmJdbcImport.fileName(catalog));
        CwmPackages.save(file, List.of(catalog));

        assertThat(file.getFileName()).hasToString("depot.xmi");
        assertThat(Files.readString(file)).contains("xmi:id=\"Table_depot.STOCK.ITEM\"");
        catalog.setName(" ");
        assertThat(CwmJdbcImport.fileName(catalog)).isEqualTo("catalog.xmi");

        org.h2.jdbcx.JdbcDataSource dataSource = new org.h2.jdbcx.JdbcDataSource();
        dataSource.setURL(url);
        dataSource.setUser("sa");
        dataSource.setPassword("");
        Catalog fromDataSource = CwmJdbcImport.load(dataSource, depot);
        assertThat(Schemas.tables(Catalogs.schemas(fromDataSource).get(0))).extracting(Table::getName)
                .containsExactly("ITEM");
    }

    /**
     * PostgreSQL in a container: its metadata report privileges, H2's do not. A rule on a schema
     * does not reach the parties; one on roles does, and brings their privileges on what is read.
     */
    @Test
    void theRolesAndTheirPrivilegesAreReadAsTheRulesSay() throws Exception {
        ActiveDatabase database = new PostgresDatabaseProvider().activate();
        try (Connection c = database.dataSource().getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE SCHEMA granted");
            s.execute("CREATE TABLE granted.customer (id BIGINT PRIMARY KEY, name VARCHAR(100))");
            s.execute("CREATE TABLE granted.secret (id BIGINT PRIMARY KEY)");
            s.execute("CREATE ROLE granted_reader");
            s.execute("GRANT SELECT ON granted.customer TO granted_reader");
            s.execute("GRANT SELECT ON granted.secret TO granted_reader");

            // the connection's current schema stays public: the privileges of every loaded schema are read
            Catalog schemaOnly = CwmJdbcImport.load(c, config("schema:granted"));
            Catalog withRoles = CwmJdbcImport.load(c, config("schema:granted", "is:role", "!table:secret"));
            Catalog withoutPrivileges = CwmJdbcImport.load(c, config("schema:granted", "is:role", "!is:privilege"));

            assertThat(schemaOnly.getOwnedElement()).noneMatch(ResponsibleParty.class::isInstance);
            ResponsibleParty reader = party(withRoles, "granted_reader");
            assertThat(PrivilegeStereotypes.isDatabaseRole(reader)).isTrue();
            assertThat(reader.getOwnedElement()).as("the privilege on the table left out goes with it")
                    .singleElement().isInstanceOfSatisfying(TablePrivilege.class,
                            p -> assertThat(p.getTable().getName()).isEqualTo("customer"));
            assertThat(withRoles.getOwnedElement()).filteredOn(ResponsibleParty.class::isInstance)
                    .allSatisfy(p -> assertThat(PrivilegeStereotypes.isDatabaseRole((ResponsibleParty) p)).isTrue());
            assertThat(party(withoutPrivileges, "granted_reader").getOwnedElement()).isEmpty();
        }
    }

    @Test
    void anIncludeRuleThatMatchesNothingIsAnError_notAnEmptyCatalog() throws Exception {
        String url = "jdbc:h2:mem:mistyped;DB_CLOSE_DELAY=-1";
        try (Connection c = Connections.connect(url, "sa", "", null, getClass().getClassLoader());
                Statement s = c.createStatement()) {
            s.execute("CREATE SCHEMA SHOP");
            s.execute("CREATE TABLE SHOP.ITEM (ID BIGINT PRIMARY KEY)");

            assertThatThrownBy(() -> CwmJdbcImport.load(c, config("schema:SHOP", "schema:shop", "!table:TMP_*")))
                    .isInstanceOfSatisfying(UnmatchedRulesException.class, e -> assertThat(e.rules())
                            .extracting(ElementRule::describe).containsExactly("schema:shop"))
                    .hasMessage("This rule matches nothing in shop: schema:shop");
        }
    }

    @Test
    void anUnknownDriverClassIsReported() {
        assertThatThrownBy(() -> Connections.connect(URL, null, null, "no.such.Driver", getClass().getClassLoader()))
                .isInstanceOf(SQLException.class).hasMessageContaining("no.such.Driver");
    }

    @Test
    void ruleTextsUseTheWordsOfTheRelationalModelAndOfThePrivileges() {
        assertThat(CwmJdbcImport.selection(List.of("schema:sales", "view:v_*", "!is:system", "!is:privilege",
                "!is:comment")).toString())
                .isEqualTo("Selection[schema:sales, view:v_*, !is:system, !is:privilege, !is:comment]");
        assertThat(CwmJdbcImport.selection(null).includes()).isEmpty();
        assertThatThrownBy(() -> CwmJdbcImport.selection(List.of("is:tabel")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("is:tabel");
    }

    /** The configuration for the catalog {@code shop} that reads what {@code rules} select. */
    private static JdbcToCwmConfig config(String... rules) {
        return JdbcToCwmConfig.defaults().withCatalogName("shop").withSelection(CwmJdbcImport.selection(List.of(rules)));
    }

    private static ResponsibleParty party(Catalog catalog, String name) {
        return catalog.getOwnedElement().stream().filter(ResponsibleParty.class::isInstance)
                .map(ResponsibleParty.class::cast).filter(p -> name.equals(p.getName())).findFirst().orElseThrow();
    }
}
