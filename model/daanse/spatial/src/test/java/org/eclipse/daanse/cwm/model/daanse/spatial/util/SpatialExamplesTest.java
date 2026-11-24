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
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.daanse.spatial.AxisOrder;
import org.eclipse.daanse.cwm.model.daanse.spatial.CoordinatePart;
import org.eclipse.daanse.cwm.model.daanse.spatial.DistanceBand;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryDerivation;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryFeature;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryHolding;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryKind;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryMultiplicity;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryStorage;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryType;
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
import org.eclipse.daanse.cwm.model.daanse.spatial.SpatialPredicate;
import org.eclipse.daanse.cwm.model.daanse.spatial.SpatialRelation;
import org.eclipse.daanse.cwm.model.daanse.spatial.SpatialWeight;
import org.eclipse.daanse.cwm.model.daanse.spatial.TieBreak;
import org.eclipse.daanse.cwm.model.daanse.spatial.UnmatchedPolicy;
import org.eclipse.daanse.cwm.model.daanse.spatial.WholeHolding;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.junit.jupiter.api.Test;

/**
 * The examples of the README, one test each, in the order of its sections and on its running scenario: the geodata of
 * a land office in the schema {@code geo} of a PostGIS database, the spatial model in the package {@code geodata}.
 * Each test builds its section and checks what a reader gets from it; {@link #everySnippetOfTheReadmeStandsHere()}
 * fails where a line of code of the README is not in this class, so that README and code cannot drift.
 */
class SpatialExamplesTest {

    private static final RelationalFactory R = RelationalFactory.eINSTANCE;

    private final Model model = owned(null, "land", CoreFactory.eINSTANCE.createModel());
    private final Schema geo = owned(model, "geo", R.createSchema());
    private final SQLSimpleType integer = type("INTEGER", 4);
    private final SQLSimpleType varchar = type("VARCHAR", 12);
    private final SQLSimpleType doublePrecision = type("DOUBLE PRECISION", 8);
    private final SQLSimpleType doubleArray = type("DOUBLE PRECISION[]", 2003);
    private final SQLSimpleType bytea = type("BYTEA", -2);
    private final SQLSimpleType geometry = type("GEOMETRY", 1111);
    private final Package geodata = owned(model, "geodata", CoreFactory.eINSTANCE.createPackage());

    // section "Reference systems"
    private final ReferenceSystem utm32 = SpatialElements.epsg(geodata, 25832, ReferenceSystemKind.PROJECTED,
            AxisOrder.EAST_NORTH);
    private final ReferenceSystem wgs84 = SpatialElements.epsg(geodata, 4326, ReferenceSystemKind.GEOGRAPHIC,
            AxisOrder.NORTH_EAST);
    private final ReferenceSystem crs84 = SpatialElements.crs84(geodata);
    private final ReferenceSystem crs84h = SpatialElements.crs84h(geodata);

    // the tables of the scenario, each made by the section that first uses it
    private Table commune;
    private Column communeId;
    private Column communeShape;
    private Table county;
    private Column countyId;
    private Table station;
    private Column stationId;
    private Column lon;
    private Column lat;

    /** Section "A geometry in a column": the communes in their column shape of PostGIS type GEOMETRY. */
    private GeometryFeature communes() {
        commune = table("commune");
        communeId = key(commune);
        column(commune, "name", varchar);
        communeShape = column(commune, "shape", geometry);
        GeometryFeature communes = SpatialElements.feature(geodata, "communes", GeometryKind.POLYGON, utm32,
                GeometryStorage.NATIVE, communeShape);
        return communes;
    }

