/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.spatial.examples;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.resource.record.Field;
import org.eclipse.daanse.cwm.model.cwm.resource.record.RecordDef;
import org.eclipse.daanse.cwm.model.cwm.resource.record.RecordFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.record.RecordFile;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.daanse.resource.json.ArrayType;
import org.eclipse.daanse.cwm.model.daanse.resource.json.JsonFactory;
import org.eclipse.daanse.cwm.model.daanse.resource.json.JsonValueKind;
import org.eclipse.daanse.cwm.model.daanse.resource.json.ObjectType;
import org.eclipse.daanse.cwm.model.daanse.resource.json.PrimitiveType;
import org.eclipse.daanse.cwm.model.daanse.resource.json.Property;
import org.eclipse.daanse.cwm.model.daanse.spatial.AxisOrder;
import org.eclipse.daanse.cwm.model.daanse.spatial.DistanceBand;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryDerivation;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryFeature;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryKind;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryStorage;
import org.eclipse.daanse.cwm.model.daanse.spatial.GridLevel;
import org.eclipse.daanse.cwm.model.daanse.spatial.ListHolding;
import org.eclipse.daanse.cwm.model.daanse.spatial.Ordinate;
import org.eclipse.daanse.cwm.model.daanse.spatial.PartsHolding;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystem;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystemKind;
import org.eclipse.daanse.cwm.model.daanse.spatial.SpatialAssignment;
import org.eclipse.daanse.cwm.model.daanse.spatial.SpatialPredicate;
import org.eclipse.daanse.cwm.model.daanse.spatial.SpatialRelation;
import org.eclipse.daanse.cwm.model.daanse.spatial.UnmatchedPolicy;
import org.eclipse.daanse.cwm.model.daanse.spatial.WholeHolding;
import org.eclipse.daanse.cwm.model.daanse.spatial.util.ReferenceSystems;
import org.eclipse.daanse.cwm.model.daanse.spatial.util.SpatialElements;
import org.eclipse.daanse.cwm.model.daanse.spatial.util.SpatialModelValidator;
import org.eclipse.daanse.cwm.model.daanse.validation.Finding;
import org.junit.jupiter.api.Test;

/**
 * The spatial model across CWM: geometries of relational tables, of JSON documents and of record files, held whole, in
 * parts and as lists, of their own and derived from another, and the rules that derive keys from a geometry held as
 * numbers only. Each test is one kind of data and asks of the model what a reader of the data asks: which attributes
 * hold the longitude and the latitude, which four columns are the envelope of a geometry, in which system and order
 * the numbers are.
 */
class SpatialAcrossCwmTest {

    private static final RelationalFactory R = RelationalFactory.eINSTANCE;

    private final Model model = owned(null, "geodata", CoreFactory.eINSTANCE.createModel());
    private final Schema db = owned(model, "public", R.createSchema());
    private final SQLSimpleType integer = sqlType("INTEGER", 4);
    private final SQLSimpleType varchar = sqlType("VARCHAR", 12);
    private final SQLSimpleType doublePrecision = sqlType("DOUBLE PRECISION", 8);
    private final SQLSimpleType geometry = sqlType("GEOMETRY", 1111);
    private final Package spatial = owned(model, "spatial", CoreFactory.eINSTANCE.createPackage());
    private final ReferenceSystem crs84 = SpatialElements.crs84(spatial);
    private final ReferenceSystem crs84h = SpatialElements.crs84h(spatial);
    private final ReferenceSystem utm32 = SpatialElements.epsg(spatial, 25832, ReferenceSystemKind.PROJECTED,
            AxisOrder.EAST_NORTH);

