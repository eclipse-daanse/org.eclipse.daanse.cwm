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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.QualifiedNames;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selected;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selection;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Selections;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeRules;
import org.eclipse.daanse.cwm.resource.relational.jdbc.standalone.CwmJdbcExport;
import org.junit.jupiter.api.Test;

/**
 * The rules of DuckDB written in Java and written as text say the same: on the shop model, the
 * copies they cut hold the same elements and leave out and retain the same, for the same reasons.
 */
class SelectionAsTextTest {

    @Test
    void theTextSelectsWhatTheJavaRulesSelect() {
        Selection java = CwmRoundTrip.DUCKDB_PART;
        Selection text = CwmJdbcExport.selection(CwmRoundTrip.DUCKDB_RULES);

        Selected<Catalog> byJava = Selections.copy(ShopModel.build(), java, PrivilegeRules.CONSEQUENCES);
        Selected<Catalog> byText = Selections.copy(ShopModel.build(), text, PrivilegeRules.CONSEQUENCES);

        assertThat(text).hasToString(java.toString());
        assertThat(elements(byText.model())).isEqualTo(elements(byJava.model()))
                .contains("Table shop.sales.customer", "Table shop.stock.product", "View shop.sales.v_top_customers",
                        "PrimaryKey shop.sales.orders.pk_orders", "SQLIndex shop.sales.ix_orders_ordered_on")
                .doesNotContain("Table shop.sales.order_log", "Table shop.stock.warehouse",
                        "ResponsibleParty shop.shop_reader");
        assertThat(byText.leftOut()).isNotEmpty().isEqualTo(byJava.leftOut());
        assertThat(byText.retained()).isNotEmpty().isEqualTo(byJava.retained());
    }

    /** Every model element of {@code model}, as its kind and qualified name, in containment order. */
    private static List<String> elements(Catalog model) {
        List<String> elements = new ArrayList<>();
        model.eAllContents().forEachRemaining(o -> {
            if (o instanceof ModelElement element) {
                elements.add(element.eClass().getName() + " " + QualifiedNames.of(element));
            }
        });
        return elements;
    }
}
