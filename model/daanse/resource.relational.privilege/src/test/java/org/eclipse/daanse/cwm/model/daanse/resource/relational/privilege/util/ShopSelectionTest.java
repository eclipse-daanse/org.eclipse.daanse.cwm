/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Description;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Stereotype;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.ElementRules;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.QualifiedNames;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selected;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selected.LeftOut;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selected.Retained;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selections;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalRules;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.RolePrivilege;
import org.junit.jupiter.api.Test;

/**
 * One schema of a catalog that holds its SQL types and its roles outside every schema, with the rules
 * of the relational, business information and privilege models together.
 */
class ShopSelectionTest {

    private static final List<String> SALES_WITHOUT_CONSTRAINTS = List.of("schema:sales", "!is:constraint",
            "!tag:daanse.ddl.documentary=true");

    @Test
    void oneSchema_withoutConstraints_andWithoutWhatIsDocumentary() {
        ShopWithGrants m = new ShopWithGrants();

        Selected<Catalog> cut = Selections.copy(m.shop,
                Selection.parse(PrivilegeRules.VOCABULARY, SALES_WITHOUT_CONSTRAINTS), PrivilegeRules.CONSEQUENCES);

        Catalog copy = cut.model();
        assertThat(names(copy, Table.class)).containsExactly("customer", "orders", "order_log");
        assertThat(names(copy, View.class)).containsExactly("v_customer_orders");
        assertThat(paths(copy, Column.class)).containsExactly("shop.sales.customer.id", "shop.sales.customer.name",
                "shop.sales.customer.email", "shop.sales.orders.id", "shop.sales.orders.customer_id",
                "shop.sales.orders.product_id", "shop.sales.orders.qty", "shop.sales.order_log.order_id");
        assertThat(names(copy, PrimaryKey.class)).containsExactly("pk_customer", "pk_orders");
        assertThat(names(copy, UniqueConstraint.class)).containsExactly("pk_customer", "pk_orders");
        assertThat(names(copy, ForeignKey.class)).isEmpty();
        assertThat(names(copy, SQLIndex.class)).containsExactly("ix_orders_customer");
        assertThat(names(copy, Trigger.class)).containsExactly("trg_orders");
        assertThat(Selections.list(copy, Selection.all(), Description.class)).hasSize(2);
        assertThat(names(copy, SQLSimpleType.class)).containsExactly("INTEGER", "VARCHAR");
        assertThat(names(copy, ResponsibleParty.class)).isEmpty();
        assertThat(names(copy, Stereotype.class)).isEmpty();

        assertThat(cut.retained()).containsExactly(
                new Retained("shop.INTEGER", "SQLSimpleType", "needed by shop.sales.customer.id.type"),
                new Retained("shop.VARCHAR", "SQLSimpleType", "needed by shop.sales.customer.name.type"));
        assertThat(cut.leftOut()).containsExactly(
                new LeftOut("shop.databaseRole", "Stereotype", Selections.NOT_INCLUDED),
                new LeftOut("shop.databaseUser", "Stereotype", Selections.NOT_INCLUDED),
                new LeftOut("shop.stock", "Schema", Selections.NOT_INCLUDED),
                new LeftOut("shop.sales.customer.uq_customer_email", "UniqueConstraint", "is:constraint"),
                new LeftOut("shop.sales.orders.fk_orders_customer", "ForeignKey", "is:constraint"),
                new LeftOut("shop.sales.orders.fk_orders_product", "ForeignKey", "is:constraint"),
                new LeftOut("shop.sales.orders.ck_orders_qty", "CheckConstraint", "is:constraint"),
                new LeftOut("shop.sales.order_log.pk_order_log", "PrimaryKey", "tag:daanse.ddl.documentary=true"),
                new LeftOut("shop.shop_reader", "ResponsibleParty", Selections.NOT_INCLUDED),
                new LeftOut("shop.shop_writer", "ResponsibleParty", Selections.NOT_INCLUDED),
                new LeftOut("shop.alice", "ResponsibleParty", Selections.NOT_INCLUDED));
    }

