/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.spatial.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Multiplicity;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.MultiplicityRange;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.daanse.spatial.AxisOrder;
import org.eclipse.daanse.cwm.model.daanse.spatial.CoordinatePart;
import org.eclipse.daanse.cwm.model.daanse.spatial.DistanceBand;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryDerivation;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryFeature;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryKind;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryMultiplicity;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryStorage;
import org.eclipse.daanse.cwm.model.daanse.spatial.GridLevel;
import org.eclipse.daanse.cwm.model.daanse.spatial.GridSystem;
import org.eclipse.daanse.cwm.model.daanse.spatial.ListHolding;
import org.eclipse.daanse.cwm.model.daanse.spatial.Ordinate;
import org.eclipse.daanse.cwm.model.daanse.spatial.PartsHolding;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystem;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystemAuthority;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystemKind;
import org.eclipse.daanse.cwm.model.daanse.spatial.ResultForm;
import org.eclipse.daanse.cwm.model.daanse.spatial.SpatialAssignment;
import org.eclipse.daanse.cwm.model.daanse.spatial.SpatialFactory;
import org.eclipse.daanse.cwm.model.daanse.spatial.SpatialPredicate;
import org.eclipse.daanse.cwm.model.daanse.spatial.SpatialRelation;
import org.eclipse.daanse.cwm.model.daanse.spatial.SpatialWeight;
import org.eclipse.daanse.cwm.model.daanse.spatial.TieBreak;
import org.eclipse.daanse.cwm.model.daanse.spatial.UnmatchedPolicy;
import org.eclipse.daanse.cwm.model.daanse.spatial.WholeHolding;
import org.eclipse.daanse.cwm.model.daanse.validation.Finding;
import org.eclipse.daanse.cwm.model.daanse.validation.Finding.Severity;
import org.eclipse.daanse.cwm.model.daanse.validation.Rule;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.InternalEObject;
import org.junit.jupiter.api.Test;

/** Each rule of {@link SpatialModelValidator}: a model that keeps it, and one that breaks it. */
class SpatialModelValidatorTest {

    private final Land land = new Land();

    @Test
    void aLandWithItsRelationsHasNoFinding() {
        land.communesInCounties();
        SpatialElements.workingSystem(land.lakesInCountries(), land.laea);
        SpatialElements.grid(land.geodata, "cells", land.stations, land.stationId, land.h3(), 7,
                SpatialElements.grid(land.geodata, "regions", land.stations, land.stationId, land.h3(), 5, null));
        SpatialElements.band(land.geodata, "toTheRiver", land.stations, land.stationId, land.rivers, "1000", "5000",
                "20000").setWorkingSystem(land.laea);
        assertThat(SpatialModelValidator.check(land.model)).isEmpty();
        assertThat(SpatialModelValidator.validate(land.model)).isEmpty();
    }

    /** An attribute holds one geometry, or one number of it: one holding names it, in one of its parts. */
    @Test
    void sp2_anAttributeIsNamedOnceByOneHolding() {
        assertThat(rules()).doesNotContain("SP2");
        SpatialElements.feature(land.geodata, "countiesAgain", GeometryKind.POLYGON, land.wgs84, GeometryStorage.NATIVE,
                land.countyShape);
        assertThat(errors("SP2")).singleElement().satisfies(f -> {
            assertThat(f.element()).isSameAs(land.countyShape);
            assertThat(f.message()).isEqualTo("land.geo.county.shape is named 2 times, by the whole holding of "
                    + "land.geodata.counties, the whole holding of land.geodata.countiesAgain; an attribute holds one "
                    + "geometry or one number of it");
        });
        land.geodata.getOwnedElement().remove(land.geodata.getOwnedElement().size() - 1);
        Table gauge = land.table("gauge");
        Column lon = Land.column(gauge, "lon", land.decimal);
        GeometryFeature gauges = SpatialElements.point(land.geodata, "gauges", land.wgs84, lon,
                Land.column(gauge, "lat", land.decimal));
        assertThat(rules()).doesNotContain("SP2");
        ((PartsHolding) gauges.getHolding().get(0)).getPart().get(1).setAttribute(lon);
        assertThat(errors("SP2")).singleElement().asString().contains("land.geo.gauge.lon is named 2 times, by "
                + "the parts of land.geodata.gauges, the parts of land.geodata.gauges");
    }

    @Test
    void sp3_theTypeOfTheColumnFitsTheStorage() {
        Table border = land.table("border");
        Column text = Land.column(border, "wkt", land.varchar);
        Column binary = Land.column(border, "wkb", land.blob);
        SpatialElements.feature(land.geodata, "asText", GeometryKind.LINE, land.utm32, GeometryStorage.WKT, text);
        SpatialElements.feature(land.geodata, "asBinary", GeometryKind.LINE, land.utm32, GeometryStorage.WKB, binary);
        assertThat(rules()).doesNotContain("SP3");
        Column number = Land.column(border, "code", land.integer);
        SpatialElements.feature(land.geodata, "asNumber", GeometryKind.LINE, land.utm32, GeometryStorage.WKB, number);
        assertThat(warnings("SP3")).singleElement().asString().contains("land.geo.border.code is of type INTEGER")
                .contains("does not hold WKB");
        assertThat(errors("SP3")).as("a warning only").isEmpty();
    }

    /** SP30: a geometry feature names its reference system. */
    @Test
    void sp30_aFeatureNamesItsReferenceSystem() {
        assertThat(rules()).doesNotContain("SP30");
        land.stations.setReferenceSystem(null);
        assertThat(errors("SP30")).singleElement().satisfies(f -> {
            assertThat(f.element()).isSameAs(land.stations);
            assertThat(f.message()).isEqualTo("land.geodata.stations names no reference system");
        });
    }

    /** SP31: the accuracy is a decimal number of metres from zero up. */
    @Test
    void sp31_theAccuracyIsADecimalNumberOfMetresFromZeroUp() {
        land.communes.setAccuracy("2.5");
        land.counties.setAccuracy("0");
        assertThat(rules()).doesNotContain("SP31");
        land.communes.setAccuracy("-1");
        land.counties.setAccuracy("a metre");
        assertThat(errors("SP31")).extracting(Finding::message).containsExactlyInAnyOrder(
                "land.geodata.communes: the accuracy '-1' is no decimal number of metres from zero up",
                "land.geodata.counties: the accuracy 'a metre' is no decimal number of metres from zero up");
    }

    /** SP32: the coordinates of a static system hold for every epoch; an epoch belongs to a dynamic one. */
    @Test
    void sp32_anEpochOnlyInADynamicSystem() {
        gaugesInADynamicSystem().setCoordinateEpoch("2010.5");
        assertThat(rules()).doesNotContain("SP32");
        land.counties.setCoordinateEpoch("1989.0");
        assertThat(errors("SP32")).singleElement().satisfies(f -> {
            assertThat(f.element()).isSameAs(land.counties);
            assertThat(f.message()).isEqualTo("land.geodata.counties names the coordinate epoch 1989.0 but EPSG 25832 "
                    + "is not dynamic; the coordinates of a static system hold for every epoch");
        });
    }

    /** SP33: an epoch is a decimal year. */
    @Test
    void sp33_anEpochIsADecimalYear() {
        GeometryFeature gauges = gaugesInADynamicSystem();
        gauges.setCoordinateEpoch("2010.5");
        assertThat(rules()).doesNotContain("SP33");
        gauges.setCoordinateEpoch("spring");
        assertThat(errors("SP33")).singleElement().satisfies(f -> {
            assertThat(f.element()).isSameAs(gauges);
            assertThat(f.message()).isEqualTo("land.geodata.gauges: the coordinate epoch 'spring' is no decimal year");
        });
        assertThat(rules()).as("in a dynamic system: one finding").doesNotContain("SP32");
    }

    /** SP34: a reference system has a code. */
    @Test
    void sp34_aReferenceSystemHasACode() {
        assertThat(rules()).doesNotContain("SP34");
        land.laea.setCode(" ");
        assertThat(errors("SP34")).singleElement().satisfies(f -> {
            assertThat(f.element()).isSameAs(land.laea);
            assertThat(f.message()).isEqualTo("land.geodata.EPSG_3035 has no code");
        });
        assertThat(rules()).as("no code is no EPSG number either: one finding").doesNotContain("SP35");
    }

    /** SP35: an EPSG code is a positive whole number; the code of another register is not. */
    @Test
    void sp35_anEpsgCodeIsAPositiveWholeNumber() {
        SpatialElements.crs84(land.geodata);
        assertThat(rules()).as("an OGC code is no number").doesNotContain("SP35");
        SpatialElements.referenceSystem(land.geodata, "named", ReferenceSystemAuthority.EPSG, "UTM32",
                ReferenceSystemKind.PROJECTED, AxisOrder.EAST_NORTH);
        SpatialElements.referenceSystem(land.geodata, "zero", ReferenceSystemAuthority.EPSG, "0",
                ReferenceSystemKind.PROJECTED, AxisOrder.EAST_NORTH);
        assertThat(errors("SP35")).extracting(Finding::message).containsExactlyInAnyOrder(
                "land.geodata.named has the EPSG code 'UTM32'; an EPSG code is a positive whole number",
                "land.geodata.zero has the EPSG code '0'; an EPSG code is a positive whole number");
    }

    /** SP36: a reference system has two axes, or three with a height or a z. */
    @Test
    void sp36_aReferenceSystemHasTwoOrThreeAxes() {
        SpatialElements.crs84h(land.geodata);
        assertThat(rules()).as("two and three axes").doesNotContain("SP36");
        land.utm32.setAxes(4);
        land.laea.setAxes(0);
        assertThat(errors("SP36")).extracting(Finding::message).containsExactlyInAnyOrder(
                "land.geodata.EPSG_25832 has 4 axes; a system has 2 or 3",
                "land.geodata.EPSG_3035 has 0 axes; a system has 2 or 3");
    }