    @Test
    void aGeometryInAColumn() {
        GeometryFeature communes = communes();
        communes.setMultiplicity(GeometryMultiplicity.MULTI);
        assertThat(communes.getHolding()).singleElement().isInstanceOfSatisfying(WholeHolding.class, whole -> {
            assertThat(whole.getAttribute()).isSameAs(communeShape);
            assertThat(whole.getStorage()).isEqualTo(GeometryStorage.NATIVE);
            assertThat(whole.getAxisOrder()).isEqualTo(AxisOrder.EAST_NORTH);
        });
        assertThat(SpatialElements.features(communeShape)).containsExactly(communes);
        assertThat(SpatialElements.holdings(communeShape)).containsExactlyElementsOf(communes.getHolding());
        assertThat(SpatialElements.attributes(communes.getHolding().get(0))).containsExactly(communeShape);
        assertThat(SpatialModelValidator.check(model)).isEmpty();
        assertThat(SpatialModelValidator.validate(model)).isEmpty();
    }

    /** Section "A geometry as text": hiking trails as GeoJSON text in WGS 84. */
    private GeometryFeature trails() {
        Table trail = table("trail");
        Column route = column(trail, "route", varchar);
        GeometryFeature trails = SpatialElements.feature(geodata, "trails", GeometryKind.LINE, crs84,
                GeometryStorage.GEOJSON, route);
        return trails;
    }

    @Test
    void aGeometryAsText() {
        GeometryFeature trails = trails();
        assertThat(((WholeHolding) trails.getHolding().get(0)).getStorage()).isEqualTo(GeometryStorage.GEOJSON);
        assertThat(trails.getMultiplicity()).isEqualTo(GeometryMultiplicity.EITHER);
        assertThat(SpatialModelValidator.check(model)).isEmpty();
    }

    /** Section "A point as two numbers": the weather stations by longitude and latitude. */
    private GeometryFeature stations() {
        station = table("station");
        stationId = key(station);
        column(station, "name", varchar);
        lon = column(station, "lon", doublePrecision);
        lat = column(station, "lat", doublePrecision);
        GeometryFeature stations = SpatialElements.point(geodata, "stations", crs84, lon, lat);
        return stations;
    }

    @Test
    void aPointAsTwoNumbers() {
        GeometryFeature stations = stations();
        assertThat(stations.getKind()).isEqualTo(GeometryKind.POINT);
        assertThat(stations.getMultiplicity()).isEqualTo(GeometryMultiplicity.SINGLE);
        PartsHolding parts = (PartsHolding) stations.getHolding().get(0);
        CoordinatePart first = parts.getPart().get(0);
        assertThat(first.getRole()).isEqualTo(Ordinate.EAST);
        assertThat(parts.getPart()).extracting(CoordinatePart::getRole, CoordinatePart::getAttribute)
                .containsExactly(tuple(Ordinate.EAST, lon), tuple(Ordinate.NORTH, lat));
        assertThat(SpatialElements.attribute(parts, Ordinate.EAST)).contains(lon);
        assertThat(SpatialModelValidator.check(model)).isEmpty();
    }

    /** The same point in two steps: a feature, then its parts. */
    @Test
    void aPointAsTwoNumbersInTwoSteps() {
        Table station = table("station");
        Column lon = column(station, "lon", doublePrecision);
        Column lat = column(station, "lat", doublePrecision);
        GeometryFeature stations = SpatialElements.feature(geodata, "stations", GeometryKind.POINT, crs84);
        SpatialElements.pointParts(stations, lon, lat);
        assertThat(SpatialElements.attributes(stations.getHolding().get(0))).containsExactly(lon, lat);
        assertThat(SpatialModelValidator.check(model)).isEmpty();
    }

    /** Section "A point with a height": radio masts with their ellipsoidal height. */
    private GeometryFeature masts() {
        Table mast = table("mast");
        Column lon = column(mast, "lon", doublePrecision);
        Column lat = column(mast, "lat", doublePrecision);
        Column height = column(mast, "height", doublePrecision);
        GeometryFeature masts = SpatialElements.point(geodata, "masts", crs84h, lon, lat, height);
        return masts;
    }

    @Test
    void aPointWithAHeight() {
        GeometryFeature masts = masts();
        assertThat(masts.isHasHeight()).isTrue();
        assertThat(crs84h.getAxes()).isEqualTo(3);
        assertThat(SpatialElements.attribute((PartsHolding) masts.getHolding().get(0), Ordinate.HEIGHT))
                .hasValueSatisfying(height -> assertThat(height.getName()).isEqualTo("height"));
        assertThat(SpatialModelValidator.check(model)).isEmpty();
    }