    /**
     * A weather service keeps its stations in {@code station(station_id, name, lon, lat)} and its radio masts with
     * their height in {@code mast(mast_id, lon, lat, height_m)}, both in WGS 84.
     */
    @Test
    void weatherStationsByLongitudeAndLatitude() {
        Table station = table("station");
        key(station, "station_id");
        column(station, "name", varchar);
        Column lon = column(station, "lon", doublePrecision);
        Column lat = column(station, "lat", doublePrecision);
        GeometryFeature stations = SpatialElements.point(spatial, "stations", crs84, lon, lat);

        // which attributes hold the longitude and the latitude of a station
        PartsHolding parts = (PartsHolding) SpatialElements.holdings(lat).get(0);
        assertThat(SpatialElements.attribute(parts, Ordinate.EAST)).contains(lon);
        assertThat(SpatialElements.attribute(parts, Ordinate.NORTH)).contains(lat);
        // in which system the numbers are, and which is the longitude: EAST in a geographic system
        assertThat(ReferenceSystems.uri(stations.getReferenceSystem()))
                .isEqualTo("http://www.opengis.net/def/crs/OGC/1.3/CRS84");
        assertThat(stations.getReferenceSystem().getKind()).isEqualTo(ReferenceSystemKind.GEOGRAPHIC);

        Table mast = table("mast");
        key(mast, "mast_id");
        Column mastLon = column(mast, "lon", doublePrecision);
        Column mastLat = column(mast, "lat", doublePrecision);
        Column heightM = column(mast, "height_m", doublePrecision);
        GeometryFeature masts = SpatialElements.point(spatial, "masts", crs84h, mastLon, mastLat, heightM);

        // which attribute holds the height, and is the system one of three axes
        assertThat(SpatialElements.attribute((PartsHolding) masts.getHolding().get(0), Ordinate.HEIGHT))
                .contains(heightM);
        assertThat(masts.isHasHeight()).isTrue();
        assertThat(masts.getReferenceSystem().getAxes()).isEqualTo(3);
        assertThat(SpatialModelValidator.check(model)).isEmpty();
    }

    /**
     * A municipality publishes the official coordinates of its addresses as easting and northing in ETRS89 / UTM zone
     * 32N, {@code house_coordinate(address_id, street, house_number, easting, northing)}; its GIS keeps the same points
     * in a geometry column of another table, {@code address_point(address_id, geom)}. One geometry, two holdings.
     */
    @Test
    void addressCoordinatesAsNumbersAndAsAGeometryColumn() {
        Table houseCoordinate = table("house_coordinate");
        key(houseCoordinate, "address_id");
        column(houseCoordinate, "street", varchar);
        column(houseCoordinate, "house_number", varchar);
        Column easting = column(houseCoordinate, "easting", doublePrecision);
        Column northing = column(houseCoordinate, "northing", doublePrecision);
        Table addressPoint = table("address_point");
        key(addressPoint, "address_id");
        Column geom = column(addressPoint, "geom", geometry);
        GeometryFeature addresses = SpatialElements.point(spatial, "addresses", utm32, easting, northing);
        SpatialElements.whole(addresses, geom, GeometryStorage.NATIVE);

        // the numbers and the geometry column hold one geometry
        assertThat(SpatialElements.features(easting)).containsExactly(addresses);
        assertThat(SpatialElements.features(geom)).containsExactly(addresses);
        // in which tables it is held, and in which form
        assertThat(addresses.getHolding()).extracting(h -> SpatialElements.attributes(h).get(0).getOwner())
                .containsExactly(houseCoordinate, addressPoint);
        assertThat(addresses.getHolding().get(1)).isInstanceOfSatisfying(WholeHolding.class,
                whole -> assertThat(whole.getStorage()).isEqualTo(GeometryStorage.NATIVE));
        // easting and northing are metres of a projected system: EAST is the easting
        assertThat(addresses.getReferenceSystem().getKind()).isEqualTo(ReferenceSystemKind.PROJECTED);
        assertThat(ReferenceSystems.epsg(addresses.getReferenceSystem())).hasValue(25832);
        assertThat(SpatialModelValidator.check(model)).isEmpty();
    }