    @Test
    void oneSchema_withItsRoles_keepsTheGrantsThatStillHold() {
        ShopWithGrants m = new ShopWithGrants();

        Selected<Catalog> cut = Selections.copy(m.shop, Selection.parse(PrivilegeRules.VOCABULARY, "schema:sales",
                "is:role", "!path:shop.sales.orders.qty"), PrivilegeRules.CONSEQUENCES);

        assertThat(cut.retained()).containsExactly(
                new Retained("shop.INTEGER", "SQLSimpleType", "needed by shop.sales.customer.id.type"),
                new Retained("shop.VARCHAR", "SQLSimpleType", "needed by shop.sales.customer.name.type"),
                new Retained("shop.databaseRole", "Stereotype", "brands shop.shop_reader"));
        assertThat(cut.leftOut()).containsExactly(
                new LeftOut("shop.databaseUser", "Stereotype", Selections.NOT_INCLUDED),
                new LeftOut("shop.stock", "Schema", Selections.NOT_INCLUDED),
                new LeftOut("shop.sales.orders.qty", "Column", "path:shop.sales.orders.qty"),
                new LeftOut("shop.alice", "ResponsibleParty", Selections.NOT_INCLUDED),
                new LeftOut("shop.shop_reader", "TablePrivilege",
                        "table privilege without its table or a column (table: shop.stock.product)"),
                new LeftOut("shop.sales.orders.fk_orders_product", "ForeignKey",
                        "foreign key without its key or a column (uniqueKey: shop.stock.product.pk_product)"),
                new LeftOut("shop.shop_writer", "TablePrivilege",
                        "table privilege without its table or a column (column: shop.sales.orders.qty)"));

        Catalog copy = cut.model();
        ResponsibleParty reader = QualifiedNames.find(copy, "shop.shop_reader", ResponsibleParty.class).orElseThrow();
        ResponsibleParty writer = QualifiedNames.find(copy, "shop.shop_writer", ResponsibleParty.class).orElseThrow();
        assertThat(PrivilegeStereotypes.databaseRoles(copy)).containsExactly(reader, writer);
        assertThat(reader.getOwnedElement()).hasSize(3);
        assertThat(writer.getOwnedElement()).extracting(e -> e.eClass().getName())
                .containsExactly("TablePrivilege", "ProcedurePrivilege", "RolePrivilege");
        assertThat(((RolePrivilege) writer.getOwnedElement().get(2)).getRole()).isSameAs(reader);
    }

    @Test
    void textAndCode_selectTheSame() {
        ShopWithGrants m = new ShopWithGrants();
        Selection text = Selection.parse(PrivilegeRules.VOCABULARY, SALES_WITHOUT_CONSTRAINTS);
        Selection code = Selection.builder()
                .include(RelationalRules.inSchema("sales"))
                .exclude(RelationalRules.CONSTRAINTS)
                .exclude(ElementRules.tagged(RelationalRules.DOCUMENTARY_TAG, "true"))
                .build();

        Selected<Catalog> byText = Selections.copy(m.shop, text, PrivilegeRules.CONSEQUENCES);
        Selected<Catalog> byCode = Selections.copy(m.shop, code, PrivilegeRules.CONSEQUENCES);

        assertThat(text).hasToString(code.toString());
        assertThat(byText.leftOut()).isEqualTo(byCode.leftOut());
        assertThat(byText.retained()).isEqualTo(byCode.retained());
        assertThat(paths(byText.model(), ModelElement.class)).isEqualTo(paths(byCode.model(), ModelElement.class));
        for (ModelElement element : Selections.list(m.shop, Selection.all(), ModelElement.class)) {
            assertThat(text.selects(element)).as(QualifiedNames.of(element)).isEqualTo(code.selects(element));
        }
    }

    private static List<String> names(Catalog catalog, Class<? extends ModelElement> type) {
        return Selections.stream(catalog, Selection.all(), type).map(ModelElement::getName).toList();
    }

    private static List<String> paths(Catalog catalog, Class<? extends ModelElement> type) {
        return Selections.stream(catalog, Selection.all(), type).map(QualifiedNames::of).toList();
    }
}
