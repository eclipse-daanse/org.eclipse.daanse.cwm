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
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.daanse.units.DerivedUnit;
import org.eclipse.daanse.cwm.model.daanse.units.PrefixedUnit;
import org.eclipse.daanse.cwm.model.daanse.units.Scale;
import org.eclipse.daanse.cwm.model.daanse.units.SystemOfUnits;
import org.eclipse.daanse.cwm.model.daanse.units.Unit;
import org.eclipse.daanse.cwm.model.daanse.units.examples.Elements;
import org.eclipse.daanse.cwm.model.daanse.units.examples.ExampleUnits;
import org.junit.jupiter.api.Test;

/** Units: what the helpers create, the dimension of a definition, and conversion between units. */
class UnitsTest {

    private final Model model = Elements.model("m");
    private final SystemOfUnits si = SI.create(model);

    @Test
    void everyHelperSetsTheDimensionOfTheDefinition() {
        Unit metre = SI.unit(si, SI.METRE);
        Unit second = SI.unit(si, SI.SECOND);
        DerivedUnit speed = Units.derived(si, "speedInMetres", "m·s⁻¹", Units.factor(metre, 1), Units.factor(second, -1));
        assertThat(speed.getDimension()).isSameAs(SI.unit(si, SI.METRE_PER_SECOND).getDimension());
        assertThat(speed.getFactor()).extracting(f -> f.getUnit().getSymbol(), f -> f.getExponent())
                .containsExactly(tuple("m", 1L), tuple("s", -1L));
        PrefixedUnit km = (PrefixedUnit) SI.unit(si, SI.KILOMETRE);
        assertThat(km.getSymbol()).isEqualTo("km");
        assertThat(km.getDimension()).isSameAs(metre.getDimension());
        assertThat(Units.definedDimension(SI.unit(si, SI.VOLT)))
                .containsExactly(Dimensions.exponents(SI.unit(si, SI.VOLT).getDimension()));
        assertThat(Units.one(Elements.model("x")).getSymbol()).isEqualTo("1");
    }

    @Test
    void theSystemUnitIsReachedThroughScalesPrefixesAndLogarithms() {
        assertThat(Units.systemUnit(SI.unit(si, SI.KILOMETRE)).getName()).isEqualTo(SI.METRE);
        assertThat(Units.systemUnit(SI.unit(si, SI.DEGREE_CELSIUS)).getName()).isEqualTo(SI.KELVIN);
        assertThat(Units.systemUnit(SI.unit(si, SI.BECQUEREL)).getName()).isEqualTo(SI.BECQUEREL);
        assertThat(Units.systemUnit(SI.unit(si, SI.KILOMETRE_PER_HOUR)).getName()).isEqualTo(SI.KILOMETRE_PER_HOUR);
        SystemOfUnits example = ExampleUnits.create(model);
        assertThat(Units.systemUnit(ExampleUnits.unit(example, "dBm")).getSymbol()).isEqualTo("W");
    }

    @Test
    void valuesConvertBetweenUnitsOfOneDimension() {
        assertThat(convert("25", SI.DEGREE_CELSIUS, SI.KELVIN)).isEqualByComparingTo("298.15");
        assertThat(convert("298.15", SI.KELVIN, SI.DEGREE_CELSIUS)).isEqualByComparingTo("25");
        assertThat(convert("1013.25", SI.HECTOPASCAL, SI.PASCAL)).isEqualByComparingTo("101325");
        assertThat(convert("36", SI.KILOMETRE_PER_HOUR, SI.METRE_PER_SECOND)).isCloseTo(new BigDecimal("10"),
                within(new BigDecimal("1e-30")));
        assertThat(convert("90", SI.MINUTE, SI.HOUR)).isEqualByComparingTo("1.5");
        assertThat(convert("1", SI.KILOWATT_HOUR, SI.JOULE)).isEqualByComparingTo("3600000");
        assertThat(convert("12.5", SI.PERCENT, SI.ONE)).isEqualByComparingTo("0.125");
        assertThat(convert("1", SI.LITRE, SI.CUBIC_METRE)).isEqualByComparingTo("0.001");
        assertThat(convert("2.5", SI.MILLIGRAM, SI.GRAM)).isEqualByComparingTo("0.0025");
        assertThat(convert("180", SI.DEGREE, SI.RADIAN)).isCloseTo(new BigDecimal(Math.PI), within(new BigDecimal("1e-12")));
    }