    /** Section "Easting and northing": wells by their UTM coordinates in metres. */
    private GeometryFeature wells() {
        Table well = table("well");
        Column easting = column(well, "easting", doublePrecision);
        Column northing = column(well, "northing", doublePrecision);
        GeometryFeature wells = SpatialElements.point(geodata, "wells", utm32, easting, northing);
        return wells;
    }

    @Test
    void eastingAndNorthing() {
        GeometryFeature wells = wells();
        assertThat(wells.getReferenceSystem().getKind()).isEqualTo(ReferenceSystemKind.PROJECTED);
        assertThat(SpatialModelValidator.check(model)).isEmpty();
    }

    /** Section "A point in one attribute": bus stops as text, latitude first; sensors as an array. */
    private List<GeometryFeature> stopsAndSensors() {
        Table stop = table("stop");
        Column position = column(stop, "position", varchar);
        Table sensor = table("sensor");
        Column location = column(sensor, "location", doubleArray);
        GeometryFeature stops = SpatialElements.feature(geodata, "stops", GeometryKind.POINT, crs84);
        ListHolding text = SpatialElements.list(stops, position, ",", Ordinate.NORTH, Ordinate.EAST);
        assertThat(text.getSeparator()).isEqualTo(",");
        GeometryFeature sensors = SpatialElements.feature(geodata, "sensors", GeometryKind.POINT, crs84);
        SpatialElements.list(sensors, location, null, Ordinate.EAST, Ordinate.NORTH);
        return List.of(stops, sensors);
    }

    @Test
    void aPointInOneAttribute() {
        List<GeometryFeature> stopsAndSensors = stopsAndSensors();
        assertThat(stopsAndSensors.get(0).getHolding()).singleElement().isInstanceOfSatisfying(ListHolding.class,
                list -> {
                    assertThat(list.getOrdinate()).containsExactly(Ordinate.NORTH, Ordinate.EAST);
                    assertThat(list.getSeparator()).isEqualTo(",");
                });
        assertThat(((ListHolding) stopsAndSensors.get(1).getHolding().get(0)).getSeparator()).isNull();
        assertThat(SpatialModelValidator.check(model)).isEmpty();
    }

    /**
     * Section "One geometry, several holdings": the stations get a geometry column beside lon and lat; the districts
     * are kept in a table and in its archive.
     */
    private GeometryFeature severalHoldings(GeometryFeature stations) {
        Column position = column(station, "position", geometry);
        WholeHolding column = SpatialElements.whole(stations, position, GeometryStorage.NATIVE);
        List<GeometryHolding> holdings = stations.getHolding();
        assertThat(holdings).hasSize(2).contains(column);
        Column districtShape = column(table("district"), "shape", geometry);
        Column archiveShape = column(table("district_archive"), "shape", geometry);
        GeometryFeature districts = SpatialElements.feature(geodata, "districts", GeometryKind.POLYGON, utm32,
                GeometryStorage.NATIVE, districtShape, archiveShape);
        return districts;
    }

    @Test
    void oneGeometrySeveralHoldings() {
        GeometryFeature stations = stations();
        GeometryFeature districts = severalHoldings(stations);
        assertThat(stations.getHolding()).hasSize(2);
        Column position = (Column) station.getFeature().get(station.getFeature().size() - 1);
        assertThat(SpatialElements.holdings(position)).containsExactly(stations.getHolding().get(1));
        assertThat(SpatialElements.features(lon)).containsExactly(stations);
        assertThat(districts.getHolding()).hasSize(2);
        assertThat(SpatialModelValidator.check(model)).isEmpty();
    }