    /**
     * SP37: a reference system states the order its register defines for its first two axes. The attribute has no
     * default: a system read from a file that does not state it is an error, not east first.
     */
    @Test
    void sp37_aReferenceSystemStatesItsAxisOrder() {
        assertThat(rules()).as("every system of the land states it").doesNotContain("SP37");
        ReferenceSystem unstated = SpatialFactory.eINSTANCE.createReferenceSystem();
        unstated.setName("unstated");
        unstated.setAuthority(ReferenceSystemAuthority.EPSG);
        unstated.setCode("4258");
        unstated.setKind(ReferenceSystemKind.GEOGRAPHIC);
        land.geodata.getOwnedElement().add(unstated);
        assertThat(errors("SP37")).singleElement().satisfies(f -> {
            assertThat(f.element()).isSameAs(unstated);
            assertThat(f.message()).isEqualTo("land.geodata.unstated states no axis order; the order its register "
                    + "defines is stated explicitly (EAST_NORTH or NORTH_EAST), there is no default");
        });
        unstated.setAxisOrder(AxisOrder.NORTH_EAST);
        assertThat(rules()).doesNotContain("SP37");
        unstated.unsetAxisOrder();
        assertThat(rules()).contains("SP37");
    }

    /** Gauges as points in EPSG 7931, a dynamic system of three axes; the epoch is set by the test. */
    private GeometryFeature gaugesInADynamicSystem() {
        ReferenceSystem moving = SpatialElements.epsg(land.geodata, 7931, ReferenceSystemKind.GEOGRAPHIC,
                AxisOrder.NORTH_EAST);
        moving.setAxes(3);
        moving.setDynamic(true);
        return SpatialElements.feature(land.geodata, "gauges", GeometryKind.POINT, moving, GeometryStorage.NATIVE,
                Land.column(land.table("gauge"), "position", land.geometry));
    }

    @Test
    void sp5_thePredicateFitsTheParent() {
        SpatialElements.relation(land.geodata, "stationsInStates", land.stations, land.stationId, land.states,
                land.stateId, SpatialPredicate.COVERED_BY, SpatialAssignment.EXACTLY_ONE, UnmatchedPolicy.KEEP_NULL)
                .setWorkingSystem(land.utm32);
        assertThat(rules()).doesNotContain("SP5");
        SpatialRelation byRiver = SpatialElements.relation(land.geodata, "communesInRivers", land.communes,
                land.communeId, land.rivers, land.riverId, SpatialPredicate.WITHIN, SpatialAssignment.EXACTLY_ONE,
                UnmatchedPolicy.KEEP_NULL);
        byRiver.setWorkingSystem(land.laea);
        assertThat(errors("SP5")).singleElement().asString().contains("WITHIN against the LINE parent");
    }

    @Test
    void sp6_theWeightFitsTheChild() {
        SpatialElements.workingSystem(SpatialElements.weight(SpatialElements.relation(land.geodata,
                "riversInCountries", land.rivers, land.riverId, land.countries, land.countryId,
                SpatialPredicate.INTERSECTS, SpatialAssignment.ALL_WEIGHTED, UnmatchedPolicy.KEEP_NULL),
                SpatialWeight.LENGTH, null), land.laea);
        assertThat(rules()).doesNotContain("SP6");
        SpatialElements.workingSystem(SpatialElements.weight(SpatialElements.relation(land.geodata,
                "stationsByArea", land.stations, land.stationId, land.states, land.stateId,
                SpatialPredicate.COVERED_BY, SpatialAssignment.ALL_WEIGHTED, UnmatchedPolicy.KEEP_NULL),
                SpatialWeight.AREA, null), land.utm32);
        assertThat(errors("SP6")).singleElement().asString().contains("weighs by AREA the POINT child");
    }

    @Test
    void sp7_exactlyOneWeighsOnlyWithAMinimumShare() {
        SpatialElements.weight(land.communesInCounties(), SpatialWeight.AREA, "0.01");
        assertThat(rules()).doesNotContain("SP7");
        SpatialRelation r = land.communesInCounties();
        r.setName("again");
        r.setWeight(SpatialWeight.AREA);
        assertThat(errors("SP7")).singleElement().asString().contains("EXACTLY_ONE with the weight AREA");
    }

    @Test
    void sp8_aDividedAssignmentNeedsAWeight() {
        SpatialElements.relation(land.geodata, "equal", land.lakes, land.lakeId, land.countries, land.countryId,
                SpatialPredicate.INTERSECTS, SpatialAssignment.ALL_EQUAL, UnmatchedPolicy.KEEP_NULL);
        land.lakesInCountries();
        assertThat(rules()).doesNotContain("SP8");
        SpatialElements.relation(land.geodata, "weighted", land.lakes, land.lakeId, land.countries, land.countryId,
                SpatialPredicate.INTERSECTS, SpatialAssignment.ALL_WEIGHTED, UnmatchedPolicy.KEEP_NULL);
        assertThat(errors("SP8")).singleElement().asString().contains("ALL_WEIGHTED without a weight");
    }

    @Test
    void sp9_theMinimumShareIsAShareBelowOneMeasuredByAWeight() {
        land.lakesInCountries().setMinimumShare("0.01");
        assertThat(rules()).doesNotContain("SP9");
        SpatialRelation large = land.lakesInCountries();
        large.setName("large");
        large.setMinimumShare("1.5");
        SpatialRelation unweighed = land.communesInCounties();
        unweighed.setMinimumShare("0.01");
        assertThat(errors("SP9")).extracting(Finding::message).hasSize(2)
                .anyMatch(m -> m.contains("1.5 is not from 0 below 1"))
                .anyMatch(m -> m.contains("0.01 has no weight"));
    }

    @Test
    void sp10_aDistanceBelongsToWithinDistance() {
        SpatialElements.withinDistance(SpatialElements.relation(land.geodata, "near", land.stations, land.stationId,
                land.rivers, land.riverId, SpatialPredicate.NEAREST, SpatialAssignment.ALL_EQUAL,
                UnmatchedPolicy.KEEP_NULL), "500").setWorkingSystem(land.laea);
        assertThat(rules()).doesNotContain("SP10");
        land.communesInCounties().setDistance("500");
        SpatialRelation none = SpatialElements.relation(land.geodata, "none", land.stations, land.stationId,
                land.rivers, land.riverId, SpatialPredicate.WITHIN_DISTANCE, SpatialAssignment.ALL_EQUAL,
                UnmatchedPolicy.KEEP_NULL);
        none.setWorkingSystem(land.laea);
        assertThat(errors("SP10")).extracting(Finding::message).hasSize(2)
                .anyMatch(m -> m.contains("distance 500 but matches CENTROID_WITHIN"))
                .anyMatch(m -> m.contains("WITHIN_DISTANCE without a distance"));
    }

    @Test
    void sp11_theNearestParentIsOne() {
        SpatialRelation nearest = SpatialElements.relation(land.geodata, "nearest", land.stations, land.stationId,
                land.rivers, land.riverId, SpatialPredicate.NEAREST, SpatialAssignment.EXACTLY_ONE,
                UnmatchedPolicy.KEEP_NULL);
        nearest.setTieBreak(TieBreak.LOWEST_PARENT_KEY);
        nearest.setWorkingSystem(land.laea);
        assertThat(rules()).doesNotContain("SP11");
        nearest.setAssignment(SpatialAssignment.ALL_WEIGHTED);
        nearest.setWeight(SpatialWeight.LENGTH);
        assertThat(errors("SP11")).singleElement().asString().contains("NEAREST but assigns ALL_WEIGHTED");
    }

    @Test
    void sp12_twoReferenceSystemsNeedAWorkingOne() {
        SpatialRelation stations = SpatialElements.relation(land.geodata, "stationsInStates", land.stations,
                land.stationId, land.states, land.stateId, SpatialPredicate.COVERED_BY, SpatialAssignment.EXACTLY_ONE,
                UnmatchedPolicy.KEEP_NULL);
        stations.setWorkingSystem(land.utm32);
        ReferenceSystem utm32Again = SpatialElements.referenceSystem(land.geodata, "utm32Again",
                ReferenceSystemAuthority.EPSG, "25832", ReferenceSystemKind.PROJECTED, AxisOrder.EAST_NORTH);
        GeometryFeature parishes = SpatialElements.feature(land.geodata, "parishes", GeometryKind.POLYGON,
                utm32Again, GeometryStorage.NATIVE, Land.column(land.table("parish"), "shape", land.geometry));
        SpatialElements.relation(land.geodata, "parishesInCounties", parishes,
                land.key((Table) whole(parishes).getAttribute().eContainer()), land.counties, land.countyId,
                SpatialPredicate.CENTROID_WITHIN, SpatialAssignment.EXACTLY_ONE, UnmatchedPolicy.KEEP_NULL);
        assertThat(rules()).as("two elements of one URI are one system").doesNotContain("SP12");
        GeometryFeature gauges = SpatialElements.feature(land.geodata, "gauges", GeometryKind.POINT, land.wgs84,
                GeometryStorage.WKB, Land.column(land.table("gauge"), "position", land.blob));
        whole(gauges).setAxisOrder(AxisOrder.NORTH_EAST);
        SpatialElements.withinDistance(SpatialElements.relation(land.geodata, "gaugesNearStations", gauges,
                land.key((Table) whole(gauges).getAttribute().eContainer()), land.stations, land.stationId,
                SpatialPredicate.NEAREST, SpatialAssignment.ALL_EQUAL, UnmatchedPolicy.KEEP_NULL), "500");
        assertThat(rules()).as("one system, whatever order each column stores its coordinates in")
                .doesNotContain("SP12");
        stations.setWorkingSystem(null);
        SpatialElements.band(land.geodata, "toTheRiver", land.stations, land.stationId, land.rivers, "1000");
        assertThat(errors("SP12")).extracting(Finding::message).hasSize(2)
                .anyMatch(m -> m.contains("relates the reference systems EPSG 4326 and EPSG 25832"))
                .anyMatch(m -> m.contains("between the reference systems EPSG 4326 and EPSG 3035"));
    }

