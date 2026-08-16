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

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Stereotype;
import org.junit.jupiter.api.Test;

class PackagesTest {

    private static final CoreFactory CF = CoreFactory.eINSTANCE;

    private static DataType type(String name) {
        DataType t = CF.createDataType();
        t.setName(name);
        return t;
    }

    @Test
    void available_isOwnedPlusImported_ownedFirst() {
        Model owner = CF.createModel();
        DataType own = type("OWN");
        owner.getOwnedElement().add(own);

        Model foreign = CF.createModel();
        DataType imported = type("IMP");
        foreign.getOwnedElement().add(imported);
        owner.getImportedElement().add(imported);

        assertThat(Packages.available(owner, DataType.class)).containsExactly(own, imported);
    }

    @Test
    void available_filtersByType_andDeduplicates() {
        Model owner = CF.createModel();
        DataType own = type("T");
        Stereotype other = CF.createStereotype();
        owner.getOwnedElement().add(own);
        owner.getOwnedElement().add(other);
        // owned and imported at once: appears exactly once
        owner.getImportedElement().add(own);

        assertThat(Packages.available(owner, DataType.class)).containsExactly(own);
        assertThat(Packages.available(owner, Stereotype.class)).containsExactly(other);
    }

    @Test
    void findAvailableByName_ownedWinsOverImport() {
        Model owner = CF.createModel();
        DataType own = type("T");
        owner.getOwnedElement().add(own);

        Model foreign = CF.createModel();
        DataType imported = type("T");
        foreign.getOwnedElement().add(imported);
        owner.getImportedElement().add(imported);

        assertThat(Packages.findAvailableByName(owner, DataType.class, "T")).containsSame(own);
        assertThat(Packages.findAvailableByName(owner, DataType.class, "X")).isEmpty();
    }

    @Test
    void nullSafe() {
        assertThat(Packages.available(null, DataType.class)).isEmpty();
        assertThat(Packages.available(CF.createModel(), null)).isEmpty();
        assertThat(Packages.findAvailableByName(CF.createModel(), DataType.class, null)).isEmpty();
    }
}
