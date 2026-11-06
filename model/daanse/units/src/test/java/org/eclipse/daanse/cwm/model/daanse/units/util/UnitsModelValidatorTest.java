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

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.daanse.units.BaseUnit;
import org.eclipse.daanse.cwm.model.daanse.units.DerivedUnit;
import org.eclipse.daanse.cwm.model.daanse.units.Dimension;
import org.eclipse.daanse.cwm.model.daanse.units.LogarithmicUnit;
import org.eclipse.daanse.cwm.model.daanse.units.Prefix;
import org.eclipse.daanse.cwm.model.daanse.units.QuantityKind;
import org.eclipse.daanse.cwm.model.daanse.units.ScaledUnit;
import org.eclipse.daanse.cwm.model.daanse.units.SystemOfUnits;
import org.eclipse.daanse.cwm.model.daanse.units.Unit;
import org.eclipse.daanse.cwm.model.daanse.units.UnitsFactory;
import org.eclipse.daanse.cwm.model.daanse.units.examples.Elements;
import org.eclipse.daanse.cwm.model.daanse.units.util.UnitsModelValidator.Finding;
import org.eclipse.daanse.cwm.model.daanse.units.util.UnitsModelValidator.Severity;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.InternalEObject;
import org.junit.jupiter.api.Test;

/** Each rule of {@link UnitsModelValidator}: a model that keeps it, and one that breaks it. */
class UnitsModelValidatorTest {

    private final Lab lab = new Lab();

    @Test
    void aMeasuredLabHasNoFinding() {
        Quantities.create(lab.quantities, "reading", lab.unit(SI.HECTOPASCAL), lab.reading);
        Quantities.create(lab.quantities, "celsius", lab.unit(SI.DEGREE_CELSIUS), lab.celsius);
        assertThat(UnitsModelValidator.check(lab.model)).isEmpty();
        assertThat(UnitsModelValidator.validate(lab.model)).isEmpty();
    }

    @Test
    void u1_aQuantityNamesAttributesParametersAndDataTypes() {
        Quantities.create(lab.quantities, "reading", lab.unit(SI.HECTOPASCAL), lab.reading);
        Quantities.create(lab.quantities, "celsius", lab.unit(SI.DEGREE_CELSIUS), lab.celsius);
        Quantities.create(lab.quantities, "limit", lab.unit(SI.HECTOPASCAL),
                Elements.parameter(lab.sensors, "limit", lab.floatType));
        assertThat(rules()).doesNotContain("U1");

        Quantities.create(lab.quantities, "meter", lab.unit(SI.METRE), lab.meter);
        assertThat(errors("U1")).singleElement().asString().contains("names Class lab.sensors.Meter");
    }

    @Test
    void u1_noDataTypeOwnsAQuantity() {
        Quantities.create(lab.celsius, "celsius", lab.unit(SI.DEGREE_CELSIUS), lab.celsius);
        assertThat(errors("U1")).singleElement().asString().contains("owned by a data type");
    }

    @Test
    void u2_aBaseUnitHasOneBaseDimension() {
        SystemOfUnits own = SystemsOfUnits.create(lab.model, "own");
        Dimension speed = Dimensions.ownedIn(own, new long[] { 1, 0, -1, 0, 0, 0, 0 });
        BaseUnit wrong = Units.base(own, "speedUnit", "su", speed);
        assertThat(errors("U2")).singleElement().extracting(Finding::element).isSameAs(wrong);
    }

    @Test
    void u2_theStatedDimensionIsTheOneOfTheDefinition() {
        assertThat(rules()).doesNotContain("U2");
        DerivedUnit speed = (DerivedUnit) lab.unit(SI.METRE_PER_SECOND);
        speed.setDimension(lab.unit(SI.METRE).getDimension());
        assertThat(errors("U2")).singleElement().asString().contains("A0E0L1I0M0H0T0D0")
                .contains("A0E0L1I0M0H0T-1D0");
    }

    @Test
    void u3_oneQuantityPerElement() {
        Quantities.create(lab.quantities, "reading", lab.unit(SI.HECTOPASCAL), lab.reading);
        assertThat(rules()).doesNotContain("U3");
        Quantities.create(lab.quantities, "readingAgain", lab.unit(SI.PASCAL), lab.reading);
        assertThat(errors("U3")).singleElement().asString().contains("has 2 quantities");
    }

