/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.FeatureCollection;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.FeatureFactory;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.FeatureProperty;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.examples.City;
import org.eclipse.daanse.cwm.model.daanse.spatial.AxisOrder;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryFeature;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryKind;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryStorage;
import org.eclipse.daanse.cwm.model.daanse.spatial.SpatialFactory;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystem;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystemAuthority;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystemKind;
import org.eclipse.daanse.cwm.model.daanse.spatial.util.SpatialElements;
import org.eclipse.daanse.cwm.model.daanse.spatial.util.SpatialModelValidator;
import org.eclipse.daanse.cwm.model.daanse.validation.Finding;
import org.eclipse.daanse.cwm.model.daanse.validation.Finding.Severity;
import org.eclipse.daanse.cwm.model.daanse.validation.Rule;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.InternalEObject;
import org.junit.jupiter.api.Test;

/** Each rule of {@link FeatureModelValidator}: the city keeps it, a change of it breaks it. */
class FeatureModelValidatorTest {

    private final City city = new City();

    @Test
    void theCityHasNoFinding() {
        assertThat(FeatureModelValidator.check(city.model)).isEmpty();
        assertThat(SpatialModelValidator.check(city.model)).as("the geodata beside it").isEmpty();
    }

    @Test
    void fc1_aCollectionStandsInAPackageAndOwnsUniqueTypedProperties() {
        assertThat(rules()).doesNotContain("FC1");
        FeatureProperties.create(city.schools, "label", city.string);
        FeatureProperty untyped = FeatureProperties.create(city.schools, "untyped", city.string);
        untyped.setType(null);
        city.schools.getFeature().add(CoreFactory.eINSTANCE.createAttribute());
        FeatureCollection loose = FeatureFactory.eINSTANCE.createFeatureCollection();
        loose.setName("loose");
        city.districts.getOwnedElement().add(loose);
        assertThat(errors("FC1")).extracting(Finding::message).hasSize(4)
                .anyMatch(m -> m.equals("city.features.schools has two properties named label"))
                .anyMatch(m -> m.equals("city.features.schools.untyped has no type"))
                .anyMatch(m -> m.contains("which is no feature property"))
                .anyMatch(m -> m.equals("city.features.districts.loose stands in no package"));
    }

    @Test
    void fc2_namesArePlainIds() {
        assertThat(rules()).doesNotContain("FC2");
        FeatureProperty spaced = FeatureProperties.create(city.schools, "opening hours", city.string);
        assertThat(warnings("FC2")).singleElement().satisfies(f -> {
            assertThat(f.element()).isSameAs(spaced);
            assertThat(f.message()).contains("'opening hours' becomes an id");
        });
        assertThat(errors("FC2")).as("a warning only").isEmpty();
    }

    @Test
    void fc3_noPropertyIsNamedLikeAQueryParameter() {
        assertThat(rules()).doesNotContain("FC3");
        FeatureProperties.create(city.schools, "limit", city.integer);
        FeatureProperties.create(city.districts, "bbox-crs", city.string);
        for (String later : List.of("profile", "exclude-properties", "zoom-level", "q")) {
            FeatureProperties.create(city.districts, later, city.string);
        }
        assertThat(errors("FC3")).extracting(Finding::message).hasSize(6)
                .anyMatch(m -> m.startsWith("city.features.schools.limit is named like the query parameter limit"))
                .anyMatch(m -> m.startsWith("city.features.districts.bbox-crs is named like"))
                .anyMatch(m -> m.startsWith("city.features.districts.profile is named like"))
                .anyMatch(m -> m.startsWith("city.features.districts.exclude-properties is named like"))
                .anyMatch(m -> m.startsWith("city.features.districts.zoom-level is named like"))
                .anyMatch(m -> m.startsWith("city.features.districts.q is named like"));
    }

    @Test
    void fc4_theKeyIsANeverNullPropertyOfTheCollection() {
        assertThat(rules()).doesNotContain("FC4");
        city.schools.setFeatureKey(city.districtId);
        FeatureCollection none = FeatureCollections.create(city.features, "none");
        FeatureProperties.create(none, "label", city.string);
        FeatureCollection nullable = FeatureCollections.create(city.features, "nullable");
        FeatureCollections.key(nullable, FeatureProperties.create(nullable, "code", city.string));
        FeatureProperties.create(nullable, "label", city.string);
        assertThat(errors("FC4")).extracting(Finding::message).hasSize(3)
                .anyMatch(m -> m.contains("schools: the feature key city.features.districts.district_id is no "
                        + "property of it"))
                .anyMatch(m -> m.equals("city.features.none names no feature key"))
                .anyMatch(m -> m.contains("nullable: the feature key code may be null"));
    }