    /**
     * An area of a relation in a geographic system is measured on the spheroid, in a projected one in its plane: no
     * finding. In a geocentric system the planar functions would read x and y only: a warning.
     */
    @Test
    void sp27_anAreaInAGeocentricSystemIsAWarning() {
        GeometryFeature parks = SpatialElements.feature(land.geodata, "parks", GeometryKind.POLYGON, land.wgs84,
                GeometryStorage.NATIVE, Land.column(land.table("park"), "shape", land.geometry));
        GeometryFeature districts = SpatialElements.feature(land.geodata, "districts", GeometryKind.POLYGON,
                land.wgs84, GeometryStorage.NATIVE, Land.column(land.table("district"), "shape", land.geometry));
        Column parkId = land.key((Table) whole(parks).getAttribute().eContainer());
        Column districtId = land.key((Table) whole(districts).getAttribute().eContainer());
        SpatialRelation share = SpatialElements.weight(SpatialElements.relation(land.geodata, "parksInDistricts",
                parks, parkId, districts, districtId, SpatialPredicate.INTERSECTS, SpatialAssignment.ALL_WEIGHTED,
                UnmatchedPolicy.KEEP_NULL), SpatialWeight.AREA, null);
        assertThat(rules()).as("both in EPSG 4326: measured on the spheroid").doesNotContain("SP27");
        share.setWorkingSystem(land.laea);
        assertThat(rules()).as("in a projected system, in its plane").doesNotContain("SP27");
        share.setWorkingSystem(SpatialElements.epsg(land.geodata, 4978, ReferenceSystemKind.GEOCENTRIC,
                AxisOrder.EAST_NORTH));
        assertThat(warnings("SP27")).singleElement().asString()
                .contains("measures area in the geocentric reference system EPSG 4978");
        assertThat(errors("SP27")).as("a warning only").isEmpty();
        assertThat(rules()).as("no code is used twice").doesNotContain("SP13");
    }

    /**
     * A band and a relation's distance in a geographic system are measured on the spheroid, in metres: no finding; in a
     * geocentric working system a warning.
     */
    @Test
    void sp27_aDistanceInAGeographicSystemIsMeasuredOnTheSpheroid() {
        GeometryFeature stops = SpatialElements.feature(land.geodata, "stops", GeometryKind.POINT, land.wgs84,
                GeometryStorage.NATIVE, Land.column(land.table("stop"), "shape", land.geometry));
        Column stopId = land.key((Table) whole(stops).getAttribute().eContainer());
        DistanceBand band = SpatialElements.band(land.geodata, "toTheStop", land.stations, land.stationId, stops,
                "300", "1000");
        SpatialRelation nearest = SpatialElements.relation(land.geodata, "nearestStop", land.stations, land.stationId,
                stops, stopId, SpatialPredicate.NEAREST, SpatialAssignment.EXACTLY_ONE, UnmatchedPolicy.KEEP_NULL);
        SpatialRelation within = SpatialElements.withinDistance(SpatialElements.relation(land.geodata, "stopsInReach",
                land.stations, land.stationId, stops, stopId, SpatialPredicate.NEAREST, SpatialAssignment.ALL_EQUAL,
                UnmatchedPolicy.KEEP_NULL), "500");
        assertThat(rules()).as("all in EPSG 4326, measured in metres on the spheroid").doesNotContain("SP27");
        ReferenceSystem geocentric = SpatialElements.epsg(land.geodata, 4978, ReferenceSystemKind.GEOCENTRIC,
                AxisOrder.EAST_NORTH);
        geocentric.setAxes(3);
        band.setWorkingSystem(geocentric);
        nearest.setWorkingSystem(geocentric);
        within.setWorkingSystem(geocentric);
        assertThat(warnings("SP27")).extracting(Finding::element).containsExactlyInAnyOrder(band, nearest, within);
        assertThat(warnings("SP27")).extracting(Finding::message)
                .allMatch(m -> m.contains("measures a distance in the geocentric reference system EPSG 4978"));
    }

    @Test
    void sp14_theKeysBelongToTheGeometryTables() {
        SpatialElements.explicitKey(land.communesInCounties(), land.communeCounty);
        assertThat(rules()).doesNotContain("SP14");
        SpatialRelation wrong = SpatialElements.relation(land.geodata, "wrong", land.communes, land.communeId,
                land.counties, land.communeCounty, SpatialPredicate.WITHIN, SpatialAssignment.EXACTLY_ONE,
                UnmatchedPolicy.KEEP_NULL);
        assertThat(errors("SP14")).singleElement().satisfies(f -> {
            assertThat(f.element()).isSameAs(wrong);
            assertThat(f.message()).startsWith("land.geodata.wrong: the parent key land.geo.commune.county_id is no "
                    + "column of a table that holds the parent geometry");
        });
    }

    @Test
    void sp14_theSourceKeyOfAGridOrABandBelongsToTheSourceTable() {
        SpatialElements.grid(land.geodata, "cells", land.stations, land.stationId, land.quadkey(), 12, null);
        SpatialElements.band(land.geodata, "toTheRiver", land.lakes, land.lakeId, land.rivers, "1000");
        assertThat(rules()).doesNotContain("SP14");
        GridLevel wrong = SpatialElements.grid(land.geodata, "wrongCells", land.stations, land.communeId,
                land.quadkey(), 12, null);
        DistanceBand none = SpatialElements.band(land.geodata, "noKey", land.lakes, land.lakeId, land.rivers, "1000");
        none.setSourceKey(null);
        assertThat(errors("SP14")).extracting(Finding::message).hasSize(2)
                .anyMatch(m -> m.startsWith("land.geodata.wrongCells: the source key land.geo.commune.id is no column "
                        + "of a table that holds the source geometry"))
                .anyMatch(m -> m.equals("land.geodata.noKey names no source key"));
        assertThat(errors("SP14")).extracting(Finding::element).containsExactlyInAnyOrder(wrong, none);
    }

    /** A grid level without its points and a band without its source or reference name no geometry: SP14 for each. */
    @Test
    void sp14_aGridOrABandNamesItsGeometries() {
        GridLevel cells = SpatialElements.grid(land.geodata, "cells", land.stations, land.stationId, land.quadkey(),
                12, null);
        DistanceBand band = SpatialElements.band(land.geodata, "toTheRiver", land.lakes, land.lakeId, land.rivers,
                "1000");
        DistanceBand other = SpatialElements.band(land.geodata, "fromNowhere", land.lakes, land.lakeId, land.rivers,
                "1000");
        assertThat(rules()).doesNotContain("SP14");
        cells.setSource(null);
        band.setReference(null);
        other.setSource(null);
        other.setReference(null);
        assertThat(errors("SP14")).extracting(Finding::message).containsExactlyInAnyOrder(
                "land.geodata.cells names no points", "land.geodata.toTheRiver names no reference",
                "land.geodata.fromNowhere names no source", "land.geodata.fromNowhere names no reference");
        assertThat(errors("SP12")).as("no system is compared where a geometry is missing").isEmpty();
        assertThat(errors("SP18")).isEmpty();
    }

    @Test
    void sp25_aKeyNotDeclaredUniqueIsAWarningOfItsOwnCode() {
        Table parish = land.table("parish");
        Column parishId = Land.column(parish, "id", land.integer);
        GeometryFeature parishes = SpatialElements.feature(land.geodata, "parishes", GeometryKind.POLYGON,
                land.utm32, GeometryStorage.NATIVE, Land.column(parish, "shape", land.geometry));
        land.communesInCounties();
        SpatialElements.grid(land.geodata, "cells", land.stations, land.stationId, land.quadkey(), 12, null);
        assertThat(rules()).doesNotContain("SP25");
        SpatialRelation undeclared = SpatialElements.relation(land.geodata, "parishesInCounties", parishes, parishId,
                land.counties, land.countyId, SpatialPredicate.CENTROID_WITHIN, SpatialAssignment.EXACTLY_ONE,
                UnmatchedPolicy.KEEP_NULL);
        GeometryFeature centres = SpatialElements.feature(land.geodata, "centres", GeometryKind.POINT, land.utm32,
                GeometryStorage.NATIVE, Land.column(parish, "centre", land.geometry));
        GridLevel parishCells = SpatialElements.grid(land.geodata, "parishCells", centres, parishId,
                land.quadkey(), 12, null);
        assertThat(warnings("SP25")).extracting(Finding::element).containsExactlyInAnyOrder(undeclared, parishCells);
        assertThat(warnings("SP25")).extracting(Finding::message)
                .anyMatch(m -> m.equals("land.geodata.parishesInCounties: the child key land.geo.parish.id is no "
                        + "declared key of land.geo.parish; the rows of one key are the parts of one geometry, united "
                        + "first"))
                .anyMatch(m -> m.equals("land.geodata.parishCells: the source key land.geo.parish.id is no "
                        + "declared key of land.geo.parish; a resolver refuses a key held in several rows, a point "
                        + "has one row"));
        DistanceBand parishBand = SpatialElements.band(land.geodata, "parishesToTheRiver", parishes, parishId,
                land.rivers, "1000");
        parishBand.setWorkingSystem(land.laea);
        assertThat(warnings("SP25")).extracting(Finding::message).anyMatch(m -> m.equals(
                "land.geodata.parishesToTheRiver: the source key land.geo.parish.id is no declared key of "
                        + "land.geo.parish; the rows of one key are the parts of one source, the nearest counts"));
        assertThat(SpatialElements.isDeclaredKey(land.communeId)).as("a primary key of the one column").isTrue();
        assertThat(SpatialElements.isDeclaredKey(parishId)).as("no key declared").isFalse();
        assertThat(SpatialElements.isDeclaredKey(land.communeCounty)).as("a column beside the key").isFalse();
        assertThat(errors("SP25")).as("a schema read without declared keys still resolves").isEmpty();
        assertThat(rules()).as("an error and a warning never share a code").doesNotContain("SP14");
        assertThat(SpatialModelValidator.validate(land.model)).as("validate gives errors only").isEmpty();
    }

