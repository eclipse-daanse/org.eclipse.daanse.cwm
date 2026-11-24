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

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.DataType;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Model;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.FeatureCollection;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.FeatureProperty;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.util.FeatureCollections;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.util.FeatureProperties;
import org.eclipse.daanse.cwm.model.daanse.spatial.AxisOrder;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryFeature;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryKind;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryMultiplicity;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystem;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystemKind;
import org.eclipse.daanse.cwm.model.daanse.spatial.util.SpatialElements;

/**
 * The worked model of open city geography, as a city publishes it: four collections. {@code districts} are polygons,
 * valid over an interval from a day until a day or still; {@code schools} are points, each in one district, opened at
 * an instant, a day. Both are stored in ETRS89 / UTM 32N (EPSG 25832), as German open geodata often are, each geometry
 * in a property of the type {@code Geometry}; the districts also offer ETRS89 in degrees (EPSG 4258), which its
 * register defines latitude first. {@code stops} are the bus stops of the transit operator, points held as two
 * numbers {@code east} and {@code north} in WGS 84, with the envelope of each kept in four numbers, as a store keeps
 * it to answer a bbox by four comparisons. {@code trees} are the trees of the tree register, each point held twice:
 * as the geometry property {@code position} and as the numbers {@code longitude} and {@code latitude}. The property
 * names are made up and no property holds data on a person.
 *
 * <p>Only the resource stands here: the collections, their properties, the geometry features that say what their
 * geometries are and whose holdings name the properties that hold them.</p>
 */
public final class City {

    public final Model model = CoreFactory.eINSTANCE.createModel();
    public final Package types = pkg(model, "types");
    public final DataType string = type("String");
    public final DataType integer = type("Integer");
    public final DataType decimal = type("Float");
    public final DataType date = type("Date");

    public final Package geodata = pkg(model, "geodata");
    public final ReferenceSystem utm32 = SpatialElements.epsg(geodata, 25832, ReferenceSystemKind.PROJECTED,
            AxisOrder.EAST_NORTH);
    public final ReferenceSystem etrs89 = SpatialElements.epsg(geodata, 4258, ReferenceSystemKind.GEOGRAPHIC,
            AxisOrder.NORTH_EAST);
    public final ReferenceSystem crs84 = SpatialElements.crs84(geodata);
    public final GeometryFeature districtShape = SpatialElements.feature(geodata, "districtShape",
            GeometryKind.POLYGON, utm32);
    public final GeometryFeature schoolPoint = SpatialElements.feature(geodata, "schoolPoint", GeometryKind.POINT,
            utm32);

    public final Package features = pkg(model, "features");

    public final FeatureCollection districts = FeatureCollections.create(features, "districts");
    public final FeatureProperty districtId = FeatureProperties.required(districts, "district_id", string);
    public final FeatureProperty districtLabel = FeatureProperties.create(districts, "label", string);
    public final FeatureProperty residents = FeatureProperties.create(districts, "residents", integer);
    public final FeatureProperty area = FeatureProperties.create(districts, "area_km2", decimal);
    public final FeatureProperty validFrom = FeatureProperties.create(districts, "valid_from", date);
    public final FeatureProperty validTo = FeatureProperties.create(districts, "valid_to", date);
    public final FeatureProperty outline = FeatureProperties.geometry(districts, "outline", types, districtShape);

    public final FeatureCollection schools = FeatureCollections.create(features, "schools");
    public final FeatureProperty schoolId = FeatureProperties.required(schools, "school_id", string);
    public final FeatureProperty schoolLabel = FeatureProperties.create(schools, "label", string);
    public final FeatureProperty kind = FeatureProperties.create(schools, "kind", string);
    public final FeatureProperty pupils = FeatureProperties.create(schools, "pupils", integer);
    public final FeatureProperty opened = FeatureProperties.create(schools, "opened", date);
    public final FeatureProperty schoolDistrict = FeatureProperties.create(schools, "district_id", string);
    public final FeatureProperty location = FeatureProperties.geometry(schools, "location", types, schoolPoint);