    /**
     * A table of building footprints keeps beside the geometry the four columns of its bounding box for an index, its
     * stored centroid and the point a label is placed at:
     * {@code building(building_id, footprint, bbox_xmin, bbox_ymin, bbox_xmax, bbox_ymax, centroid, label_point)}.
     */
    @Test
    void buildingFootprintsWithTheirBoxCentroidAndLabelPoint() {
        Table building = table("building");
        key(building, "building_id");
        Column footprint = column(building, "footprint", geometry);
        Column xmin = column(building, "bbox_xmin", doublePrecision);
        Column ymin = column(building, "bbox_ymin", doublePrecision);
        Column xmax = column(building, "bbox_xmax", doublePrecision);
        Column ymax = column(building, "bbox_ymax", doublePrecision);
        Column centroid = column(building, "centroid", geometry);
        Column labelPoint = column(building, "label_point", geometry);
        GeometryFeature footprints = SpatialElements.feature(spatial, "footprints", GeometryKind.POLYGON, utm32,
                GeometryStorage.NATIVE, footprint);
        SpatialElements.envelope(spatial, "footprintBoxes", footprints, xmin, ymin, xmax, ymax);
        SpatialElements.whole(SpatialElements.derived(spatial, "footprintCentroids", GeometryDerivation.CENTROID,
                footprints), centroid, GeometryStorage.NATIVE);
        SpatialElements.whole(SpatialElements.derived(spatial, "footprintLabels", GeometryDerivation.POINT_ON_SURFACE,
                footprints), labelPoint, GeometryStorage.NATIVE);

        // which four columns are the envelope of the footprint, edge by edge
        GeometryFeature envelope = derived(footprints, GeometryDerivation.ENVELOPE);
        PartsHolding edges = (PartsHolding) envelope.getHolding().get(0);
        assertThat(List.of(Ordinate.MIN_EAST, Ordinate.MIN_NORTH, Ordinate.MAX_EAST, Ordinate.MAX_NORTH).stream()
                .map(role -> SpatialElements.attribute(edges, role).orElseThrow().getName()))
                .containsExactly("bbox_xmin", "bbox_ymin", "bbox_xmax", "bbox_ymax");
        // where the centroid and the label point are stored
        assertThat(SpatialElements.attributes(derived(footprints, GeometryDerivation.CENTROID).getHolding().get(0)))
                .containsExactly(centroid);
        assertThat(SpatialElements.attributes(derived(footprints, GeometryDerivation.POINT_ON_SURFACE).getHolding()
                .get(0))).containsExactly(labelPoint);
        // every derived value is in the system of the footprint
        assertThat(SpatialElements.derivations(footprints)).extracting(GeometryFeature::getReferenceSystem)
                .containsOnly(utm32);
        assertThat(SpatialModelValidator.check(model)).isEmpty();
    }

    /**
     * A JSON document type of nature observations: an object {@code Observation} with a GeoJSON geometry member
     * {@code geometry} and its bounding box {@code bbox}, an array of four numbers as GeoJSON writes it. A second type
     * of sensor readings carries its position as an array {@code [longitude, latitude]}.
     */
    @Test
    void jsonDocumentsWithAGeoJsonMemberABboxAndACoordinatePair() {
        org.eclipse.daanse.cwm.model.daanse.resource.json.Schema schema = owned(model, "observation",
                JsonFactory.eINSTANCE.createSchema());
        PrimitiveType text = primitive(schema, "Text", JsonValueKind.JSON_STRING);
        PrimitiveType number = primitive(schema, "Number", JsonValueKind.JSON_NUMBER);
        ObjectType geoJsonGeometry = owned(schema, "GeoJsonGeometry", JsonFactory.eINSTANCE.createObjectType());
        ArrayType bboxType = array(schema, "Bbox", number, 4);
        ObjectType observation = owned(schema, "Observation", JsonFactory.eINSTANCE.createObjectType());
        property(observation, "id", text);
        Property geometryMember = property(observation, "geometry", geoJsonGeometry);
        Property bbox = property(observation, "bbox", bboxType);
        GeometryFeature observed = SpatialElements.feature(spatial, "observedAt", GeometryKind.MIXED, crs84,
                GeometryStorage.GEOJSON, geometryMember);
        GeometryFeature observedBox = SpatialElements.derived(spatial, "observedBox", GeometryDerivation.ENVELOPE,
                observed);
        SpatialElements.list(observedBox, bbox, null, Ordinate.MIN_EAST, Ordinate.MIN_NORTH, Ordinate.MAX_EAST,
                Ordinate.MAX_NORTH);

        // which member holds the geometry, in which form and order
        assertThat(SpatialElements.holdings(geometryMember)).singleElement().isInstanceOfSatisfying(
                WholeHolding.class, whole -> {
                    assertThat(whole.getStorage()).isEqualTo(GeometryStorage.GEOJSON);
                    assertThat(whole.getAxisOrder()).isEqualTo(AxisOrder.EAST_NORTH);
                });
        // in which order the four numbers of bbox are, and that they are values of an array, not a text
        assertThat(SpatialElements.holdings(bbox)).singleElement().isInstanceOfSatisfying(ListHolding.class, list -> {
            assertThat(list.getOrdinate()).containsExactly(Ordinate.MIN_EAST, Ordinate.MIN_NORTH, Ordinate.MAX_EAST,
                    Ordinate.MAX_NORTH);
            assertThat(list.getSeparator()).isNull();
        });
        assertThat(SpatialElements.features(bbox)).containsExactly(observedBox);
        assertThat(observedBox.getOf()).isSameAs(observed);

        ArrayType pair = array(schema, "Position", number, 2);
        ObjectType reading = owned(schema, "Reading", JsonFactory.eINSTANCE.createObjectType());
        property(reading, "deviceId", text);
        Property position = property(reading, "position", pair);
        GeometryFeature readings = SpatialElements.feature(spatial, "readingPositions", GeometryKind.POINT, crs84);
        ListHolding coordinates = SpatialElements.list(readings, position, null, Ordinate.EAST, Ordinate.NORTH);

        // which number of the pair is the longitude
        assertThat(coordinates.getOrdinate().indexOf(Ordinate.EAST)).isZero();
        assertThat(SpatialModelValidator.check(model)).isEmpty();
        // an array read as a text with a separator is a contradiction the validator names
        coordinates.setSeparator(",");
        assertThat(SpatialModelValidator.check(model)).extracting(Finding::rule).containsExactly("SP47");
    }

