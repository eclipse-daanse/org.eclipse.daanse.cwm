/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.examples.jdbc.roundtrip;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;

import org.eclipse.daanse.cwm.examples.jdbc.roundtrip.CwmRoundTrip.Result;
import org.eclipse.daanse.cwm.examples.jdbc.roundtrip.CwmRoundTrip.Target;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Namespaces;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selected.LeftOut;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selections;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLIndexes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Views;
import org.eclipse.daanse.cwm.resource.relational.jdbc.standalone.CwmJdbcExport;
import org.eclipse.daanse.cwm.resource.relational.jdbc.standalone.ExportSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The second and third run of {@link CwmRoundTrip#main}, in a DuckDB file of a temporary directory:
 * the part the rules choose goes in and comes back, chosen in Java and chosen by the same rules as
 * text, and the cut says what it left out and why. DuckDB writes no routines: the function of the
 * shop is left out, which the log says, and which the comparison names as the one difference.
 */
class DuckDbRoundTripTest {

    @Test
    void thePartGoesInAndComesBackTheSame_byRulesInJavaAndAsText(@TempDir Path out) throws Exception {
        Target duckdb = new Target("duckdb", "jdbc:duckdb:" + out.resolve("shop.duckdb"), null, null);
        ExportSettings settings = ExportSettings.defaults().withDropFirst(true);
        ByteArrayOutputStream log = new ByteArrayOutputStream();

        Result java = CwmRoundTrip.roundTrip(ShopModel.build(), duckdb, CwmRoundTrip.DUCKDB_PART, settings,
                out.resolve("duckdb-java"), new PrintStream(log, true, UTF_8));
        Result text = CwmRoundTrip.roundTrip(ShopModel.build(), duckdb,
                CwmJdbcExport.selection(CwmRoundTrip.DUCKDB_RULES), settings, out.resolve("duckdb-text"),
                new PrintStream(log, true, UTF_8));

        String function = "function shop.sales.orders_of(INTEGER): only left";
        assertThat(java.diff().lines()).containsExactly(function);
        assertThat(text.diff().lines()).containsExactly(function);
        assertThat(java.exported().dialect()).isEqualTo("duckdb");
        assertThat(text.exported().skipped()).as("the second run drops what the first created").isEmpty();
        assertThat(text.exported().selected().leftOut()).isEqualTo(java.exported().selected().leftOut());
        assertThat(log.toString(UTF_8)).contains("5. 1 difference:", "   " + function)
                .doesNotContain("no differences");
        assertThat(out.resolve("duckdb-java").resolve("shop-duckdb.sql")).isRegularFile();

        assertThat(java.exported().selected().leftOut()).contains(
                new LeftOut("shop.sales.order_log", "Table", "table:*_log"),
                new LeftOut("shop.sales.customer.fk_customer_referred_by", "ForeignKey", "is:documentary"),
                new LeftOut("shop.sales.customer.uq_customer_email", "UniqueConstraint", "is:constraint"),
                new LeftOut("shop.sales.orders.fk_orders_customer", "ForeignKey", "is:constraint"),
                new LeftOut("shop.sales.orders.fk_orders_product", "ForeignKey", "is:constraint"),
                new LeftOut("shop.sales.orders.ck_orders_qty", "CheckConstraint", "is:constraint"),
                new LeftOut("shop.sales.trg_orders_ordered_on", "Trigger", "kind:Trigger"),
                new LeftOut("shop.stock.warehouse", "Table", Selections.NOT_INCLUDED),
                new LeftOut("shop.stock.stock_level", "Table", Selections.NOT_INCLUDED),
                new LeftOut("shop.shop_reader", "ResponsibleParty", Selections.NOT_INCLUDED),
                new LeftOut("shop.shop_clerk", "ResponsibleParty", Selections.NOT_INCLUDED));
        assertThat(java.exported().selected().retained()).extracting(r -> r.path())
                .as("the SQL types of the columns, which the catalog owns").contains("shop.INTEGER", "shop.VARCHAR");

        Catalog read = java.readBack();
        assertThat(Catalogs.schemas(read)).as("not main, which DuckDB keeps for itself")
                .extracting(Schema::getName).containsExactlyInAnyOrder("sales", "stock");
        Schema sales = Catalogs.findSchema(read, "sales").orElseThrow();
        Schema stock = Catalogs.findSchema(read, "stock").orElseThrow();
        assertThat(Schemas.tables(sales)).extracting(Table::getName).containsExactlyInAnyOrder("customer", "orders");
        assertThat(Schemas.tables(stock)).extracting(Table::getName).containsExactly("product");
        assertThat(Schemas.views(sales)).extracting(v -> v.getName())
                .containsExactlyInAnyOrder("v_customer_orders", "v_top_customers");
        assertThat(Views.queryBody(Schemas.findView(sales, "v_top_customers").orElseThrow())).isPresent();
        for (Table table : Schemas.tables(sales)) {
            assertThat(Tables.findPrimaryKey(table)).as("the primary key of " + table.getName()).isPresent();
            assertThat(Tables.foreignKeys(table)).isEmpty();
            assertThat(Namespaces.ownedElements(table, CheckConstraint.class)).isEmpty();
        }
        assertThat(Namespaces.ownedElements(sales, Trigger.class)).isEmpty();
        assertThat(SQLIndexes.indexes(sales)).extracting(index -> index.getName())
                .containsExactly("ix_orders_ordered_on");
    }
}