    @Test
    void sp26_aViewCannotStop() {
        SpatialRelation viewed = land.communesInCounties();
        viewed.setResultForm(ResultForm.VIEW);
        viewed.setTieBreak(TieBreak.LOWEST_PARENT_KEY);
        SpatialRelation table = SpatialElements.relation(land.geodata, "stopsAtATie", land.communes, land.communeId,
                land.counties, land.countyId, SpatialPredicate.CENTROID_WITHIN, SpatialAssignment.EXACTLY_ONE,
                UnmatchedPolicy.ERROR);
        assertThat(rules()).as("a view with a tie break, a table that stops").doesNotContain("SP26");
        viewed.setTieBreak(TieBreak.ERROR);
        table.setResultForm(ResultForm.VIEW);
        table.setTieBreak(TieBreak.LOWEST_PARENT_KEY);
        assertThat(errors("SP26")).extracting(Finding::message).hasSize(2)
                .anyMatch(m -> m.equals("land.geodata.communesInCounties writes a VIEW but assigns EXACTLY_ONE "
                        + "without the tie break LOWEST_PARENT_KEY; a view cannot stop at a tie, write a TABLE"))
                .anyMatch(m -> m.equals("land.geodata.stopsAtATie writes a VIEW but stops at a child without a "
                        + "parent (ERROR); a view cannot stop, write a TABLE"));
    }

    /**
     * A grid level and a band stop under ERROR at a point without a cell and a source without a class: a view of them
     * cannot, a table can; a view that keeps or drops them is no finding.
     */
    @Test
    void sp26_aViewOfAGridOrABandCannotStop() {
        GridLevel cells = SpatialElements.grid(land.geodata, "cells", land.stations, land.stationId,
                land.quadkey(), 12, null);
        DistanceBand band = SpatialElements.band(land.geodata, "toTheRiver", land.lakes, land.lakeId, land.rivers,
                "1000");
        cells.setResultForm(ResultForm.VIEW);
        band.setResultForm(ResultForm.VIEW);
        band.setUnmatched(UnmatchedPolicy.DROP);
        assertThat(errors("SP26")).as("views that keep or drop").isEmpty();
        cells.setResultForm(ResultForm.TABLE);
        cells.setUnmatched(UnmatchedPolicy.ERROR);
        assertThat(errors("SP26")).as("a table that stops").isEmpty();
        cells.setResultForm(ResultForm.VIEW);
        band.setUnmatched(UnmatchedPolicy.ERROR);
        assertThat(errors("SP26")).extracting(Finding::message).containsExactlyInAnyOrder(
                "land.geodata.cells writes a VIEW but stops at a point without a cell (ERROR); a view cannot stop, "
                        + "write a TABLE",
                "land.geodata.toTheRiver writes a VIEW but stops at a source without a class (ERROR); a view cannot "
                        + "stop, write a TABLE");
    }

    /**
     * A property of the data type Geometry holds a geometry in that type (NATIVE); held beside the text column of the
     * same meaning it is a holding of its own, with its own storage.
     */
    @Test
    void sp3_aPropertyOfTheTypeGeometryIsHeldNative() {
        Table border = land.table("border");
        Column text = Land.column(border, "wkt", land.varchar);
        org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class borders = CoreFactory.eINSTANCE.createClass();
        borders.setName("borders");
        land.geodata.getOwnedElement().add(borders);
        Attribute line = CoreFactory.eINSTANCE.createAttribute();
        line.setName("line");
        line.setType(SpatialElements.geometryType(land.geodata));
        borders.getFeature().add(line);
        GeometryFeature lines = SpatialElements.feature(land.geodata, "asText", GeometryKind.LINE, land.utm32,
                GeometryStorage.WKT, text);
        WholeHolding logical = SpatialElements.whole(lines, line, GeometryStorage.NATIVE);
        assertThat(rules()).as("the column holds text, the property the logical type").doesNotContain("SP3", "SP2");
        assertThat(SpatialElements.geometryType(land.geodata)).as("one type per namespace")
                .isSameAs(line.getType());
        logical.setStorage(GeometryStorage.WKT);
        assertThat(warnings("SP3")).singleElement().asString().contains("land.geodata.borders.line is of type "
                + "Geometry (geometry), which does not hold WKT");
    }

    @Test
    void sp16_theResultOfAGridOrABandHasItsShape() {
        Table cells = land.table("station_cell");
        Land.column(cells, "station_id", land.integer);
        Land.column(cells, "cell", land.varchar);
        GridLevel grid = SpatialElements.result(SpatialElements.grid(land.geodata, "cells", land.stations,
                land.stationId, land.quadkey(), 12, null), cells);
        Table classes = land.table("lake_band");
        Land.column(classes, "lake_id", land.integer);
        Land.column(classes, "band", land.integer);
        SpatialElements.result(SpatialElements.band(land.geodata, "toTheRiver", land.lakes, land.lakeId, land.rivers,
                "1000"), classes);
        assertThat(rules()).doesNotContain("SP16");
        Table numbered = land.table("station_cell_number");
        Land.column(numbered, "station_id", land.integer);
        Land.column(numbered, "cell", land.integer);
        grid.setResult(numbered);
        Land.column(classes, "distance", land.decimal);
        assertThat(errors("SP16")).extracting(Finding::message).hasSize(2)
                .anyMatch(m -> m.contains("the cell key land.geo.station_cell_number.cell is of type INTEGER, "
                        + "not text"))
                .anyMatch(m -> m.contains("the result land.geo.lake_band has 3 columns; it gives 2, source key and "
                        + "class"));
        grid.setSystem(land.h3());
        grid.setResolution(9);
        assertThat(errors("SP16")).as("the cell of a system that does not nest may be a number").hasSize(1);
    }

    /** The result of a grid level below others holds the source key and the cell of each level, the coarsest first. */
    @Test
    void sp16_theResultOfAChainHoldsACellPerLevel() {
        GridLevel coarse = SpatialElements.grid(land.geodata, "coarse", land.stations, land.stationId,
                land.geohash(), 4, null);
        GridLevel middle = SpatialElements.grid(land.geodata, "middle", land.stations, land.stationId,
                land.geohash(), 5, coarse);
        Table cells = land.table("station_cells");
        Land.column(cells, "station_id", land.integer);
        Land.column(cells, "cell_4", land.varchar);
        Land.column(cells, "cell_5", land.varchar);
        Land.column(cells, "cell_6", land.varchar);
        GridLevel fine = SpatialElements.result(SpatialElements.grid(land.geodata, "fine", land.stations,
                land.stationId, land.geohash(), 6, middle), cells);
        assertThat(SpatialElements.chain(fine)).containsExactly(coarse, middle, fine);
        assertThat(rules()).doesNotContain("SP16");
        middle.setResult(cells);
        assertThat(errors("SP16")).singleElement().asString()
                .contains("has 4 columns; it gives 3, source key and the cell key of 2 levels, the coarsest first");
        middle.setResult(null);
        Land.column(cells, "cell_count", land.integer);
        fine.setParent(null);
        assertThat(errors("SP16")).singleElement().asString().contains("has 5 columns; it gives 2");
        fine.setParent(middle);
        Table numbered = land.table("station_cells_numbered");
        Land.column(numbered, "station_id", land.integer);
        Land.column(numbered, "cell_4", land.varchar);
        Land.column(numbered, "cell_5", land.integer);
        Land.column(numbered, "cell_6", land.varchar);
        fine.setResult(numbered);
        assertThat(errors("SP16")).singleElement().asString()
                .contains("the cell key land.geo.station_cells_numbered.cell_5 is of type INTEGER, not text");
    }

    @Test
    void sp15_droppingTheChildrenWithoutAParentIsAWarning() {
        land.communesInCounties().setUnmatched(UnmatchedPolicy.ERROR);
        assertThat(rules()).doesNotContain("SP15");
        SpatialRelation dropped = SpatialElements.relation(land.geodata, "dropped", land.stations, land.stationId,
                land.states, land.stateId, SpatialPredicate.COVERED_BY, SpatialAssignment.EXACTLY_ONE,
                UnmatchedPolicy.DROP);
        dropped.setWorkingSystem(land.utm32);
        assertThat(warnings("SP15")).singleElement().asString().contains("drops the children without a parent");
    }