    /** OGC API Features Part 5: the id is a string or an integer; a geometry key is FC8's, a type not known none. */
    @Test
    void fc4_theKeyIsAStringOrAnInteger() {
        FeatureCollection numbered = FeatureCollections.create(city.features, "numbered");
        FeatureCollections.key(numbered, FeatureProperties.required(numbered, "number", city.integer));
        FeatureProperties.create(numbered, "label", city.string);
        assertThat(rules()).doesNotContain("FC4");
        FeatureCollection measured = FeatureCollections.create(city.features, "measured");
        FeatureCollections.key(measured, FeatureProperties.required(measured, "reading", city.decimal));
        FeatureProperties.create(measured, "label", city.string);
        FeatureCollection dated = FeatureCollections.create(city.features, "dated");
        FeatureCollections.key(dated, FeatureProperties.required(dated, "day", city.date));
        FeatureProperties.create(dated, "label", city.string);
        assertThat(errors("FC4")).extracting(Finding::message).containsExactlyInAnyOrder(
                "city.features.measured: the feature key reading is of the type Float; an id is a string or an "
                        + "integer",
                "city.features.dated: the feature key day is of the type Date; an id is a string or an integer");
    }

    @Test
    void fc5_theTimePropertiesArePropertiesOfTheCollection() {
        assertThat(rules()).doesNotContain("FC5");
        city.schools.setInstant(city.validFrom);
        city.districts.setIntervalEnd(city.opened);
        assertThat(errors("FC5")).extracting(Finding::message).hasSize(2)
                .anyMatch(m -> m.contains("the instant city.features.districts.valid_from is no property"))
                .anyMatch(m -> m.contains("the interval end city.features.schools.opened is no property"));
    }

    /**
     * The server writes the geometry of a feature from the properties of the collection: a holding of the primary
     * geometry lies in them, whole or as numbers; one in the properties of another collection does not count.
     */
    @Test
    void fc19_thePrimaryGeometryIsHeldInPropertiesOfTheCollection() {
        assertThat(rules()).as("whole, as numbers, and both").doesNotContain("FC19");
        city.schools.setGeometry(city.districtShape);
        GeometryFeature elsewhere = SpatialElements.feature(city.geodata, "elsewhere", GeometryKind.POINT,
                city.utm32);
        SpatialElements.pointParts(elsewhere, city.east, city.residents);
        city.stops.setGeometry(elsewhere);
        assertThat(errors("FC19")).extracting(Finding::message).containsExactlyInAnyOrder(
                "city.features.schools: the primary geometry city.geodata.districtShape has no holding in its "
                        + "properties; the server writes the geometry of a feature from the properties of the "
                        + "collection",
                "city.features.stops: the primary geometry city.geodata.elsewhere has no holding in its properties; "
                        + "the server writes the geometry of a feature from the properties of the collection");
        assertThat(rules()).as("FC6 is retired").doesNotContain("FC6");
    }

    @Test
    void fc7_aPropertyHeldWholeIsOfTheDataTypeGeometryAndHeldByAFeature() {
        assertThat(rules()).as("the numbers east and north keep their type").doesNotContain("FC7");
        FeatureProperty text = FeatureProperties.create(city.schools, "entrance", city.string);
        SpatialElements.whole(city.schoolPoint, text, GeometryStorage.WKT);
        FeatureProperties.create(city.districts, "centre", SpatialElements.geometryType(city.types));
        assertThat(errors("FC7")).extracting(Finding::message).hasSize(2)
                .anyMatch(m -> m.contains("entrance is held whole by the geometry feature city.geodata.schoolPoint "
                        + "but is of the type String"))
                .anyMatch(m -> m.contains("centre is of the data type Geometry but no geometry feature holds it "
                        + "whole"));
    }

    @Test
    void fc8_aGeometryIsNeitherKeyNorTimeAndHasNoValueRules() {
        assertThat(rules()).doesNotContain("FC8");
        city.outline.setPattern("x+");
        city.outline.setSortable(true);
        city.schools.setFeatureKey(city.location);
        assertThat(errors("FC8")).extracting(Finding::message).hasSize(3)
                .anyMatch(m -> m.contains("outline is a geometry and carries allowed values, a bound or a pattern"))
                .anyMatch(m -> m.contains("outline is a geometry and sortable"))
                .anyMatch(m -> m.contains("schools: the feature key location is a geometry"));
    }