    @Test
    void fahrenheitUsesTheMultiplierAndOffsetOfQudt() {
        SystemOfUnits example = ExampleUnits.create(model);
        Unit fahrenheit = ExampleUnits.unit(example, "F");
        Unit celsius = ExampleUnits.unit(example, "°C");
        assertThat(Units.convert(new BigDecimal("212"), fahrenheit, celsius)).isCloseTo(new BigDecimal("100"),
                within(new BigDecimal("1e-12")));
        assertThat(Units.convert(new BigDecimal("32"), fahrenheit, celsius)).isCloseTo(BigDecimal.ZERO,
                within(new BigDecimal("1e-12")));
    }

    @Test
    void aDifferenceConvertsWithoutTheOffset() {
        assertThat(Units.convert(BigDecimal.ONE, SI.unit(si, SI.DEGREE_CELSIUS), SI.unit(si, SI.KELVIN), Scale.RELATIVE))
                .isEqualByComparingTo("1");
        assertThat(Units.convert(BigDecimal.ONE, SI.unit(si, SI.DEGREE_CELSIUS), SI.unit(si, SI.KELVIN), Scale.ABSOLUTE))
                .isEqualByComparingTo("274.15");
    }

    @Test
    void aLogarithmicUnitConvertsThroughItsReference() {
        SystemOfUnits example = ExampleUnits.create(model);
        Unit dbm = ExampleUnits.unit(example, "dBm");
        Unit watt = ExampleUnits.unit(example, "W");
        Unit milliwatt = ExampleUnits.unit(example, "mW");
        assertThat(Units.convert(BigDecimal.ZERO, dbm, milliwatt)).isCloseTo(BigDecimal.ONE, within(new BigDecimal("1e-12")));
        assertThat(Units.convert(new BigDecimal("30"), dbm, watt)).isCloseTo(BigDecimal.ONE, within(new BigDecimal("1e-12")));
        assertThat(Units.convert(new BigDecimal("0.01"), watt, dbm)).isCloseTo(BigDecimal.TEN,
                within(new BigDecimal("1e-9")));
        assertThatThrownBy(() -> Units.toBase(dbm)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("logarithmic");
        assertThatThrownBy(() -> Units.convert(BigDecimal.ONE, dbm, watt, Scale.RELATIVE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unitsOfDifferentDimensionsDoNotConvert() {
        assertThat(Units.compatible(SI.unit(si, SI.METRE), SI.unit(si, SI.KILOMETRE))).isTrue();
        assertThat(Units.compatible(SI.unit(si, SI.METRE), SI.unit(si, SI.SECOND))).isFalse();
        assertThatThrownBy(() -> convert("1", SI.METRE, SI.SECOND)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A0E0L1I0M0H0T0D0").hasMessageContaining("A0E0L0I0M0H0T1D0");
    }

    @Test
    void aProductOverAUnitWithAnOffsetHasNoLinearConversion() {
        Unit celsius = SI.unit(si, SI.DEGREE_CELSIUS);
        DerivedUnit perCelsius = Units.derived(model, "perDegreeCelsius", "1/°C", Units.factor(celsius, -1));
        assertThatThrownBy(() -> Units.toBase(perCelsius)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("offset");
    }

    @Test
    void aUnitIsFoundBySymbolAlsoAmongTheUnitsASystemAllows() {
        SystemOfUnits example = ExampleUnits.create(model);
        assertThat(Units.bySymbol(si, "°C")).get().extracting(Unit::getName).isEqualTo(SI.DEGREE_CELSIUS);
        assertThat(Units.bySymbol(si, "dBm")).isEmpty();
        SystemsOfUnits.allow(si, ExampleUnits.unit(example, "dBm"));
        assertThat(Units.bySymbol(si, "dBm")).get().extracting(Unit::getName).isEqualTo("decibelMilliwatt");
    }

    @Test
    void helpersRefuseWhatIsNoUnit() {
        assertThatThrownBy(() -> Units.factor(SI.unit(si, SI.METRE), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Units.scaled(model, "x", "x", SI.unit(si, SI.METRE), "ten"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ten");
        assertThatThrownBy(() -> Units.base(null, "x", "x", SI.unit(si, SI.METRE).getDimension()))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("owner");
    }

    private BigDecimal convert(String value, String from, String to) {
        return Units.convert(new BigDecimal(value), SI.unit(si, from), SI.unit(si, to));
    }
}