    /**
     * Two CSV files: a register of street trees, {@code street_trees.csv}, with the point of each tree as well-known
     * text, and a list of sightings, {@code sightings.csv}, whose position is a text "lat,lon", latitude first. The
     * fields of both files are separated by {@code ;}.
     */
    @Test
    void recordFilesWithWellKnownTextAndALatLonText() {
        Package types = owned(model, "types", CoreFactory.eINSTANCE.createPackage());
        DataType string = owned(types, "String", CoreFactory.eINSTANCE.createDataType());
        RecordFile treeFile = owned(model, "street_trees.csv", RecordFactory.eINSTANCE.createRecordFile());
        treeFile.setIsSelfDescribing(true);
        RecordDef tree = record(treeFile, "tree");
        field(tree, "tree_id", string);
        field(tree, "species", string);
        Field location = field(tree, "location", string);
        GeometryFeature streetTrees = SpatialElements.feature(spatial, "streetTrees", GeometryKind.POINT, crs84,
                GeometryStorage.WKT, location);
        RecordFile sightingFile = owned(model, "sightings.csv", RecordFactory.eINSTANCE.createRecordFile());
        RecordDef sighting = record(sightingFile, "sighting");
        field(sighting, "sighting_id", string);
        field(sighting, "observed_on", string);
        Field position = field(sighting, "position", string);
        GeometryFeature sightings = SpatialElements.feature(spatial, "sightings", GeometryKind.POINT, crs84);
        SpatialElements.list(sightings, position, ",", Ordinate.NORTH, Ordinate.EAST);

        // which field holds the point of a tree, and how it is written
        assertThat(((WholeHolding) SpatialElements.holdings(location).get(0)).getStorage())
                .isEqualTo(GeometryStorage.WKT);
        assertThat(SpatialElements.features(location)).containsExactly(streetTrees);
        // how the text of a sighting is read: split at the comma, the first number the latitude
        ListHolding latLon = (ListHolding) SpatialElements.holdings(position).get(0);
        assertThat(latLon.getSeparator()).isEqualTo(",");
        assertThat(latLon.getOrdinate()).containsExactly(Ordinate.NORTH, Ordinate.EAST);
        // the separator of the numbers is not the one of the fields
        assertThat(sighting.getFieldDelimiter()).isEqualTo(";");
        assertThat(SpatialModelValidator.check(model)).isEmpty();
    }