    /** Section "An envelope as four numbers": the bounding box of each commune beside its shape. */
    private GeometryFeature communeBoxes(GeometryFeature communes) {
        Column minX = column(commune, "min_x", doublePrecision);
        Column minY = column(commune, "min_y", doublePrecision);
        Column maxX = column(commune, "max_x", doublePrecision);
        Column maxY = column(commune, "max_y", doublePrecision);
        GeometryFeature communeBoxes = SpatialElements.envelope(geodata, "communeBoxes", communes, minX, minY, maxX,
                maxY);
        return communeBoxes;
    }

    @Test
    void anEnvelopeAsFourNumbers() {
        GeometryFeature communes = communes();
        GeometryFeature communeBoxes = communeBoxes(communes);
        assertThat(communeBoxes.getDerivation()).isEqualTo(GeometryDerivation.ENVELOPE);
        assertThat(communeBoxes.getOf()).isSameAs(communes);
        assertThat(communeBoxes.getKind()).isEqualTo(GeometryKind.POLYGON);
        assertThat(SpatialElements.derivations(communes)).containsExactly(communeBoxes);
        assertThat(SpatialModelValidator.check(model)).isEmpty();
    }

    /** The counties in their column shape, keyed by id. */
    private GeometryFeature counties() {
        county = table("county");
        countyId = key(county);
        column(county, "name", varchar);
        Column countyShape = column(county, "shape", geometry);
        return SpatialElements.feature(geodata, "counties", GeometryKind.POLYGON, utm32, GeometryStorage.NATIVE,
                countyShape);
    }

    /** Section "An envelope as a list": the bounding box of a county as an array, as GeoJSON writes a bbox. */
    private GeometryFeature countyBoxes(GeometryFeature counties) {
        Column bbox = column(county, "bbox", doubleArray);
        GeometryFeature countyBoxes = SpatialElements.derived(geodata, "countyBoxes", GeometryDerivation.ENVELOPE,
                counties);
        SpatialElements.list(countyBoxes, bbox, null, Ordinate.MIN_EAST, Ordinate.MIN_NORTH, Ordinate.MAX_EAST,
                Ordinate.MAX_NORTH);
        return countyBoxes;
    }

    @Test
    void anEnvelopeAsAList() {
        GeometryFeature countyBoxes = countyBoxes(counties());
        assertThat(countyBoxes.getReferenceSystem()).as("the system of the county").isSameAs(utm32);
        assertThat(SpatialModelValidator.check(model)).isEmpty();
    }

    /** Section "An envelope as one geometry": the extent of a state as well-known text. */
    private GeometryFeature stateExtents() {
        Table state = table("state");
        Column stateShape = column(state, "shape", geometry);
        Column extent = column(state, "extent", varchar);
        GeometryFeature states = SpatialElements.feature(geodata, "states", GeometryKind.POLYGON, utm32,
                GeometryStorage.NATIVE, stateShape);
        GeometryFeature stateExtents = SpatialElements.derived(geodata, "stateExtents", GeometryDerivation.ENVELOPE,
                states);
        SpatialElements.whole(stateExtents, extent, GeometryStorage.WKT);
        return stateExtents;
    }

    @Test
    void anEnvelopeAsOneGeometry() {
        GeometryFeature stateExtents = stateExtents();
        assertThat(stateExtents.getKind()).isEqualTo(GeometryKind.POLYGON);
        assertThat(SpatialModelValidator.check(model)).isEmpty();
    }

    /** Section "An envelope with a height": the box of a building in six numbers, part by part. */
    private PartsHolding buildingBoxes() {
        Table building = table("building");
        Column volume = column(building, "volume", geometry);
        Column minX = column(building, "min_x", doublePrecision);
        Column minY = column(building, "min_y", doublePrecision);
        Column minZ = column(building, "min_z", doublePrecision);
        Column maxX = column(building, "max_x", doublePrecision);
        Column maxY = column(building, "max_y", doublePrecision);
        Column maxZ = column(building, "max_z", doublePrecision);
        GeometryFeature buildings = SpatialElements.feature(geodata, "buildings", GeometryKind.POLYGON, crs84h,
                GeometryStorage.NATIVE, volume);
        buildings.setHasHeight(true);
        GeometryFeature buildingBoxes = SpatialElements.derived(geodata, "buildingBoxes", GeometryDerivation.ENVELOPE,
                buildings);
        buildingBoxes.setHasHeight(true);
        PartsHolding box = SpatialElements.parts(buildingBoxes);
        SpatialElements.part(box, Ordinate.MIN_EAST, minX);
        SpatialElements.part(box, Ordinate.MIN_NORTH, minY);
        SpatialElements.part(box, Ordinate.MIN_HEIGHT, minZ);
        SpatialElements.part(box, Ordinate.MAX_EAST, maxX);
        SpatialElements.part(box, Ordinate.MAX_NORTH, maxY);
        SpatialElements.part(box, Ordinate.MAX_HEIGHT, maxZ);
        return box;
    }

