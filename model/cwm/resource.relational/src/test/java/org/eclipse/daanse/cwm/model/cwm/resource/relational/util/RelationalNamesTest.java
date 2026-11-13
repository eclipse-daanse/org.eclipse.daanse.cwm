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

import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Test;

class RelationalNamesTest {

    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;

    private final Catalog shop = Catalogs.create("shop");
    private final Schema sales = Schemas.create(shop, "sales");
    private final Table orders = Tables.create(sales, "orders");
    private final Column id = Columns.create(orders, "id", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
    private final Column code = Columns.create(orders, "code", SQLSimpleTypes.ownedIn(shop, "INTEGER"));

    @Test
    void eachKindHasItsPrefixAndTheNameOfItsTable() {
        PrimaryKey key = unnamed(PrimaryKeys.create(orders, "x", id));
        UniqueConstraint unique = unnamed(UniqueConstraints.create(orders, "x", code));
        CheckConstraint check = check(orders);
        ForeignKey fk = unnamed(ForeignKeys.create(orders, "x", key, code));
        SQLIndex index = SQLIndexes.index(null, false, orders, code);
        sales.getOwnedElement().add(index);
        Trigger trigger = RF.createTrigger();
        trigger.setTable(orders);
        sales.getOwnedElement().add(trigger);

        assertThat(RelationalNames.of(key)).isEqualTo("pk_orders");
        assertThat(RelationalNames.of(unique)).isEqualTo("uc_orders");
        assertThat(RelationalNames.of(check)).isEqualTo("ck_orders");
        assertThat(RelationalNames.of(fk)).isEqualTo("fk_orders");
        assertThat(RelationalNames.of(index)).isEqualTo("idx_orders");
        assertThat(RelationalNames.of(trigger)).isEqualTo("trg_orders");
    }

    @Test
    void twoOfAKindAreNumberedAndANameTakenInTheSchemaIsSkipped() {
        UniqueConstraint named = UniqueConstraints.create(orders, "UC_ORDERS", code);
        UniqueConstraint first = unnamed(UniqueConstraints.create(orders, "x", code));
        UniqueConstraint second = unnamed(UniqueConstraints.create(orders, "x", id));
        Tables.create(sales, "uc_orders_2");
        Table other = Tables.create(sales, "other");
        Column otherId = Columns.create(other, "id", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        UniqueConstraint ofOther = unnamed(UniqueConstraints.create(other, "x", otherId));

        Map<ModelElement, String> names = RelationalNames.in(shop);

        assertThat(names.get(named)).isEqualTo("UC_ORDERS");
        assertThat(names.get(first)).as("UC_ORDERS is taken, whatever its case").isEqualTo("uc_orders_3");
        assertThat(names.get(second)).isEqualTo("uc_orders_4");
        assertThat(names.get(ofOther)).isEqualTo("uc_other");
        assertThat(RelationalNames.of(second)).isEqualTo("uc_orders_4");
    }

    @Test
    void eachSchemaIsANamespaceOfItsOwn() {
        Schema stock = Schemas.create(shop, "stock");
        Table stockOrders = Tables.create(stock, "orders");
        Column stockId = Columns.create(stockOrders, "id", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        PrimaryKey inSales = unnamed(PrimaryKeys.create(orders, "x", id));
        PrimaryKey inStock = unnamed(PrimaryKeys.create(stockOrders, "x", stockId));

        assertThat(RelationalNames.of(inSales)).isEqualTo("pk_orders");
        assertThat(RelationalNames.of(inStock)).isEqualTo("pk_orders");
    }

    @Test
    void aCheckOwnedElsewhereBelongsToTheTableOfItsColumn() {
        CheckConstraint check = check(sales);
        check.getConstrainedElement().add(code);

        assertThat(RelationalNames.of(check)).isEqualTo("ck_orders");
    }

    @Test
    void aCopyOfTheModelGivesTheSameNamesAndAssignWritesThemIn() {
        UniqueConstraint first = unnamed(UniqueConstraints.create(orders, "x", code));
        UniqueConstraint second = unnamed(UniqueConstraints.create(orders, "x", id));
        Catalog copy = EcoreUtil.copy(shop);

        RelationalNames.assign(copy);

        Table copied = Schemas.tables(Catalogs.schemas(copy).get(0)).get(0);
        assertThat(Tables.uniqueConstraints(copied)).extracting(ModelElement::getName)
                .containsExactly(RelationalNames.of(first), RelationalNames.of(second))
                .containsExactly("uc_orders", "uc_orders_2");
        assertThat(first.getName()).as("the original keeps no name").isNull();
    }

    @Test
    void anElementOfNoTableKeepsWhatItHas() {
        SQLIndex loose = RF.createSQLIndex();
        sales.getOwnedElement().add(loose);

        assertThat(RelationalNames.of(loose)).isNull();
        assertThat(RelationalNames.of(orders)).isEqualTo("orders");
        assertThat(RelationalNames.of(null)).isNull();
    }

    /** {@code element} without its name: the create helpers want one. */
    private static <T extends ModelElement> T unnamed(T element) {
        element.setName(null);
        return element;
    }

    private static CheckConstraint check(org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace owner) {
        CheckConstraint check = RF.createCheckConstraint();
        owner.getOwnedElement().add(check);
        return check;
    }
}