    /**
     * Two indexes of tiles: orthophotos by their upper left and lower right corner as points,
     * {@code orthophoto_tile(tile_id, file_name, footprint, upper_left, lower_right)}, and elevation tiles by their
     * lower left and upper right corner as four numbers, {@code elevation_tile(tile_id, footprint, ll_x, ll_y, ur_x,
     * ur_y)}.
     */
    @Test
    void tileIndexesByTwoCorners() {
        Table orthophoto = table("orthophoto_tile");
        key(orthophoto, "tile_id");
        column(orthophoto, "file_name", varchar);
        Column photoFootprint = column(orthophoto, "footprint", geometry);
        Column upperLeft = column(orthophoto, "upper_left", geometry);
        Column lowerRight = column(orthophoto, "lower_right", geometry);
        Table elevation = table("elevation_tile");
        key(elevation, "tile_id");
        Column demFootprint = column(elevation, "footprint", geometry);
        Column llX = column(elevation, "ll_x", doublePrecision);
        Column llY = column(elevation, "ll_y", doublePrecision);
        Column urX = column(elevation, "ur_x", doublePrecision);
        Column urY = column(elevation, "ur_y", doublePrecision);
        GeometryFeature photoTiles = SpatialElements.feature(spatial, "photoTiles", GeometryKind.POLYGON, utm32,
                GeometryStorage.NATIVE, photoFootprint);
        SpatialElements.whole(SpatialElements.derived(spatial, "photoTileUpperLeft",
                GeometryDerivation.CORNER_NORTH_WEST, photoTiles), upperLeft, GeometryStorage.NATIVE);
        GeometryFeature photoLowerRight = SpatialElements.derived(spatial, "photoTileLowerRight",
                GeometryDerivation.CORNER_SOUTH_EAST, photoTiles);
        SpatialElements.whole(photoLowerRight, lowerRight, GeometryStorage.NATIVE);
        GeometryFeature demTiles = SpatialElements.feature(spatial, "elevationTiles", GeometryKind.POLYGON, utm32,
                GeometryStorage.NATIVE, demFootprint);
        SpatialElements.pointParts(SpatialElements.derived(spatial, "elevationTileLowerLeft",
                GeometryDerivation.CORNER_SOUTH_WEST, demTiles), llX, llY);
        SpatialElements.pointParts(SpatialElements.derived(spatial, "elevationTileUpperRight",
                GeometryDerivation.CORNER_NORTH_EAST, demTiles), urX, urY);

        // which corners give the envelope of a tile, and where each is held
        assertThat(SpatialElements.derivations(photoTiles)).extracting(GeometryFeature::getDerivation)
                .containsExactlyInAnyOrder(GeometryDerivation.CORNER_NORTH_WEST, GeometryDerivation.CORNER_SOUTH_EAST);
        assertThat(SpatialElements.attributes(derived(demTiles, GeometryDerivation.CORNER_SOUTH_WEST).getHolding()
                .get(0))).containsExactly(llX, llY);
        assertThat(SpatialElements.derivations(demTiles)).extracting(GeometryFeature::getKind)
                .containsOnly(GeometryKind.POINT);
        assertThat(SpatialModelValidator.check(model)).isEmpty();
        // an upper left beside a lower left gives no envelope
        photoLowerRight.setDerivation(GeometryDerivation.CORNER_SOUTH_WEST);
        assertThat(SpatialModelValidator.check(model)).extracting(Finding::rule).containsExactly("SP53");
    }

