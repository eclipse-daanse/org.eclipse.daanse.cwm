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

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.daanse.units.AlternateUnit;
import org.eclipse.daanse.cwm.model.daanse.units.BaseUnit;
import org.eclipse.daanse.cwm.model.daanse.units.Dimension;
import org.eclipse.daanse.cwm.model.daanse.units.Prefix;
import org.eclipse.daanse.cwm.model.daanse.units.QuantityKind;
import org.eclipse.daanse.cwm.model.daanse.units.SystemOfUnits;
import org.eclipse.daanse.cwm.model.daanse.units.Unit;
import org.eclipse.daanse.cwm.model.daanse.units.examples.Elements;
import org.junit.jupiter.api.Test;

/** The SI as built by {@link SI#create}: what it holds, and that it validates. */
class SITest {

    private final Model model = Elements.model("m");
    private final SystemOfUnits si = SI.create(model);

    @Test
    void theCatalogueHoldsTheBaseUnitsKindsAndPrefixes() {
        assertThat(si.getName()).isEqualTo("SI");
        assertThat(si.getAbbreviation()).isEqualTo("SI");
        assertThat(of(BaseUnit.class)).extracting(Unit::getSymbol).containsExactly("m", "kg", "s", "A", "K", "mol", "cd");
        assertThat(of(QuantityKind.class)).hasSize(33);
        assertThat(of(Prefix.class)).hasSize(24).extracting(Prefix::getSymbol).contains("Q", "k", "h", "da", "µ", "q");
        assertThat(of(Dimension.class)).extracting(Dimension::getName).doesNotHaveDuplicates()
                .allMatch(n -> n.matches("A-?\\d+E-?\\d+L-?\\d+I-?\\d+M-?\\d+H-?\\d+T-?\\d+D[01]"));
        assertThat(of(AlternateUnit.class)).extracting(Unit::getSymbol).containsExactly("rad", "sr", "Bq", "Sv");
    }

    @Test
    void theCoherentDerivedUnitsHaveTheirSymbols() {
        assertThat(List.of("rad", "sr", "Hz", "N", "Pa", "J", "W", "C", "V", "F", "Ω", "S", "Wb", "T", "H", "°C",
                "lm", "lx", "Bq", "Gy", "Sv", "kat")).allSatisfy(s -> assertThat(Units.bySymbol(si, s)).as(s).isPresent());
        assertThat(List.of("min", "h", "d", "°", "L", "t", "%", "ha", "g")).allSatisfy(
                s -> assertThat(Units.bySymbol(si, s)).as(s).isPresent());
        assertThat(List.of("m²", "m³", "m/s", "m/s²", "km", "cm", "mm", "km/h", "mm/h", "hPa", "kPa", "kJ",
                "kW", "kWh", "W/m²", "mA", "mV", "ms", "mg", "kg/m²"))
                .allSatisfy(s -> assertThat(Units.bySymbol(si, s)).as(s).isPresent());
    }

    @Test
    void everyUnitHasAUcumCodeAndTheExpectedDimension() {
        assertThat(Units.of(si)).hasSize(59).extracting(Unit::getSymbol).doesNotHaveDuplicates();
        assertThat(Units.of(si)).allSatisfy(u -> assertThat(u.getUcumCode()).as(u.getName()).isNotBlank());
        assertThat(SI.unit(si, SI.DEGREE_CELSIUS).getUcumCode()).isEqualTo("Cel");
        assertThat(Dimensions.code(SI.unit(si, SI.VOLT).getDimension())).isEqualTo("A0E-1L2I0M1H0T-3D0");
        assertThat(Dimensions.code(SI.unit(si, SI.METRE_PER_SECOND).getDimension())).isEqualTo("A0E0L1I0M0H0T-1D0");
        assertThat(SI.unit(si, SI.PASCAL).getQuantityKind()).extracting(QuantityKind::getName).containsExactly("Pressure");
    }

    @Test
    void theCatalogueValidates() {
        assertThat(UnitsModelValidator.check(model)).isEmpty();
    }

    private <T> List<T> of(java.lang.Class<T> type) {
        return si.getOwnedElement().stream().filter(type::isInstance).map(type::cast).toList();
    }
}