    /**
     * OGC API Features Part 5: the time of a feature is an instant or an interval; a start alone or an end alone is a
     * half-bounded interval, an instant beside the ends of an interval is an error.
     */
    @Test
    void fc18_theTimeIsAnInstantOrAnIntervalOfDates() {
        assertThat(rules()).as("an interval and an instant").doesNotContain("FC18");
        FeatureCollections.interval(city.schools, city.opened, null);
        FeatureCollections.interval(city.districts, null, city.validTo);
        assertThat(rules()).as("a start alone, an end alone: half-bounded").doesNotContain("FC18", "FC5");
        city.schools.setInstant(city.opened);
        city.districts.setIntervalStart(city.residents);
        assertThat(errors("FC18")).extracting(Finding::message).containsExactlyInAnyOrder(
                "city.features.schools names an instant and the ends of an interval; the time of a feature is one of "
                        + "the two",
                "city.features.districts: the time property residents is of the type Integer; a time is a date or a "
                        + "timestamp");
        assertThat(rules()).as("no code is used twice").doesNotContain("FC9");
    }

    @Test
    void fc10_aTimeZoneIsValidAndReadsALocalTimestamp() {
        org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType timestamp = CoreFactory.eINSTANCE
                .createDataType();
        timestamp.setName("Timestamp");
        city.types.getOwnedElement().add(timestamp);
        city.opened.setType(timestamp);
        city.schools.setTimeZone("Europe/Berlin");
        assertThat(rules()).doesNotContain("FC10");
        city.schools.setTimeZone("Mars/Olympus");
        city.districts.setTimeZone("+01:00");
        assertThat(warnings("FC10")).extracting(Finding::message).hasSize(2)
                .anyMatch(m -> m.contains("the time zone 'Mars/Olympus' is no IANA name or offset"))
                .anyMatch(m -> m.contains("districts states the time zone +01:00 but no time property is a "
                        + "timestamp without a zone"));
        assertThat(errors("FC10")).as("warnings only").isEmpty();
    }

    @Test
    void fc11_valueRulesFitTheType() {
        assertThat(rules()).doesNotContain("FC11");
        city.pupils.setMaximum("2000");
        city.schoolLabel.setPattern("[A-Z].*");
        assertThat(rules()).doesNotContain("FC11");
        city.residents.setMinimum("100");
        city.residents.setMaximum("10");
        city.pupils.setPattern("[0-9]+");
        city.schoolLabel.setPattern("(");
        city.schoolLabel.setMinimum("1");
        city.pupils.getAllowedValues().add("many");
        city.opened.getAllowedValues().add("2020-01-01");
        assertThat(errors("FC11")).extracting(Finding::message).hasSize(6)
                .anyMatch(m -> m.contains("residents: the minimum 100 lies above the maximum 10"))
                .anyMatch(m -> m.contains("pupils has a pattern but is of the type Integer"))
                .anyMatch(m -> m.contains("label: the pattern '(' is no regular expression"))
                .anyMatch(m -> m.contains("label has a minimum but is of the type String"))
                .anyMatch(m -> m.contains("pupils: the allowed value 'many' is no whole number"))
                .anyMatch(m -> m.contains("opened has allowed values but is of the type Date"));
    }

    @Test
    void fc12_allowedValuesBesideAnEnumerationAreACopy() {
        assertThat(rules()).doesNotContain("FC12");
        city.kind.setType(enumeration());
        assertThat(warnings("FC12")).singleElement().asString().contains("kind lists allowed values beside its "
                + "enumeration type SchoolKind");
        city.kind.getAllowedValues().clear();
        assertThat(rules()).doesNotContain("FC12");
    }

    @Test
    void fc13_aThreeAxisSystemOnlyForAGeometryWithAHeight() {
        ReferenceSystem withHeight = SpatialElements.epsg(city.geodata, 4937, ReferenceSystemKind.GEOGRAPHIC,
                AxisOrder.NORTH_EAST);
        withHeight.setAxes(3);
        assertThat(rules()).doesNotContain("FC13");
        city.districts.getReferenceSystems().add(withHeight);
        assertThat(errors("FC13")).singleElement().asString().contains("offers EPSG 4937 of three axes but its "
                + "primary geometry holds no height");
        ReferenceSystem utmHeight = SpatialElements.epsg(city.geodata, 5555, ReferenceSystemKind.PROJECTED,
                AxisOrder.EAST_NORTH);
        utmHeight.setAxes(3);
        city.districtShape.setReferenceSystem(utmHeight);
        city.districtShape.setHasHeight(true);
        assertThat(rules()).as("a geometry with a height").doesNotContain("FC13");
    }

    @Test
    void fc14_theDefaultAndTheStorageSystemAreNotOffered() {
        assertThat(rules()).doesNotContain("FC14");
        city.districts.getReferenceSystems().add(city.utm32);
        city.schools.getReferenceSystems().add(SpatialElements.referenceSystem(city.geodata, "crs84",
                ReferenceSystemAuthority.OGC, "CRS84", ReferenceSystemKind.GEOGRAPHIC, AxisOrder.EAST_NORTH));
        assertThat(warnings("FC14")).extracting(Finding::message).hasSize(2)
                .anyMatch(m -> m.contains("districts offers EPSG 25832, the storage system of its geometry"))
                .anyMatch(m -> m.contains("schools offers OGC CRS84, the default"));
        assertThat(errors("FC14")).isEmpty();
    }

