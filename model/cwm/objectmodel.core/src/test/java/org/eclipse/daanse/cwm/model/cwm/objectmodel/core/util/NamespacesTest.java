/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Subsystem;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Stereotype;
import org.junit.jupiter.api.Test;

class NamespacesTest {

    private static final CoreFactory CF = CoreFactory.eINSTANCE;

    @Test
    void walkUpTo_returnsFirstAncestorOfType() {
        Model cat = CF.createModel();
        Subsystem sch = CF.createSubsystem();
        cat.getOwnedElement().add(sch);
        DataType t = CF.createDataType();
        sch.getOwnedElement().add(t);

        assertThat(Namespaces.walkUpTo(t.getNamespace(), Subsystem.class)).containsSame(sch);
        assertThat(Namespaces.walkUpTo(t.getNamespace(), Model.class)).containsSame(cat);
        assertThat(Namespaces.walkUpTo(null, Subsystem.class)).isEmpty();
    }

    @Test
    void ownedElementStream_filtersByType() {
        Subsystem sch = CF.createSubsystem();
        DataType t = CF.createDataType();
        t.setName("T");
        Stereotype v = CF.createStereotype();
        sch.getOwnedElement().add(t);
        sch.getOwnedElement().add(v);

        assertThat(Namespaces.ownedElementStream(sch, DataType.class)).containsExactly(t);
        assertThat(Namespaces.ownedElementStream(sch, Stereotype.class)).containsExactly(v);
        assertThat(Namespaces.ownedElementStream(null, DataType.class)).isEmpty();
    }

    @Test
    void findOwnedByName_typedFirstMatch() {
        Model cat = CF.createModel();
        Subsystem sales = CF.createSubsystem();
        sales.setName("SALES");
        Subsystem hr = CF.createSubsystem();
        hr.setName("HR");
        cat.getOwnedElement().add(sales);
        cat.getOwnedElement().add(hr);

        assertThat(Namespaces.findOwnedByName(cat, Subsystem.class, "HR")).containsSame(hr);
        assertThat(Namespaces.findOwnedByName(cat, Subsystem.class, "MISSING")).isEmpty();
        assertThat(Namespaces.findOwnedByName(cat, DataType.class, "SALES")).isEmpty();
    }

    @Test
    void nullSafe() {
        assertThat(Namespaces.findOwnedByName(null, Subsystem.class, "X")).isEmpty();
        assertThat(Namespaces.findOwnedByName(CF.createModel(), null, "X")).isEmpty();
        assertThat(Namespaces.findOwnedByName(CF.createModel(), Subsystem.class, null)).isEmpty();
    }
}
