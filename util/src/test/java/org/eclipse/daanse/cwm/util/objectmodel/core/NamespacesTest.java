/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.util.objectmodel.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.junit.jupiter.api.Test;

class NamespacesTest {

    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;

    @Test
    void walkUpTo_returnsFirstAncestorOfType() {
        Catalog cat = RF.createCatalog();
        Schema sch = RF.createSchema();
        cat.getOwnedElement().add(sch);
        Table t = RF.createTable();
        sch.getOwnedElement().add(t);

        assertThat(Namespaces.walkUpTo(t.getNamespace(), Schema.class)).containsSame(sch);
        assertThat(Namespaces.walkUpTo(t.getNamespace(), Catalog.class)).containsSame(cat);
        assertThat(Namespaces.walkUpTo(null, Schema.class)).isEmpty();
    }

    @Test
    void ownedElementStream_filtersByType() {
        Schema sch = RF.createSchema();
        Table t = RF.createTable();
        t.setName("T");
        View v = RF.createView();
        sch.getOwnedElement().add(t);
        sch.getOwnedElement().add(v);

        assertThat(Namespaces.ownedElementStream(sch, Table.class)).containsExactly(t);
        assertThat(Namespaces.ownedElementStream(sch, View.class)).containsExactly(v);
        assertThat(Namespaces.ownedElementStream(null, Table.class)).isEmpty();
    }

    @Test
    void findOwnedByName_typedFirstMatch() {
        Catalog cat = RF.createCatalog();
        Schema sales = RF.createSchema();
        sales.setName("SALES");
        Schema hr = RF.createSchema();
        hr.setName("HR");
        cat.getOwnedElement().add(sales);
        cat.getOwnedElement().add(hr);

        assertThat(Namespaces.findOwnedByName(cat, Schema.class, "HR")).containsSame(hr);
        assertThat(Namespaces.findOwnedByName(cat, Schema.class, "MISSING")).isEmpty();
        assertThat(Namespaces.findOwnedByName(cat, Table.class, "SALES")).isEmpty();
    }

    @Test
    void nullSafe() {
        assertThat(Namespaces.findOwnedByName(null, Schema.class, "X")).isEmpty();
        assertThat(Namespaces.findOwnedByName(RF.createCatalog(), null, "X")).isEmpty();
        assertThat(Namespaces.findOwnedByName(RF.createCatalog(), Schema.class, null)).isEmpty();
    }
}
