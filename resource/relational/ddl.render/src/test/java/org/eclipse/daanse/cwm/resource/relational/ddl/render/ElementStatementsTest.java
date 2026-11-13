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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.PrimaryKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLIndexes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Views;
import org.junit.jupiter.api.Test;

/** The statements of one element are those made for it, not those that happen to mention its name. */
class ElementStatementsTest {

    private final CwmDdlRenderer renderer = new CwmDdlRenderer(List.of(Dialects.require("postgresql")));

    @Test
    void aViewGivesItsOwnStatementNotThoseOfTheViewsReadingIt() {
        Catalog shop = Catalogs.create("shop");
        Schema sales = Schemas.create(shop, "sales");
        Table orders = Tables.create(sales, "orders");
        Columns.create(orders, "qty", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        View all = Views.create(sales, "v1", "SELECT qty FROM sales.orders");
        Views.create(sales, "v2", "SELECT qty FROM sales.v1 WHERE qty > 1");

        assertThat(renderer.render(all)).singleElement()
                .satisfies(ddl -> assertThat(ddl.statements()).singleElement().asString().contains("\"v1\" AS"));
    }

    @Test
    void anIndexGivesItsOwnStatementNotThatOfAnIndexOnATableOfItsName() {
        Catalog shop = Catalogs.create("shop");
        Schema sales = Schemas.create(shop, "sales");
        Table orders = Tables.create(sales, "orders");
        Column qty = Columns.create(orders, "qty", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        PrimaryKeys.create(orders, "pk_orders", qty);
        // an index named like a table, and an index on that table
        Table ixQty = Tables.create(sales, "ix_qty");
        Column id = Columns.create(ixQty, "id", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        sales.getOwnedElement().add(SQLIndexes.index("ix_qty", false, orders, qty));
        sales.getOwnedElement().add(SQLIndexes.index("ix_id", false, ixQty, id));

        var index = SQLIndexes.spanning(orders).get(0);
        assertThat(renderer.render(index)).singleElement()
                .satisfies(ddl -> assertThat(ddl.statements()).singleElement().asString()
                        .startsWith("CREATE INDEX IF NOT EXISTS \"ix_qty\""));
    }
}
