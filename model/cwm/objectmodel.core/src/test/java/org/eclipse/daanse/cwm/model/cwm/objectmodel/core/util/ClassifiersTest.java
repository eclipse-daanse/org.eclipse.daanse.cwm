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

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.junit.jupiter.api.Test;

class ClassifiersTest {

    private static final CoreFactory CF = CoreFactory.eINSTANCE;

    @Test
    void featureStream_classifierOverload() {
        DataType t = CF.createDataType();
        Attribute c = CF.createAttribute();
        c.setName("X");
        t.getFeature().add(c);

        assertThat(Classifiers.featureStream(t, Attribute.class)).containsExactly(c);
    }

    @Test
    void featureStream_listOverload_coversNonClassifierTypes() {
        // Der List-Overload greift dort, wo der Halter der Features kein Classifier ist
        // (z. B. UniqueKey.feature in foundation/keysindexes).
        Attribute id = CF.createAttribute();
        id.setName("ID");

        assertThat(Classifiers.featureStream(java.util.List.of(id), Attribute.class))
                .containsExactly(id);
    }

    @Test
    void findFeatureByName() {
        DataType t = CF.createDataType();
        Attribute a = CF.createAttribute();
        a.setName("A");
        Attribute b = CF.createAttribute();
        b.setName("B");
        t.getFeature().add(a);
        t.getFeature().add(b);

        assertThat(Classifiers.findFeatureByName(t, Attribute.class, "B")).hasValue(b);
        assertThat(Classifiers.findFeatureByName(t, Attribute.class, "MISSING")).isEmpty();
    }

    @Test
    void nullSafe() {
        assertThat(Classifiers.featureStream((DataType) null, Attribute.class)).isEmpty();
        assertThat(Classifiers.featureStream((java.util.List<Attribute>) null, Attribute.class)).isEmpty();
        assertThat(Classifiers.findFeatureByName(null, Attribute.class, "X")).isEmpty();
        assertThat(Classifiers.findFeatureByName(CF.createDataType(), null, "X")).isEmpty();
        assertThat(Classifiers.findFeatureByName(CF.createDataType(), Attribute.class, null)).isEmpty();
    }
}
