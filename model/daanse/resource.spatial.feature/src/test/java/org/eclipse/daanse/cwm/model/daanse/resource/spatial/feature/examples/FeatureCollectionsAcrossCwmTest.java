/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.examples;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.FeatureCollection;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.FeatureProperty;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.util.FeatureCollections;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.util.FeatureModelValidator;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.util.FeatureProperties;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.util.Queryables;
import org.eclipse.daanse.cwm.model.daanse.spatial.AxisOrder;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryFeature;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryHolding;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryKind;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryStorage;
import org.eclipse.daanse.cwm.model.daanse.spatial.Ordinate;
import org.eclipse.daanse.cwm.model.daanse.spatial.PartsHolding;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystem;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystemKind;
import org.eclipse.daanse.cwm.model.daanse.spatial.WholeHolding;
import org.eclipse.daanse.cwm.model.daanse.spatial.util.SpatialElements;
import org.eclipse.daanse.cwm.model.daanse.spatial.util.SpatialModelValidator;
import org.junit.jupiter.api.Test;

/**
 * Feature collections across CWM: a collection over a relational table that holds its points as longitude and
 * latitude, over a class of a logical model with a geometry property, a collection whose geometry is held twice, and
 * what a reader asks of any collection: its geometry, the holdings of it within the collection, its envelope and its
 * reference system. The rows of a collection come from elsewhere; a mapping model, not shown here, binds a property to
 * its column. What the table and the collection hold of one geometry is said by one geometry feature.
 */
class FeatureCollectionsAcrossCwmTest {

    private static final RelationalFactory R = RelationalFactory.eINSTANCE;

    private final Model model = owned(null, "mobility", CoreFactory.eINSTANCE.createModel());
    private final Schema db = owned(model, "public", R.createSchema());
    private final SQLSimpleType varchar = sqlType("VARCHAR", 12);
    private final SQLSimpleType doublePrecision = sqlType("DOUBLE PRECISION", 8);
    private final Package types = owned(model, "types", CoreFactory.eINSTANCE.createPackage());
    private final DataType string = owned(types, "String", CoreFactory.eINSTANCE.createDataType());
    private final DataType decimal = owned(types, "Float", CoreFactory.eINSTANCE.createDataType());
    private final Package geodata = owned(model, "geodata", CoreFactory.eINSTANCE.createPackage());
    private final ReferenceSystem crs84 = SpatialElements.crs84(geodata);
    private final ReferenceSystem utm32 = SpatialElements.epsg(geodata, 25832, ReferenceSystemKind.PROJECTED,
            AxisOrder.EAST_NORTH);
    private final Package features = owned(model, "features", CoreFactory.eINSTANCE.createPackage());

    /**
     * The charging stations for electric cars stand in {@code charging_station(station_id, operator, lon, lat)}; the
     * collection {@code charging_stations} offers them with the same names. One point, two holdings in parts: the
     * columns of the table and the properties of the collection.
     */
    @Test
    void aCollectionOverATableWithLongitudeAndLatitude() {
        Table table = owned(db, "charging_station", R.createTable());
        column(table, "station_id", varchar);
        column(table, "operator", varchar);
        Column lonColumn = column(table, "lon", doublePrecision);
        Column latColumn = column(table, "lat", doublePrecision);
        GeometryFeature chargers = SpatialElements.point(geodata, "chargingStations", crs84, lonColumn, latColumn);
        FeatureCollection stations = FeatureCollections.create(features, "charging_stations");
        FeatureProperty stationId = FeatureProperties.required(stations, "station_id", string);
        FeatureProperties.create(stations, "operator", string);
        FeatureProperty lon = FeatureProperties.create(stations, "lon", decimal);
        FeatureProperty lat = FeatureProperties.create(stations, "lat", decimal);
        SpatialElements.pointParts(chargers, lon, lat);
        FeatureCollections.key(stations, stationId);
        FeatureCollections.geometry(stations, chargers);

        // which properties of the collection hold the point, and which is the longitude
        assertThat(FeatureCollections.holdings(chargers, stations)).singleElement()
                .isInstanceOfSatisfying(PartsHolding.class, parts -> {
                    assertThat(SpatialElements.attribute(parts, Ordinate.EAST)).contains(lon);
                    assertThat(SpatialElements.attribute(parts, Ordinate.NORTH)).contains(lat);
                });
        // which column of the table holds the longitude: the other holding of the same point
        PartsHolding inTable = (PartsHolding) SpatialElements.holdings(lonColumn).get(0);
        assertThat(SpatialElements.attribute(inTable, Ordinate.EAST)).contains(lonColumn);
        assertThat(SpatialElements.features(lon)).isEqualTo(SpatialElements.features(lonColumn));
        // the numbers are queryable numbers, the default system is the storage system
        assertThat(Queryables.property(lon)).isEqualTo(Map.of("title", "lon", "type", "number"));
        assertThat(FeatureCollections.crs(stations)).containsExactly(FeatureCollections.CRS84);
        assertThat(FeatureModelValidator.check(model)).isEmpty();
        assertThat(SpatialModelValidator.check(model)).isEmpty();
    }

