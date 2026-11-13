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
package org.eclipse.daanse.cwm.resource.relational.ddl.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ForeignKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.PrimaryKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlStatement;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.db.duckdb.DuckDbDialect;
import org.eclipse.daanse.sql.dialect.db.h2.H2Dialect;
import org.eclipse.daanse.sql.dialect.db.postgresql.PostgreSqlDialect;
import org.junit.jupiter.api.Test;

/**
 * Foreign keys between schemas: the order of one script over several schemas, and the name of the
 * referenced table.
 */
class CrossSchemaTest {

    /** {@code sales.orders} refers to {@code stock.product}; the model lists {@code sales} first. */
    private record Shop(Schema sales, Schema stock, Table orders, Table product) {
    }

    private static Shop shop() {
        Catalog shop = Catalogs.create("shop");
        Schema sales = Schemas.create(shop, "sales");
        Schema stock = Schemas.create(shop, "stock");
        Table product = Tables.create(stock, "product");
        Column productId = Columns.create(product, "id", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        PrimaryKey productKey = PrimaryKeys.create(product, "pk_product", productId);
        Table orders = Tables.create(sales, "orders");
        Column orderId = Columns.create(orders, "id", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        Column productRef = Columns.create(orders, "product_id", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        PrimaryKeys.create(orders, "pk_orders", orderId);
        ForeignKeys.create(orders, "fk_orders_product", productKey, productRef);
        return new Shop(sales, stock, orders, product);
    }

    @Test
    void aForeignKeyIntoAnotherSchemaNamesThatSchemaAlsoWhenTablesAreWrittenBare() {
        Shop shop = shop();

        List<String> ddl = create(new PostgreSqlDialect(), DdlSettings.defaults().withIncludeSchema(false),
                List.of(shop.sales()));

        assertThat(ddl).filteredOn(s -> s.contains("FOREIGN KEY")).singleElement().satisfies(
                fk -> assertThat(fk).contains("REFERENCES \"stock\".\"product\"").contains("TABLE \"orders\""));
    }

    @Test
    void bareNamesWriteOneSchemaOnly() {
        Shop shop = shop();

        assertThatThrownBy(() -> create(new PostgreSqlDialect(), DdlSettings.defaults().withIncludeSchema(false),
                List.of(shop.sales(), shop.stock()))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shop.sales, shop.stock");
    }

    @Test
    void oneScriptOverSeveralSchemasCreatesAllTablesBeforeAnyForeignKey() throws SQLException {
        Shop shop = shop();
        List<String> ddl = create(new H2Dialect(), DdlSettings.defaults(), List.of(shop.sales(), shop.stock()));

        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:crossSchema;DB_CLOSE_DELAY=-1");
                Statement s = c.createStatement()) {
            for (String sql : ddl) {
                s.execute(sql);
            }
            for (String sql : drop(new H2Dialect(), DdlSettings.defaults(), List.of(shop.sales(), shop.stock()))) {
                s.execute(sql);
            }
        }
    }

    @Test
    void foreignKeysDeclaredInCreateTableAreOrderedAcrossSchemas() {
        // DuckDB declares foreign keys in CREATE TABLE; it refuses one across schemas when it runs
        // the script, but the order is the generator's
        Shop shop = shop();

        List<DdlStatement> create = new DdlGeneratorImpl(new DuckDbDialect()).create(List.of(shop.sales(),
                shop.stock()));
        List<DdlStatement> drop = new DdlGeneratorImpl(new DuckDbDialect()).drop(List.of(shop.sales(),
                shop.stock()));

        assertThat(create).extracting(DdlStatement::element).containsSubsequence(shop.sales(), shop.stock(),
                shop.product(), shop.orders());
        assertThat(drop).extracting(DdlStatement::element).containsSubsequence(shop.orders(), shop.product(),
                shop.stock(), shop.sales());
        assertThat(create).filteredOn(s -> s.element() == shop.orders()).singleElement()
                .satisfies(s -> assertThat(s.sql()).contains("REFERENCES \"stock\".\"product\""));
    }

    @Test
    void aCycleOfForeignKeysThatCannotBeAddedLaterIsReportedWithItsTables() {
        Shop shop = shop();
        // product refers back to orders: neither can be created first where the keys go into CREATE TABLE
        Column lastOrder = Columns.create(shop.product(), "last_order_id",
                SQLSimpleTypes.ownedIn(Schemas.findCatalog(shop.stock()).orElseThrow(), "INTEGER"));
        ForeignKeys.create(shop.product(), "fk_product_last_order", Tables.findPrimaryKey(shop.orders()).orElseThrow(),
                lastOrder);

        assertThatThrownBy(() -> create(new DuckDbDialect(), DdlSettings.defaults(),
                List.of(shop.sales(), shop.stock()))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sales.orders").hasMessageContaining("stock.product");
    }

    /** The one script over {@code schemas}. */
    private static List<String> create(Dialect dialect, DdlSettings settings, List<Schema> schemas) {
        return DdlStatement.sql(new DdlGeneratorImpl(dialect, settings).create(schemas));
    }

    private static List<String> drop(Dialect dialect, DdlSettings settings, List<Schema> schemas) {
        return DdlStatement.sql(new DdlGeneratorImpl(dialect, settings).drop(schemas));
    }
}
