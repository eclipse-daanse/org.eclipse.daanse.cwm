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
package org.eclipse.daanse.cwm.model.cwm.resource.relational.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ConsequenceRule;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ElementRule;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ElementRules;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.QualifiedNames;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selected;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selected.LeftOut;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selected.Retained;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selections;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.TaggedValues;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalPackage;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RelationalRulesTest {

    @Test
    void kindRules_matchTheirKinds_primaryKeysAreNoConstraint() {
        ShopCatalog m = new ShopCatalog();

        assertThat(RelationalRules.TABLES.test(m.orders)).isTrue();
        assertThat(RelationalRules.TABLES.test(m.view)).isFalse();
        assertThat(RelationalRules.VIEWS.test(m.view)).isTrue();
        assertThat(RelationalRules.PRIMARY_KEYS.test(m.pkOrders)).isTrue();
        assertThat(RelationalRules.UNIQUES.test(m.uqEmail)).isTrue();
        assertThat(RelationalRules.UNIQUES.test(m.pkOrders)).as("a primary key is no unique here").isFalse();
        assertThat(RelationalRules.CHECKS.test(m.checkQty)).isTrue();
        assertThat(RelationalRules.FOREIGN_KEYS.test(m.fkProduct)).isTrue();
        for (ModelElement constraint : List.of(m.uqEmail, m.checkQty, m.fkCustomer, m.fkProduct)) {
            assertThat(RelationalRules.CONSTRAINTS.test(constraint)).as(constraint.getName()).isTrue();
        }
        assertThat(RelationalRules.CONSTRAINTS.test(m.pkOrders)).isFalse();
        assertThat(RelationalRules.INDEXES.test(m.ixOrdersCustomer)).isTrue();
        assertThat(RelationalRules.TRIGGERS.test(m.trigger)).isTrue();
        assertThat(RelationalRules.PROCEDURES.test(m.procedure)).isTrue();
        assertThat(RelationalRules.DOCUMENTARY.test(m.pkOrderLog)).isTrue();
        assertThat(RelationalRules.DOCUMENTARY.test(m.pkOrders)).isFalse();
        assertThat(RelationalRules.CONSTRAINTS.describe()).isEqualTo("is:constraint");
    }

    @Test
    void excludesKind_constraintsButNotPrimaryKeys() {
        Selection noConstraints = Selection.parse(RelationalRules.VOCABULARY, "!is:constraint");

        assertThat(noConstraints.excludesKind(RelationalPackage.Literals.PRIMARY_KEY)).isFalse();
        assertThat(noConstraints.excludesKind(RelationalPackage.Literals.UNIQUE_CONSTRAINT)).isTrue();
        assertThat(noConstraints.excludesKind(RelationalPackage.Literals.FOREIGN_KEY)).isTrue();
        assertThat(noConstraints.excludesKind(RelationalPackage.Literals.CHECK_CONSTRAINT)).isTrue();
        assertThat(noConstraints.excludesKind(RelationalPackage.Literals.TABLE)).isFalse();
        Selection noIndexes = Selection.parse(RelationalRules.VOCABULARY, "!is:index", "!is:table");
        assertThat(noIndexes.excludesKind(RelationalPackage.Literals.SQL_INDEX)).isTrue();
        assertThat(noIndexes.excludesKind(RelationalPackage.Literals.TABLE)).isTrue();
        assertThat(noIndexes.excludesKind(RelationalPackage.Literals.VIEW)).isFalse();
        assertThat(Selection.parse(RelationalRules.VOCABULARY, "!is:documentary")
                .excludesKind(RelationalPackage.Literals.PRIMARY_KEY)).isFalse();
    }

    @Test
    void inSchema_matchesTheSchema_byNameOrWithItsCatalog() {
        ShopCatalog m = new ShopCatalog();

        assertThat(RelationalRules.inSchema("sales").test(m.sales)).isTrue();
        assertThat(RelationalRules.inSchema("sales").test(m.stock)).isFalse();
        assertThat(RelationalRules.inSchema("sales").test(m.orders)).as("its contents follow by owner").isFalse();
        assertThat(RelationalRules.inSchema("shop.sales").test(m.sales)).isTrue();
        assertThat(RelationalRules.inSchema("other.sales").test(m.sales)).isFalse();
        assertThat(RelationalRules.inSchema("s*").test(m.stock)).isTrue();
        assertThat(Selections.list(m.shop, Selection.parse(RelationalRules.VOCABULARY, "schema:sales"),
                ModelElement.class)).contains(m.sales, m.orders, m.qty).doesNotContain(m.product, m.integer);
        assertThatThrownBy(() -> RelationalRules.inSchema("a.b.c")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schema:a.b.c");
    }

    @Test
    void table_matchesTheTable_withItsIndexesAndTriggers() {
        ShopCatalog m = new ShopCatalog();
        ElementRule orders = RelationalRules.table("orders");

        assertThat(orders.describe()).isEqualTo("table:orders");
        assertThat(orders.test(m.orders)).isTrue();
        assertThat(orders.test(m.ixOrdersCustomer)).isTrue();
        assertThat(orders.test(m.trigger)).isTrue();
        assertThat(orders.test(m.ixCustomerEmail)).isFalse();
        assertThat(orders.test(m.customer)).isFalse();
        assertThat(RelationalRules.table("sales.orders").test(m.orders)).isTrue();
        assertThat(RelationalRules.table("stock.orders").test(m.orders)).isFalse();
        assertThat(RelationalRules.table("shop.sales.order*").test(m.orderLog)).isTrue();
        assertThat(RelationalRules.table("*_log").test(m.orderLog)).isTrue();
        assertThat(RelationalRules.table("*_log").test(m.orders)).isFalse();
        assertThatThrownBy(() -> RelationalRules.table("a.b.c.d")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void view_matchesTheView_withItsIndexes() {
        ShopCatalog m = new ShopCatalog();
        SQLIndex ixView = RelationalFactory.eINSTANCE.createSQLIndex();
        ixView.setName("ix_v_customer_orders");
        ixView.setSpannedClass(m.view);
        m.sales.getOwnedElement().add(ixView);
        ElementRule view = RelationalRules.view("v_*");

        assertThat(view.describe()).isEqualTo("view:v_*");
        assertThat(view.test(m.view)).isTrue();
        assertThat(view.test(ixView)).isTrue();
        assertThat(view.test(m.ixOrdersCustomer)).isFalse();
        assertThat(view.test(m.orders)).as("a table is no view").isFalse();
        assertThat(RelationalRules.table("v_*").test(m.view)).as("a view is no table").isFalse();
        assertThat(RelationalRules.view("sales.v_customer_orders").test(m.view)).isTrue();
        assertThat(RelationalRules.view("stock.v_customer_orders").test(m.view)).isFalse();
        assertThat(RelationalRules.view("shop.sales.v_*").test(m.view)).isTrue();
        assertThat(RelationalRules.view("other.sales.v_*").test(m.view)).isFalse();
        assertThatThrownBy(() -> RelationalRules.view("a.b.c.d")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("view:a.b.c.d").hasMessageContaining("[[catalog.]schema.]view");
        assertThat(Selections.list(m.shop, Selection.parse(RelationalRules.VOCABULARY, "view:v_*"),
                ModelElement.class)).contains(m.view, ixView).doesNotContain(m.orders, m.sales);
    }

    @Test
    void systemTemporaryAndMaterialized_matchByWhatTheTableOrViewSays() {
        ShopCatalog m = new ShopCatalog();
        m.orderLog.setIsTemporary(true);
        m.product.setIsSystem(true);
        TaggedValues.set(m.view, RelationalRules.MATERIALIZED_TAG, "true");

        assertThat(RelationalRules.SYSTEM_TABLES.test(m.product)).isTrue();
        assertThat(RelationalRules.SYSTEM_TABLES.test(m.orders)).isFalse();
        assertThat(RelationalRules.TEMPORARY_TABLES.test(m.orderLog)).isTrue();
        assertThat(RelationalRules.TEMPORARY_TABLES.test(m.orders)).isFalse();
        assertThat(RelationalRules.MATERIALIZED.test(m.view)).isTrue();
        assertThat(RelationalRules.MATERIALIZED.test(m.orders)).isFalse();
        assertThat(RelationalRules.VIEWS.test(m.view)).as("a materialized view is a view").isTrue();
        Selection selection = Selection.parse(RelationalRules.VOCABULARY, "!is:system", "!is:temporary");
        assertThat(selection.excludesKind(RelationalPackage.Literals.TABLE)).as("they depend on the table").isFalse();
        assertThat(Selection.parse(RelationalRules.VOCABULARY, "is:system")
                .mayInclude(RelationalPackage.Literals.VIEW)).as("only tables are system tables").isFalse();
    }

    /** Each text reads as written and selects what its rule in code selects. */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            schema:sales                  | schema
            table:sales.order*            | table
            view:sales.v_*                | view
            is:system                     | system
            is:temporary                  | temporary
            is:materialized               | materialized
            tag:materialized=true         | materializedTag
            is:table                      | tables
            is:view                       | views
            is:primaryKey                 | primaryKeys
            is:unique                     | uniques
            is:check                      | checks
            is:foreignKey                 | foreignKeys
            is:constraint                 | constraints
            is:index                      | indexes
            is:trigger                    | triggers
            is:procedure                  | procedures
            is:documentary                | documentary
            tag:daanse.ddl.documentary=true | documentaryTag
            """)
    void textEqualsCode(String text, String which) {
        ShopCatalog m = new ShopCatalog();
        m.product.setIsSystem(true);
        m.orderLog.setIsTemporary(true);
        TaggedValues.set(m.view, RelationalRules.MATERIALIZED_TAG, "true");
        ElementRule code = switch (which) {
        case "schema" -> RelationalRules.inSchema("sales");
        case "table" -> RelationalRules.table("sales.order*");
        case "view" -> RelationalRules.view("sales.v_*");
        case "system" -> RelationalRules.SYSTEM_TABLES;
        case "temporary" -> RelationalRules.TEMPORARY_TABLES;
        case "materialized" -> RelationalRules.MATERIALIZED;
        case "materializedTag" -> ElementRules.tagged(RelationalRules.MATERIALIZED_TAG, "true");
        case "tables" -> RelationalRules.TABLES;
        case "views" -> RelationalRules.VIEWS;
        case "primaryKeys" -> RelationalRules.PRIMARY_KEYS;
        case "uniques" -> RelationalRules.UNIQUES;
        case "checks" -> RelationalRules.CHECKS;
        case "foreignKeys" -> RelationalRules.FOREIGN_KEYS;
        case "constraints" -> RelationalRules.CONSTRAINTS;
        case "indexes" -> RelationalRules.INDEXES;
        case "triggers" -> RelationalRules.TRIGGERS;
        case "procedures" -> RelationalRules.PROCEDURES;
        case "documentary" -> RelationalRules.DOCUMENTARY;
        case "documentaryTag" -> ElementRules.tagged(RelationalRules.DOCUMENTARY_TAG, "true");
        default -> throw new IllegalArgumentException(which);
        };

        ElementRule parsed = RelationalRules.VOCABULARY.parse(text);

        assertThat(parsed.describe()).isEqualTo(text).isEqualTo(code.describe());
        for (ModelElement element : Selections.list(m.shop, Selection.all(), ModelElement.class)) {
            assertThat(parsed.test(element)).as(QualifiedNames.of(element)).isEqualTo(code.test(element));
        }
    }

    @Test
    void copy_ofOneSchema_retainsTheTypes_andDropsTheForeignKeyIntoTheOther() {
        ShopCatalog m = new ShopCatalog();

        Selected<Catalog> cut = Selections.copy(m.shop, Selection.parse(RelationalRules.VOCABULARY, "schema:sales"),
                RelationalRules.CONSEQUENCES);

        assertThat(cut.retained()).containsExactly(
                new Retained("shop.INTEGER", "SQLSimpleType", "needed by shop.sales.customer.id.type"),
                new Retained("shop.VARCHAR", "SQLSimpleType", "needed by shop.sales.customer.name.type"));
        assertThat(cut.leftOut()).containsExactly(
                new LeftOut("shop.stock", "Schema", Selections.NOT_INCLUDED),
                new LeftOut("shop.sales.orders.fk_orders_product", "ForeignKey",
                        "foreign key without its key or a column (uniqueKey: shop.stock.product.pk_product)"));
        assertThat(QualifiedNames.find(cut.model(), "shop.sales.orders.fk_orders_customer")).isPresent();
        Column id = QualifiedNames.find(cut.model(), "shop.sales.orders.id", Column.class).orElseThrow();
        assertThat(id.getType()).isNotSameAs(m.integer);
        assertThat(QualifiedNames.of(id.getType())).isEqualTo("shop.INTEGER");
    }

    @Test
    void copy_keyAndIndexThatLoseAColumn_go() {
        ShopCatalog m = new ShopCatalog();

        Selected<Catalog> cut = Selections.copy(m.shop,
                Selection.parse(RelationalRules.VOCABULARY, "!path:shop.sales.customer.email"),
                RelationalRules.CONSEQUENCES);

        assertThat(cut.leftOut()).containsExactly(
                new LeftOut("shop.sales.customer.email", "Column", "path:shop.sales.customer.email"),
                new LeftOut("shop.sales.customer.uq_customer_email", "UniqueConstraint",
                        "key without a column (feature: shop.sales.customer.email)"),
                new LeftOut("shop.sales.ix_customer_email", "SQLIndexColumn",
                        "index without its table or a column (feature: shop.sales.customer.email)"),
                new LeftOut("shop.sales.ix_customer_email", "SQLIndex",
                        "index without its table or a column (indexedFeature: shop.sales.ix_customer_email)"));
    }

    @Test
    void copy_foreignKeyThatLosesAColumn_goes() {
        ShopCatalog m = new ShopCatalog();

        Selected<Catalog> cut = Selections.copy(m.shop,
                Selection.parse(RelationalRules.VOCABULARY, "!path:shop.sales.orders.customer_id"),
                RelationalRules.CONSEQUENCES);

        assertThat(cut.leftOut()).extracting(LeftOut::path, LeftOut::kind).containsExactly(
                tuple("shop.sales.orders.customer_id", "Column"),
                tuple("shop.sales.orders.fk_orders_customer", "ForeignKey"),
                tuple("shop.sales.ix_orders_customer", "SQLIndexColumn"),
                tuple("shop.sales.ix_orders_customer", "SQLIndex"));
        assertThat(cut.leftOut().get(1).reason())
                .isEqualTo("foreign key without its key or a column (feature: shop.sales.orders.customer_id)");
    }

    @Test
    void copy_primaryKeyThatLosesAColumn_goes_andWithItTheForeignKeyOnIt() {
        ShopCatalog m = new ShopCatalog();

        Selected<Catalog> cut = Selections.copy(m.shop,
                Selection.parse(RelationalRules.VOCABULARY, "!path:shop.sales.customer.id"),
                RelationalRules.CONSEQUENCES);

        assertThat(cut.leftOut()).containsExactly(
                new LeftOut("shop.sales.customer.id", "Column", "path:shop.sales.customer.id"),
                new LeftOut("shop.sales.customer.pk_customer", "PrimaryKey",
                        "key without a column (feature: shop.sales.customer.id)"),
                new LeftOut("shop.sales.orders.fk_orders_customer", "ForeignKey",
                        "foreign key without its key or a column (uniqueKey: shop.sales.customer.pk_customer)"));
    }

    @Test
    void copy_triggerAndIndexWithoutTheirTable_go_theViewStays() {
        ShopCatalog m = new ShopCatalog();

        Selected<Catalog> cut = Selections.copy(m.shop,
                Selection.parse(RelationalRules.VOCABULARY, "!path:shop.sales.orders"),
                RelationalRules.CONSEQUENCES);

        assertThat(cut.leftOut()).containsExactly(
                new LeftOut("shop.sales.orders", "Table", "path:shop.sales.orders"),
                new LeftOut("shop.sales.ix_orders_customer", "SQLIndex",
                        "index without its table or a column (spannedClass: shop.sales.orders)"),
                new LeftOut("shop.sales.trg_orders", "Trigger",
                        "trigger without its table (table: shop.sales.orders)"));
        Selected<Catalog> withoutCustomer = Selections.copy(m.shop,
                Selection.parse(RelationalRules.VOCABULARY, "!path:shop.sales.customer"),
                RelationalRules.CONSEQUENCES);
        assertThat(QualifiedNames.find(withoutCustomer.model(), "shop.sales.v_customer_orders"))
                .as("a view names its tables only in its query text").isPresent();
    }

    @Test
    void consequences_ownRulesFirst_coreLast() {
        assertThat(RelationalRules.CONSEQUENCES).map(ConsequenceRule::describe).containsExactly("SQL data type",
                "foreign key without its key or a column", "key without a column",
                "index without its table or a column", "trigger without its table",
                "dependency on a removed supplier", "required reference");
    }
}