    @Test
    void u3_theKindHasTheDimensionOfTheUnit() {
        Quantities.kind(Quantities.create(lab.quantities, "reading", lab.unit(SI.HECTOPASCAL), lab.reading),
                SI.kind(lab.si, "Pressure"));
        assertThat(rules()).doesNotContain("U3");
        Quantities.kind(Quantities.create(lab.quantities, "inside", lab.unit(SI.KELVIN), lab.inside),
                SI.kind(lab.si, "Length"));
        assertThat(errors("U3")).singleElement().asString().contains("the kind Length");
    }

    @Test
    void u3_aKindTheUnitDoesNotMeasureIsAWarning() {
        QuantityKind energy = SI.kind(lab.si, "Energy");
        QuantityKind heat = QuantityKinds.broader(QuantityKinds.create(lab.si, "Heat", energy.getDimension()), energy);
        Quantities.kind(Quantities.create(lab.quantities, "reading", lab.unit(SI.JOULE), lab.reading), heat);
        assertThat(rules()).doesNotContain("U3");
        QuantityKind torque = QuantityKinds.create(lab.si, "Torque", energy.getDimension());
        Quantities.kind(Quantities.create(lab.quantities, "inside", lab.unit(SI.JOULE), lab.inside), torque);
        assertThat(warnings("U3")).singleElement().asString().contains("not Torque");
    }

    @Test
    void u4_numbersParseAndPrefixesHaveBaseTwoOrTen() {
        SystemOfUnits own = SystemsOfUnits.create(lab.model, "own");
        ScaledUnit scaled = Units.scaled(own, "double", "dbl", lab.unit(SI.METRE), "2");
        Prefix prefix = Prefixes.create(own, "kibi", "Ki", 2, 10);
        LogarithmicUnit bel = Units.logarithmic(own, "bel", "B", lab.unit(SI.ONE), "10", "1");
        assertThat(rules()).doesNotContain("U4");
        scaled.setMultiplier("two");
        prefix.setBase(7);
        prefix.setExponent(0);
        bel.setBase("1");
        bel.setDivisor("0");
        assertThat(errors("U4")).extracting(Finding::message).hasSize(5).anyMatch(m -> m.contains("'two'"))
                .anyMatch(m -> m.contains("has base 7")).anyMatch(m -> m.contains("has exponent 0"))
                .anyMatch(m -> m.contains("above zero and not one")).anyMatch(m -> m.contains("divisor is zero"));
    }

    @Test
    void u4_noUnitIsDefinedThroughItselfAndNoProductHasAnOffset() {
        SystemOfUnits own = SystemsOfUnits.create(lab.model, "own");
        ScaledUnit a = Units.scaled(own, "a", "a", lab.unit(SI.METRE), "2");
        ScaledUnit b = Units.scaled(own, "b", "b", a, "3");
        Units.derived(own, "perCelsius", "1/°C", Units.factor(lab.unit(SI.DEGREE_CELSIUS), -1));
        a.setReference(b);
        assertThat(errors("U4")).extracting(Finding::message).anyMatch(m -> m.contains("defined through itself"))
                .anyMatch(m -> m.contains("°C has an offset"));
    }

    @Test
    void u4_broaderKindsKeepTheDimensionAndEndSomewhere() {
        QuantityKind length = SI.kind(lab.si, "Length");
        QuantityKind width = QuantityKinds.broader(QuantityKinds.create(lab.si, "Width", length.getDimension()), length);
        assertThat(rules()).doesNotContain("U4");
        width.setBroader(SI.kind(lab.si, "Time"));
        QuantityKind a = QuantityKinds.create(lab.si, "A", length.getDimension());
        QuantityKind b = QuantityKinds.broader(QuantityKinds.create(lab.si, "B", length.getDimension()), a);
        a.setBroader(b);
        assertThat(errors("U4")).extracting(Finding::message).anyMatch(m -> m.contains("its broader kind"))
                .anyMatch(m -> m.contains("narrower than itself"));
    }

