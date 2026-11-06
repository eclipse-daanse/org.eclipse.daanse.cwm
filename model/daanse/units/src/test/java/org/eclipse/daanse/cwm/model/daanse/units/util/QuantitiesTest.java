/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.units.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.eclipse.daanse.cwm.model.daanse.units.Quantity;
import org.eclipse.daanse.cwm.model.daanse.units.Scale;
import org.eclipse.daanse.cwm.model.daanse.units.Unit;
import org.junit.jupiter.api.Test;

/** Which unit an element has: its own quantity's, else its data type's. */
class QuantitiesTest {

    private final Lab lab = new Lab();

    @Test
    void anElementHasTheUnitOfItsQuantity() {
        Quantity q = Quantities.create(lab.quantities, "reading", lab.unit(SI.HECTOPASCAL), lab.reading);
        assertThat(q.getNamespace()).isSameAs(lab.quantities);
        assertThat(q.getScale()).isEqualTo(Scale.ABSOLUTE);
        assertThat(Quantities.own(lab.reading)).containsExactly(q);
        assertThat(Quantities.unitOf(lab.reading)).get().extracting(Unit::getSymbol).isEqualTo("hPa");
        assertThat(Quantities.unitOf(lab.label)).isEmpty();
    }

    @Test
    void anAttributeOfADataTypeWithAQuantityHasItsUnit() {
        Quantities.create(lab.quantities, "celsius", lab.unit(SI.DEGREE_CELSIUS), lab.celsius);
        assertThat(Quantities.unitOf(lab.inside)).get().extracting(Unit::getSymbol).isEqualTo("°C");
        assertThat(Quantities.unitOf(lab.outside)).get().extracting(Unit::getSymbol).isEqualTo("°C");
        assertThat(Quantities.typeOf(lab.inside)).contains(lab.celsius);
    }

    @Test
    void anOwnQuantityComesBeforeTheOneOfTheType() {
        Quantities.create(lab.quantities, "celsius", lab.unit(SI.DEGREE_CELSIUS), lab.celsius);
        Quantities.relative(Quantities.create(lab.quantities, "outside", lab.unit(SI.KELVIN), lab.outside));
        assertThat(Quantities.unitOf(lab.outside)).get().extracting(Unit::getSymbol).isEqualTo("K");
        assertThat(Quantities.of(lab.outside)).get().extracting(Quantity::getScale).isEqualTo(Scale.RELATIVE);
        assertThat(Quantities.unitOf(lab.inside)).get().extracting(Unit::getSymbol).isEqualTo("°C");
    }

    @Test
    void oneQuantityMayNameSeveralElements() {
        Quantity q = Quantities.kind(Quantities.create(lab.quantities, "temperatures", lab.unit(SI.KELVIN), lab.inside,
                lab.outside), SI.kind(lab.si, "Temperature"));
        assertThat(Quantities.of(lab.inside)).contains(q);
        assertThat(Quantities.of(lab.outside)).contains(q);
    }

    @Test
    void aQuantityNamesAtLeastOneElement() {
        assertThatThrownBy(() -> Quantities.create(lab.quantities, "nothing", lab.unit(SI.METRE)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Quantities.create(lab.quantities, "x", null, lab.reading))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("unit");
    }
}
