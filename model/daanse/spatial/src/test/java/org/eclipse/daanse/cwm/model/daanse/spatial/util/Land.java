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
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryFeature;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryKind;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryStorage;
import org.eclipse.daanse.cwm.model.daanse.spatial.GridSystem;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystem;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystemKind;
import org.eclipse.daanse.cwm.model.daanse.spatial.SpatialAssignment;
import org.eclipse.daanse.cwm.model.daanse.spatial.SpatialPredicate;
import org.eclipse.daanse.cwm.model.daanse.spatial.SpatialRelation;
import org.eclipse.daanse.cwm.model.daanse.spatial.SpatialWeight;
import org.eclipse.daanse.cwm.model.daanse.spatial.UnmatchedPolicy;

/**
 * A land in a relational schema {@code geo} (types included): communes, counties and states as areas in ETRS89 / UTM
 * 32N (EPSG 25832); weather stations as points in WGS 84 (EPSG 4326, geographic, which its register defines latitude
 * first; the stations store longitude first); lakes, countries and rivers in ETRS89 LAEA (EPSG 3035), an equal-area
 * system, which its register defines northing first. Each table has a primary key {@code id}; the commune has the
 * column {@code county_id} its administration keeps. The reference systems and the geometry features stand in the
 * package {@code geodata}; the relations are made by each test.
 */
final class Land {

    private static final RelationalFactory R = RelationalFactory.eINSTANCE;

    final Model model = CoreFactory.eINSTANCE.createModel();
    final Schema geo = owned(model, "geo", R.createSchema());
    final SQLSimpleType integer = type("INTEGER", 4);
    final SQLSimpleType varchar = type("VARCHAR", 12);
    final SQLSimpleType decimal = type("DECIMAL", 3);
    final SQLSimpleType blob = type("BLOB", 2004);
    final SQLSimpleType geometry = type("GEOMETRY", 1111);

    final Table commune = table("commune");
    final Column communeId = key(commune);
    final Column communeShape = column(commune, "shape", geometry);
    final Column communeCounty = column(commune, "county_id", integer);
    final Table county = table("county");
    final Column countyId = key(county);
    final Column countyShape = column(county, "shape", geometry);
    final Table state = table("state");
    final Column stateId = key(state);
    final Column stateShape = column(state, "shape", geometry);
    final Table station = table("station");
    final Column stationId = key(station);
    final Column stationPosition = column(station, "position", geometry);
    final Table lake = table("lake");
    final Column lakeId = key(lake);
    final Column lakeShape = column(lake, "shape", geometry);
    final Table country = table("country");
    final Column countryId = key(country);
    final Column countryShape = column(country, "shape", geometry);
    final Table river = table("river");
    final Column riverId = key(river);
    final Column riverCourse = column(river, "course", geometry);

    final Package geodata = pkg("geodata");
    final ReferenceSystem utm32 = SpatialElements.epsg(geodata, 25832, ReferenceSystemKind.PROJECTED,
            AxisOrder.EAST_NORTH);
    final ReferenceSystem wgs84 = SpatialElements.epsg(geodata, 4326, ReferenceSystemKind.GEOGRAPHIC,
            AxisOrder.NORTH_EAST);
    final ReferenceSystem laea = SpatialElements.epsg(geodata, 3035, ReferenceSystemKind.PROJECTED,
            AxisOrder.NORTH_EAST);
    final GeometryFeature communes = SpatialElements.feature(geodata, "communes", GeometryKind.POLYGON, utm32,
            GeometryStorage.NATIVE, communeShape);
    final GeometryFeature counties = SpatialElements.feature(geodata, "counties", GeometryKind.POLYGON, utm32,
            GeometryStorage.NATIVE, countyShape);
    final GeometryFeature states = SpatialElements.feature(geodata, "states", GeometryKind.POLYGON, utm32,
            GeometryStorage.NATIVE, stateShape);
    final GeometryFeature stations = SpatialElements.feature(geodata, "stations", GeometryKind.POINT, wgs84,
            GeometryStorage.NATIVE, stationPosition);
    final GeometryFeature lakes = SpatialElements.feature(geodata, "lakes", GeometryKind.POLYGON, laea,
            GeometryStorage.NATIVE, lakeShape);
    final GeometryFeature countries = SpatialElements.feature(geodata, "countries", GeometryKind.POLYGON, laea,
            GeometryStorage.NATIVE, countryShape);
    final GeometryFeature rivers = SpatialElements.feature(geodata, "rivers", GeometryKind.LINE, laea,
            GeometryStorage.NATIVE, riverCourse);

    /** Communes into counties: the centroid lies within one county, a commune without one keeps a null parent. */
    /** Geohash of the land's model, made in {@code geodata} once. */
    GridSystem geohash() {
        return SpatialElements.geohash(geodata);
    }

    /** H3 of the land's model, made in {@code geodata} once. */
    GridSystem h3() {
        return SpatialElements.h3(geodata);
    }

    /** The quadkey tiles of the land's model, made in {@code geodata} once. */
    GridSystem quadkey() {
        return SpatialElements.quadkey(geodata);
    }

    SpatialRelation communesInCounties() {
        return SpatialElements.relation(geodata, "communesInCounties", communes, communeId, counties, countyId,
                SpatialPredicate.CENTROID_WITHIN, SpatialAssignment.EXACTLY_ONE, UnmatchedPolicy.KEEP_NULL);
    }

    /** Lakes into the countries they lie in, each with its share of the lake's area. */
    SpatialRelation lakesInCountries() {
        return SpatialElements.weight(SpatialElements.relation(geodata, "lakesInCountries", lakes, lakeId,
                countries, countryId, SpatialPredicate.INTERSECTS, SpatialAssignment.ALL_WEIGHTED,
                UnmatchedPolicy.KEEP_NULL), SpatialWeight.AREA, null);
    }

    /** A table of the schema {@code geo}. */
    Table table(String name) {
        return owned(geo, name, R.createTable());
    }

    /** A column of {@code type} in {@code table}. */
    static Column column(Table table, String name, Classifier type) {
        Column column = R.createColumn();
        column.setName(name);
        column.setType(type);
        table.getFeature().add(column);
        return column;
    }

    /** The column {@code id} of {@code table} and its primary key. */
    Column key(Table table) {
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

    private Package pkg(String name) {
        return owned(model, name, CoreFactory.eINSTANCE.createPackage());
    }

    private static <T extends org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement> T owned(
            org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace owner, String name, T element) {
        element.setName(name);
        owner.getOwnedElement().add(element);
        return element;
    }

    Land() {
        model.setName("land");
    }
}