    /** A grid level that drops its points without a cell and a band its sources without a class: warnings. */
    @Test
    void sp15_droppingThePointsWithoutACellOrTheSourcesWithoutAClassIsAWarning() {
        GridLevel cells = SpatialElements.grid(land.geodata, "cells", land.stations, land.stationId,
                land.quadkey(), 12, null);
        DistanceBand band = SpatialElements.band(land.geodata, "toTheRiver", land.lakes, land.lakeId, land.rivers,
                "1000");
        assertThat(cells.getUnmatched()).as("a point without a cell keeps its row unless said otherwise")
                .isEqualTo(UnmatchedPolicy.KEEP_NULL);
        assertThat(band.getUnmatched()).isEqualTo(UnmatchedPolicy.KEEP_NULL);
        cells.setUnmatched(UnmatchedPolicy.ERROR);
        assertThat(rules()).doesNotContain("SP15");
        cells.setUnmatched(UnmatchedPolicy.DROP);
        band.setUnmatched(UnmatchedPolicy.DROP);
        assertThat(warnings("SP15")).extracting(Finding::message).containsExactlyInAnyOrder(
                "land.geodata.cells drops the points without a cell; facts that reach the cell through its result "
                        + "vanish from the sums",
                "land.geodata.toTheRiver drops the sources without a class; facts that reach the class through its "
                        + "result vanish from the sums");
        assertThat(errors("SP15")).as("a warning only").isEmpty();
    }

    @Test
    void sp16_theResultHasTheShapeOfTheAssignment() {
        Table pairs = land.table("commune_county");
        Land.column(pairs, "commune_id", land.integer);
        Land.column(pairs, "county_id", land.integer);
        SpatialElements.result(land.communesInCounties(), pairs);
        Table shares = land.table("lake_country");
        Land.column(shares, "lake_id", land.integer);
        Land.column(shares, "country_id", land.integer);
        Land.column(shares, "share", land.decimal);
        SpatialRelation lakes = SpatialElements.result(SpatialElements.workingSystem(land.lakesInCountries(),
                land.laea), shares);
        assertThat(rules()).doesNotContain("SP16");
        Table two = land.table("lake_country_two");
        Land.column(two, "lake_id", land.integer);
        Land.column(two, "country_id", land.integer);
        lakes.setResult(two);
        assertThat(errors("SP16")).singleElement().asString().contains("has 2 columns; ALL_WEIGHTED gives 3");
    }

    @Test
    void sp17_noPlaceIsItsOwnAncestor() {
        land.communesInCounties();
        SpatialElements.relation(land.geodata, "countiesInStates", land.counties, land.countyId, land.states,
                land.stateId, SpatialPredicate.CENTROID_WITHIN, SpatialAssignment.EXACTLY_ONE,
                UnmatchedPolicy.KEEP_NULL);
        assertThat(rules()).doesNotContain("SP17");
        SpatialRelation back = SpatialElements.relation(land.geodata, "statesInCommunes", land.states, land.stateId,
                land.communes, land.communeId, SpatialPredicate.CENTROID_WITHIN, SpatialAssignment.EXACTLY_ONE,
                UnmatchedPolicy.KEEP_NULL);
        assertThat(errors("SP17")).hasSize(3).extracting(Finding::element).contains(back);
    }

    @Test
    void sp18_aGridTakesPoints() {
        GridLevel regions = SpatialElements.grid(land.geodata, "regions", land.stations, land.stationId, land.h3(), 5,
                null);
        SpatialElements.grid(land.geodata, "cells", land.stations, land.stationId, land.h3(), 7, regions);
        assertThat(rules()).doesNotContain("SP18");
        GridLevel areas = SpatialElements.grid(land.geodata, "areas", land.communes, land.communeId, land.quadkey(),
                10, null);
        assertThat(errors("SP18")).singleElement().satisfies(f -> {
            assertThat(f.element()).isSameAs(areas);
            assertThat(f.message()).contains("POLYGON geometry land.geodata.communes into cells");
        });
    }

    /** A grid level names the system of its cells: none is SP18, one that does not resolve SP20. */
    @Test
    void sp18_aGridLevelNamesItsSystem() {
        GridLevel cells = SpatialElements.grid(land.geodata, "cells", land.stations, land.stationId, land.quadkey(),
                12, null);
        assertThat(rules()).doesNotContain("SP18");
        cells.setSystem(null);
        assertThat(errors("SP18")).singleElement().asString().contains("land.geodata.cells names no grid system");
        GridSystem elsewhere = SpatialFactory.eINSTANCE.createGridSystem();
        ((InternalEObject) elsewhere).eSetProxyURI(URI.createURI("other.xmi#//quadkey"));
        cells.setSystem(elsewhere);
        assertThat(rules()).doesNotContain("SP18");
        assertThat(warnings("SP20")).singleElement().asString().contains("the grid system does not resolve");
    }

    /** The resolution of a level lies within the resolutions its system states; the validator holds no table. */
    @Test
    void sp38_theResolutionLiesWithinTheSystem() {
        GridLevel cells = SpatialElements.grid(land.geodata, "cells", land.stations, land.stationId, land.h3(), 15,
                null);
        GridLevel tiles = SpatialElements.grid(land.geodata, "tiles", land.stations, land.stationId, land.quadkey(), 1,
                null);
        assertThat(rules()).doesNotContain("SP38");
        cells.setResolution(16);
        tiles.setResolution(0);
        assertThat(errors("SP38")).extracting(Finding::message).containsExactlyInAnyOrder(
                "land.geodata.cells has the resolution 16; h3 has 0 to 15",
                "land.geodata.tiles has the resolution 0; quadkey has 1 to 23");
        GridSystem coarse = SpatialElements.gridSystem(land.geodata, "https://grids.example/coarse", 2, 4, true);
        cells.setSystem(coarse);
        cells.setResolution(4);
        tiles.setResolution(23);
        assertThat(rules()).as("the range of a system the model states").doesNotContain("SP38");
        cells.setResolution(5);
        assertThat(errors("SP38")).singleElement().asString()
                .contains("has the resolution 5; https://grids.example/coarse has 2 to 4");
        assertThat(errors("SP18")).isEmpty();
    }

    @Test
    void sp39_theResolutionsOfAGridSystemRunInOrder() {
        GridSystem system = SpatialElements.gridSystem(land.geodata, "https://grids.example/flat", 0, 0, false);
        land.geohash();
        assertThat(rules()).as("one resolution only, from zero").doesNotContain("SP39");
        system.setMinResolution(3);
        system.setMaxResolution(2);
        assertThat(errors("SP39")).singleElement().satisfies(f -> {
            assertThat(f.element()).isSameAs(system);
            assertThat(f.message()).isEqualTo("land.geodata.https://grids.example/flat has the resolutions 3 to 2; "
                    + "they run from zero up, the minimum at most the maximum");
        });
        system.setMinResolution(-1);
        assertThat(errors("SP39")).singleElement().asString().contains("has the resolutions -1 to 2");
    }

    /** A resolver knows a grid system by its name: it has one, once in its model. */
    @Test
    void sp40_aGridSystemIsKnownOnceByItsIdentifier() {
        GridSystem quadkey = land.quadkey();
        assertThat(land.quadkey()).as("found again, never made twice").isSameAs(quadkey);
        land.geohash();
        assertThat(rules()).doesNotContain("SP40");
        GridSystem twice = SpatialFactory.eINSTANCE.createGridSystem();
        twice.setName("quadkey");
        twice.setMinResolution(1);
        twice.setMaxResolution(23);
        org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package more = CoreFactory.eINSTANCE.createPackage();
        more.setName("more");
        land.model.getOwnedElement().add(more);
        more.getOwnedElement().add(twice);
        GridSystem nameless = SpatialFactory.eINSTANCE.createGridSystem();
        land.geodata.getOwnedElement().add(nameless);
        assertThat(errors("SP40")).extracting(Finding::element).containsExactlyInAnyOrder(twice, nameless);
        assertThat(errors("SP40")).extracting(Finding::message)
                .anyMatch(m -> m.equals("land.more.quadkey has the identifier quadkey of land.geodata.quadkey; a "
                        + "grid system is made once in a model and found again by its identifier"))
                .anyMatch(m -> m.endsWith("has no identifier; a resolver knows a grid system by its name"));
    }

    /** The levels of one chain lie in one system. */
    @Test
    void sp41_aChainStaysInOneSystem() {
        GridLevel regions = SpatialElements.grid(land.geodata, "regions", land.stations, land.stationId, land.h3(), 5,
                null);
        SpatialElements.grid(land.geodata, "cells", land.stations, land.stationId, land.h3(), 7, regions);
        assertThat(rules()).doesNotContain("SP41");
        GridLevel hash = SpatialElements.grid(land.geodata, "hash", land.stations, land.stationId, land.geohash(), 6,
                regions);
        assertThat(errors("SP41")).singleElement().satisfies(f -> {
            assertThat(f.element()).isSameAs(hash);
            assertThat(f.message()).isEqualTo("land.geodata.hash of geohash lies below land.geodata.regions of h3; a "
                    + "level lies below one of its own system");
        });
        assertThat(errors("SP18")).isEmpty();
    }

    /** An unset resolution is none, not 0, which is a resolution of H3. */
    @Test
    void sp18_aGridLevelNamesItsResolution() {
        GridLevel regions = SpatialElements.grid(land.geodata, "regions", land.stations, land.stationId, land.h3(),
                0, null);
        assertThat(rules()).as("0 is a resolution of H3").doesNotContain("SP18");
        regions.unsetResolution();
        assertThat(errors("SP18")).singleElement().asString()
                .contains("land.geodata.regions names no resolution; h3 has 0 to 15");
    }

    /** The levels of one hierarchy put the same points, by the same key, into cells. */
    @Test
    void sp18_theLevelAboveTakesTheSamePoints() {
        GridLevel coarse = SpatialElements.grid(land.geodata, "coarse", land.stations, land.stationId,
                land.quadkey(), 10, null);
        SpatialElements.grid(land.geodata, "fine", land.stations, land.stationId, land.quadkey(), 12, coarse);
        assertThat(rules()).doesNotContain("SP18");
        GeometryFeature gauges = SpatialElements.feature(land.geodata, "gauges", GeometryKind.POINT, land.wgs84,
                GeometryStorage.NATIVE, Land.column(land.table("gauge"), "shape", land.geometry));
        Column gaugeId = land.key((Table) whole(gauges).getAttribute().eContainer());
        GridLevel other = SpatialElements.grid(land.geodata, "gaugeCells", gauges, gaugeId, land.quadkey(), 14,
                coarse);
        assertThat(errors("SP18")).singleElement().satisfies(f -> {
            assertThat(f.element()).isSameAs(other);
            assertThat(f.message()).contains("lies below land.geodata.coarse, which puts other points into cells");
        });
    }

