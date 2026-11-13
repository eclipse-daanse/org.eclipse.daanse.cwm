/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.resource.relational.ddl.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.PrimaryKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.junit.jupiter.api.Test;

class DialectsTest {

    @Test
    void duckdb_isOfflineRenderable() {
        List<Dialects.NamedDialect> dialects = Dialects.of(List.of("duckdb"));
        assertThat(dialects).extracting(Dialects.NamedDialect::name).containsExactly("DuckDB");

        Catalog shop = Catalogs.create("SHOP");
        Schema sales = Schemas.create(shop, "SALES");
        Table customer = Tables.create(sales, "CUSTOMER");
        var id = Columns.create(customer, "ID", SQLSimpleTypes.ownedIn(shop, "BIGINT"));
        Columns.create(customer, "NAME", SQLSimpleTypes.ownedIn(shop, "VARCHAR"), NullableType.COLUMN_NULLABLE, 100,
                0, 0);
        PrimaryKeys.create(customer, "PK_CUSTOMER", id);

        List<String> ddl = new DdlGeneratorFactoryImpl().create(dialects.get(0).dialect(), DdlSettings.defaults())
                .createSchema(sales);

        assertThat(String.join("\n", ddl)).contains("CREATE TABLE").contains("CUSTOMER").contains("NAME")
                .contains("100").containsIgnoringCase("PRIMARY KEY");
    }

    @Test
    void require_resolvesIdsAndAliasesAndNamesTheKnownOnesForAnUnknownId() {
        assertThat(Dialects.IDS).allSatisfy(id -> assertThat(Dialects.require(id)).isNotNull());
        assertThat(Dialects.require("PG").name()).isEqualTo("PostgreSQL");
        assertThat(Dialects.id(" PG ")).isEqualTo("postgresql");
        assertThat(Dialects.id("MSSQL")).isEqualTo("sqlserver");
        assertThat(Dialects.id("duckdb")).isEqualTo("duckdb");
        assertThatThrownBy(() -> Dialects.require("db2")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("db2").hasMessageContaining("postgresql, mysql");
    }

    @Test
    void forProduct_readsTheProductNameAndTheUrlAsFallback() {
        assertThat(Dialects.forProduct("PostgreSQL", null)).contains("postgresql");
        assertThat(Dialects.forProduct("MySQL", "jdbc:mysql://db/shop")).contains("mysql");
        assertThat(Dialects.forProduct("MariaDB", "jdbc:mariadb://db/shop")).contains("mariadb");
        assertThat(Dialects.forProduct("Oracle", null)).contains("oracle");
        assertThat(Dialects.forProduct("Microsoft SQL Server", null)).contains("sqlserver");
        assertThat(Dialects.forProduct("H2", null)).contains("h2");
        assertThat(Dialects.forProduct("DuckDB", null)).contains("duckdb");
        assertThat(Dialects.forProduct(null, "jdbc:postgresql://db/shop")).contains("postgresql");
        assertThat(Dialects.forProduct("Some Server", "JDBC:MSSQL://db")).as("an alias in the URL")
                .contains("sqlserver");
        assertThat(Dialects.forProduct("DB2/LINUXX8664", "jdbc:db2://db:50000/shop")).isEmpty();
        assertThat(Dialects.forProduct(null, "jdbc:ansi:x")).as("no database speaks plain ANSI").isEmpty();
        assertThat(Dialects.forProduct(null, null)).isEmpty();
    }

    @Test
    void forConnection_asksTheMetadata_andNamesTheKnownIdsForAnUnknownDatabase() throws Exception {
        try (Connection h2 = DriverManager.getConnection("jdbc:h2:mem:dialects")) {
            assertThat(Dialects.forConnection(h2)).isEqualTo("h2");
        }
        Connection db2 = connection("DB2/LINUXX8664", "jdbc:db2://db:50000/shop");
        assertThatThrownBy(() -> Dialects.forConnection(db2)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("no dialect for the database DB2/LINUXX8664 (jdbc:db2://db:50000/shop); known: "
                        + String.join(", ", Dialects.IDS));
    }

    /** A connection whose metadata report {@code product} at {@code url}, and nothing else. */
    private static Connection connection(String product, String url) {
        DatabaseMetaData metaData = (DatabaseMetaData) Proxy.newProxyInstance(DialectsTest.class.getClassLoader(),
                new Class<?>[] { DatabaseMetaData.class }, (proxy, method, args) -> switch (method.getName()) {
                case "getDatabaseProductName" -> product;
                case "getURL" -> url;
                default -> throw new UnsupportedOperationException(method.getName());
                });
        return (Connection) Proxy.newProxyInstance(DialectsTest.class.getClassLoader(),
                new Class<?>[] { Connection.class }, (proxy, method, args) -> {
                    if (method.getName().equals("getMetaData")) {
                        return metaData;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void createAndDropScript_onePerDialectWithTheSettings() {
        Catalog shop = Catalogs.create("SHOP");
        Schema sales = Schemas.create(shop, "SALES");
        Table customer = Tables.create(sales, "CUSTOMER");
        var id = Columns.create(customer, "ID", SQLSimpleTypes.ownedIn(shop, "BIGINT"));
        PrimaryKeys.create(customer, "PK_CUSTOMER", id);
        CwmDdlRenderer renderer = new CwmDdlRenderer(List.of(Dialects.require("postgresql"), Dialects.require("h2")),
                DdlSettings.defaults().withIfNotExists(false).withIncludeSchema(false));

        List<DdlScript> scripts = renderer.scripts(shop);

        assertThat(scripts).extracting(DdlScript::dialect).containsExactly("PostgreSQL", "H2");
        assertThat(String.join("\n", scripts.get(0).createSql())).contains("CREATE TABLE").contains("CUSTOMER")
                .doesNotContain("IF NOT EXISTS").doesNotContain("SALES").doesNotContain("CREATE SCHEMA");
        assertThat(String.join("\n", scripts.get(1).dropSql())).contains("DROP TABLE").contains("CUSTOMER")
                .doesNotContain("IF EXISTS");
    }
}
