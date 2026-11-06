/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.units.examples;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.analysis.olap.Cube;
import org.eclipse.daanse.cwm.model.cwm.analysis.olap.Measure;
import org.eclipse.daanse.cwm.model.cwm.analysis.olap.OlapFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Parameter;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.Association;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.AssociationEnd;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.relationships.RelationshipsFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.record.Field;
import org.eclipse.daanse.cwm.model.cwm.resource.record.RecordDef;
import org.eclipse.daanse.cwm.model.cwm.resource.record.RecordFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.daanse.units.DerivedUnit;
import org.eclipse.daanse.cwm.model.daanse.units.Quantity;
import org.eclipse.daanse.cwm.model.daanse.units.QuantityKind;
import org.eclipse.daanse.cwm.model.daanse.units.Scale;
import org.eclipse.daanse.cwm.model.daanse.units.SystemOfUnits;
import org.eclipse.daanse.cwm.model.daanse.units.Unit;
import org.eclipse.daanse.cwm.model.daanse.units.util.Dimensions;
import org.eclipse.daanse.cwm.model.daanse.units.util.Quantities;
import org.eclipse.daanse.cwm.model.daanse.units.util.QuantityKinds;
import org.eclipse.daanse.cwm.model.daanse.units.util.SI;
import org.eclipse.daanse.cwm.model.daanse.units.util.SystemsOfUnits;
import org.eclipse.daanse.cwm.model.daanse.units.util.Units;
import org.eclipse.daanse.cwm.model.daanse.units.util.UnitsModelValidator;
import org.eclipse.daanse.cwm.model.daanse.units.util.UnitsModelValidator.Finding;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;

/**
 * The units model across CWM: units defined six ways, which unit applies to an element, every place
 * of CWM that takes a unit and those that do not, and the XMI a model with quantities serializes
 * to.
 */
class UnitsAcrossCwmTest {

    private final Model plant = Elements.model("plant");
    private final SystemOfUnits si = SI.create(plant);
    private final Package quantities = Elements.pkg(plant, "quantities");
    private final Package design = Elements.pkg(plant, "design");
    private final DataType floatType = Elements.dataType(design, "Float");

    // 2 Defining units

    @Test
    void s2_1_aBaseUnitHasOneBaseDimension() {
        Unit metre = SI.unit(si, SI.METRE);
        assertThat(Dimensions.code(metre.getDimension())).isEqualTo("A0E0L1I0M0H0T0D0");
        assertThat(metre.getQuantityKind()).extracting(QuantityKind::getName).containsExactly("Length");
    }

    @Test
    void s2_2_aDerivedUnitIsAProductOfPowers() {
        DerivedUnit newton = (DerivedUnit) SI.unit(si, SI.NEWTON);
        assertThat(newton.getFactor()).extracting(f -> f.getUnit().getSymbol() + "^" + f.getExponent())
                .containsExactly("kg^1", "m^1", "s^-2");
        assertThat(Dimensions.code(newton.getDimension())).isEqualTo("A0E0L1I0M1H0T-2D0");
        assertThat(Units.convert(new BigDecimal("36"), SI.unit(si, SI.KILOMETRE_PER_HOUR), SI.unit(si,
                SI.METRE_PER_SECOND))).isCloseTo(BigDecimal.TEN, within(new BigDecimal("1e-30")));
    }

    @Test
    void s2_3_aScaledUnitHasAMultiplierAndAnOffset() {
        assertThat(Units.convert(new BigDecimal("25"), SI.unit(si, SI.DEGREE_CELSIUS), SI.unit(si, SI.KELVIN)))
                .isEqualByComparingTo("298.15");
        assertThat(Units.convert(new BigDecimal("2"), SI.unit(si, SI.HOUR), SI.unit(si, SI.SECOND)))
                .isEqualByComparingTo("7200");
    }