    @Test
    void anEnvelopeWithAHeight() {
        PartsHolding box = buildingBoxes();
        assertThat(box.getPart()).hasSize(6);
        assertThat(((GeometryFeature) box.eContainer()).isHasHeight()).isTrue();
        assertThat(SpatialModelValidator.check(model)).isEmpty();
    }

    /**
     * Section "Two corners": map sheets by their upper left and lower right corner as points, tiles by their least
     * and greatest corner as four numbers.
     */
    private List<GeometryFeature> corners() {
        Table sheet = table("sheet");
        Column area = column(sheet, "area", geometry);
        Column upperLeftCorner = column(sheet, "upper_left", geometry);
        Column lowerRightCorner = column(sheet, "lower_right", geometry);
        Table tile = table("tile");
        Column tileShape = column(tile, "shape", geometry);
        Column minX = column(tile, "min_x", doublePrecision);
        Column minY = column(tile, "min_y", doublePrecision);
        Column maxX = column(tile, "max_x", doublePrecision);
        Column maxY = column(tile, "max_y", doublePrecision);
        GeometryFeature sheets = SpatialElements.feature(geodata, "sheets", GeometryKind.POLYGON, utm32,
                GeometryStorage.NATIVE, area);
        GeometryFeature tiles = SpatialElements.feature(geodata, "tiles", GeometryKind.POLYGON, utm32,
                GeometryStorage.NATIVE, tileShape);
        GeometryFeature upperLeft = SpatialElements.derived(geodata, "sheetUpperLeft",
                GeometryDerivation.CORNER_NORTH_WEST, sheets);
        SpatialElements.whole(upperLeft, upperLeftCorner, GeometryStorage.NATIVE);
        GeometryFeature lowerRight = SpatialElements.derived(geodata, "sheetLowerRight",
                GeometryDerivation.CORNER_SOUTH_EAST, sheets);
        SpatialElements.whole(lowerRight, lowerRightCorner, GeometryStorage.NATIVE);
        GeometryFeature least = SpatialElements.derived(geodata, "tileLeast", GeometryDerivation.CORNER_SOUTH_WEST,
                tiles);
        SpatialElements.pointParts(least, minX, minY);
        GeometryFeature greatest = SpatialElements.derived(geodata, "tileGreatest",
                GeometryDerivation.CORNER_NORTH_EAST, tiles);
        SpatialElements.pointParts(greatest, maxX, maxY);
        return List.of(upperLeft, lowerRight, least, greatest);
    }

    @Test
    void twoCorners() {
        assertThat(corners()).extracting(GeometryFeature::getKind).containsOnly(GeometryKind.POINT);
        assertThat(SpatialModelValidator.check(model)).isEmpty();
    }

    /** Section "A stored centroid": the centre of each commune and the point its label is placed at. */
    private List<GeometryFeature> centres(GeometryFeature communes) {
        Column centre = column(commune, "centre", geometry);
        Column labelPoint = column(commune, "label_point", geometry);
        GeometryFeature centres = SpatialElements.derived(geodata, "communeCentres", GeometryDerivation.CENTROID,
                communes);
        SpatialElements.whole(centres, centre, GeometryStorage.NATIVE);
        GeometryFeature labels = SpatialElements.derived(geodata, "communeLabels",
                GeometryDerivation.POINT_ON_SURFACE, communes);
        SpatialElements.whole(labels, labelPoint, GeometryStorage.NATIVE);
        return List.of(centres, labels);
    }