    /**
     * A logical model of a park department has a class {@code Playground} whose property {@code area} is of the type
     * Geometry; the collection {@code playgrounds} offers the same property. One polygon, two whole holdings.
     */
    @Test
    void aCollectionOverAClassWithAGeometryProperty() {
        Package parks = owned(model, "parks", CoreFactory.eINSTANCE.createPackage());
        org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class playground = owned(parks, "Playground",
                CoreFactory.eINSTANCE.createClass());
        attribute(playground, "name", string);
        Attribute classArea = attribute(playground, "area", SpatialElements.geometryType(types));
        GeometryFeature playgroundAreas = SpatialElements.feature(geodata, "playgroundAreas", GeometryKind.POLYGON,
                utm32, GeometryStorage.NATIVE, classArea);
        FeatureCollection playgrounds = FeatureCollections.create(features, "playgrounds");
        FeatureCollections.key(playgrounds, FeatureProperties.required(playgrounds, "playground_id", string));
        FeatureProperties.create(playgrounds, "name", string);
        FeatureProperty area = FeatureProperties.geometry(playgrounds, "area", types, playgroundAreas);
        FeatureCollections.geometry(playgrounds, playgroundAreas);

        // the holding within the collection is the property, not the attribute of the class
        assertThat(FeatureCollections.holding(playgroundAreas, playgrounds)).get().isInstanceOfSatisfying(
                WholeHolding.class, whole -> assertThat(whole.getAttribute()).isSameAs(area));
        assertThat(playgroundAreas.getHolding()).hasSize(2);
        // the queryable of a polygon, single or multi, and the storage system in metres
        assertThat(Queryables.property(area)).containsEntry("format", "geometry-polygon-or-multipolygon");
        assertThat(FeatureCollections.storageSystem(playgrounds)).containsSame(utm32);
        assertThat(FeatureModelValidator.check(model)).isEmpty();
        assertThat(SpatialModelValidator.check(model)).isEmpty();
    }

    /**
     * Bike racks are offered with a geometry {@code location} and, for clients without a geometry library, the same
     * point as {@code lon} and {@code lat}: one geometry held twice within the collection.
     */
    @Test
    void aCollectionWhoseGeometryIsHeldTwice() {
        FeatureCollection bikeRacks = FeatureCollections.create(features, "bike_racks");
        FeatureCollections.key(bikeRacks, FeatureProperties.required(bikeRacks, "rack_id", string));
        FeatureProperty capacity = FeatureProperties.create(bikeRacks, "capacity", decimal);
        FeatureProperty lon = FeatureProperties.create(bikeRacks, "lon", decimal);
        FeatureProperty lat = FeatureProperties.create(bikeRacks, "lat", decimal);
        GeometryFeature racks = SpatialElements.feature(geodata, "bikeRacks", GeometryKind.POINT, crs84);
        FeatureProperty location = FeatureProperties.geometry(bikeRacks, "location", types, racks);
        SpatialElements.pointParts(racks, lon, lat);
        FeatureCollections.geometry(bikeRacks, racks);

        // both holdings lie in the collection; a server writes the geometry from the whole one
        List<GeometryHolding> holdings = FeatureCollections.holdings(racks, bikeRacks);
        assertThat(holdings).hasSize(2);
        assertThat(holdings.get(0)).isInstanceOf(WholeHolding.class);
        assertThat(FeatureCollections.holding(racks, bikeRacks)).containsSame(holdings.get(0));
        // a geometry property and two numbers: each a queryable of its kind
        assertThat(FeatureProperties.isGeometry(location)).isTrue();
        assertThat(FeatureProperties.isGeometry(lon)).isFalse();
        assertThat(Queryables.simple(lat)).isTrue();
        assertThat(Queryables.simple(capacity)).isTrue();
        assertThat(FeatureModelValidator.check(model)).isEmpty();
        assertThat(SpatialModelValidator.check(model)).isEmpty();
    }

