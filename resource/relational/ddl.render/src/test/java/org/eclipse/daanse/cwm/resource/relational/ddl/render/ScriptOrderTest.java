/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.resource.relational.ddl.render;

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
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Views;
import org.junit.jupiter.api.Test;

/** One script over several schemas runs whatever the order of the schemas in the model. */
class ScriptOrderTest {

    @Test
    void aForeignKeyAndAViewIntoASchemaListedLaterRun() throws SQLException {
        Catalog shop = Catalogs.create("shop");
        Schema sales = Schemas.create(shop, "sales");
        Schema stock = Schemas.create(shop, "stock");
        Table orders = Tables.create(sales, "orders");
        Column productId = Columns.create(orders, "product_id", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        Views.create(sales, "v_orders", "SELECT p.\"id\" FROM \"sales\".\"orders\" o JOIN \"stock\".\"v_product\" p"
                + " ON o.\"product_id\" = p.\"id\"");
        Table product = Tables.create(stock, "product");
        Column id = Columns.create(product, "id", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        PrimaryKey key = PrimaryKeys.create(product, "pk_product", id);
        ForeignKeys.create(orders, "fk_orders_product", key, productId);
        Views.create(stock, "v_product", "SELECT \"id\" FROM \"stock\".\"product\"");

        DdlScript script = new CwmDdlRenderer(List.of(Dialects.require("h2"))).scripts(shop).get(0);
        List<String> create = script.createSql();
        List<String> drop = script.dropSql();

        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:scriptOrder;DB_CLOSE_DELAY=-1");
                Statement s = c.createStatement()) {
            for (String sql : create) {
                s.execute(sql);
            }
            for (String sql : drop) {
                s.execute(sql);
            }
        }
    }
}