    @Test
    void aStoredCentroid() {
        GeometryFeature communes = communes();
        List<GeometryFeature> centres = centres(communes);
        assertThat(SpatialElements.derivations(communes)).containsExactlyElementsOf(centres);
        assertThat(SpatialModelValidator.check(model)).isEmpty();
    }

    /** Section "Keys from geometry": communes into counties, by a point of their own. */
    @Test
    void keysFromGeometry() {
        GeometryFeature communes = communes();
        GeometryFeature counties = counties();
        Column communeCountyKey = column(commune, "county_id", integer);
        SpatialRelation r = SpatialElements.relation(geodata, "communesInCounties", communes, communeId, counties,
                countyId, SpatialPredicate.CENTROID_WITHIN, SpatialAssignment.EXACTLY_ONE, UnmatchedPolicy.KEEP_NULL);
        SpatialElements.explicitKey(r, communeCountyKey);
        r.setTieBreak(TieBreak.LOWEST_PARENT_KEY);
        Table pairs = table("commune_county");
        column(pairs, "commune_id", integer);
        column(pairs, "county_id", integer);
        SpatialElements.result(r, pairs);
        assertThat(SpatialElements.asChild(communes)).containsExactly(r);
        assertThat(SpatialElements.asParent(counties)).containsExactly(r);
        assertThat(SpatialElements.isDeclaredKey(communeId)).isTrue();
        assertThat(SpatialModelValidator.check(model)).isEmpty();
    }

    /** Section "Keys from geometry", shares and distances. */
    @Test
    void sharesAndDistances() {
        GeometryFeature communes = communes();
        Table electoral = table("electoral_district");
        Column electoralId = key(electoral);
        GeometryFeature electoralDistricts = SpatialElements.feature(geodata, "electoralDistricts",
                GeometryKind.POLYGON, wgs84, GeometryStorage.NATIVE, column(electoral, "shape", geometry));
        SpatialRelation shares = SpatialElements.relation(geodata, "communesInElectoralDistricts", communes,
                communeId, electoralDistricts, electoralId, SpatialPredicate.INTERSECTS, SpatialAssignment.ALL_WEIGHTED,
                UnmatchedPolicy.KEEP_NULL);
        SpatialElements.weight(shares, SpatialWeight.AREA, "0.01");
        SpatialElements.workingSystem(shares, utm32);
        GeometryFeature stations = stations();
        SpatialRelation near = SpatialElements.relation(geodata, "stationsNearCommunes", stations, stationId,
                communes, communeId, SpatialPredicate.WITHIN_DISTANCE, SpatialAssignment.ALL_EQUAL,
                UnmatchedPolicy.KEEP_NULL);
        SpatialElements.withinDistance(near, "5000");
        SpatialElements.workingSystem(near, utm32);
        assertThat(SpatialModelValidator.check(model)).isEmpty();
    }

    /** Section "Grid cells": the stations, held as numbers, into quadkey tiles of two zooms and into H3 cells. */
    @Test
    void gridCells() {
        GeometryFeature stations = stations();
        GridSystem quadkey = SpatialElements.quadkey(geodata);
        GridLevel tiles = SpatialElements.grid(geodata, "stationTiles", stations, stationId, quadkey, 12, null);
        GridLevel cells = SpatialElements.grid(geodata, "stationCells", stations, stationId, quadkey, 15, tiles);
        GridLevel hexagons = SpatialElements.grid(geodata, "stationHexagons", stations, stationId,
                SpatialElements.h3(geodata), 7, null);
        hexagons.setResultForm(ResultForm.VIEW);
        GridSystem geohash = SpatialElements.geohash(geodata);
        GridSystem s2 = SpatialElements.gridSystem(geodata, "https://s2geometry.io/", 0, 30, true);
        Table stationCells = table("station_cells");
        column(stationCells, "station_id", integer);
        column(stationCells, "tile_12", varchar);
        column(stationCells, "tile_15", varchar);
        SpatialElements.result(cells, stationCells);
        assertThat(SpatialElements.chain(cells)).containsExactly(tiles, cells);
        assertThat(SpatialElements.gridSystem(station, "quadkey")).contains(quadkey);
        assertThat(SpatialElements.gridSystems(model)).containsExactly(quadkey, hexagons.getSystem(), geohash, s2);
        assertThat(SpatialModelValidator.check(model)).isEmpty();
    }

