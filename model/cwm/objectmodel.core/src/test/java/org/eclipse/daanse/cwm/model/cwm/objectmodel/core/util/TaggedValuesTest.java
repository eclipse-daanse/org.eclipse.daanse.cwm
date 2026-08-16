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
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.TaggedValue;
import org.junit.jupiter.api.Test;

class TaggedValuesTest {

    private static final CoreFactory CF = CoreFactory.eINSTANCE;

    @Test
    void set_createsAndUpserts_keepingC34() {
        DataType t = CF.createDataType();

        TaggedValue created = TaggedValues.set(t, "a", "1");
        assertThat(created.getTag()).isEqualTo("a");
        assertThat(created.getValue()).isEqualTo("1");
        assertThat(TaggedValues.all(t)).containsExactly(created);

        TaggedValue updated = TaggedValues.set(t, "a", "2");
        assertThat(updated).isSameAs(created);
        assertThat(TaggedValues.all(t)).hasSize(1);
        assertThat(TaggedValues.value(t, "a")).contains("2");
    }

    @Test
    void set_storesNullValueAsEmptyString() {
        DataType t = CF.createDataType();
        assertThat(TaggedValues.set(t, "a", null).getValue()).isEmpty();
    }

    @Test
    void find_matchesExactTag() {
        DataType t = CF.createDataType();
        TaggedValues.set(t, "a", "1");
        TaggedValues.set(t, "b", "2");

        assertThat(TaggedValues.find(t, "b")).map(TaggedValue::getValue).contains("2");
        assertThat(TaggedValues.find(t, "c")).isEmpty();
        assertThat(TaggedValues.value(t, "c")).isEmpty();
    }

    @Test
    void nullSafe() {
        assertThat(TaggedValues.all(null)).isEmpty();
        assertThat(TaggedValues.find(null, "a")).isEmpty();
        assertThat(TaggedValues.find(CF.createDataType(), null)).isEmpty();
        assertThat(TaggedValues.value(null, "a")).isEmpty();
    }
}