    public final FeatureCollection stops = FeatureCollections.create(features, "stops");
    public final FeatureProperty stopId = FeatureProperties.required(stops, "stop_id", string);
    public final FeatureProperty stopLabel = FeatureProperties.create(stops, "label", string);
    public final FeatureProperty east = FeatureProperties.create(stops, "east", decimal);
    public final FeatureProperty north = FeatureProperties.create(stops, "north", decimal);
    public final FeatureProperty minEast = FeatureProperties.create(stops, "min_east", decimal);
    public final FeatureProperty minNorth = FeatureProperties.create(stops, "min_north", decimal);
    public final FeatureProperty maxEast = FeatureProperties.create(stops, "max_east", decimal);
    public final FeatureProperty maxNorth = FeatureProperties.create(stops, "max_north", decimal);
    public final GeometryFeature stopPoint = SpatialElements.point(geodata, "stopPoint", crs84, east, north);
    public final GeometryFeature stopBox = SpatialElements.envelope(geodata, "stopBox", stopPoint, minEast, minNorth,
            maxEast, maxNorth);

    public final FeatureCollection trees = FeatureCollections.create(features, "trees");
    public final FeatureProperty treeId = FeatureProperties.required(trees, "tree_id", string);
    public final FeatureProperty species = FeatureProperties.create(trees, "species", string);
    public final FeatureProperty longitude = FeatureProperties.create(trees, "longitude", decimal);
    public final FeatureProperty latitude = FeatureProperties.create(trees, "latitude", decimal);
    public final GeometryFeature treePoint = SpatialElements.feature(geodata, "treePoint", GeometryKind.POINT, crs84);
    public final FeatureProperty position = FeatureProperties.geometry(trees, "position", types, treePoint);

    public City() {
        model.setName("city");
        districtShape.setMultiplicity(GeometryMultiplicity.MULTI);
        schoolPoint.setMultiplicity(GeometryMultiplicity.SINGLE);

        districts.setDisplayName("City districts");
        FeatureCollections.describe(districts, "The districts of the city, each valid from a day until a day.");
        FeatureCollections.key(districts, districtId);
        FeatureCollections.geometry(districts, districtShape);
        FeatureCollections.interval(districts, validFrom, validTo);
        districts.getReferenceSystems().add(etrs89);
        districtId.setQueryable(false);
        districtLabel.setSortable(true);
        residents.setSortable(true);
        residents.setMinimum("0");

        schools.setDisplayName("Schools");
        FeatureCollections.key(schools, schoolId);
        FeatureCollections.geometry(schools, schoolPoint);
        FeatureCollections.instant(schools, opened);
        schoolId.setQueryable(false);
        schoolLabel.setSortable(true);
        kind.getAllowedValues().addAll(List.of("primary", "secondary", "vocational"));
        pupils.setMinimum("0");
        FeatureCollections.describe(schoolDistrict, "The district the school lies in.");

        stops.setDisplayName("Bus stops");
        FeatureCollections.key(stops, stopId);
        FeatureCollections.geometry(stops, stopPoint);
        stopId.setQueryable(false);
        for (FeatureProperty edge : List.of(minEast, minNorth, maxEast, maxNorth)) {
            FeatureProperties.filterOnly(edge);
        }

        trees.setDisplayName("Trees");
        FeatureCollections.key(trees, treeId);
        SpatialElements.pointParts(treePoint, longitude, latitude);
        FeatureCollections.geometry(trees, treePoint);
        treeId.setQueryable(false);
        species.setSortable(true);
    }

    private DataType type(String name) {
        DataType type = CoreFactory.eINSTANCE.createDataType();
        return owned(types, name, type);
    }

    private static Package pkg(Model model, String name) {
        return owned(model, name, CoreFactory.eINSTANCE.createPackage());
    }

    private static <T extends ModelElement> T owned(Namespace owner, String name, T element) {
        element.setName(name);
        owner.getOwnedElement().add(element);
        return element;
    }
}