    @Test
    void u4_warnings() {
        SystemOfUnits own = SystemsOfUnits.create(lab.model, "own");
        Units.scaled(own, "same", "x", lab.unit(SI.METRE), "1");
        Units.scaled(own, "other", "x", lab.unit(SI.METRE), "2");
        Units.prefixed(own, SI.prefix(lab.si, "kilo"), lab.unit(SI.KILOMETRE));
        assertThat(errors("U4")).isEmpty();
        assertThat(warnings("U4")).extracting(Finding::message).hasSize(3)
                .anyMatch(m -> m.contains("scales by 1 without offset")).anyMatch(m -> m.contains("used twice"))
                .anyMatch(m -> m.contains("prefixes the prefixed unit km"));
    }

    @Test
    void u4_anAllowedUnitWithASymbolTheSystemUsesIsAWarning() {
        SystemOfUnits own = SystemsOfUnits.create(lab.model, "own");
        Unit fahrenheit = Units.scaled(own, "degreeFahrenheit", "F", lab.unit(SI.KELVIN), "0.5555555555555556",
                "459.67");
        Unit rankine = Units.scaled(own, "degreeRankine", "°R", lab.unit(SI.KELVIN), "0.5555555555555556");
        SystemsOfUnits.allow(lab.si, rankine, lab.unit(SI.METRE));
        assertThat(rules()).doesNotContain("U4");
        SystemsOfUnits.allow(lab.si, fahrenheit);
        assertThat(warnings("U4")).singleElement().asString().contains("allows lab.own.degreeFahrenheit")
                .contains("symbol F");
    }

    @Test
    void u5_aQuantityOnTextIsAWarning() {
        Quantities.create(lab.quantities, "reading", lab.unit(SI.HECTOPASCAL), lab.reading);
        assertThat(rules()).doesNotContain("U5");
        Quantities.create(lab.quantities, "label", lab.unit(SI.METRE), lab.label);
        assertThat(warnings("U5")).singleElement().asString().contains("of type String");
    }

    @Test
    void u6_anOwnQuantityOfAnotherDimensionThanTheTypesIsAWarning() {
        Quantities.create(lab.quantities, "celsius", lab.unit(SI.DEGREE_CELSIUS), lab.celsius);
        Quantities.create(lab.quantities, "inside", lab.unit(SI.KELVIN), lab.inside);
        assertThat(rules()).doesNotContain("U6");
        Quantities.create(lab.quantities, "outside", lab.unit(SI.METRE), lab.outside);
        assertThat(warnings("U6")).singleElement().asString().contains("its type Celsius in °C");
    }

    @Test
    void u7_aReferenceThatDoesNotResolveIsAWarning() {
        Quantities.create(lab.quantities, "reading", lab.unit(SI.HECTOPASCAL), lab.reading);
        assertThat(rules()).doesNotContain("U7");
        Unit missing = UnitsFactory.eINSTANCE.createBaseUnit();
        ((InternalEObject) missing).eSetProxyURI(URI.createURI("units.xmi#_nowhere"));
        Attribute other = Lab.attribute(lab.meter, "other", lab.floatType);
        Quantities.create(lab.quantities, "other", missing, other);
        assertThat(warnings("U7")).singleElement().asString().contains("the unit does not resolve");
    }

    @Test
    void validateReturnsOnlyErrors() {
        Quantities.create(lab.quantities, "label", lab.unit(SI.METRE), lab.label);
        Quantities.create(lab.quantities, "meter", lab.unit(SI.METRE), lab.meter);
        assertThat(UnitsModelValidator.validate(lab.model)).singleElement().asString().startsWith("U1 ");
    }

    private List<String> rules() {
        return UnitsModelValidator.check(lab.model).stream().map(Finding::rule).toList();
    }

    private List<Finding> errors(String rule) {
        return UnitsModelValidator.check(lab.model).stream()
                .filter(f -> f.severity() == Severity.ERROR && f.rule().equals(rule)).toList();
    }

    private List<Finding> warnings(String rule) {
        return UnitsModelValidator.check(lab.model).stream()
                .filter(f -> f.severity() == Severity.WARNING && f.rule().equals(rule)).toList();
    }
}
