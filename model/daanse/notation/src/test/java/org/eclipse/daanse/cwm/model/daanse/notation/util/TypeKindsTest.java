/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.notation.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.TaggedValues;
import org.eclipse.daanse.cwm.model.daanse.notation.util.TypeKinds.Info;
import org.eclipse.daanse.cwm.model.daanse.notation.util.TypeKinds.Kind;
import org.junit.jupiter.api.Test;

/** What a type holds: by its name, its JDBC code, its Java class. */
class TypeKindsTest {

    private final Stations stations = new Stations();

    @Test
    void sqlTypesAreStoredTypes() {
        Info varchar = TypeKinds.of(Stations.sqlType(stations.thuringia, "VARCHAR(40)", 12));
        assertThat(varchar.kind()).as("text by its name, the length dropped").isEqualTo(Kind.TEXT);
        assertThat(varchar.storage()).as("a stored type").isTrue();
        assertThat(TypeKinds.of(Stations.sqlType(stations.thuringia, "INT8", -5)).kind()).as("a number")
                .isEqualTo(Kind.NUMBER);
        Info tz = TypeKinds.of(stations.timestampTz);
        assertThat(tz.kind()).as("a time").isEqualTo(Kind.TEMPORAL);
        assertThat(tz.instant()).as("with a zone, an instant").isTrue();
        Info byCode = TypeKinds.of(Stations.sqlType(stations.thuringia, "MYSTERY", 93));
        assertThat(byCode.kind()).as("by its JDBC code").isEqualTo(Kind.TEMPORAL);
        assertThat(byCode.localTime()).as("TIMESTAMP is local").isTrue();
        assertThat(TypeKinds.of(Stations.sqlType(stations.thuringia, "GEOMETRY", 1111)).kind()).as("unknown")
                .isEqualTo(Kind.UNKNOWN);
    }

    @Test
    void typesOfValues() {
        assertThat(TypeKinds.of(stations.decimal).storage()).as("a data type of a value").isFalse();
        assertThat(TypeKinds.of(stations.instant).instant()).as("an instant by its name").isTrue();
        assertThat(TypeKinds.of(stations.localDateTime).localTime()).as("a local date and time").isTrue();
        DataType eString = Stations.owned(stations.design, "EString", CoreFactory.eINSTANCE.createDataType());
        assertThat(TypeKinds.of(eString).kind()).as("an Ecore name").isEqualTo(Kind.TEXT);
        DataType tagged = Stations.owned(stations.design, "Moment", CoreFactory.eINSTANCE.createDataType());
        TaggedValues.set(tagged, "daanse.ecore.instanceClassName", "java.time.OffsetDateTime");
        assertThat(TypeKinds.of(tagged).instant()).as("by its Java class").isTrue();
        DataType flag = Stations.owned(stations.design, "Flag", CoreFactory.eINSTANCE.createDataType());
        TaggedValues.set(flag, "daanse.ecore.instanceClassName", "java.lang.Boolean");
        assertThat(TypeKinds.of(flag).kind()).as("a Java boolean").isEqualTo(Kind.BOOLEAN);
        assertThat(TypeKinds.plain("timestamp(3)  with time zone")).as("precision and spaces")
                .isEqualTo("timestamp with time zone");
    }
}
