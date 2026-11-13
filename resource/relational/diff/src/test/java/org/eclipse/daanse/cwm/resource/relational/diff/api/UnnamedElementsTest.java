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
package org.eclipse.daanse.cwm.resource.relational.diff.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.daanse.cwm.resource.relational.diff.api.TestModels.check;
import static org.eclipse.daanse.cwm.resource.relational.diff.api.TestModels.column;
import static org.eclipse.daanse.cwm.resource.relational.diff.api.TestModels.table;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLIndexes;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.SchemaDifferImpl;
import org.junit.jupiter.api.Test;

/**
 * Keys, constraints and indexes without a name are compared under the name RelationalNames gives
 * them, the name they have in the database, instead of being passed over.
 */
class UnnamedElementsTest {

    private final SchemaDiffer differ = new SchemaDifferImpl();

    @Test
    void anUnnamedUniqueConstraintThatGoesIsDropped() {
        Schema oldSchema = orders(true, false, false);
        Schema newSchema = orders(false, false, false);

        SchemaDiff diff = differ.diff(oldSchema, newSchema);

        assertThat(diff.tablesChanged()).singleElement()
                .satisfies(td -> assertThat(td.uniqueConstraintsDropped()).hasSize(1));
    }

    @Test
    void anUnnamedCheckAndAnUnnamedIndexThatComeAreAdded() {
        Schema oldSchema = orders(false, false, false);
        Schema newSchema = orders(false, true, true);

        SchemaDiff diff = differ.diff(oldSchema, newSchema);

        assertThat(diff.tablesChanged()).singleElement().satisfies(td -> {
            assertThat(td.checksAdded()).hasSize(1);
            assertThat(td.indexesAdded()).hasSize(1);
        });
    }

    @Test
    void theSameUnnamedElementsOnBothSidesAreNoDifference() {
        assertThat(differ.diff(orders(true, true, true), orders(true, true, true)).isEmpty()).isTrue();
    }

    @Test
    void anUnnamedCheckWhoseConditionChangesIsDroppedAndAdded() {
        Schema oldSchema = orders(false, true, false);
        Schema newSchema = orders(false, false, false);
        check(table(newSchema, "orders"), null, "qty > 1");

        SchemaDiff diff = differ.diff(oldSchema, newSchema);

        assertThat(diff.tablesChanged()).singleElement().satisfies(td -> {
            assertThat(td.checksDropped()).hasSize(1);
            assertThat(td.checksAdded()).hasSize(1);
        });
    }

    /** {@code sales.orders(id, code, qty)} with an unnamed unique on code, check on qty and index on qty. */
    private static Schema orders(boolean unique, boolean check, boolean index) {
        Schema sales = TestModels.sales();
        Table orders = table(sales, "orders", "id", "code", "qty");
        if (unique) {
            UniqueConstraint byCode = RelationalFactory.eINSTANCE.createUniqueConstraint();
            byCode.getFeature().add(column(orders, "code"));
            orders.getOwnedElement().add(byCode);
        }
        if (check) {
            check(orders, null, "qty > 0");
        }
        if (index) {
            sales.getOwnedElement().add(SQLIndexes.index(null, false, orders, column(orders, "qty")));
        }
        return sales;
    }
}
