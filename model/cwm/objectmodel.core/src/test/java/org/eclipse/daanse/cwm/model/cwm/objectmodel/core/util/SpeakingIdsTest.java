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

import java.util.Locale;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMIResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.junit.jupiter.api.Test;

class SpeakingIdsTest {

    @Test
    void assign_buildsKindAndNamePath_andDeduplicates() {
        Model root = Models.create("Warehouse");
        Model sub = Models.create(root, "Database Ä");
        DataType a = DataTypes.create(sub, "T");
        DataType b = DataTypes.create(sub, "T");
        TaggedValues.set(a, "kind", "x");
        XMIResource resource = new XMIResourceImpl(URI.createURI("test:/ids.xmi"));
        resource.getContents().add(root);

        SpeakingIds.assign(resource);

        assertThat(resource.getID(root)).isEqualTo("Model_Warehouse");
        assertThat(resource.getID(sub)).isEqualTo("Model_Warehouse.Database_Ae");
        assertThat(resource.getID(a)).isEqualTo("DataType_Warehouse.Database_Ae.T");
        assertThat(resource.getID(b)).isEqualTo("DataType_Warehouse.Database_Ae.T_2");
        assertThat(resource.getID(a.getTaggedValue().get(0))).isEqualTo("TaggedValue_Warehouse.Database_Ae.T.kind");
    }

    /**
     * An unnamed root is named by its kind in lower case, alike in every default locale: in a
     * Turkish one, {@code Index} lower-cased by the rules of the language is {@code ındex}, with a
     * dotless i, which the id would turn into {@code _ndex}.
     */
    @Test
    void anUnnamedRootIsNamedByItsKind_alikeInATurkishLocale() {
        EObject index = unnamed("Index");
        XMIResource resource = new XMIResourceImpl(URI.createURI("test:/ids.xmi"));
        resource.getContents().add(index);
        Locale before = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            SpeakingIds.assign(resource);
        } finally {
            Locale.setDefault(before);
        }

        assertThat(resource.getID(index)).isEqualTo("Index_index");
    }

    /** An object of a class {@code name} without a name feature, in a package of its own. */
    static EObject unnamed(String name) {
        EPackage pkg = EcoreFactory.eINSTANCE.createEPackage();
        pkg.setName("kinds");
        pkg.setNsURI("test:/kinds");
        pkg.setNsPrefix("kinds");
        EClass kind = EcoreFactory.eINSTANCE.createEClass();
        kind.setName(name);
        pkg.getEClassifiers().add(kind);
        return EcoreUtil.create(kind);
    }
}