    /** Section "Distance classes": how far each station lies from the nearest river. */
    @Test
    void distanceClasses() {
        GeometryFeature stations = stations();
        Column course = column(table("river"), "course", geometry);
        GeometryFeature rivers = SpatialElements.feature(geodata, "rivers", GeometryKind.LINE, utm32,
                GeometryStorage.NATIVE, course);
        DistanceBand toTheRiver = SpatialElements.band(geodata, "toTheRiver", stations, stationId, rivers, "500",
                "2000", "10000");
        toTheRiver.setWorkingSystem(utm32);
        Table classes = table("station_river_band");
        column(classes, "station_id", integer);
        column(classes, "band", integer);
        SpatialElements.result(toTheRiver, classes);
        assertThat(toTheRiver.getBound()).containsExactly("500", "2000", "10000");
        assertThat(SpatialModelValidator.check(model)).isEmpty();
    }

    /**
     * Section "Reference systems": the URI, the EPSG number, one system in two elements; the stored order of a whole
     * holding against the order of the register.
     */
    @Test
    void referenceSystems() {
        ReferenceSystem utm32Again = SpatialElements.referenceSystem(geodata, "utm32", ReferenceSystemAuthority.EPSG,
                "25832", ReferenceSystemKind.PROJECTED, AxisOrder.EAST_NORTH);
        assertThat(ReferenceSystems.uri(utm32)).isEqualTo("http://www.opengis.net/def/crs/EPSG/0/25832");
        assertThat(ReferenceSystems.uri(crs84)).isEqualTo("http://www.opengis.net/def/crs/OGC/1.3/CRS84");
        assertThat(ReferenceSystems.epsg(utm32)).hasValue(25832);
        assertThat(ReferenceSystems.same(utm32, utm32Again)).isTrue();
        assertThat(ReferenceSystems.name(wgs84)).isEqualTo("EPSG 4326");
        Table survey = table("survey");
        Column position = column(survey, "position", bytea);
        GeometryFeature surveys = SpatialElements.feature(geodata, "surveys", GeometryKind.POINT, wgs84);
        SpatialElements.whole(surveys, position, GeometryStorage.WKB).setAxisOrder(AxisOrder.NORTH_EAST);
        assertThat(((WholeHolding) surveys.getHolding().get(0)).getAxisOrder()).isEqualTo(AxisOrder.NORTH_EAST);
        assertThat(wgs84.getAxisOrder()).isEqualTo(AxisOrder.NORTH_EAST);
        assertThat(SpatialModelValidator.check(model)).isEmpty();
    }

    /** Section "The standard set": the systems, grid systems and the type Geometry from the jar. */
    @Test
    void theStandardSet() {
        ResourceSet resourceSet = new ResourceSetImpl();
        Package spatial = SpatialStandards.load(resourceSet);
        ReferenceSystem utm32 = SpatialStandards.epsg(spatial, 25832).orElseThrow();
        ReferenceSystem crs84 = SpatialStandards.referenceSystem(spatial, ReferenceSystemAuthority.OGC, "CRS84")
                .orElseThrow();
        GeometryType geometryType = SpatialElements.geometryType(spatial);
        assertThat(ReferenceSystems.uri(utm32)).isEqualTo("http://www.opengis.net/def/crs/EPSG/0/25832");
        assertThat(crs84.getAxisOrder()).isEqualTo(AxisOrder.EAST_NORTH);
        assertThat(SpatialElements.isGeometryType(geometryType)).isTrue();
        assertThat(SpatialElements.gridSystem(spatial, SpatialElements.H3)).isPresent();
        ResourceSet reading = new ResourceSetImpl();
        SpatialStandards.map(reading);
        assertThat(reading.getURIConverter().getURIMap()).isNotEmpty();
    }