    @Test
    void sp19_theBoundsAscend() {
        DistanceBand band = SpatialElements.band(land.geodata, "toTheRiver", land.lakes, land.lakeId, land.rivers,
                "1000", "5000", "20000");
        assertThat(rules()).doesNotContain("SP19");
        band.getBound().clear();
        band.getBound().addAll(List.of("5000", "1000", "0"));
        assertThat(errors("SP19")).extracting(Finding::message).hasSize(2)
                .anyMatch(m -> m.contains("the bound 1000 follows 5000"))
                .anyMatch(m -> m.contains("the bound '0' is no decimal number above zero"));
    }

    @Test
    void sp20_aReferenceThatDoesNotResolveIsAWarning() {
        SpatialRelation r = land.communesInCounties();
        assertThat(rules()).doesNotContain("SP20");
        GeometryFeature missing = SpatialFactory.eINSTANCE.createGeometryFeature();
        ((InternalEObject) missing).eSetProxyURI(URI.createURI("geodata.xmi#_nowhere"));
        r.setParent(missing);
        assertThat(warnings("SP20")).singleElement().asString().contains("the parent geometry does not resolve");
        assertThat(errors("SP5")).as("no rule reads what does not resolve").isEmpty();
    }

    @Test
    void sp21_anExplicitKeyBesideTheNearestParentIsAWarning() {
        SpatialElements.explicitKey(land.communesInCounties(), land.communeCounty);
        assertThat(rules()).doesNotContain("SP21");
        SpatialRelation nearest = SpatialElements.explicitKey(SpatialElements.relation(land.geodata, "nearest",
                land.communes, land.communeId, land.counties, land.countyId, SpatialPredicate.NEAREST,
                SpatialAssignment.EXACTLY_ONE, UnmatchedPolicy.KEEP_NULL), land.communeCounty);
        assertThat(warnings("SP21")).singleElement().extracting(Finding::element).isSameAs(nearest);
    }

    @Test
    void validateReturnsOnlyErrors() {
        land.communesInCounties().setUnmatched(UnmatchedPolicy.DROP);
        SpatialElements.feature(land.geodata, "unheld", GeometryKind.POLYGON, land.utm32);
        assertThat(SpatialModelValidator.validate(land.model)).singleElement().asString().startsWith("SP42 ");
    }

    @Test
    void sp22_aGridTakesSinglePoints() {
        land.stations.setMultiplicity(GeometryMultiplicity.SINGLE);
        SpatialElements.grid(land.geodata, "cells", land.stations, land.stationId, land.geohash(), 6, null);
        assertThat(rules()).doesNotContain("SP22");
        land.stations.setMultiplicity(GeometryMultiplicity.EITHER);
        assertThat(rules()).as("either: each row is checked by a resolver").doesNotContain("SP22");
        land.stations.setMultiplicity(GeometryMultiplicity.MULTI);
        assertThat(errors("SP22")).singleElement().asString().contains("puts the multi-points land.geodata.stations");
    }

    @Test
    void sp23_aHeightIsTheThirdAxis() {
        ReferenceSystem withHeight = SpatialElements.referenceSystem(land.geodata, "crs84h",
                ReferenceSystemAuthority.OGC, "CRS84h", ReferenceSystemKind.GEOGRAPHIC, AxisOrder.EAST_NORTH);
        withHeight.setAxes(3);
        GeometryFeature masts = SpatialElements.feature(land.geodata, "masts", GeometryKind.POINT, withHeight,
                GeometryStorage.NATIVE, Land.column(land.table("mast"), "position", land.geometry));
        masts.setHasHeight(true);
        assertThat(rules()).doesNotContain("SP23");
        masts.setReferenceSystem(land.wgs84);
        assertThat(errors("SP23")).singleElement().satisfies(f -> {
            assertThat(f.element()).isSameAs(masts);
            assertThat(f.message()).contains("holds a height in land.geodata.EPSG_4326 of 2 axes");
        });
    }

    /**
     * The epoch is a fact of the coordinates: two columns of one dynamic system may hold two epochs, each named by its
     * feature, and a feature in a dynamic system that names none is a warning.
     */
    @Test
    void sp24_aFeatureInADynamicSystemNamesItsEpoch() {
        ReferenceSystem moving = SpatialElements.epsg(land.geodata, 7931, ReferenceSystemKind.GEOGRAPHIC,
                AxisOrder.NORTH_EAST);
        moving.setAxes(3);
        moving.setDynamic(true);
        GeometryFeature survey2010 = SpatialElements.feature(land.geodata, "survey2010", GeometryKind.POINT, moving,
                GeometryStorage.NATIVE, Land.column(land.table("survey_2010"), "position", land.geometry));
        survey2010.setCoordinateEpoch("2010.5");
        GeometryFeature survey2024 = SpatialElements.feature(land.geodata, "survey2024", GeometryKind.POINT, moving,
                GeometryStorage.NATIVE, Land.column(land.table("survey_2024"), "position", land.geometry));
        survey2024.setCoordinateEpoch("2024.0");
        assertThat(rules()).as("one dynamic system, two epochs").doesNotContain("SP24", "SP32", "SP33");
        survey2024.setCoordinateEpoch(null);
        assertThat(warnings("SP24")).singleElement().satisfies(f -> {
            assertThat(f.element()).isSameAs(survey2024);
            assertThat(f.message()).isEqualTo("land.geodata.survey2024 is in the dynamic system EPSG 7931 but names no "
                    + "coordinate epoch; its coordinates cannot be compared with those of another epoch");
        });
        assertThat(errors("SP24")).as("a warning only").isEmpty();
    }

    /** RFC 7946: GeoJSON holds longitude first; a GeoJSON column stored north first is an error. */
    @Test
    void sp28_geoJsonHoldsItsCoordinatesEastFirst() {
        Table track = land.table("track");
        GeometryFeature tracks = SpatialElements.feature(land.geodata, "tracks", GeometryKind.LINE, land.wgs84,
                GeometryStorage.GEOJSON, Land.column(track, "course", land.varchar));
        GeometryFeature binary = SpatialElements.feature(land.geodata, "binaryTracks", GeometryKind.LINE, land.wgs84,
                GeometryStorage.WKB, Land.column(track, "course_wkb", land.blob));
        whole(binary).setAxisOrder(AxisOrder.NORTH_EAST);
        assertThat(rules()).as("the system defines latitude first; GeoJSON and a binary column may differ")
                .doesNotContain("SP28");
        whole(tracks).setAxisOrder(AxisOrder.NORTH_EAST);
        assertThat(errors("SP28")).singleElement().satisfies(f -> {
            assertThat(f.element()).isSameAs(tracks);
            assertThat(f.message()).isEqualTo("land.geodata.tracks is stored as GEOJSON with its coordinates "
                    + "NORTH_EAST; GeoJSON holds them east first");
        });
    }

    /** RFC 7946: GeoJSON is WGS 84 longitude and latitude; another system only by prior arrangement, a warning. */
    @Test
    void sp29_geoJsonHoldsLongitudeAndLatitudeOfWgs84() {
        Table track = land.table("track");
        SpatialElements.feature(land.geodata, "tracks", GeometryKind.LINE, land.wgs84, GeometryStorage.GEOJSON,
                Land.column(track, "course", land.varchar));
        SpatialElements.feature(land.geodata, "tracks84", GeometryKind.LINE, SpatialElements.crs84(land.geodata),
                GeometryStorage.GEOJSON, Land.column(track, "course84", land.varchar));
        assertThat(rules()).doesNotContain("SP29");
        GeometryFeature projected = SpatialElements.feature(land.geodata, "projectedTracks", GeometryKind.LINE,
                land.utm32, GeometryStorage.GEOJSON, Land.column(track, "course_utm", land.varchar));
        assertThat(warnings("SP29")).singleElement().satisfies(f -> {
            assertThat(f.element()).isSameAs(projected);
            assertThat(f.message()).contains("is stored as GEOJSON in EPSG 25832");
        });
        assertThat(errors("SP29")).as("a warning only").isEmpty();
    }

    /** A feature has a holding: none is SP42, as is a holding that names no attribute. */
    @Test
    void sp42_aFeatureIsHeldAndEachHoldingNamesItsAttributes() {
        assertThat(rules()).doesNotContain("SP42");
        GeometryFeature unheld = SpatialElements.feature(land.geodata, "unheld", GeometryKind.POINT, land.wgs84);
        GeometryFeature gauges = SpatialElements.feature(land.geodata, "gauges", GeometryKind.POINT, land.wgs84,
                GeometryStorage.NATIVE, Land.column(land.table("gauge"), "position", land.geometry));
        whole(gauges).setAttribute(null);
        SpatialElements.parts(gauges);
        CoordinatePart east = SpatialFactory.eINSTANCE.createCoordinatePart();
        east.setRole(Ordinate.EAST);
        SpatialElements.parts(gauges).getPart().add(east);
        ListHolding list = SpatialFactory.eINSTANCE.createListHolding();
        list.getOrdinate().addAll(List.of(Ordinate.EAST, Ordinate.NORTH));
        gauges.getHolding().add(list);
        assertThat(errors("SP42")).extracting(Finding::message).containsExactlyInAnyOrder(
                "land.geodata.unheld has no holding; nothing says where the geometry is held",
                "land.geodata.gauges: a whole holding names no attribute",
                "land.geodata.gauges: a holding in parts has no part",
                "land.geodata.gauges: the part EAST names no attribute",
                "land.geodata.gauges: a list names no attribute");
        assertThat(errors("SP42")).extracting(Finding::element).containsOnly(unheld, gauges);
    }

