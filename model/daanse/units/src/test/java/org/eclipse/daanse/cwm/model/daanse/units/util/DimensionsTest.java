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

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.daanse.units.Dimension;
import org.eclipse.daanse.cwm.model.daanse.units.examples.Elements;
import org.junit.jupiter.api.Test;

/** Dimensions: their exponents, their QUDT code, and finding or creating one in a namespace. */
class DimensionsTest {

    private static final long[] SPEED = { 1, 0, -1, 0, 0, 0, 0 };

    @Test
    void theCodeIsTheQudtDimensionVector() {
        assertThat(Dimensions.code(SPEED)).isEqualTo("A0E0L1I0M0H0T-1D0");
        assertThat(Dimensions.code(Dimensions.none())).isEqualTo("A0E0L0I0M0H0T0D1");
        assertThat(Dimensions.code(new long[] { 2, 1, -3, -1, 0, 0, 0 })).isEqualTo("A0E-1L2I0M1H0T-3D0");
        assertThat(Dimensions.code(Dimensions.base(Dimensions.TEMPERATURE, 1))).isEqualTo("A0E0L0I0M0H1T0D0");
        assertThat(Dimensions.code(Dimensions.base(Dimensions.AMOUNT_OF_SUBSTANCE, 1))).isEqualTo("A1E0L0I0M0H0T0D0");
        assertThat(Dimensions.code(Dimensions.base(Dimensions.LUMINOUS_INTENSITY, 1))).isEqualTo("A0E0L0I1M0H0T0D0");
    }

    @Test
    void ownedInFindsTheDimensionOrCreatesItNamedByItsCode() {
        Model model = Elements.model("m");
        Dimension speed = Dimensions.ownedIn(model, SPEED);
        assertThat(speed.getName()).isEqualTo("A0E0L1I0M0H0T-1D0");
        assertThat(speed.getLength()).isEqualTo(1);
        assertThat(speed.getTime()).isEqualTo(-1);
        assertThat(Dimensions.ownedIn(model, SPEED.clone())).isSameAs(speed);
        assertThat(Dimensions.find(model, Dimensions.none())).isEmpty();
        assertThat(model.getOwnedElement()).containsExactly(speed);
    }

    @Test
    void productsAddExponents() {
        long[] metre = Dimensions.base(Dimensions.LENGTH, 1);
        long[] second = Dimensions.base(Dimensions.TIME, 1);
        assertThat(Dimensions.plus(metre, second, -1)).containsExactly(SPEED);
        Model model = Elements.model("m");
        Dimension a = Dimensions.create(model, "speed", 1, 0, -1, 0, 0, 0, 0);
        Dimension b = Dimensions.ownedIn(Elements.model("other"), SPEED);
        assertThat(Dimensions.same(a, b)).isTrue();
        assertThat(Dimensions.isDimensionless(a)).isFalse();
        assertThat(Dimensions.isDimensionless(Dimensions.ownedIn(model, Dimensions.none()))).isTrue();
        assertThatThrownBy(() -> Dimensions.code(new long[3])).isInstanceOf(IllegalArgumentException.class);
    }
}