    /** Section "A property of the type Geometry": a class of a logical model with a property that holds a geometry. */
    @Test
    void aPropertyOfTheTypeGeometry() {
        GeometryFeature stations = stations();
        Package logical = owned(model, "logical", CoreFactory.eINSTANCE.createPackage());
        org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class weatherStation = owned(logical, "WeatherStation",
                CoreFactory.eINSTANCE.createClass());
        Attribute location = CoreFactory.eINSTANCE.createAttribute();
        location.setName("location");
        weatherStation.getFeature().add(location);
        location.setType(SpatialElements.geometryType(logical));
        SpatialElements.whole(stations, location, GeometryStorage.NATIVE);
        assertThat(SpatialElements.isGeometryType(location.getType())).isTrue();
        assertThat(stations.getHolding()).hasSize(2);
        assertThat(SpatialModelValidator.check(model)).isEmpty();
    }

    /** Every row of "Which form do I use", built into one model: each form stands beside the others. */
    @Test
    void everyRowOfWhichFormDoIUse() {
        GeometryFeature communes = communes();
        trails();
        GeometryFeature stations = stations();
        masts();
        wells();
        stopsAndSensors();
        severalHoldings(stations);
        communeBoxes(communes);
        countyBoxes(counties());
        stateExtents();
        buildingBoxes();
        corners();
        centres(communes);
        assertThat(SpatialModelValidator.check(model)).isEmpty();
        assertThat(geodata.getOwnedElement().stream().filter(GeometryFeature.class::isInstance)).hasSize(23);
    }

    /** Every line of code of the README stands in this class, so that README and code cannot drift. */
    @Test
    void everySnippetOfTheReadmeStandsHere() throws IOException {
        Path module = Path.of(System.getProperty("user.dir"));
        String source = Files.readAllLines(module.resolve(
                "src/test/java/org/eclipse/daanse/cwm/model/daanse/spatial/util/SpatialExamplesTest.java")).stream()
                .map(String::trim).collect(Collectors.joining(" "));
        List<String> missing = new ArrayList<>();
        int snippets = 0;
        boolean block = false;
        boolean java = false;
        for (String line : Files.readAllLines(module.resolve("README.md"))) {
            if (line.startsWith("```")) {
                java = !block && line.equals("```java");
                snippets += java ? 1 : 0;
                block = !block;
                continue;
            }
            String code = line.trim();
            int comment = code.indexOf(" // ");
            code = comment >= 0 ? code.substring(0, comment).trim() : code;
            code = code.endsWith(";") ? code.substring(0, code.length() - 1) : code;
            if (java && !code.isEmpty() && !code.startsWith("//") && !source.contains(code)) {
                missing.add(line);
            }
        }
        assertThat(snippets).as("the README shows its functions in code").isGreaterThan(10);
        assertThat(missing).as("lines of the README that no test runs").isEmpty();
    }

    private Table table(String name) {
        return owned(geo, name, R.createTable());
    }

    private static Column column(Table table, String name, Classifier type) {
        Column column = R.createColumn();
        column.setName(name);
        column.setType(type);
        table.getFeature().add(column);
        return column;
    }

    /** The column {@code id} of {@code table} and its primary key. */
    private Column key(Table table) {
        Column id = column(table, "id", integer);
        PrimaryKey pk = R.createPrimaryKey();
        pk.setName(table.getName() + "_pk");
        pk.getFeature().add(id);
        table.getOwnedElement().add(pk);
        return id;
    }

    private SQLSimpleType type(String name, long typeNumber) {
        SQLSimpleType type = R.createSQLSimpleType();
        type.setTypeNumber(typeNumber);
        return owned(geo, name, type);
    }

    private static <T extends org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement> T owned(
            org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace owner, String name, T element) {
        element.setName(name);
        if (owner != null) {
            owner.getOwnedElement().add(element);
        }
        return element;
    }
}