    /**
     * Air quality measuring points are held only as numbers, {@code measuring_point(point_id, lon, lat)} in WGS 84.
     * They are assigned to the districts they lie in, put into H3 cells and classed by the distance to the nearest
     * river; districts and rivers are geometry columns in ETRS89 / UTM zone 32N, where it is all measured.
     */
    @Test
    void keysFromAGeometryHeldAsNumbers() {
        Table measuringPoint = table("measuring_point");
        Column pointId = key(measuringPoint, "point_id");
        Column lon = column(measuringPoint, "lon", doublePrecision);
        Column lat = column(measuringPoint, "lat", doublePrecision);
        Table district = table("district");
        Column districtId = key(district, "district_id");
        Column shape = column(district, "shape", geometry);
        Table river = table("river");
        key(river, "river_id");
        Column course = column(river, "course", geometry);
        GeometryFeature points = SpatialElements.point(spatial, "measuringPoints", crs84, lon, lat);
        GeometryFeature districts = SpatialElements.feature(spatial, "districts", GeometryKind.POLYGON, utm32,
                GeometryStorage.NATIVE, shape);
        GeometryFeature rivers = SpatialElements.feature(spatial, "rivers", GeometryKind.LINE, utm32,
                GeometryStorage.NATIVE, course);
        SpatialRelation inDistricts = SpatialElements.workingSystem(SpatialElements.relation(spatial,
                "measuringPointsInDistricts", points, pointId, districts, districtId, SpatialPredicate.COVERED_BY,
                SpatialAssignment.EXACTLY_ONE, UnmatchedPolicy.KEEP_NULL), utm32);
        GridLevel cells = SpatialElements.grid(spatial, "measuringPointCells", points, pointId,
                SpatialElements.h3(spatial), 8, null);
        DistanceBand toTheRiver = SpatialElements.band(spatial, "measuringPointsToTheRiver", points, pointId, rivers,
                "100", "500", "2000");
        toTheRiver.setWorkingSystem(utm32);

        // which rules read the measuring points
        assertThat(SpatialElements.asChild(points)).containsExactly(inDistricts);
        assertThat(cells.getSource()).isSameAs(points);
        // what a resolver reads them from: the numbers in the table of the key, a point it builds
        assertThat(points.getHolding()).allMatch(PartsHolding.class::isInstance);
        assertThat(SpatialElements.attributes(points.getHolding().get(0))).extracting(Attribute::getOwner)
                .containsOnly(pointId.getOwner());
        assertThat(SpatialModelValidator.check(model)).isEmpty();
    }

    /** The geometry derived from {@code of} as {@code derivation}. */
    private static GeometryFeature derived(GeometryFeature of, GeometryDerivation derivation) {
        return SpatialElements.derivations(of).stream()
                .filter(d -> d.isSetDerivation() && d.getDerivation() == derivation).findFirst().orElseThrow();
    }

    private Table table(String name) {
        return owned(db, name, R.createTable());
    }

    private static Column column(Table table, String name, Classifier type) {
        Column column = R.createColumn();
        column.setName(name);
        column.setType(type);
        table.getFeature().add(column);
        return column;
    }

    /** The integer column {@code name} of {@code table} and its primary key. */
    private Column key(Table table, String name) {
        Column id = column(table, name, integer);
        PrimaryKey pk = R.createPrimaryKey();
        pk.setName(table.getName() + "_pk");
        pk.getFeature().add(id);
        table.getOwnedElement().add(pk);
        return id;
    }

    private SQLSimpleType sqlType(String name, long typeNumber) {
        SQLSimpleType type = R.createSQLSimpleType();
        type.setTypeNumber(typeNumber);
        return owned(db, name, type);
    }

    private static PrimitiveType primitive(Namespace schema, String name, JsonValueKind kind) {
        PrimitiveType type = owned(schema, name, JsonFactory.eINSTANCE.createPrimitiveType());
        type.setValueKind(kind);
        return type;
    }

    /** An array of exactly {@code size} items of {@code item}. */
    private static ArrayType array(Namespace schema, String name, Classifier item, long size) {
        ArrayType type = owned(schema, name, JsonFactory.eINSTANCE.createArrayType());
        type.setItemType(item);
        type.setMinItems(size);
        type.setMaxItems(size);
        return type;
    }

    private static Property property(ObjectType object, String name, Classifier type) {
        Property property = JsonFactory.eINSTANCE.createProperty();
        property.setName(name);
        property.setType(type);
        object.getFeature().add(property);
        return property;
    }

    /** A record of {@code file} whose fields are separated by {@code ;}. */
    private static RecordDef record(RecordFile file, String name) {
        RecordDef record = owned(file, name, RecordFactory.eINSTANCE.createRecordDef());
        record.setFieldDelimiter(";");
        file.getRecord().add(record);
        return record;
    }

    private static Field field(RecordDef record, String name, Classifier type) {
        Field field = RecordFactory.eINSTANCE.createField();
        field.setName(name);
        field.setType(type);
        record.getFeature().add(field);
        return field;
    }

    private static <T extends ModelElement> T owned(Namespace owner, String name, T element) {
        element.setName(name);
        if (owner != null) {
            owner.getOwnedElement().add(element);
        }
        return element;
    }
}