    /** Numbers hold a single point or the envelope of another geometry: a polygon of its own or a multi-point not. */
    @Test
    void sp43_numbersHoldASinglePointOrAnEnvelope() {
        Table parcel = land.table("parcel");
        GeometryFeature corners = SpatialElements.point(land.geodata, "corners", land.utm32,
                Land.column(parcel, "x", land.decimal), Land.column(parcel, "y", land.decimal));
        SpatialElements.envelope(land.geodata, "communeBoxes", land.communes, Land.column(land.commune, "min_x",
                land.decimal), Land.column(land.commune, "min_y", land.decimal), Land.column(land.commune, "max_x",
                        land.decimal), Land.column(land.commune, "max_y", land.decimal));
        assertThat(rules()).as("a point and an envelope").doesNotContain("SP43");
        GeometryFeature parcels = SpatialElements.feature(land.geodata, "parcels", GeometryKind.POLYGON, land.utm32);
        SpatialElements.pointParts(parcels, Land.column(parcel, "e", land.decimal),
                Land.column(parcel, "n", land.decimal));
        corners.setMultiplicity(GeometryMultiplicity.MULTI);
        assertThat(errors("SP43")).extracting(Finding::message).containsExactlyInAnyOrder(
                "land.geodata.corners is a MULTI point held as numbers in its parts; numbers hold a single point",
                "land.geodata.parcels is a POLYGON held as numbers in its parts; numbers hold a single point or the "
                        + "envelope of another geometry");
        assertThat(rules()).as("no shape, no roles to hold against it").doesNotContain("SP45");
    }

    @Test
    void sp44_eachRoleIsHeldOnce() {
        Table stop = land.table("stop");
        GeometryFeature stops = SpatialElements.feature(land.geodata, "stops", GeometryKind.POINT, land.wgs84);
        ListHolding list = SpatialElements.list(stops, Land.column(stop, "position", land.varchar), ",",
                Ordinate.NORTH, Ordinate.EAST);
        assertThat(rules()).doesNotContain("SP44");
        list.getOrdinate().add(Ordinate.NORTH);
        assertThat(errors("SP44")).singleElement().satisfies(f -> {
            assertThat(f.element()).isSameAs(stops);
            assertThat(f.message()).isEqualTo("land.geodata.stops holds NORTH more than once in its list; each role "
                    + "is held once");
        });
    }

    /** A point is held by EAST and NORTH, an envelope by its four edges; neither holds the roles of the other. */
    @Test
    void sp45_theRolesFitTheShape() {
        Table well = land.table("well");
        Column easting = Land.column(well, "easting", land.decimal);
        Column northing = Land.column(well, "northing", land.decimal);
        GeometryFeature wells = SpatialElements.point(land.geodata, "wells", land.utm32, easting, northing);
        GeometryFeature boxes = SpatialElements.derived(land.geodata, "countyBoxes", GeometryDerivation.ENVELOPE,
                land.counties);
        ListHolding bbox = SpatialElements.list(boxes, Land.column(land.county, "bbox", land.varchar), " ",
                Ordinate.MIN_EAST, Ordinate.MIN_NORTH, Ordinate.MAX_EAST, Ordinate.MAX_NORTH);
        assertThat(rules()).doesNotContain("SP45");
        ((PartsHolding) wells.getHolding().get(0)).getPart().get(1).setRole(Ordinate.MAX_NORTH);
        bbox.getOrdinate().set(3, Ordinate.NORTH);
        assertThat(errors("SP45")).extracting(Finding::message).containsExactlyInAnyOrder(
                "land.geodata.wells holds no NORTH in its parts; a point is held by EAST and NORTH",
                "land.geodata.wells holds MAX_NORTH in its parts, the edges of an envelope; a point has none",
                "land.geodata.countyBoxes holds no MAX_NORTH in its list; an envelope is held by MIN_EAST, MIN_NORTH, "
                        + "MAX_EAST and MAX_NORTH",
                "land.geodata.countyBoxes holds NORTH in its list, the axes of a point; an envelope has none");
    }

    /** A height is held exactly where the geometry has one: HEIGHT of a point, MIN_HEIGHT and MAX_HEIGHT of a box. */
    @Test
    void sp46_aHeightIsHeldExactlyWhereThereIsOne() {
        Table mast = land.table("mast");
        ReferenceSystem crs84h = SpatialElements.crs84h(land.geodata);
        GeometryFeature masts = SpatialElements.point(land.geodata, "masts", crs84h, Land.column(mast, "lon",
                land.decimal), Land.column(mast, "lat", land.decimal), Land.column(mast, "height", land.decimal));
        GeometryFeature volumes = SpatialElements.feature(land.geodata, "volumes", GeometryKind.POLYGON, crs84h,
                GeometryStorage.NATIVE, Land.column(mast, "volume", land.geometry));
        volumes.setHasHeight(true);
        GeometryFeature boxes = SpatialElements.derived(land.geodata, "volumeBoxes", GeometryDerivation.ENVELOPE,
                volumes);
        boxes.setHasHeight(true);
        PartsHolding box = SpatialElements.parts(boxes);
        for (Ordinate role : List.of(Ordinate.MIN_EAST, Ordinate.MIN_NORTH, Ordinate.MAX_EAST, Ordinate.MAX_NORTH,
                Ordinate.MIN_HEIGHT, Ordinate.MAX_HEIGHT)) {
            SpatialElements.part(box, role, Land.column(mast, role.getName().toLowerCase(java.util.Locale.ROOT),
                    land.decimal));
        }
        assertThat(rules()).as("a point and an envelope with a height").doesNotContain("SP46");
        masts.setHasHeight(false);
        box.getPart().remove(5);
        assertThat(errors("SP46")).extracting(Finding::message).containsExactlyInAnyOrder(
                "land.geodata.masts has no height but holds HEIGHT in its parts; a height needs hasHeight and a "
                        + "system of three axes",
                "land.geodata.volumeBoxes has a height but holds no MAX_HEIGHT in its parts");
    }

    /**
     * The attribute of a part is a number; a list's is text where it names a separator, else several numbers; a type
     * that is not known, or a number without a stated multiplicity, is no finding.
     */
    @Test
    void sp47_theAttributesOfNumbersAreNumeric() {
        Table stop = land.table("stop");
        Column text = Land.column(stop, "position", land.varchar);
        SQLSimpleType doubles = RelationalFactory.eINSTANCE.createSQLSimpleType();
        doubles.setName("DOUBLE[]");
        land.geo.getOwnedElement().add(doubles);
        Column array = Land.column(stop, "location", doubles);
        Column many = Land.column(stop, "coordinates", land.decimal);
        many.setMultiplicity(multiplicity(2, 2));
        Column x = Land.column(stop, "x", land.decimal);
        Column y = Land.column(stop, "y", land.decimal);
        GeometryFeature stops = SpatialElements.point(land.geodata, "stops", land.wgs84, x, y);
        SpatialElements.list(stops, text, ",", Ordinate.NORTH, Ordinate.EAST);
        SpatialElements.list(stops, array, null, Ordinate.EAST, Ordinate.NORTH);
        ListHolding values = SpatialElements.list(stops, many, null, Ordinate.EAST, Ordinate.NORTH);
        assertThat(rules()).doesNotContain("SP47");
        x.setType(land.varchar);
        ((ListHolding) stops.getHolding().get(1)).setSeparator(null);
        ((ListHolding) stops.getHolding().get(2)).setSeparator(";");
        many.setMultiplicity(multiplicity(0, 1));
        assertThat(warnings("SP47")).extracting(Finding::message).containsExactlyInAnyOrder(
                "land.geodata.stops: the part EAST land.geo.stop.x is of type VARCHAR (text), not a number",
                "land.geodata.stops: the list land.geo.stop.position is of type VARCHAR (text); a list in a text "
                        + "names its separator",
                "land.geodata.stops: the list land.geo.stop.location is of type DOUBLE[] (array); a list with a "
                        + "separator is text",
                "land.geodata.stops: the list land.geo.stop.coordinates is of type DECIMAL (number), one number; "
                        + "several numbers without a separator are an array or a multi-valued attribute");
        assertThat(errors("SP47")).as("a warning only").isEmpty();
        many.setMultiplicity(null);
        assertThat(warnings("SP47")).as("a number without a stated multiplicity is not judged")
                .noneMatch(f -> f.message().contains("coordinates"));
        assertThat(values.getAttribute()).isSameAs(many);
    }

    /** A holding in parts is one row of one holder: its attributes lie in one table; a whole one may lie in two. */
    @Test
    void sp48_thePartsLieInOneClassifier() {
        Table station = land.table("weather_station");
        Table archive = land.table("weather_station_archive");
        Column lat = Land.column(station, "lat", land.decimal);
        GeometryFeature stations = SpatialElements.point(land.geodata, "weatherStations", land.wgs84,
                Land.column(station, "lon", land.decimal), lat);
        SpatialElements.feature(land.geodata, "areas", GeometryKind.POLYGON, land.laea, GeometryStorage.NATIVE,
                Land.column(station, "area", land.geometry), Land.column(archive, "area", land.geometry));
        assertThat(rules()).doesNotContain("SP48");
        archive.getFeature().add(lat);
        assertThat(errors("SP48")).singleElement().satisfies(f -> {
            assertThat(f.element()).isSameAs(stations);
            assertThat(f.message()).isEqualTo("land.geodata.weatherStations: the parts lie in 2 classifiers, "
                    + "land.geo.weather_station, land.geo.weather_station_archive; a holding in parts is one row of "
                    + "one holder");
        });
    }