    @Test
    void s2_4_aPrefixedUnitNamesItsPrefix() {
        Unit hectopascal = SI.unit(si, SI.HECTOPASCAL);
        assertThat(hectopascal.getSymbol()).isEqualTo("hPa");
        assertThat(Units.convert(new BigDecimal("1013.25"), hectopascal, SI.unit(si, SI.PASCAL)))
                .isEqualByComparingTo("101325");
    }

    @Test
    void s2_5_anAlternateUnitIsTheSameUnitForAnotherKind() {
        Unit becquerel = SI.unit(si, SI.BECQUEREL);
        Unit hertz = SI.unit(si, SI.HERTZ);
        assertThat(Units.compatible(becquerel, hertz)).isTrue();
        assertThat(becquerel.getQuantityKind()).extracting(QuantityKind::getName).containsExactly("Radioactivity");
        assertThat(hertz.getQuantityKind()).extracting(QuantityKind::getName).containsExactly("Frequency");
    }

    @Test
    void s2_6_aLogarithmicUnitConvertsThroughItsReference() {
        SystemOfUnits project = ExampleUnits.create(plant);
        assertThat(Units.convert(new BigDecimal("30"), ExampleUnits.unit(project, "dBm"), ExampleUnits.unit(project,
                "W"))).isCloseTo(BigDecimal.ONE, within(new BigDecimal("1e-12")));
    }

    @Test
    void s2_7_aProjectHasItsOwnSystemNextToTheSi() {
        SystemOfUnits project = ExampleUnits.create(plant);
        Unit fahrenheit = ExampleUnits.unit(project, "F");
        assertThat(Units.convert(new BigDecimal("212"), fahrenheit, SI.unit(si, SI.DEGREE_CELSIUS)))
                .isCloseTo(new BigDecimal("100"), within(new BigDecimal("1e-12")));
        assertThat(Units.bySymbol(project, "F")).contains(fahrenheit);
        assertThat(Units.bySymbol(si, "F")).get().extracting(Unit::getName).isEqualTo(SI.FARAD);
        Unit decibelMilliwatt = ExampleUnits.unit(project, "dBm");
        assertThat(Units.bySymbol(si, "dBm")).isEmpty();
        SystemsOfUnits.allow(si, decibelMilliwatt);
        assertThat(Units.bySymbol(si, "dBm")).contains(decibelMilliwatt);
        assertThat(UnitsModelValidator.check(plant)).isEmpty();
    }

    // 3 Which unit applies

    @Test
    void s3_1_anAttributeHasTheUnitOfItsQuantity() {
        Class boiler = cls(design, "Boiler");
        Attribute pressure = attribute(boiler, "pressure", floatType);
        Quantities.create(quantities, "pressure", SI.unit(si, SI.HECTOPASCAL), pressure);
        assertThat(Quantities.unitOf(pressure)).get().extracting(Unit::getSymbol).isEqualTo("hPa");
    }

    @Test
    void s3_2_aDataTypeGivesItsAttributesItsUnit() {
        DataType celsius = Elements.dataType(design, "Celsius");
        Class boiler = cls(design, "Boiler");
        Attribute inlet = attribute(boiler, "inlet", celsius);
        Attribute outlet = attribute(boiler, "outlet", celsius);
        Quantities.create(quantities, "celsius", SI.unit(si, SI.DEGREE_CELSIUS), celsius);
        assertThat(List.of(inlet, outlet)).allSatisfy(a -> assertThat(Quantities.unitOf(a)).get()
                .extracting(Unit::getSymbol).isEqualTo("°C"));
    }

    @Test
    void s3_3_anOwnQuantityComesFirst() {
        DataType celsius = Elements.dataType(design, "Celsius");
        Class boiler = cls(design, "Boiler");
        Attribute inlet = attribute(boiler, "inlet", celsius);
        Attribute flue = attribute(boiler, "flue", celsius);
        Quantities.create(quantities, "celsius", SI.unit(si, SI.DEGREE_CELSIUS), celsius);
        Quantities.create(quantities, "flue", SI.unit(si, SI.KELVIN), flue);
        assertThat(Quantities.unitOf(inlet)).get().extracting(Unit::getSymbol).isEqualTo("°C");
        assertThat(Quantities.unitOf(flue)).get().extracting(Unit::getSymbol).isEqualTo("K");
        assertThat(UnitsModelValidator.check(plant)).isEmpty();
    }