    /**
     * What a reader asks of a collection: the geometry, its holdings within the collection, its envelope and its
     * reference system. The lakes of a state are polygons in {@code outline}, each with its bounding box in
     * {@code min_x}, {@code min_y}, {@code max_x} and {@code max_y}, in ETRS89 / UTM zone 32N.
     */
    @Test
    void readingTheGeometryOfACollection() {
        FeatureCollection lakes = FeatureCollections.create(features, "lakes");
        FeatureCollections.key(lakes, FeatureProperties.required(lakes, "lake_id", string));
        FeatureProperties.create(lakes, "name", string);
        GeometryFeature lakeShapes = SpatialElements.feature(geodata, "lakeShapes", GeometryKind.POLYGON, utm32);
        FeatureProperties.geometry(lakes, "outline", types, lakeShapes);
        FeatureProperty minX = FeatureProperties.create(lakes, "min_x", decimal);
        FeatureProperty minY = FeatureProperties.create(lakes, "min_y", decimal);
        FeatureProperty maxX = FeatureProperties.create(lakes, "max_x", decimal);
        FeatureProperty maxY = FeatureProperties.create(lakes, "max_y", decimal);
        SpatialElements.envelope(geodata, "lakeBoxes", lakeShapes, minX, minY, maxX, maxY);
        FeatureCollections.geometry(lakes, lakeShapes);

        // the geometry and its holdings within the collection
        GeometryFeature geometry = lakes.getGeometry();
        assertThat(geometry.getKind()).isEqualTo(GeometryKind.POLYGON);
        assertThat(FeatureCollections.holdings(geometry, lakes)).singleElement()
                .extracting(h -> SpatialElements.attributes(h).get(0).getName()).isEqualTo("outline");
        // its envelope: which property holds which edge
        GeometryFeature envelope = FeatureCollections.envelope(lakes).orElseThrow();
        PartsHolding edges = (PartsHolding) FeatureCollections.holding(envelope, lakes).orElseThrow();
        assertThat(List.of(Ordinate.MIN_EAST, Ordinate.MIN_NORTH, Ordinate.MAX_EAST, Ordinate.MAX_NORTH).stream()
                .map(role -> SpatialElements.attribute(edges, role).orElseThrow())).containsExactly(minX, minY, maxX,
                        maxY);
        // its reference system
        assertThat(FeatureCollections.storageSystem(lakes)).containsSame(utm32);
        assertThat(FeatureCollections.storageCrs(lakes)).contains("http://www.opengis.net/def/crs/EPSG/0/25832");
        assertThat(FeatureCollections.hasHeight(lakes)).isFalse();
        assertThat(FeatureModelValidator.check(model)).isEmpty();
        assertThat(SpatialModelValidator.check(model)).isEmpty();
    }

    private static Column column(Table table, String name, Classifier type) {
        Column column = R.createColumn();
        column.setName(name);
        column.setType(type);
        table.getFeature().add(column);
        return column;
    }

    private static Attribute attribute(org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class owner, String name,
            Classifier type) {
        Attribute attribute = CoreFactory.eINSTANCE.createAttribute();
        attribute.setName(name);
        attribute.setType(type);
        owner.getFeature().add(attribute);
        return attribute;
    }

    private SQLSimpleType sqlType(String name, long typeNumber) {
        SQLSimpleType type = R.createSQLSimpleType();
        type.setTypeNumber(typeNumber);
        return owned(db, name, type);
    }

    private static <T extends ModelElement> T owned(Namespace owner, String name, T element) {
        element.setName(name);
        if (owner != null) {
            owner.getOwnedElement().add(element);
        }
        return element;
    }
}
