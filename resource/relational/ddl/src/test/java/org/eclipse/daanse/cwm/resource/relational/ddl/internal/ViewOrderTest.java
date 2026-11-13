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

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Dependencies;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Views;
import org.eclipse.daanse.sql.dialect.db.h2.H2Dialect;
import org.junit.jupiter.api.Test;

/** A view that reads another view is created after it and dropped before it, whatever the model order. */
class ViewOrderTest {

    private final DdlGeneratorImpl generator = new DdlGeneratorImpl(new H2Dialect());

    @Test
    void aViewReadingAViewNamedInItsQueryComesAfterIt() throws SQLException {
        Catalog shop = Catalogs.create("shop");
        Schema sales = Schemas.create(shop, "sales");
        Table orders = Tables.create(sales, "orders");
        Columns.create(orders, "qty", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        Views.create(sales, "v_big", "SELECT \"qty\" FROM \"sales\".\"v_all\" WHERE \"qty\" > 10");
        Views.create(sales, "v_all", "SELECT \"qty\" FROM \"sales\".\"orders\"");

        runs(sales, "viewsByText");
    }

    @Test
    void dependenciesOrderTheViewsWhereTheModelHasThem() throws SQLException {
        Catalog shop = Catalogs.create("shop");
        Schema sales = Schemas.create(shop, "sales");
        Table orders = Tables.create(sales, "orders");
        Columns.create(orders, "qty", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        // each query names the other view, once as a column: the texts alone read as a cycle
        View reading = Views.create(sales, "v_b", "SELECT \"v_b\" FROM \"sales\".\"v_a\"");
        View read = Views.create(sales, "v_a", "SELECT \"qty\" AS \"v_b\" FROM \"sales\".\"orders\"");
        Dependencies.create(sales, "v_b reads v_a", "reads", reading, read);

        runs(sales, "viewsByDependency");
    }

    @Test
    void viewsWhoseQueriesNameEachOtherAreACycleThatIsNamed() {
        Catalog shop = Catalogs.create("shop");
        Schema sales = Schemas.create(shop, "sales");
        Table orders = Tables.create(sales, "orders");
        Columns.create(orders, "qty", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        Views.create(sales, "v_b", "SELECT \"v_b\" FROM \"sales\".\"v_a\"");
        Views.create(sales, "v_a", "SELECT \"qty\" AS \"v_b\" FROM \"sales\".\"orders\"");

        assertThatThrownBy(() -> generator.create(List.of(sales))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shop.sales.v_b, shop.sales.v_a").hasMessageContaining("core::Dependency");
    }

    private void runs(Schema sales, String database) throws SQLException {
        List<String> create = generator.createSchema(sales);
        List<String> drop = generator.dropSchema(sales);
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:" + database + ";DB_CLOSE_DELAY=-1");
                Statement s = c.createStatement()) {
            for (String sql : create) {
                s.execute(sql);
            }
            for (String sql : drop) {
                s.execute(sql);
            }
        }
        assertThat(create).isNotEmpty();
    }
}
