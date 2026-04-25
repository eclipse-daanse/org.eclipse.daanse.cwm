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

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.junit.jupiter.api.Test;

class ClassifiersTest {

    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;

    @Test
    void featureStream_classifierOverload() {
        Table t = RF.createTable();
        Column c = RF.createColumn();
        c.setName("X");
        t.getFeature().add(c);

        assertThat(Classifiers.featureStream(t, Column.class)).containsExactly(c);
    }

    @Test
    void featureStream_listOverload_coversNonClassifierTypes() {
        // PrimaryKey extends UniqueKey (non-Classifier) but exposes getFeature().
        PrimaryKey pk = RF.createPrimaryKey();
        Column id = RF.createColumn();
        pk.getFeature().add(id);

        assertThat(Classifiers.featureStream(pk.getFeature(), Column.class)).containsExactly(id);
    }

    @Test
    void findFeatureByName() {
        Table t = RF.createTable();
        Column a = RF.createColumn();
        a.setName("A");
        Column b = RF.createColumn();
        b.setName("B");
        t.getFeature().add(a);
        t.getFeature().add(b);

        assertThat(Classifiers.findFeatureByName(t, Column.class, "B")).hasValue(b);
        assertThat(Classifiers.findFeatureByName(t, Column.class, "MISSING")).isEmpty();
    }

    @Test
    void nullSafe() {
        assertThat(Classifiers.featureStream((Table) null, Column.class)).isEmpty();
        assertThat(Classifiers.featureStream((java.util.List<Column>) null, Column.class)).isEmpty();
        assertThat(Classifiers.findFeatureByName(null, Column.class, "X")).isEmpty();
        assertThat(Classifiers.findFeatureByName(RF.createTable(), null, "X")).isEmpty();
        assertThat(Classifiers.findFeatureByName(RF.createTable(), Column.class, null)).isEmpty();
    }
}