    @Test
    void fc15_aCollectionWithAGeometryNamesItAndOneWithoutOffersNoSystem() {
        FeatureCollection plain = FeatureCollections.create(city.features, "counters");
        FeatureCollections.key(plain, FeatureProperties.required(plain, "counter_id", city.string));
        FeatureProperties.create(plain, "label", city.string);
        assertThat(rules()).as("a collection without geometry").doesNotContain("FC15");
        plain.getReferenceSystems().add(city.etrs89);
        city.schools.setGeometry(null);
        city.stops.setGeometry(null);
        assertThat(errors("FC15")).extracting(Finding::message).hasSize(3)
                .anyMatch(m -> m.equals("city.features.counters has no geometry but offers reference systems"))
                .anyMatch(m -> m.contains("schools holds a geometry in its properties but names no primary geometry"))
                .anyMatch(m -> m.contains("stops holds a geometry in its properties but names no primary geometry"));
    }

    @Test
    void fc16_aCollectionHasAQueryableBesideItsKey() {
        assertThat(rules()).doesNotContain("FC16");
        for (FeatureProperty p : FeatureCollections.properties(city.schools)) {
            p.setQueryable(false);
        }
        assertThat(warnings("FC16")).singleElement().extracting(Finding::element).isSameAs(city.schools);
    }

    @Test
    void fc17_aReferenceThatDoesNotResolveIsAWarning() {
        assertThat(rules()).doesNotContain("FC17");
        FeatureProperty missing = FeatureFactory.eINSTANCE.createFeatureProperty();
        ((InternalEObject) missing).eSetProxyURI(URI.createURI("city.xmi#_nowhere"));
        city.districts.setIntervalEnd(missing);
        assertThat(warnings("FC17")).singleElement().asString().contains("the interval end does not resolve");
        assertThat(errors("FC5")).as("no rule reads what does not resolve").isEmpty();
        GeometryFeature unresolved = SpatialFactory.eINSTANCE.createGeometryFeature();
        ((InternalEObject) unresolved).eSetProxyURI(URI.createURI("geodata.xmi#_nowhere"));
        city.trees.setGeometry(unresolved);
        assertThat(warnings("FC17")).extracting(Finding::message)
                .contains("city.features.trees: the primary geometry does not resolve");
        assertThat(errors("FC19")).isEmpty();
    }

    @Test
    void validateReturnsOnlyErrors() {
        FeatureProperties.create(city.schools, "opening hours", city.string);
        FeatureProperties.create(city.schools, "limit", city.integer);
        assertThat(FeatureModelValidator.validate(city.model)).singleElement().asString().startsWith("FC3 ");
    }

    private org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier enumeration() {
        org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.Enumeration e =
                org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.DatatypesFactory.eINSTANCE.createEnumeration();
        e.setName("SchoolKind");
        for (String name : List.of("primary", "secondary", "vocational")) {
            org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.EnumerationLiteral literal =
                    org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.DatatypesFactory.eINSTANCE
                            .createEnumerationLiteral();
            literal.setName(name);
            e.getLiteral().add(literal);
        }
        city.types.getOwnedElement().add(e);
        return e;
    }

    private List<String> rules() {
        return FeatureModelValidator.check(city.model).stream().map(Finding::rule).toList();
    }

    private List<Finding> errors(String rule) {
        return FeatureModelValidator.check(city.model).stream()
                .filter(f -> f.severity() == Severity.ERROR && f.rule().equals(rule)).toList();
    }

    private List<Finding> warnings(String rule) {
        return FeatureModelValidator.check(city.model).stream()
                .filter(f -> f.severity() == Severity.WARNING && f.rule().equals(rule)).toList();
    }

    /** Each rule of the validator is stated once, of its module, an error or a warning: never both under one code. */
    @Test
    void everyRuleIsStatedOnceWithOneSeverity() {
        assertThat(FeatureModelValidator.RULES).as("the rules").extracting(Rule::code).doesNotHaveDuplicates()
                .containsExactly("FC1", "FC2", "FC3", "FC4", "FC5", "FC7", "FC8", "FC10", "FC11", "FC12", "FC13",
                        "FC14", "FC15", "FC16", "FC17", "FC18", "FC19");
        assertThat(FeatureModelValidator.RULES).as("the rules").allSatisfy(rule -> assertThat(rule.module())
                .isEqualTo(FeatureModelValidator.MODULE));
        assertThat(FeatureModelValidator.RULES.stream().filter(rule -> rule.severity() == Finding.Severity.WARNING)
                .map(Rule::code)).as("the warnings")
                .containsExactly("FC2", "FC10", "FC12", "FC14", "FC16", "FC17");
    }
}