    @Test
    void sp49_aDerivationAndWhatItIsDerivedFromGoTogether() {
        GeometryFeature centres = SpatialElements.derived(land.geodata, "communeCentres", GeometryDerivation.CENTROID,
                land.communes);
        SpatialElements.whole(centres, Land.column(land.commune, "centre", land.geometry), GeometryStorage.NATIVE);
        assertThat(rules()).doesNotContain("SP49");
        centres.setOf(null);
        land.stations.setOf(land.communes);
        assertThat(errors("SP49")).extracting(Finding::message).containsExactlyInAnyOrder(
                "land.geodata.communeCentres is the CENTROID of nothing; it names no geometry it is derived from",
                "land.geodata.stations is derived from land.geodata.communes but names no derivation");
    }

    @Test
    void sp50_noGeometryIsDerivedFromItself() {
        GeometryFeature labels = SpatialElements.derived(land.geodata, "labels", GeometryDerivation.POINT_ON_SURFACE,
                land.communes);
        SpatialElements.whole(labels, Land.column(land.commune, "label_point", land.geometry), GeometryStorage.WKB)
                .setAttribute(Land.column(land.commune, "label_point_wkb", land.blob));
        assertThat(rules()).doesNotContain("SP50");
        labels.setOf(labels);
        assertThat(errors("SP50")).singleElement().asString()
                .contains("land.geodata.labels is derived from itself; a derived geometry comes from another one");
        GeometryFeature boxes = SpatialElements.derived(land.geodata, "boxes", GeometryDerivation.ENVELOPE,
                land.communes);
        SpatialElements.whole(boxes, Land.column(land.commune, "box", land.geometry), GeometryStorage.NATIVE);
        labels.setOf(boxes);
        boxes.setOf(labels);
        boxes.setKind(GeometryKind.POLYGON);
        assertThat(errors("SP50")).extracting(Finding::message).containsExactlyInAnyOrder(
                "land.geodata.labels is derived from itself through land.geodata.boxes; a derived geometry comes from "
                        + "another one",
                "land.geodata.boxes is derived from itself through land.geodata.labels; a derived geometry comes from "
                        + "another one");
    }

    @Test
    void sp51_aDerivedGeometryKeepsTheReferenceSystem() {
        GeometryFeature boxes = SpatialElements.envelope(land.geodata, "communeBoxes", land.communes,
                Land.column(land.commune, "min_x", land.decimal), Land.column(land.commune, "min_y", land.decimal),
                Land.column(land.commune, "max_x", land.decimal), Land.column(land.commune, "max_y", land.decimal));
        boxes.setReferenceSystem(SpatialElements.referenceSystem(land.geodata, "utm32Again",
                ReferenceSystemAuthority.EPSG, "25832", ReferenceSystemKind.PROJECTED, AxisOrder.EAST_NORTH));
        assertThat(rules()).as("one URI is one system").doesNotContain("SP51");
        boxes.setReferenceSystem(land.wgs84);
        assertThat(errors("SP51")).singleElement().asString().contains("land.geodata.communeBoxes is the ENVELOPE "
                + "of land.geodata.communes in EPSG 25832 but is in EPSG 4326; a derived geometry keeps the system");
    }

    @Test
    void sp52_theKindFollowsTheDerivation() {
        GeometryFeature extents = SpatialElements.derived(land.geodata, "extents", GeometryDerivation.ENVELOPE,
                land.states);
        SpatialElements.whole(extents, Land.column(land.state, "extent", land.geometry), GeometryStorage.NATIVE);
        GeometryFeature centres = SpatialElements.derived(land.geodata, "centres", GeometryDerivation.CENTROID,
                land.states);
        SpatialElements.whole(centres, Land.column(land.state, "centre", land.geometry), GeometryStorage.NATIVE);
        assertThat(rules()).doesNotContain("SP52");
        extents.setKind(GeometryKind.POINT);
        centres.setMultiplicity(GeometryMultiplicity.MULTI);
        assertThat(errors("SP52")).extracting(Finding::message).containsExactlyInAnyOrder(
                "land.geodata.extents is the ENVELOPE of land.geodata.states but of kind POINT; an envelope is a "
                        + "single POLYGON",
                "land.geodata.centres is the CENTROID of land.geodata.states but MULTI; a centroid is a single POINT");
    }

    /**
     * Two corners of one geometry in one table give its envelope only where they are opposite; corners of one geometry
     * in two tables are not compared.
     */
    @Test
    void sp53_twoCornersOfOneGeometryAreOpposite() {
        Table sheet = land.table("sheet");
        GeometryFeature sheets = SpatialElements.feature(land.geodata, "sheets", GeometryKind.POLYGON, land.utm32,
                GeometryStorage.NATIVE, Land.column(sheet, "area", land.geometry));
        GeometryFeature upperLeft = SpatialElements.derived(land.geodata, "upperLeft",
                GeometryDerivation.CORNER_NORTH_WEST, sheets);
        SpatialElements.whole(upperLeft, Land.column(sheet, "upper_left", land.geometry), GeometryStorage.NATIVE);
        GeometryFeature lowerRight = SpatialElements.derived(land.geodata, "lowerRight",
                GeometryDerivation.CORNER_SOUTH_EAST, sheets);
        SpatialElements.whole(lowerRight, Land.column(sheet, "lower_right", land.geometry), GeometryStorage.NATIVE);
        GeometryFeature printed = SpatialElements.derived(land.geodata, "printed",
                GeometryDerivation.CORNER_SOUTH_WEST, sheets);
        SpatialElements.whole(printed, Land.column(land.table("print"), "origin", land.geometry),
                GeometryStorage.NATIVE);
        assertThat(rules()).as("opposite in one table; another corner in another table").doesNotContain("SP53");
        lowerRight.setDerivation(GeometryDerivation.CORNER_NORTH_EAST);
        assertThat(errors("SP53")).singleElement().satisfies(f -> {
            assertThat(f.element()).isSameAs(lowerRight);
            assertThat(f.message()).isEqualTo("land.geodata.lowerRight is the CORNER_NORTH_EAST of "
                    + "land.geodata.sheets beside its CORNER_NORTH_WEST land.geodata.upperLeft in land.geo.sheet; two "
                    + "corners of one geometry in one holder are opposite: south-west and north-east, or north-west "
                    + "and south-east");
        });
    }

    /** The stored axis order is a fact of a whole holding; numbers have none, and GeoJSON rules do not read them. */
    @Test
    void sp28_sp29_readOnlyAWholeHolding() {
        Table stop = land.table("stop");
        GeometryFeature stops = SpatialElements.point(land.geodata, "stops", land.utm32,
                Land.column(stop, "easting", land.decimal), Land.column(stop, "northing", land.decimal));
        assertThat(rules()).as("numbers in a projected system, no GeoJSON").doesNotContain("SP28", "SP29");
        SpatialElements.whole(stops, Land.column(stop, "geojson", land.varchar), GeometryStorage.GEOJSON);
        assertThat(warnings("SP29")).singleElement().asString().contains("stops is stored as GEOJSON in EPSG 25832");
    }

    private static Multiplicity multiplicity(long lower, long upper) {
        Multiplicity multiplicity = CoreFactory.eINSTANCE.createMultiplicity();
        MultiplicityRange range = CoreFactory.eINSTANCE.createMultiplicityRange();
        range.setLower(lower);
        range.setUpper(upper);
        multiplicity.getRange().add(range);
        return multiplicity;
    }

    /** The first holding of {@code feature}, a whole one. */
    private static WholeHolding whole(GeometryFeature feature) {
        return (WholeHolding) feature.getHolding().get(0);
    }

    private List<String> rules() {
        return SpatialModelValidator.check(land.model).stream().map(Finding::rule).toList();
    }

    private List<Finding> errors(String rule) {
        return SpatialModelValidator.check(land.model).stream()
                .filter(f -> f.severity() == Severity.ERROR && f.rule().equals(rule)).toList();
    }

    private List<Finding> warnings(String rule) {
        return SpatialModelValidator.check(land.model).stream()
                .filter(f -> f.severity() == Severity.WARNING && f.rule().equals(rule)).toList();
    }

    /** Each rule of the validator is stated once, of its module, an error or a warning: never both under one code. */
    @Test
    void everyRuleIsStatedOnceWithOneSeverity() {
        assertThat(SpatialModelValidator.RULES).as("the rules; SP1, SP4 and SP13 are retired").extracting(Rule::code)
                .doesNotHaveDuplicates()
                .containsExactly("SP2", "SP3", "SP5", "SP6", "SP7", "SP8", "SP9", "SP10", "SP11", "SP12", "SP14",
                        "SP15", "SP16", "SP17", "SP18", "SP19", "SP20", "SP21", "SP22", "SP23", "SP24", "SP25", "SP26",
                        "SP27", "SP28", "SP29", "SP30", "SP31", "SP32", "SP33", "SP34", "SP35", "SP36", "SP37",
                        "SP38", "SP39", "SP40", "SP41", "SP42", "SP43", "SP44", "SP45", "SP46", "SP47", "SP48", "SP49",
                        "SP50", "SP51", "SP52", "SP53");
        assertThat(SpatialModelValidator.RULES).as("the rules").allSatisfy(rule -> assertThat(rule.module())
                .isEqualTo(SpatialModelValidator.MODULE));
        assertThat(SpatialModelValidator.RULES.stream().filter(rule -> rule.severity() == Finding.Severity.WARNING)
                .map(Rule::code)).as("the warnings")
                .containsExactly("SP3", "SP15", "SP20", "SP21", "SP24", "SP25", "SP27", "SP29", "SP47");
    }
}
