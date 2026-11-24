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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.FeatureCollection;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.examples.City;
import org.eclipse.daanse.cwm.model.daanse.spatial.AxisOrder;
import org.eclipse.daanse.cwm.model.daanse.spatial.PartsHolding;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystem;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystemKind;
import org.eclipse.daanse.cwm.model.daanse.spatial.WholeHolding;
import org.eclipse.daanse.cwm.model.daanse.spatial.util.SpatialElements;
import org.junit.jupiter.api.Test;

/** The helpers of the module: creation, the reference systems of a collection, the families of types. */
class FeatureHelpersTest {

    private final City city = new City();

    @Test
    void aCollectionOwnsItsPropertiesInOrder() {
        assertThat(city.districts.getNamespace()).isSameAs(city.features);
        assertThat(FeatureCollections.properties(city.districts)).extracting(p -> p.getName()).containsExactly(
                "district_id", "label", "residents", "area_km2", "valid_from", "valid_to", "outline");
        assertThat(FeatureCollections.property(city.schools, "kind")).containsSame(city.kind);
        assertThat(FeatureProperties.required(city.districtId)).isTrue();
        assertThat(FeatureProperties.required(city.districtLabel)).isFalse();
        assertThat(FeatureProperties.isGeometry(city.outline)).isTrue();
        assertThat(city.outline.getType()).isSameAs(SpatialElements.geometryType(city.types));
        assertThat(SpatialElements.features(city.outline)).as("a holding of the feature names the property")
                .containsExactly(city.districtShape);
        assertThat(FeatureCollections.geometryFeature(city.outline)).containsSame(city.districtShape);
        assertThat(FeatureProperties.filterOnly(city.pupils).isReturnable()).isFalse();
    }

    /**
     * The holding a server reads a geometry from: a whole one before numbers, and only one in the properties of the
     * collection; the envelope kept beside the primary geometry.
     */
    @Test
    void theHoldingOfTheGeometryAndItsEnvelope() {
        assertThat(FeatureCollections.holding(city.schoolPoint, city.schools)).get().isInstanceOf(WholeHolding.class);
        assertThat(FeatureCollections.holding(city.stopPoint, city.stops)).get()
                .isInstanceOf(PartsHolding.class);
        assertThat(FeatureCollections.holding(city.treePoint, city.trees)).as("the property before the numbers")
                .get().isSameAs(city.treePoint.getHolding().get(0));
        assertThat(FeatureCollections.holding(city.schoolPoint, city.districts)).isEmpty();
        assertThat(FeatureCollections.envelope(city.stops)).contains(city.stopBox);
        assertThat(FeatureCollections.envelope(city.schools)).isEmpty();
        assertThat(FeatureProperties.isGeometry(city.east)).as("a number of a geometry is a number").isFalse();
        assertThat(FeatureProperties.isGeometry(city.position)).isTrue();
        assertThat(FeatureCollections.geometryFeature(city.longitude)).contains(city.treePoint);
        assertThat(FeatureCollections.crs(city.stops)).as("stored in the default").containsExactly(
                FeatureCollections.CRS84);
    }

    /** The list crs: the default, the storage system, then the offered ones; the storage system as a URI. */
    @Test
    void theReferenceSystemsOfACollection() {
        assertThat(FeatureCollections.crs(city.districts)).containsExactly(FeatureCollections.CRS84,
                "http://www.opengis.net/def/crs/EPSG/0/25832", "http://www.opengis.net/def/crs/EPSG/0/4258");
        assertThat(FeatureCollections.crs(city.schools)).containsExactly(FeatureCollections.CRS84,
                "http://www.opengis.net/def/crs/EPSG/0/25832");
        assertThat(FeatureCollections.storageCrs(city.schools)).contains("http://www.opengis.net/def/crs/EPSG/0/25832");
        assertThat(FeatureCollections.storageCrsCoordinateEpoch(city.schools)).as("a static system").isEmpty();
        FeatureCollection none = FeatureCollections.create(city.features, "none");
        assertThat(FeatureCollections.crs(none)).isEmpty();
        assertThat(FeatureCollections.storageCrs(none)).isEmpty();
    }