    @Test
    void s3_4_aDifferenceIsRelative() {
        Class boiler = cls(design, "Boiler");
        Attribute spread = attribute(boiler, "spread", floatType);
        Quantity q = Quantities.relative(Quantities.create(quantities, "spread", SI.unit(si, SI.DEGREE_CELSIUS), spread));
        assertThat(Units.convert(new BigDecimal("5"), q.getUnit(), SI.unit(si, SI.KELVIN), q.getScale()))
                .isEqualByComparingTo("5");
        assertThat(q.getScale()).isEqualTo(Scale.RELATIVE);
    }

    @Test
    void s3_5_theKindTellsWhatAUnitMeasures() {
        Class boiler = cls(design, "Boiler");
        Attribute heat = attribute(boiler, "heat", floatType);
        QuantityKind energy = SI.kind(si, "Energy");
        QuantityKind heatKind = QuantityKinds.broader(QuantityKinds.create(design, "Heat", energy.getDimension()), energy);
        Quantity q = Quantities.kind(Quantities.create(quantities, "heat", SI.unit(si, SI.KILOJOULE), heat), heatKind);
        assertThat(QuantityKinds.isA(q.getQuantityKind(), energy)).isTrue();
        assertThat(UnitsModelValidator.check(plant)).isEmpty();
    }

    // 4 Place by place

    @Test
    void s4_1_aColumn() {
        Catalog warehouse = Elements.catalog(plant, "warehouse");
        Schema sales = Elements.schema(warehouse, "sales");
        Table shipments = Elements.table(sales, "shipments");
        Column weight = Elements.column(shipments, "weight", Elements.sqlType(sales, "DECIMAL", 3));
        Quantities.create(quantities, "weight", SI.unit(si, SI.KILOGRAM), weight);
        assertThat(Quantities.unitOf(weight)).get().extracting(Unit::getSymbol).isEqualTo("kg");
        assertThat(UnitsModelValidator.check(plant)).isEmpty();
    }

    @Test
    void s4_2_aRecordField() {
        RecordDef reading = RecordFactory.eINSTANCE.createRecordDef();
        reading.setName("reading");
        design.getOwnedElement().add(reading);
        Field rain = RecordFactory.eINSTANCE.createField();
        rain.setName("rain");
        rain.setType(floatType);
        reading.getFeature().add(rain);
        Quantities.create(quantities, "rain", SI.unit(si, SI.MILLIMETRE_PER_HOUR), rain);
        assertThat(Quantities.unitOf(rain)).get().extracting(Unit::getSymbol).isEqualTo("mm/h");
        assertThat(UnitsModelValidator.check(plant)).isEmpty();
    }

    @Test
    void s4_3_aMeasureOfACube() {
        Cube energy = OlapFactory.eINSTANCE.createCube();
        energy.setName("energy");
        design.getOwnedElement().add(energy);
        Measure consumption = OlapFactory.eINSTANCE.createMeasure();
        consumption.setName("consumption");
        consumption.setType(floatType);
        energy.getFeature().add(consumption);
        Quantities.create(quantities, "consumption", SI.unit(si, SI.KILOWATT_HOUR), consumption);
        assertThat(Quantities.unitOf(consumption)).get().extracting(Unit::getSymbol).isEqualTo("kWh");
        assertThat(UnitsModelValidator.check(plant)).isEmpty();
    }

    @Test
    void s4_4_aParameter() {
        Parameter threshold = Elements.parameter(design, "threshold", floatType);
        Quantities.create(quantities, "threshold", SI.unit(si, SI.KILOPASCAL), threshold);
        assertThat(Quantities.unitOf(threshold)).get().extracting(Unit::getSymbol).isEqualTo("kPa");
        assertThat(UnitsModelValidator.check(plant)).isEmpty();
    }

