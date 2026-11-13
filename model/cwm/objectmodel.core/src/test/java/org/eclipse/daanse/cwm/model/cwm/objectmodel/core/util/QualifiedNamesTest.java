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
package org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.junit.jupiter.api.Test;

class QualifiedNamesTest {

    @Test
    void of_joinsTheNamesOfTheOwnerChain() {
        Shop model = new Shop();

        assertThat(QualifiedNames.of(model.shop)).isEqualTo("shop");
        assertThat(QualifiedNames.of(model.orders)).isEqualTo("shop.sales.orders");
        // an attribute is held by Classifier.feature, not by ownedElement
        assertThat(QualifiedNames.of(model.id)).isEqualTo("shop.sales.orders.id");
        assertThat(QualifiedNames.of(null)).isEmpty();
    }

    @Test
    void of_skipsElementsWithoutName() {
        Shop model = new Shop();
        Package unnamed = Shop.CF.createPackage();
        model.sales.getOwnedElement().add(unnamed);
        Class inside = Shop.type(unnamed, "inside");

        assertThat(QualifiedNames.of(inside)).isEqualTo("shop.sales.inside");
        assertThat(QualifiedNames.find(model.shop, "shop.sales.inside")).containsSame(inside);
    }

    @Test
    void find_isTheInverseOfOf_forEveryElement() {
        Shop model = new Shop();

        for (ModelElement element : Selections.list(model.shop, Selection.all(), ModelElement.class)) {
            assertThat(QualifiedNames.find(model.shop, QualifiedNames.of(element))).as(QualifiedNames.of(element))
                    .containsSame(element);
        }
    }

    @Test
    void find_readsNamesRelativeToTheRootToo() {
        Shop model = new Shop();

        assertThat(QualifiedNames.find(model.shop, "sales.orders")).containsSame(model.orders);
        assertThat(QualifiedNames.find(model.sales, "shop.sales.orders.id")).containsSame(model.id);
        assertThat(QualifiedNames.find(model.sales, "orders.id")).containsSame(model.id);
        assertThat(QualifiedNames.find(model.sales, "shop.stock.product")).isEmpty();
        assertThat(QualifiedNames.find(model.shop, "shop.sales.nothing")).isEmpty();
    }

    @Test
    void find_byType_skipsElementsOfAnotherKind() {
        Shop model = new Shop();
        Package orders = Packages.create(model.sales, "orders");

        assertThat(QualifiedNames.find(model.shop, "shop.sales.orders")).containsSame(model.orders);
        assertThat(QualifiedNames.find(model.shop, "shop.sales.orders", Package.class)).containsSame(orders);
        assertThat(QualifiedNames.find(model.shop, "shop.sales.orders.id", Attribute.class)).containsSame(model.id);
    }

    @Test
    void find_handlesNamesWithDots() {
        Shop model = new Shop();
        Class dotted = Shop.type(model.stock, "v1.2");

        assertThat(QualifiedNames.of(dotted)).isEqualTo("shop.stock.v1.2");
        assertThat(QualifiedNames.find(model.shop, "shop.stock.v1.2")).containsSame(dotted);
    }

    @Test
    void nullSafe() {
        Shop model = new Shop();

        assertThat(QualifiedNames.find(null, "shop")).isEmpty();
        assertThat(QualifiedNames.find(model.shop, null)).isEmpty();
        assertThat(QualifiedNames.find(model.shop, "shop", null)).isEmpty();
    }
}