    /**
     * A geometry with a height: the default is CRS84h; in a dynamic storage system the epoch its geometry feature names
     * for the coordinates.
     */
    @Test
    void aHeightAndADynamicSystem() {
        ReferenceSystem moving = SpatialElements.epsg(city.geodata, 7931, ReferenceSystemKind.GEOGRAPHIC,
                AxisOrder.NORTH_EAST);
        moving.setAxes(3);
        moving.setDynamic(true);
        city.schoolPoint.setCoordinateEpoch("2024.5");
        city.schoolPoint.setReferenceSystem(moving);
        city.schoolPoint.setHasHeight(true);
        assertThat(FeatureCollections.crs(city.schools)).containsExactly(FeatureCollections.CRS84H,
                "http://www.opengis.net/def/crs/EPSG/0/7931");
        assertThat(FeatureCollections.storageCrsCoordinateEpoch(city.schools)).contains("2024.5");
    }

    /**
     * The epoch belongs to the coordinates (OGC Topic 2): two collections stored in one dynamic system at two epochs
     * give one storage system and two epochs.
     */
    @Test
    void twoCollectionsOfOneDynamicSystemHoldTwoEpochs() {
        ReferenceSystem moving = SpatialElements.epsg(city.geodata, 7912, ReferenceSystemKind.GEOGRAPHIC,
                AxisOrder.NORTH_EAST);
        moving.setAxes(3);
        moving.setDynamic(true);
        city.districtShape.setReferenceSystem(moving);
        city.districtShape.setHasHeight(true);
        city.districtShape.setCoordinateEpoch("2010.5");
        city.schoolPoint.setReferenceSystem(moving);
        city.schoolPoint.setHasHeight(true);
        city.schoolPoint.setCoordinateEpoch("2024.5");
        assertThat(FeatureCollections.storageCrs(city.districts))
                .isEqualTo(FeatureCollections.storageCrs(city.schools));
        assertThat(FeatureCollections.storageCrsCoordinateEpoch(city.districts)).contains("2010.5");
        assertThat(FeatureCollections.storageCrsCoordinateEpoch(city.schools)).contains("2024.5");
    }

    /** The families of types by name and by JDBC code, and what the queryables make of them. */
    @Test
    void theFamilyOfAType() {
        Map<String, TypeFamily> byName = new LinkedHashMap<>();
        for (String name : List.of("String", "VARCHAR(40)", "Integer", "BIGINT", "Float", "DECIMAL(10,2)",
                "Boolean", "Date", "Time", "Timestamp", "TIMESTAMP WITH TIME ZONE", "EString", "Geometry", "Blob")) {
            DataType type = CoreFactory.eINSTANCE.createDataType();
            type.setName(name);
            byName.put(name, TypeFamily.of(type));
        }
        assertThat(byName).containsExactlyEntriesOf(ordered("String", TypeFamily.STRING, "VARCHAR(40)",
                TypeFamily.STRING, "Integer", TypeFamily.INTEGER, "BIGINT", TypeFamily.INTEGER, "Float",
                TypeFamily.NUMBER, "DECIMAL(10,2)", TypeFamily.NUMBER, "Boolean", TypeFamily.BOOLEAN, "Date",
                TypeFamily.DATE, "Time", TypeFamily.TIME, "Timestamp", TypeFamily.TIMESTAMP,
                "TIMESTAMP WITH TIME ZONE", TypeFamily.TIMESTAMP_WITH_ZONE, "EString", TypeFamily.STRING, "Geometry",
                TypeFamily.GEOMETRY, "Blob", TypeFamily.OTHER));
        assertThat(TypeFamily.of(SpatialElements.geometryType(city.types))).as("the logical type of a geometry")
                .isEqualTo(TypeFamily.GEOMETRY);
        SQLSimpleType coded = RelationalFactory.eINSTANCE.createSQLSimpleType();
        coded.setName("INT4");
        coded.setTypeNumber(2014);
        assertThat(TypeFamily.of(coded)).as("the JDBC code decides before the name")
                .isEqualTo(TypeFamily.TIMESTAMP_WITH_ZONE);
        assertThat(TypeFamily.TIMESTAMP.jsonType()).contains("string");
        assertThat(TypeFamily.TIMESTAMP.format()).contains("date-time");
        assertThat(TypeFamily.GEOMETRY.jsonType()).isEmpty();
        assertThat(TypeFamily.DATE.temporal()).isTrue();
        assertThat(TypeFamily.TIME.temporal()).as("a time of day is no time of a feature").isFalse();
    }

    private static Map<String, TypeFamily> ordered(Object... pairs) {
        Map<String, TypeFamily> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (TypeFamily) pairs[i + 1]);
        }
        return map;
    }
}