    // 5 Where a unit does not apply

    @Test
    void s5_aClassAndAnAssociationEndHaveNoUnit() {
        Class boiler = cls(design, "Boiler");
        Class burner = cls(design, "Burner");
        Association fires = RelationshipsFactory.eINSTANCE.createAssociation();
        fires.setName("fires");
        design.getOwnedElement().add(fires);
        AssociationEnd end = RelationshipsFactory.eINSTANCE.createAssociationEnd();
        end.setName("burner");
        end.setType(burner);
        fires.getFeature().add(end);
        Quantities.create(quantities, "boiler", SI.unit(si, SI.KILOGRAM), boiler);
        Quantities.create(quantities, "burner", SI.unit(si, SI.ONE), end);
        assertThat(UnitsModelValidator.check(plant)).extracting(Finding::rule).containsExactly("U1", "U1");
        assertThatThrownBy(() -> Units.convert(BigDecimal.ONE, SI.unit(si, SI.METRE), SI.unit(si, SI.SECOND)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 7 In XMI

    /** What the model of {@code s7} serializes to, line by line, with short ids. */
    private static final String XMI = """
            <ownedElement xsi:type="units:Dimension" name="A0E0L0I0M0H1T0D0" thermodynamicTemperature="1"/>
            <ownedElement xsi:type="units:QuantityKind" name="Temperature" dimension="_A0E0L0I0M0H1T0D0"/>
            <ownedElement xsi:type="units:BaseUnit" name="kelvin" symbol="K" ucumCode="K" \
            dimension="_A0E0L0I0M0H1T0D0" quantityKind="_Temperature"/>
            <ownedElement xsi:type="units:ScaledUnit" name="degreeCelsius" symbol="°C" ucumCode="Cel" \
            dimension="_A0E0L0I0M0H1T0D0" quantityKind="_Temperature" reference="_kelvin" multiplier="1" \
            offset="273.15"/>
            <ownedElement xsi:type="units:DerivedUnit" name="metrePerSecond" symbol="m/s" ucumCode="m/s" \
            dimension="_A0E0L1I0M0H0T-1D0" quantityKind="_Speed">
            <factor unit="_metre" exponent="1"/>
            <factor unit="_second" exponent="-1"/>
            <ownedElement xsi:type="units:PrefixedUnit" name="hectopascal" symbol="hPa" ucumCode="hPa" \
            dimension="_A0E0L-1I0M1H0T-2D0" quantityKind="_Pressure" reference="_pascal" prefix="_hecto"/>
            <ownedElement xsi:type="units:Quantity" name="temperature" modelElement="_Boiler.temperature" \
            unit="_degreeCelsius" quantityKind="_Temperature"/>
            """;

    @Test
    void s7_aModelSerializesWithItsQuantities() throws Exception {
        Class boiler = cls(design, "Boiler");
        Attribute temperature = attribute(boiler, "temperature", floatType);
        Quantities.kind(Quantities.create(quantities, "temperature", SI.unit(si, SI.DEGREE_CELSIUS), temperature),
                SI.kind(si, "Temperature"));

        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
        Resource r = rs.createResource(URI.createURI("plant.xmi"));
        r.getContents().add(plant);
        Elements.shortIds(r);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        r.save(out, Map.of(XMLResource.OPTION_ENCODING, "UTF-8"));
        String xmi = out.toString(StandardCharsets.UTF_8).replaceAll(" xmi:id=\"[^\"]*\"", "");

        for (String line : XMI.split("\n")) {
            assertThat(xmi).as(line).contains(line.trim());
        }
    }

    private static Class cls(Namespace owner, String name) {
        Class c = CoreFactory.eINSTANCE.createClass();
        c.setName(name);
        owner.getOwnedElement().add(c);
        return c;
    }

    private static Attribute attribute(Class owner, String name, Classifier type) {
        Attribute a = CoreFactory.eINSTANCE.createAttribute();
        a.setName(name);
        a.setType(type);
        owner.getFeature().add(a);
        return a;
    }
}
