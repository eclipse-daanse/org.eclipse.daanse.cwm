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

import java.sql.Types;
import java.util.Locale;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.SchemaDifferImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The comparison does not depend on the default locale of the JVM: in Turkish, {@code "i"}
 * upper-cases to a dotted {@code "İ"}, so {@code integer} and {@code INTEGER} would differ.
 */
class DefaultLocaleDiffTest {

    private Locale before;

    @BeforeEach
    void turkish() {
        before = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
    }

    @AfterEach
    void restore() {
        Locale.setDefault(before);
    }

    @Test
    void typeNamesAndConditionsInAnotherCaseAreTheSameInTurkishToo() {
        SchemaDiff diff = new SchemaDifferImpl().diff(orders("integer", "qty in (1, 2)"),
                orders("INTEGER", "QTY IN (1, 2)"));

        assertThat(diff.isEmpty()).as("%s", diff).isTrue();
    }

    private static Schema orders(String typeName, String condition) {
        Schema sales = TestModels.sales();
        Table orders = Tables.create(sales, "orders");
        Columns.create(orders, "qty", SQLSimpleTypes.create(sales.getNamespace(), typeName, Types.INTEGER),
                NullableType.COLUMN_NO_NULLS);
        check(orders, "ck_orders_qty", condition);
        return sales;
    }
}
