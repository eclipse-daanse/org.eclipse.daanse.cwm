/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.spatial.build;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.eclipse.daanse.cwm.model.daanse.spatial.util.SpatialModelValidator;
import org.eclipse.daanse.cwm.model.daanse.validation.Findings;
import org.eclipse.daanse.cwm.model.daanse.validation.Rule;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EModelElement;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

/**
 * Programmatic builder for the {@code spatial} metamodel: what a geometry is, where and how it is held, and the rules
 * by which a resolver derives keys from geometry.
 *
 * <p>A spatial relation is resolved once into an ordinary relational one; whoever reads the result, a cube for
 * instance, reads only keys. The model does not describe a join an engine performs: it describes how the assignment of
 * a child row to parent rows arises from geometry, and a resolver evaluates it in the database and leaves a foreign key
 * (a key pair, every child at most one parent) or a bridge with a share (a child in several parents, a part each). It
 * holds no coordinates and knows no OLAP class; a model that reads a result does not depend on it.</p>
 *
 * <p>Every non-containment reference into CWM and between the classes of the package is one-way with an EMOF
 * {@code Property.oppositeRoleName} (prefix {@code spatial}); a geometry feature owns its holdings, a holding in parts
 * its parts. Every element and every enumeration literal carries its documentation. Construction and serialization
 * follow {@code model/daanse/units/.../UnitsEcoreBuilder}.</p>
 */
public final class SpatialEcoreBuilder {

    private static final String GENMODEL = "http://www.eclipse.org/emf/2002/GenModel";
    private static final String ECORE = "http://www.eclipse.org/emf/2002/Ecore";
    private static final String ECORE_OCL = "http://www.eclipse.org/emf/2002/Ecore/OCL";
    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    /** The namespace URI of the package. */
    public static final String NS_URI = "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.spatial";
    /** The source of the metadata of the invariants of a class: name, description and level per rule. */
    public static final String CONSTRAINTS = NS_URI + Findings.CONSTRAINTS;

    private static final String EPL_HEADER = String.join("\n",
            "Copyright (c) 2026 Contributors to the Eclipse Foundation.",
            "",
            "This program and the accompanying materials are made",
            "available under the terms of the Eclipse Public License 2.0",
            "which is available at https://www.eclipse.org/legal/epl-2.0/",
            "",
            "SPDX-License-Identifier: EPL-2.0");

    private final EcoreFactory ef = EcoreFactory.eINSTANCE;
    private final CwmRefs cwm;

    /** The classifiers of {@code objectmodel/core} the metamodel refers to. */
    public static final class CwmRefs {
        final EClass element;
        final EClass modelElement;
        final EClass attribute;
        final EClass cwmClass;
        final EClass dataType;
        final EDataType floatType;
        final EDataType integerType;
        final EDataType booleanType;
        final EDataType stringType;

        CwmRefs(EPackage core) {
            this.element = eClass(core, "Element");
            this.modelElement = eClass(core, "ModelElement");
            this.attribute = eClass(core, "Attribute");
            this.cwmClass = eClass(core, "Class");
            this.dataType = eClass(core, "DataType");
            this.floatType = eDataType(core, "Float");
            this.integerType = eDataType(core, "Integer");
            this.booleanType = eDataType(core, "Boolean");
            this.stringType = eDataType(core, "String");
        }
    }

    public SpatialEcoreBuilder(CwmRefs cwm) {
        this.cwm = cwm;
    }

    /**
     * Builds the metamodel from the CWM Ecores next to {@code moduleDir} (module directory
     * {@code model/daanse/spatial}) and writes it to {@code outFile}.
     */
    public static void generate(File moduleDir, File outFile) throws IOException {
        File cwmDir = new File(moduleDir, "../../cwm").getCanonicalFile();
        ResourceSet rs = newResourceSet();
        EPackage core = loadPackage(rs, ecoreUri(cwmDir, "objectmodel.core/model/core.ecore"));
        outFile.getParentFile().mkdirs();
        save(new SpatialEcoreBuilder(new CwmRefs(core)).build(), rs, URI.createFileURI(outFile.getAbsolutePath()));
    }

    /** Creates the EPackage {@code spatial}: 11 classes and the abstract holding, 14 enumerations. */
    public EPackage build() {
        EPackage p = ef.createEPackage();
        p.setName("spatial");
        p.setNsURI(NS_URI);
        p.setNsPrefix("spatial");
        EAnnotation pa = annotate(p,
                "Geodata for CWM attributes: what a geometry is, where and how it is held, and the rules by which "
                        + "keys are derived from geometry. A GeometryFeature says what a geometry is: the kind, "
                        + "single or multi, with a height or without, the spatial reference system and the epoch of "
                        + "the coordinates, the accuracy, and whether it is a geometry of its own or derived from "
                        + "another one (its envelope, a corner, its centroid, a point on its surface). It owns its "
                        + "holdings, which say where and how the values are held: one attribute with the whole "
                        + "geometry (WholeHolding: in the database's geometry type, as WKB, WKT or GeoJSON), one "
                        + "number per attribute (PartsHolding: the columns lon and lat of a table station), or "
                        + "several numbers in one attribute (ListHolding: an array [west, south, east, north], a "
                        + "text 50.93,11.59). A ReferenceSystem names a coordinate reference system by its "
                        + "authority and code, as an OGC URI does, with what its register defines: its kind, axes "
                        + "and axis order. A GeometryType is the logical data type of a property that holds a whole "
                        + "geometry. Distances and the bounds of distance classes are metres, whatever the "
                        + "reference system. A SpatialRelation "
                        + "says how the rows of a child geometry are assigned to the rows of a parent geometry: a "
                        + "predicate, what happens when several parents match or none, what is measured to divide "
                        + "them. A resolver evaluates it once in the database and leaves a key pair (every child "
                        + "at most one parent, a foreign key) or a bridge with a share (a child in several "
                        + "parents); from there on it is plain schema, and whoever reads it, a cube for instance, "
                        + "reads only keys. A GridLevel "
                        + "puts points into the cells of a GridSystem, a DistanceBand puts the distance to a reference "
                        + "geometry into classes. The model holds no coordinates and knows no OLAP class; a model "
                        + "that reads a result does not depend on it. Non-containment references are hidden "
                        + "association ends "
                        + "(Property.oppositeRoleName, prefix spatial).");
        pa.getDetails().put("modelName", "Spatial");
        pa.getDetails().put("copyrightText", EPL_HEADER);
        pa.getDetails().put("oSGiCompatible", "true");
        pa.getDetails().put("basePackage", "org.eclipse.daanse.cwm.model.daanse");
        pa.getDetails().put("resource", "XMI");
        pa.getDetails().put("prefix", "Spatial");

        EAnnotation delegates = ef.createEAnnotation();
        delegates.setSource(ECORE);
        delegates.getDetails().put("invocationDelegates", ECORE_OCL);
        delegates.getDetails().put("settingDelegates", ECORE_OCL);
        delegates.getDetails().put("validationDelegates", ECORE_OCL);
        p.getEAnnotations().add(delegates);
        EAnnotation ho = ef.createEAnnotation();
        ho.setSource(ECORE_OCL);
        ho.getDetails().put("hiddenOpposites", "true");
        p.getEAnnotations().add(ho);

        EClass feature = cls(p, "GeometryFeature",
                "What a geometry is: its kind, single or multi, with a height or without, the spatial reference "
                        + "system and the epoch of its coordinates, its accuracy, and whether it is a geometry of its "
                        + "own (the shape of a district) or derived from another one (the envelope of that shape, "
                        + "stored for a search by area). Where and how its values are held says each of its "
                        + "holdings, which it owns: the column shape of the table district (a WholeHolding), or the "
                        + "columns lon and lat of the table station (a PartsHolding). All holdings of one feature "
                        + "hold the same geometry, so that a reader takes any of them: the shape of a district in "
                        + "the tables district and district_archive, or the position of a station both in its "
                        + "geometry column and in its columns lon and lat. A feature states something about "
                        + "attributes, it is not one, as a units::Quantity states a unit.");
        EClass holding = elementCls(p, "GeometryHolding", true,
                "Where and how a geometry is held: as a whole in one attribute (WholeHolding), as numbers one per "
                        + "attribute (PartsHolding), or as several numbers in one attribute (ListHolding). It is "
                        + "owned by the GeometryFeature that says what the geometry is; the feature may own several, "
                        + "each holding the same geometry. An attribute belongs to one holding only.");
        EClass whole = subCls(p, "WholeHolding", holding,
                "One attribute holds the whole geometry, in the form storage says: the column shape of type "
                        + "GEOMETRY of a PostGIS table district (NATIVE), a BLOB of well-known binary (WKB), a "
                        + "text of well-known text or GeoJSON, or a property of the data type Geometry of a logical "
                        + "model, such as a feature collection's (NATIVE). Any kind of geometry is held so. "
                        + "axisOrder says in which order the stored coordinates hold the first two axes.");
        EClass parts = subCls(p, "PartsHolding", holding,
                "The geometry is held as numbers, one per attribute, each named by a CoordinatePart with its "
                        + "role: a table station with the columns lon and lat holds a point by the parts EAST lon "
                        + "and NORTH lat; the columns min_x, min_y, max_x, max_y hold the envelope of the geometry "
                        + "in geom by the parts MIN_EAST, MIN_NORTH, MAX_EAST and MAX_NORTH. Numbers hold a single "
                        + "point or the envelope of another geometry, nothing else, and the attributes of the parts "
                        + "belong to one classifier, so that one row holds one value. A resolver builds the "
                        + "geometry from them (ST_Point, ST_MakeEnvelope).");
        EClass part = elementCls(p, "CoordinatePart", false,
                "One number of a geometry held in parts: its role (the axis, and for an envelope the edge) and "
                        + "the numeric attribute that holds it, such as the column lat of the table station for "
                        + "the role NORTH.");
        EClass list = subCls(p, "ListHolding", holding,
                "The geometry is held as several numbers in one attribute, in the order of ordinate: a "
                        + "multi-valued attribute or an array (a column bbox of type DOUBLE[] with [west, south, "
                        + "east, north], as GeoJSON writes a bbox), or a text with a separator (a column position "
                        + "of type VARCHAR with 50.93,11.59, latitude first: the order NORTH, EAST and the "
                        + "separator ,). Numbers hold a single point or the envelope of another geometry, nothing "
                        + "else.");
        EClass relation = cls(p, "SpatialRelation",
                "A rule that assigns the rows of a child geometry to the rows of a parent geometry: when a "
                        + "parent matches (predicate), what happens when several match (assignment, weight, "
                        + "minimum share, tie break) or none (unmatched), in which reference system it is evaluated. "
                        + "A resolver evaluates it once and writes its result: a key pair (child key, parent key) "
                        + "for EXACTLY_ONE and LARGEST_SHARE, a bridge (child key, parent key, share) for "
                        + "ALL_WEIGHTED and ALL_EQUAL. It never alters a source table. The result holds no time: "
                        + "a territory that changes gets one result per state. Of a geometry with several holdings "
                        + "a resolver reads one in the table of the key, which hold the same value: a whole one "
                        + "where that table has one, else it builds the point or the envelope from the numbers.");
        EClass grid = cls(p, "GridLevel",
                "The key of the cell of a grid a point lies in, of one system and resolution; the cells of a "
                        + "coarser resolution are the level above. No geometric join: both the cell and its "
                        + "parent come from a function, and in a strictly nested grid the parent's key is the "
                        + "head of the cell's. A resolver writes its result, per point the source key and the "
                        + "cell key of each level from the top of its chain down to it; a point without a "
                        + "geometry gets no cell and goes as unmatched says. It never alters the table of the "
                        + "points. A point held as numbers (the columns lon and lat) is read as the point they "
                        + "make.");
        EClass band = cls(p, "DistanceBand",
                "The distance of a geometry to the nearest row of a reference geometry, put into classes by "
                        + "upper bounds in metres (0 to 1 km, 1 to 5 km, above), like a discretization. A resolver "
                        + "writes its result, a pair (source key, class) per source row, the class 1 up to and "
                        + "including the first bound and one more above each bound; a row whose distance cannot be "
                        + "measured gets no class and goes as unmatched says. It never alters the source table. "
                        + "Each geometry is read from a holding in the table of its key, as for a relation.");
        EClass system = cls(p, "ReferenceSystem",
                "A coordinate reference system, named as an OGC URI names it: "
                        + "http://www.opengis.net/def/crs/{authority}/{version}/{code}, EPSG 25832 or OGC CRS84. It "
                        + "holds what its register defines: its kind (degrees, a map projection, Cartesian metres), "
                        + "its axes and their order, and whether it is dynamic. How an attribute stores the "
                        + "coordinates is said by the holding (the stored order of a whole one, the roles of "
                        + "numbers), the epoch they hold for by the geometry feature. Owned by the model; the "
                        + "features and the working systems of relations and bands refer to it.");
        EClass gridSystem = cls(p, "GridSystem",
                "A system of grid cells, known to a resolver by its name, the identifier: geohash (Geohash "
                        + "rectangles, 1 to 12 characters), h3 (H3 hexagons, 0 to 15), quadkey (tiles of the web map, "
                        + "zoom 1 to 23) or a URI. It holds its resolutions and whether its cells nest. Owned by the "
                        + "model, once per identifier; the grid levels refer to it.");
        EClass geometryType = ef.createEClass();
        geometryType.setName("GeometryType");
        geometryType.getESuperTypes().add(cwm.dataType);
        p.getEClassifiers().add(geometryType);
        annotate(geometryType,
                "The logical data type of a property that holds a whole geometry, as the SQL types of a catalog are "
                        + "its elements; named Geometry by convention. A property location of a feature collection "
                        + "is of this type and named by a WholeHolding of storage NATIVE. What the geometry is "
                        + "(kind, multiplicity, height, reference system) is said by the geometry feature that owns "
                        + "the holding, not by the type; the type says nothing of how a database stores a value: "
                        + "the SQL type of a column (GEOMETRY, GEOGRAPHY, a text) is no GeometryType. Numbers that "
                        + "hold a geometry keep their numeric types.");

        EEnum kind = enm(p, "GeometryKind",
                "The dimension of a geometry; a multi form belongs to its kind, the multiplicity says whether rows "
                        + "hold one.",
                "POINT", "A point, or several as a multi-point: a weather station, a tree, the centroid of a "
                        + "district, a corner of an envelope.",
                "LINE", "A line string, or several as a multi-line string: a river, a road, a bus route.",
                "POLYGON", "A polygon with its holes, or several as a multi-polygon: a district, a lake, a parcel. "
                        + "An envelope is a polygon.",
                "MIXED", "Any kind, row by row: a column of geometry collections or of mixed kinds; a resolver "
                        + "takes each row by its own kind.");
        EEnum multiplicity = enm(p, "GeometryMultiplicity",
                "Whether a row holds one geometry of its kind (a point, a line, a polygon) or several as one "
                        + "(a multi-point, a multi-line, a multi-polygon), or either of the two.",
                "EITHER", "Some rows hold one geometry, others a multi form; the first literal, what an unset "
                        + "value reads.",
                "SINGLE", "Every row holds one point, line or polygon. A geometry held as numbers and a derived "
                        + "geometry are single.",
                "MULTI", "Every row holds a multi form: a multi-polygon for a commune with an exclave.");
        EEnum authority = enm(p, "ReferenceSystemAuthority",
                "The register that defines a reference system: the EPSG geodetic parameter dataset, or the OGC "
                        + "(CRS84, CRS84h: WGS 84 with longitude first).",
                "EPSG", "The EPSG geodetic parameter dataset; a code is a positive whole number, 25832 for ETRS89 "
                        + "/ UTM zone 32N, 4326 for WGS 84.",
                "OGC", "The Open Geospatial Consortium: CRS84 (WGS 84, longitude first) and CRS84h (with the "
                        + "ellipsoidal height), the defaults of OGC API Features.");
        EEnum systemKind = enm(p, "ReferenceSystemKind",
                "What the coordinates of a reference system are: longitude and latitude in degrees "
                        + "(GEOGRAPHIC), easting and northing on a map projection in the unit of its axes, metres "
                        + "for most and feet for some (PROJECTED), or Cartesian metres from the earth's centre "
                        + "(GEOCENTRIC).",
                "GEOGRAPHIC", "Longitude and latitude in degrees on an ellipsoid, WGS 84 or ETRS89; areas, lengths "
                        + "and distances are measured on the spheroid.",
                "PROJECTED", "Easting and northing on a map projection in the unit of its axes, metres for UTM "
                        + "and LAEA; areas, lengths and distances are measured in its plane.",
                "GEOCENTRIC", "Cartesian x, y and z in metres from the earth's centre, EPSG 4978; the planar "
                        + "functions read x and y only.");
        EEnum axisOrder = enm(p, "AxisOrder",
                "The order of the first two axes: east (x: the longitude, the easting) first, or north (y: the "
                        + "latitude, the northing) first. A reference system states the order its register "
                        + "defines; a whole holding the order its stored coordinates hold. Numbers have no order "
                        + "of axes: their roles say which number is which.",
                "EAST_NORTH", "East first: x then y, longitude then latitude; the order CRS84 and EPSG 25832 "
                        + "define, and the order in which PostGIS, DuckDB and GeoJSON store coordinates.",
                "NORTH_EAST", "North first: y then x, latitude then longitude; the order EPSG 4326 and EPSG 3035 "
                        + "define.");
        EEnum storage = enm(p, "GeometryStorage",
                "How the attribute of a whole holding holds the geometry: in a geometry type, or as well-known "
                        + "binary, well-known text or GeoJSON, which a resolver converts first. A geometry held as "
                        + "numbers has no storage; its holding is a PartsHolding or a ListHolding.",
                "NATIVE", "A geometry type: the database's own (GEOMETRY or GEOGRAPHY of PostGIS, GEOMETRY of "
                        + "DuckDB spatial, SDO_GEOMETRY of Oracle), or the data type Geometry of a property of a "
                        + "logical model.",
                "WKB", "Well-known binary of OGC Simple Features, or its extended form EWKB, in a binary column.",
                "WKT", "Well-known text in a text column: POINT (11.59 50.93).",
                "GEOJSON", "A GeoJSON geometry object in a text or JSON column: {\"type\": \"Point\", "
                        + "\"coordinates\": [11.59, 50.93]}; longitude and latitude of WGS 84, east first "
                        + "(RFC 7946).");
        EEnum ordinate = enm(p, "Ordinate",
                "The role of one number of a geometry held as numbers: the axis it lies on and, for an envelope, "
                        + "the edge. The axes are named by direction, whatever the reference system calls them: "
                        + "east is the longitude in a geographic system (WGS 84, ETRS89) and the easting in a "
                        + "projected one (UTM); north is the latitude or the northing. The roles say which number "
                        + "is which axis, so that a holding of numbers has no stored axis order.",
                "EAST", "East of a point: the longitude in a geographic system (11.59 for Jena in WGS 84), the "
                        + "easting in metres in a projected one (ETRS89 / UTM zone 32N).",
                "NORTH", "North of a point: the latitude in a geographic system (50.93 for Jena in WGS 84), the "
                        + "northing in metres in a projected one.",
                "HEIGHT", "The height of a point, the third axis of a system of three axes: the ellipsoidal "
                        + "height in metres of CRS84h. Held exactly where the geometry has a height.",
                "MIN_EAST", "The west edge of an envelope (minx of a bounding box): its least longitude in a "
                        + "geographic system, its least easting in a projected one. An envelope across the "
                        + "antimeridian has its west edge east of its east edge, as a GeoJSON bbox writes it.",
                "MIN_NORTH", "The south edge of an envelope (miny): its least latitude or northing.",
                "MAX_EAST", "The east edge of an envelope (maxx): its greatest longitude or easting.",
                "MAX_NORTH", "The north edge of an envelope (maxy): its greatest latitude or northing.",
                "MIN_HEIGHT", "The lowest height of an envelope with a height (minz).",
                "MAX_HEIGHT", "The highest height of an envelope with a height (maxz).");
        EEnum derivation = enm(p, "GeometryDerivation",
                "How a geometry is derived from another one, the one its feature names as of. A derived "
                        + "geometry is stored beside the one it comes from so that a reader need not compute it: "
                        + "a bounding box in four columns for a search by area, a centroid to place a label. A "
                        + "feature without a derivation is a geometry of its own.",
                "ENVELOPE", "The envelope: the least rectangle along the axes that holds the geometry, its "
                        + "bounding box, a single POLYGON. The four columns min_x, min_y, max_x, max_y of the "
                        + "geometry in geom; a GeoJSON bbox; a polygon in a column extent.",
                "CORNER_SOUTH_WEST", "The south-west corner of the envelope, its least east and least north, a "
                        + "single POINT; with CORNER_NORTH_EAST it gives the envelope as its least and its "
                        + "greatest corner.",
                "CORNER_NORTH_EAST", "The north-east corner of the envelope, its greatest east and greatest "
                        + "north, a single POINT; the opposite of CORNER_SOUTH_WEST.",
                "CORNER_NORTH_WEST", "The north-west corner of the envelope, its least east and greatest north, "
                        + "a single POINT: the upper left of a map; with CORNER_SOUTH_EAST it gives the envelope "
                        + "as its upper left and its lower right corner.",
                "CORNER_SOUTH_EAST", "The south-east corner of the envelope, its greatest east and least north, "
                        + "a single POINT: the lower right of a map; the opposite of CORNER_NORTH_WEST.",
                "CENTROID", "The centroid, the centre of mass of the geometry, a single POINT; it may lie "
                        + "outside the geometry, as the centroid of a crescent does.",
                "POINT_ON_SURFACE", "A point that lies on the geometry for certain, as ST_PointOnSurface gives "
                        + "it, a single POINT: where a label is placed, or by which an area is assigned.");
        EEnum predicate = enm(p, "SpatialPredicate",
                "When a parent matches a child: the child lies within the parent (Within of OGC Simple "
                        + "Features), no point of the child lies outside the parent (CoveredBy as JTS, GEOS and "
                        + "PostGIS define it; unlike Within, a point on a border meets it), the two share a point "
                        + "(Intersects of OGC Simple Features), one point of the child lies within the parent "
                        + "(CENTROID_WITHIN: the child's centroid where it lies on the child, else a point on the "
                        + "child's surface, so that a crescent is placed by a point of its own), the parent is the "
                        + "nearest, or it lies within a distance.",
                "WITHIN", "The child lies within the parent: Within of OGC Simple Features; a point on the "
                        + "parent's border does not match.",
                "COVERED_BY", "No point of the child lies outside the parent: CoveredBy as JTS, GEOS and PostGIS "
                        + "define it; the only predicate a point on a border meets.",
                "INTERSECTS", "The child and the parent share a point: Intersects of OGC Simple Features; a river "
                        + "matches every country it touches.",
                "CENTROID_WITHIN", "One point of the child lies within the parent: its centroid where that lies "
                        + "on the child, else a point on its surface; a commune is placed in one county.",
                "NEAREST", "The parent is the nearest one; it assigns EXACTLY_ONE.",
                "WITHIN_DISTANCE", "The parent lies within the distance of the relation, in metres.");
        EEnum assignment = enm(p, "SpatialAssignment",
                "What happens when several parents match: at most one may (EXACTLY_ONE), the one with the "
                        + "largest part wins (LARGEST_SHARE), all stay with their parts divided by their sum "
                        + "(ALL_WEIGHTED), or all stay with equal shares (ALL_EQUAL).",
                "EXACTLY_ONE", "At most one parent may match; a second is an error, or broken by the tie break. "
                        + "The result is a foreign key.",
                "LARGEST_SHARE", "The parent with the largest part of the child by weight wins. The result is a "
                        + "foreign key.",
                "ALL_WEIGHTED", "All matching parents stay, each with its part by weight divided by their sum. "
                        + "The result is a bridge with a share.",
                "ALL_EQUAL", "All matching parents stay with equal shares, 1/n each. The result is a bridge with "
                        + "a share.");
        EEnum weight = enm(p, "SpatialWeight",
                "What is measured to compare or divide the matches of a child: the area of the intersection "
                        + "relative to the child's area, or the length relative to the child's length.",
                "AREA", "The area of the intersection relative to the area of the child, for a polygon.",
                "LENGTH", "The length of the intersection relative to the length of the child, for a line.");
        EEnum unmatched = enm(p, "UnmatchedPolicy",
                "What happens to a row a rule cannot place (a child no parent matches, a point without a cell, "
                        + "a source whose distance cannot be measured): it stays with a null parent key, cell or "
                        + "class (KEEP_NULL; how that shows is the policy of the model that reads the result), a "
                        + "resolver stops and names it (ERROR), or it leaves the result (DROP).",
                "KEEP_NULL", "The row stays with a null parent key, cell or class; the first literal, what an "
                        + "unset value reads.",
                "ERROR", "A resolver stops and names the rows it cannot place.",
                "DROP", "The row leaves the result; facts that reach the parent through it vanish from the sums.");
        EEnum tieBreak = enm(p, "TieBreak",
                "Which parent wins an exact tie of EXACTLY_ONE: none, the tie is an error (as without a tie "
                        + "break), or the parent with the lowest key.",
                "ERROR", "No parent wins: the tie is an error, as without a tie break.",
                "LOWEST_PARENT_KEY", "The parent with the lowest key wins, so that a view need not stop.");
        EEnum resultForm = enm(p, "ResultForm",
                "How a resolver leaves its result: a table written once, which any database reads, or a view "
                        + "recomputed whenever read, which needs the spatial functions in the database.",
                "TABLE", "A table written once; any database reads it, and a rule that has to stop can.",
                "VIEW", "A view recomputed whenever it is read; it needs the spatial functions and cannot stop.");

        attr(feature, "kind", kind, 1, 1,
                "The kind of the geometry. A derived geometry has the kind of its derivation: an envelope is a "
                        + "polygon, a corner, a centroid and a point on the surface are points. A geometry held as "
                        + "numbers is a point or an envelope.");
        attr(feature, "multiplicity", multiplicity, 1, 1,
                "Whether a row holds a single geometry of the kind, a multi form, or either; a geometry held as "
                        + "numbers and a derived geometry are single.");
        attr(feature, "hasHeight", cwm.booleanType, 1, 1,
                "Whether the coordinates hold a height as a third value; that needs a reference system of three "
                        + "axes. Held as numbers, a point with a height has a HEIGHT number, an envelope with a "
                        + "height a MIN_HEIGHT and a MAX_HEIGHT; without a height they have none.")
                .setDefaultValueLiteral("false");
        EReference featureSystem = ref(feature, "referenceSystem", system, 1, 1,
                "The coordinate reference system of the geometry, the same in every holding: OGC CRS84 for the "
                        + "longitude and latitude of weather stations, EPSG 25832 for the parcels of a land "
                        + "register. A derived geometry is in the system of the geometry it is derived from.");
        oppositeRole(featureSystem, "spatialGeometryFeatureInSystem");
        attr(feature, "coordinateEpoch", cwm.floatType, 0, 1,
                "The epoch the coordinates hold for, a decimal year such as 2010.5, stated where the reference "
                        + "system is dynamic and only there: with the system it is the coordinate metadata of the "
                        + "geometry (OGC Topic 2), so that two geometries of one dynamic system may hold two "
                        + "epochs.");
        attr(feature, "accuracy", cwm.floatType, 0, 1,
                "The positional accuracy in metres, a decimal number; for diagnostics only.");
        attr(feature, "derivation", derivation, 0, 1,
                "Whether the geometry is derived from the one of, and how: its ENVELOPE, a corner of the "
                        + "envelope, its CENTROID, a POINT_ON_SURFACE. Stated exactly where of is; unset, the "
                        + "geometry is one of its own, as the shape of a district is. The envelope of the shape "
                        + "in the column geom, stored in the columns min_x, min_y, max_x and max_y of the same "
                        + "table, is a feature of the derivation ENVELOPE of the shape's feature, held in parts.")
                .setUnsettable(true);
        EReference of = ref(feature, "of", feature, 0, 1,
                "The geometry this one is derived from, as derivation says; stated exactly where derivation is. "
                        + "Another feature, never this one nor one derived from this one, in the same reference "
                        + "system: the feature of the shape of a district, of which this one is the envelope.");
        oppositeRole(of, "spatialDerivedGeometryFeature");
        cont(feature, "holding", holding, 1, -1,
                "Where and how the geometry is held, at least once: one attribute with the whole geometry, one "
                        + "number per attribute, or several numbers in one attribute. Several holdings hold the "
                        + "same geometry: the same meaning in two tables (the column shape of the tables district "
                        + "and district_archive), or one table that holds a point both in a geometry column and "
                        + "in two columns of numbers.");

        EReference wholeAttribute = ref(whole, "attribute", cwm.attribute, 1, 1,
                "The attribute that holds the geometry: a relational column, a record field, or a property of a "
                        + "class (of the data type Geometry where the model is logical).");
        oppositeRole(wholeAttribute, "spatialWholeHolding");
        attr(whole, "storage", storage, 1, 1, "How the attribute holds the geometry.");
        attr(whole, "axisOrder", axisOrder, 1, 1,
                "The order in which the stored coordinates hold the first two axes: east (the longitude, the "
                        + "easting) first, as PostGIS, DuckDB and GeoJSON hold them, or north (the latitude, the "
                        + "northing) first. It may differ from the order the reference system defines (EPSG 4326 "
                        + "defines latitude first, a PostGIS column of it holds longitude first), and two holdings "
                        + "of one system may store their coordinates in two orders; GEOJSON storage holds east "
                        + "first always (RFC 7946).");

        cont(parts, "part", part, 1, -1,
                "The numbers and the attributes that hold them, each role once: EAST and NORTH for a point, and "
                        + "HEIGHT where it has a height; MIN_EAST, MIN_NORTH, MAX_EAST and MAX_NORTH for an "
                        + "envelope, and MIN_HEIGHT and MAX_HEIGHT where it has a height. Their order carries no "
                        + "meaning: the role says which number a part is.");

        attr(part, "role", ordinate, 1, 1, "Which number the attribute holds: an axis of a point, an edge of an "
                + "envelope.");
        EReference partAttribute = ref(part, "attribute", cwm.attribute, 1, 1,
                "The attribute that holds the number, of a numeric type: the column lat of the table station.");
        oppositeRole(partAttribute, "spatialCoordinatePart");

        EReference listAttribute = ref(list, "attribute", cwm.attribute, 1, 1,
                "The attribute that holds the numbers: a multi-valued attribute or one of an array type where "
                        + "there is no separator, one of a text type where there is one.");
        oppositeRole(listAttribute, "spatialListHolding");
        EAttribute ordinates = attr(list, "ordinate", ordinate, 1, -1,
                "The roles of the numbers in the order they are held, each once: NORTH, EAST for the text "
                        + "50.93,11.59; MIN_EAST, MIN_NORTH, MAX_EAST, MAX_NORTH for a GeoJSON bbox.");
        ordinates.setUnique(false);
        attr(list, "separator", cwm.stringType, 0, 1,
                "The text between two numbers where the attribute is a text, such as , or a blank; unset where "
                        + "the attribute holds the numbers as several values. A number in the text is written as "
                        + "SQL and JSON write it: an optional sign, digits, a point before the fraction.");

        EReference child = ref(relation, "child", feature, 1, 1, "The geometry that is assigned.");
        oppositeRole(child, "spatialRelationAsChild");
        EReference parent = ref(relation, "parent", feature, 1, 1, "The geometry it is assigned to.");
        oppositeRole(parent, "spatialRelationAsParent");
        EReference childKey = ref(relation, "childKey", cwm.attribute, 1, 1,
                "The key column of a table that holds the child geometry; the first column of the result. Where "
                        + "the table declares no unique key of this column alone, the rows of one key are the parts "
                        + "of one child and are united first (a postcode area held in four rows).");
        oppositeRole(childKey, "spatialRelationOfChildKey");
        EReference parentKey = ref(relation, "parentKey", cwm.attribute, 1, 1,
                "The key column of a table that holds the parent geometry; the second column of the result. "
                        + "Where the table declares no unique key of this column alone, the rows of one key are the "
                        + "parts of one parent and are united first.");
        oppositeRole(parentKey, "spatialRelationOfParentKey");
        attr(relation, "predicate", predicate, 1, 1, "When a parent matches a child.");
        attr(relation, "distance", cwm.floatType, 0, 1,
                "The distance of WITHIN_DISTANCE in metres, a decimal number above zero, whatever the reference "
                        + "system it is measured in: in a geographic system it is measured on the spheroid.");
        attr(relation, "assignment", assignment, 1, 1, "What happens when more than one parent matches.");
        attr(relation, "weight", weight, 0, 1,
                "What is measured to compare or divide the matches of a child; in a geographic reference system "
                        + "areas and lengths are measured in metres on the spheroid.")
                .setUnsettable(true);
        attr(relation, "unmatched", unmatched, 1, 1, "What happens when no parent matches a child.");
        attr(relation, "minimumShare", cwm.floatType, 0, 1,
                "The smallest share of the child by weight that counts as a match, a decimal number from 0 "
                        + "below 1 (0.01 is 1 %); a smaller part is a remainder of digitising. A share, not a "
                        + "distance.")
                .setDefaultValueLiteral("0");
        attr(relation, "tieBreak", tieBreak, 0, 1, "Which parent wins an exact tie of EXACTLY_ONE.");
        EReference explicitKey = ref(relation, "explicitKey", cwm.attribute, 0, 1,
                "A column of the child table that already holds a parent key; where it is not null it wins "
                        + "over the geometry (an exclave belongs where it is administered).");
        oppositeRole(explicitKey, "spatialRelationOfExplicitKey");
        EReference relationSystem = ref(relation, "workingSystem", system, 0, 1,
                "The reference system in which predicate and measure are evaluated; needed where child and "
                        + "parent differ in their system. Where it (else the features' own system) is geographic, "
                        + "areas, lengths and distances are measured on the spheroid; a distance is metres in every "
                        + "system.");
        oppositeRole(relationSystem, "spatialRelationWorkingIn");
        EReference result = ref(relation, "result", cwm.cwmClass, 0, 1,
                "The table or view a resolver writes (a relational column set): child key, parent key and, "
                        + "for ALL_WEIGHTED and ALL_EQUAL, the share, in this order. Left out until a resolver "
                        + "has run, or declared in advance.");
        oppositeRole(result, "spatialRelationResult");
        attr(relation, "resultForm", resultForm, 1, 1, "Whether the result is a table or a view.");

        EReference gridSource = ref(grid, "source", feature, 1, 1, "The points that are put into cells.");
        oppositeRole(gridSource, "spatialGridLevel");
        EReference gridSourceKey = ref(grid, "sourceKey", cwm.attribute, 1, 1,
                "The key column of the table that holds the points; the first column of the result. A point has "
                        + "one row: a key held in several rows has no one cell.");
        oppositeRole(gridSourceKey, "spatialGridLevelOfSourceKey");
        EReference gridSystemOfLevel = ref(grid, "system", gridSystem, 1, 1, "The system of the cells.");
        oppositeRole(gridSystemOfLevel, "spatialGridLevelOfSystem");
        attr(grid, "resolution", cwm.integerType, 1, 1,
                "The resolution, precision or zoom within the system, from its minResolution to its "
                        + "maxResolution; unset is none (0 is a resolution of H3).")
                .setUnsettable(true);
        EReference gridParent = ref(grid, "parent", grid, 0, 1,
                "The coarser level above: the same system at a lower resolution, over the same points and key; a "
                        + "chain of levels stays within one system.");
        oppositeRole(gridParent, "spatialGridLevelChild");
        EReference gridResult = ref(grid, "result", cwm.cwmClass, 0, 1,
                "The table or view a resolver writes (a relational column set): the source key, then the cell "
                        + "key of each level from the top of the chain down to this one, in this order. Left out "
                        + "until a resolver has run, or declared in advance.");
        oppositeRole(gridResult, "spatialGridLevelResult");
        attr(grid, "resultForm", resultForm, 1, 1, "Whether the result is a table or a view.");
        attr(grid, "unmatched", unmatched, 1, 1,
                "What happens to a point that gets no cell (it has no geometry): it keeps a row with null cells "
                        + "(KEEP_NULL), a resolver stops and names it (ERROR), or it leaves the result (DROP).");

        EReference bandSource = ref(band, "source", feature, 1, 1, "The geometry whose distance is measured.");
        oppositeRole(bandSource, "spatialDistanceBandAsSource");
        EReference bandSourceKey = ref(band, "sourceKey", cwm.attribute, 1, 1,
                "The key column of the table that holds the source geometry; the first column of the result. The "
                        + "rows of one key are the parts of one source: the nearest of them counts.");
        oppositeRole(bandSourceKey, "spatialDistanceBandOfSourceKey");
        EReference bandReference = ref(band, "reference", feature, 1, 1,
                "The geometry it is measured to; the nearest of its rows counts.");
        oppositeRole(bandReference, "spatialDistanceBandAsReference");
        EAttribute bound = attr(band, "bound", cwm.floatType, 1, -1,
                "The upper bounds of the classes in metres, decimal numbers ascending, whatever the reference "
                        + "system the distance is measured in (in a geographic system on the spheroid). A class "
                        + "holds the distances up to and including its bound, a "
                        + "distance on a bound belongs to the class below it, and a last class above the last bound "
                        + "is added.");
        bound.setUnique(false);
        EReference bandSystem = ref(band, "workingSystem", system, 0, 1,
                "The reference system in which the distance is measured, as in a spatial relation.");
        oppositeRole(bandSystem, "spatialDistanceBandWorkingIn");
        EReference bandResult = ref(band, "result", cwm.cwmClass, 0, 1,
                "The table or view a resolver writes (a relational column set): source key and class, in this "
                        + "order; the class is 1 up to and including the first bound, one more above each bound, "
                        + "none where no distance can be measured. Left out until a resolver has run, or declared "
                        + "in advance.");
        oppositeRole(bandResult, "spatialDistanceBandResult");
        attr(band, "resultForm", resultForm, 1, 1, "Whether the result is a table or a view.");
        attr(band, "unmatched", unmatched, 1, 1,
                "What happens to a source row that gets no class, because no distance can be measured (it has "
                        + "no geometry, or the reference has no row): it keeps a row with a null class "
                        + "(KEEP_NULL), a resolver stops and names it (ERROR), or it leaves the result (DROP).");

        attr(system, "authority", authority, 1, 1, "The register that defines the system.");
        attr(system, "code", cwm.stringType, 1, 1,
                "The code of the system in its register, as text: 25832, 4326, CRS84, CRS84h.");
        attr(system, "version", cwm.stringType, 0, 1,
                "The version of the register's definition; none is 0, as the OGC URI writes it.")
                .setDefaultValueLiteral("0");
        attr(system, "kind", systemKind, 1, 1,
                "Whether the coordinates are degrees, units of a map projection or Cartesian metres.");
        attr(system, "axes", cwm.integerType, 1, 1,
                "The number of axes, 2 or 3 (a third for the height or, geocentric, for z).")
                .setDefaultValueLiteral("2");
        attr(system, "axisOrder", axisOrder, 1, 1,
                "The order of the first two axes as the register defines them: east first (CRS84, EPSG 25832) or "
                        + "north first (EPSG 4326, EPSG 3035); the order of a request or an answer in this system. "
                        + "Required and stated explicitly: it has no default, since a default would copy the "
                        + "register wrongly for every system of the other order; unset is not stated. How an "
                        + "attribute stores the coordinates is said by its holding.")
                .setUnsettable(true);
        attr(system, "dynamic", cwm.booleanType, 1, 1,
                "Whether the system moves with a tectonic plate, so that a coordinate holds for one epoch, which "
                        + "the geometry feature names.")
                .setDefaultValueLiteral("false");

        attr(gridSystem, "minResolution", cwm.integerType, 1, 1,
                "The coarsest resolution of the system: 1 for geohash and quadkey, 0 for h3.");
        attr(gridSystem, "maxResolution", cwm.integerType, 1, 1,
                "The finest resolution of the system: 12 for geohash, 15 for h3, 23 for quadkey.");
        attr(gridSystem, "nested", cwm.booleanType, 1, 1,
                "Whether the coarser cell holds its finer cells strictly, so that the parent's key is the head of "
                        + "the cell's (geohash, quadkey); false where the parent is a function of the cell, which "
                        + "holds its finer cells only approximately (h3).")
                .setDefaultValueLiteral("false");

        invariants(feature, relation, grid, band, system, gridSystem);
        return p;
    }

    /**
     * The rules of {@link SpatialModelValidator} the metamodel states, each an OCL invariant of the class it is about,
     * named by the code of the rule; the other rules ({@link SpatialModelValidator#CHECKED_IN_JAVA}) cannot be stated in
     * OCL and stay with the validator. A rule the validator reports for a feature about its holdings is an invariant of
     * the feature, so that its finding names the feature.
     */
    private void invariants(EClass feature, EClass relation, EClass grid, EClass band, EClass system,
            EClass gridSystem) {
        String envelope = "(not self.of.oclIsUndefined() and self.derivation = GeometryDerivation::ENVELOPE)";
        String numbers = "self.holding->select(h | not h.oclIsKindOf(WholeHolding))";
        String geoJson = "h.oclIsKindOf(WholeHolding) and h.oclAsType(WholeHolding).storage = GeometryStorage::GEOJSON";

        constrain(feature, SpatialModelValidator.SP23, "HeightNeedsThreeAxes", "not self.hasHeight "
                + "or self.referenceSystem.oclIsUndefined() or self.referenceSystem.axes = 3");
        constrain(feature, SpatialModelValidator.SP24, "EpochInADynamicSystem", "self.referenceSystem.oclIsUndefined() "
                + "or not self.referenceSystem.dynamic or not self.coordinateEpoch.oclIsUndefined()");
        constrain(feature, SpatialModelValidator.SP28, "GeoJsonEastFirst", "self.holding->forAll(h | not (" + geoJson
                + ") or h.oclAsType(WholeHolding).axisOrder = AxisOrder::EAST_NORTH)");
        constrain(feature, SpatialModelValidator.SP29, "GeoJsonInWgs84", "self.referenceSystem.oclIsUndefined() "
                + "or self.holding->forAll(h | not (" + geoJson + ")) "
                + "or (not self.referenceSystem.code.oclIsUndefined() "
                + "and (if self.referenceSystem.authority = ReferenceSystemAuthority::OGC then Set{'CRS84', 'CRS84h'} "
                + "else Set{'4326', '4979'} endif)->includes(self.referenceSystem.code.trim()))");
        constrain(feature, SpatialModelValidator.SP30, "NamesItsReferenceSystem",
                "not self.referenceSystem.oclIsUndefined()");
        constrain(feature, SpatialModelValidator.SP31, "AccuracyInMetres", "self.accuracy.oclIsUndefined() "
                + "or (" + decimal("self.accuracy") + " and self.accuracy.toReal() >= 0)");
        constrain(feature, SpatialModelValidator.SP32, "EpochOnlyWhenDynamic", "self.coordinateEpoch.oclIsUndefined() "
                + "or self.referenceSystem.oclIsUndefined() or self.referenceSystem.dynamic");
        constrain(feature, SpatialModelValidator.SP33, "EpochIsADecimalYear",
                "self.coordinateEpoch.oclIsUndefined() or " + decimal("self.coordinateEpoch"));
        constrain(feature, SpatialModelValidator.SP42, "HeldAndNamed", "self.holding->notEmpty() "
                + "and self.holding->forAll(h | "
                + "(h.oclIsKindOf(WholeHolding) implies not h.oclAsType(WholeHolding).attribute.oclIsUndefined()) "
                + "and (h.oclIsKindOf(ListHolding) implies not h.oclAsType(ListHolding).attribute.oclIsUndefined()) "
                + "and (h.oclIsKindOf(PartsHolding) implies (h.oclAsType(PartsHolding).part->notEmpty() "
                + "and h.oclAsType(PartsHolding).part->forAll(p | not p.attribute.oclIsUndefined()))))");
        constrain(feature, SpatialModelValidator.SP43, "NumbersHoldAPointOrAnEnvelope",
                "self.holding->forAll(h | h.oclIsKindOf(WholeHolding)) or " + envelope
                        + " or (self.kind = GeometryKind::POINT and self.multiplicity <> GeometryMultiplicity::MULTI)");
        constrain(feature, SpatialModelValidator.SP44, "EachRoleOnce", "self.holding->forAll(h | "
                + "(h.oclIsKindOf(PartsHolding) implies h.oclAsType(PartsHolding).part->isUnique(p | p.role)) "
                + "and (h.oclIsKindOf(ListHolding) implies h.oclAsType(ListHolding).ordinate->isUnique(o | o)))");
        constrain(feature, SpatialModelValidator.SP45, "RolesFitTheShape", numbers + "->forAll(h | "
                + "let roles : Bag(Ordinate) = " + roles("h") + " in "
                + "(h.oclIsKindOf(PartsHolding) and roles->isEmpty()) "
                + "or (if " + envelope + " then roles->includesAll(" + EDGES + ") and roles->excludesAll(" + AXES + ") "
                + "else self.kind <> GeometryKind::POINT or (roles->includesAll(Set{Ordinate::EAST, Ordinate::NORTH}) "
                + "and roles->excludesAll(" + EDGES_AND_HEIGHTS + ")) endif))");
        constrain(feature, SpatialModelValidator.SP46, "HeightRolesExactlyWithAHeight", numbers + "->forAll(h | "
                + "let roles : Bag(Ordinate) = " + roles("h") + " in "
                + "(h.oclIsKindOf(PartsHolding) and roles->isEmpty()) "
                + "or (not " + envelope + " and self.kind <> GeometryKind::POINT) "
                + "or (let height : Set(Ordinate) = if " + envelope + " then "
                + "Set{Ordinate::MIN_HEIGHT, Ordinate::MAX_HEIGHT} else Set{Ordinate::HEIGHT} endif in "
                + "if self.hasHeight then roles->includesAll(height) else roles->excludesAll(height) endif))");
        constrain(feature, SpatialModelValidator.SP48, "PartsInOneClassifier", "self.holding->forAll(h | "
                + "h.oclIsKindOf(PartsHolding) implies h.oclAsType(PartsHolding).part->collect(p | p.attribute)"
                + "->select(a | not a.oclIsUndefined())->collect(a | a.owner)->select(o | not o.oclIsUndefined())"
                + "->asSet()->size() <= 1)");
        constrain(feature, SpatialModelValidator.SP50, "NotDerivedFromItself",
                "self.of.oclIsUndefined() or self.of->closure(of)->excludes(self)");
        constrain(feature, SpatialModelValidator.SP51, "DerivedKeepsTheSystem", "self.of.oclIsUndefined() "
                + "or self.referenceSystem.oclIsUndefined() or self.of.referenceSystem.oclIsUndefined() or "
                + sameSystem("self.referenceSystem", "self.of.referenceSystem"));
        constrain(feature, SpatialModelValidator.SP52, "KindFollowsDerivation", "self.of.oclIsUndefined() "
                + "or ((if self.derivation = GeometryDerivation::ENVELOPE then self.kind = GeometryKind::POLYGON "
                + "else self.kind = GeometryKind::POINT endif) and self.multiplicity <> GeometryMultiplicity::MULTI)");

        constrain(relation, SpatialModelValidator.SP5, "AnAreaParentForAnAreaPredicate",
                "self.parent.oclIsUndefined() or Set{SpatialPredicate::WITHIN, SpatialPredicate::COVERED_BY, "
                        + "SpatialPredicate::INTERSECTS, SpatialPredicate::CENTROID_WITHIN}->excludes(self.predicate) "
                        + "or Set{GeometryKind::POLYGON, GeometryKind::MIXED}->includes(self.parent.kind)");
        constrain(relation, SpatialModelValidator.SP10, "DistanceOnlyForWithinDistance",
                "if self.predicate = SpatialPredicate::WITHIN_DISTANCE then not self.distance.oclIsUndefined() "
                        + "and " + decimal("self.distance") + " and self.distance.toReal() > 0 "
                        + "else self.distance.oclIsUndefined() endif");
        constrain(relation, SpatialModelValidator.SP11, "TheNearestIsOne",
                "self.predicate <> SpatialPredicate::NEAREST or self.assignment = SpatialAssignment::EXACTLY_ONE");
        constrain(relation, SpatialModelValidator.SP12, "OneSystemOrAWorkingOne",
                "not self.workingSystem.oclIsUndefined() or self.child.oclIsUndefined() "
                        + "or self.parent.oclIsUndefined() or self.child.referenceSystem.oclIsUndefined() "
                        + "or self.parent.referenceSystem.oclIsUndefined() or "
                        + sameSystem("self.child.referenceSystem", "self.parent.referenceSystem"));
        constrain(relation, SpatialModelValidator.SP14, "KeysOfTheGeometryTables",
                "not self.child.oclIsUndefined() and not self.parent.oclIsUndefined() "
                        + "and not self.childKey.oclIsUndefined() and not self.parentKey.oclIsUndefined() "
                        + "and " + tables("self.child") + "->includes(self.childKey.owner) "
                        + "and " + tables("self.parent") + "->includes(self.parentKey.owner) "
                        + "and (self.explicitKey.oclIsUndefined() or " + tables("self.child")
                        + "->includes(self.explicitKey.owner))");
        constrain(relation, SpatialModelValidator.SP15, "DropIsAWarning", "self.unmatched <> UnmatchedPolicy::DROP");
        constrain(relation, SpatialModelValidator.SP21, "NoExplicitKeyForTheNearest",
                "self.explicitKey.oclIsUndefined() or self.predicate <> SpatialPredicate::NEAREST");
        constrain(relation, SpatialModelValidator.SP26, "AViewDoesNotStop", "self.resultForm <> ResultForm::VIEW "
                + "or (self.unmatched <> UnmatchedPolicy::ERROR and (self.assignment <> SpatialAssignment::EXACTLY_ONE "
                + "or self.tieBreak = TieBreak::LOWEST_PARENT_KEY))");

        constrain(grid, SpatialModelValidator.SP14, "KeyOfThePointsTable",
                "not self.source.oclIsUndefined() and not self.sourceKey.oclIsUndefined() and "
                        + tables("self.source") + "->includes(self.sourceKey.owner)");
        constrain(grid, SpatialModelValidator.SP15, "DropIsAWarning", "self.unmatched <> UnmatchedPolicy::DROP");
        constrain(grid, SpatialModelValidator.SP22, "SinglePoints", "self.source.oclIsUndefined() "
                + "or self.source.kind <> GeometryKind::POINT or self.source.multiplicity <> GeometryMultiplicity::MULTI");
        constrain(grid, SpatialModelValidator.SP26, "AViewDoesNotStop",
                "self.resultForm <> ResultForm::VIEW or self.unmatched <> UnmatchedPolicy::ERROR");
        constrain(grid, SpatialModelValidator.SP38, "ResolutionInRange", "self.system.oclIsUndefined() "
                + "or (self.resolution >= self.system.minResolution and self.resolution <= self.system.maxResolution)");
        constrain(grid, SpatialModelValidator.SP41, "ParentOfTheSameSystem", "self.parent.oclIsUndefined() "
                + "or self.system.oclIsUndefined() or self.parent.system.oclIsUndefined() "
                + "or self.parent.system = self.system or (not self.system.name.oclIsUndefined() "
                + "and not self.parent.system.name.oclIsUndefined() "
                + "and self.parent.system.name.trim() = self.system.name.trim())");

        constrain(band, SpatialModelValidator.SP12, "OneSystemOrAWorkingOne",
                "not self.workingSystem.oclIsUndefined() or self.source.oclIsUndefined() "
                        + "or self.reference.oclIsUndefined() or self.source.referenceSystem.oclIsUndefined() "
                        + "or self.reference.referenceSystem.oclIsUndefined() or "
                        + sameSystem("self.source.referenceSystem", "self.reference.referenceSystem"));
        constrain(band, SpatialModelValidator.SP14, "KeyOfTheSourceTable",
                "not self.source.oclIsUndefined() and not self.reference.oclIsUndefined() "
                        + "and not self.sourceKey.oclIsUndefined() and "
                        + tables("self.source") + "->includes(self.sourceKey.owner)");
        constrain(band, SpatialModelValidator.SP15, "DropIsAWarning", "self.unmatched <> UnmatchedPolicy::DROP");
        constrain(band, SpatialModelValidator.SP19, "BoundsAscend", "self.bound->notEmpty() "
                + "and self.bound->forAll(b | " + decimal("b") + " and b.toReal() > 0) "
                + "and Sequence{2..self.bound->size()}->forAll(i | "
                + "self.bound->at(i - 1).toReal() < self.bound->at(i).toReal())");
        constrain(band, SpatialModelValidator.SP26, "AViewDoesNotStop",
                "self.resultForm <> ResultForm::VIEW or self.unmatched <> UnmatchedPolicy::ERROR");

        constrain(system, SpatialModelValidator.SP34, "HasACode",
                "not self.code.oclIsUndefined() and self.code.trim() <> ''");
        constrain(system, SpatialModelValidator.SP35, "EpsgCodeIsAPositiveNumber",
                "self.authority <> ReferenceSystemAuthority::EPSG or self.code.oclIsUndefined() "
                        + "or self.code.trim() = '' or self.code.trim().matches('[+]?0*[1-9][0-9]*')");
        constrain(system, SpatialModelValidator.SP36, "AxesTwoOrThree", "self.axes = 2 or self.axes = 3");

        constrain(gridSystem, SpatialModelValidator.SP39, "ResolutionsInOrder",
                "self.minResolution >= 0 and self.minResolution <= self.maxResolution");
    }

    /** OCL: the four edges of an envelope. */
    private static final String EDGES = "Set{Ordinate::MIN_EAST, Ordinate::MIN_NORTH, Ordinate::MAX_EAST, "
            + "Ordinate::MAX_NORTH}";
    /** OCL: the axes of a point. */
    private static final String AXES = "Set{Ordinate::EAST, Ordinate::NORTH, Ordinate::HEIGHT}";
    /** OCL: the roles of an envelope, its edges and its heights. */
    private static final String EDGES_AND_HEIGHTS = "Set{Ordinate::MIN_EAST, Ordinate::MIN_NORTH, Ordinate::MAX_EAST, "
            + "Ordinate::MAX_NORTH, Ordinate::MIN_HEIGHT, Ordinate::MAX_HEIGHT}";

    /**
     * OCL: whether the text {@code text} is a decimal number as SQL and JSON write it, an optional sign, digits, a point
     * before the fraction, an exponent; blanks around it are left out.
     */
    private static String decimal(String text) {
        return text + ".trim().matches('[+-]?([0-9]+[.]?[0-9]*|[.][0-9]+)([eE][+-]?[0-9]+)?')";
    }

    /** OCL: the attributes the holding {@code holding} names, as a set; a part that names none adds none. */
    private static String attributes(String holding) {
        return "(if " + holding + ".oclIsKindOf(WholeHolding) then " + holding + ".oclAsType(WholeHolding).attribute"
                + "->asSet() else if " + holding + ".oclIsKindOf(ListHolding) then " + holding
                + ".oclAsType(ListHolding).attribute->asSet() else " + holding + ".oclAsType(PartsHolding).part"
                + "->collect(p | p.attribute)->select(a | not a.oclIsUndefined())->asSet() endif endif)";
    }

    /** OCL: the classifiers (tables, classes) that own the attributes of the holdings of {@code feature}. */
    private static String tables(String feature) {
        return feature + ".holding->collect(h | " + attributes("h") + ")->collect(a | a.owner)"
                + "->select(o | not o.oclIsUndefined())->asSet()";
    }

    /** OCL: the roles of the numbers the holding {@code holding} holds, in parts or as a list. */
    private static String roles(String holding) {
        return "(if " + holding + ".oclIsKindOf(PartsHolding) then " + holding + ".oclAsType(PartsHolding).part"
                + "->collect(p | p.role)->asBag() else " + holding + ".oclAsType(ListHolding).ordinate->asBag() endif)";
    }

    /**
     * OCL: whether the reference systems {@code a} and {@code b} are one: the same element, or one authority, version
     * and code, as their URIs are; a system without a version has the version 0.
     */
    private static String sameSystem(String a, String b) {
        return "(" + a + " = " + b + " or (" + a + ".authority = " + b + ".authority and " + code(a) + " = " + code(b)
                + " and " + version(a) + " = " + version(b) + "))";
    }

    private static String code(String system) {
        return "(if " + system + ".code.oclIsUndefined() then '' else " + system + ".code.trim() endif)";
    }

    private static String version(String system) {
        return "(if " + system + ".version.oclIsUndefined() or " + system + ".version.trim() = '' then '0' else "
                + system + ".version.trim() endif)";
    }

    /**
     * Adds the rule {@code rule} as an OCL invariant of {@code c}, named by the code of the rule: the Ecore constraints
     * list, the OCL expression, and its metadata in {@link #CONSTRAINTS}: a short name, the sentence of the rule as its
     * description, its severity as its level.
     */
    private void constrain(EClass c, Rule rule, String name, String ocl) {
        String code = rule.code();
        EAnnotation constraints = c.getEAnnotation(ECORE);
        if (constraints == null) {
            constraints = ef.createEAnnotation();
            constraints.setSource(ECORE);
            c.getEAnnotations().add(0, constraints);
        }
        String names = constraints.getDetails().get("constraints");
        constraints.getDetails().put("constraints", names == null ? code : names + " " + code);
        EAnnotation expressions = c.getEAnnotation(ECORE_OCL);
        if (expressions == null) {
            expressions = ef.createEAnnotation();
            expressions.setSource(ECORE_OCL);
            c.getEAnnotations().add(1, expressions);
        }
        expressions.getDetails().put(code, ocl);
        EAnnotation metadata = c.getEAnnotation(CONSTRAINTS);
        if (metadata == null) {
            metadata = ef.createEAnnotation();
            metadata.setSource(CONSTRAINTS);
            c.getEAnnotations().add(2, metadata);
        }
        metadata.getDetails().put(code + ".name", name);
        metadata.getDetails().put(code + ".description", rule.says());
        metadata.getDetails().put(code + ".level", rule.severity().name().toLowerCase(Locale.ROOT));
    }

    /** {@code Property.oppositeRoleName} pair (convention for hidden association ends). */
    private void oppositeRole(EReference r, String roleName) {
        EAnnotation a1 = ef.createEAnnotation();
        a1.setSource(EMOF);
        a1.getDetails().put("Property.oppositeRoleName", roleName);
        r.getEAnnotations().add(a1);
        EAnnotation a2 = ef.createEAnnotation();
        a2.setSource(EMOF + "#Property.oppositeRoleName");
        a2.getDetails().put("body", roleName);
        r.getEAnnotations().add(a2);
    }

    /** A class extending {@code core::ModelElement}. */
    private EClass cls(EPackage pkg, String name, String doc) {
        EClass c = ef.createEClass();
        c.setName(name);
        c.getESuperTypes().add(cwm.modelElement);
        pkg.getEClassifiers().add(c);
        annotate(c, doc);
        return c;
    }

    /** A class extending {@code core::Element}: a part owned by another element, without a name of its own. */
    private EClass elementCls(EPackage pkg, String name, boolean isAbstract, String doc) {
        EClass c = subCls(pkg, name, cwm.element, doc);
        c.setAbstract(isAbstract);
        return c;
    }

    /** A class extending {@code superType}. */
    private EClass subCls(EPackage pkg, String name, EClass superType, String doc) {
        EClass c = ef.createEClass();
        c.setName(name);
        c.getESuperTypes().add(superType);
        pkg.getEClassifiers().add(c);
        annotate(c, doc);
        return c;
    }

    /** An enumeration; {@code literals} are pairs of a literal's name and its documentation. */
    private EEnum enm(EPackage pkg, String name, String doc, String... literals) {
        EEnum e = ef.createEEnum();
        e.setName(name);
        for (int i = 0; i < literals.length; i += 2) {
            EEnumLiteral l = ef.createEEnumLiteral();
            l.setName(literals[i]);
            l.setValue(i / 2);
            annotate(l, literals[i + 1]);
            e.getELiterals().add(l);
        }
        pkg.getEClassifiers().add(e);
        annotate(e, doc);
        return e;
    }

    private EAttribute attr(EClass owner, String name, EClassifier type, int lower, int upper, String doc) {
        EAttribute a = ef.createEAttribute();
        a.setName(name);
        a.setEType(type);
        a.setLowerBound(lower);
        a.setUpperBound(upper);
        owner.getEStructuralFeatures().add(a);
        annotate(a, doc);
        return a;
    }

    private EReference ref(EClass owner, String name, EClassifier type, int lower, int upper, String doc) {
        EReference r = ef.createEReference();
        r.setName(name);
        r.setEType(type);
        r.setLowerBound(lower);
        r.setUpperBound(upper);
        if (upper != 1) {
            r.setOrdered(false);
        }
        owner.getEStructuralFeatures().add(r);
        annotate(r, doc);
        return r;
    }

    /** A containment reference; it keeps the order of its elements. */
    private EReference cont(EClass owner, String name, EClassifier type, int lower, int upper, String doc) {
        EReference r = ef.createEReference();
        r.setName(name);
        r.setEType(type);
        r.setLowerBound(lower);
        r.setUpperBound(upper);
        r.setContainment(true);
        owner.getEStructuralFeatures().add(r);
        annotate(r, doc);
        return r;
    }

    private EAnnotation annotate(EModelElement el, String doc) {
        EAnnotation a = ef.createEAnnotation();
        a.setSource(GENMODEL);
        a.getDetails().put("documentation", doc);
        el.getEAnnotations().add(a);
        return a;
    }

    /** A resource set reading {@code .ecore} files. */
    public static ResourceSet newResourceSet() {
        ResourceSet rs = new ResourceSetImpl();
        rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new XMIResourceFactoryImpl());
        return rs;
    }

    /**
     * Loads a CWM ecore; its resource URI becomes the nsURI, so that references serialize as
     * {@code <nsURI>#//Class}.
     */
    private static EPackage loadPackage(ResourceSet rs, URI ecoreUri) {
        Resource res = rs.getResource(ecoreUri, true);
        EPackage pkg = (EPackage) res.getContents().get(0);
        res.setURI(URI.createURI(pkg.getNsURI()));
        rs.getPackageRegistry().put(pkg.getNsURI(), pkg);
        return pkg;
    }

    private static URI ecoreUri(File cwmDir, String path) {
        File f = new File(cwmDir, path);
        if (!f.isFile()) {
            throw new IllegalStateException("CWM ecore not found: " + f);
        }
        return URI.createFileURI(f.getAbsolutePath());
    }

    private static EClass eClass(EPackage pkg, String name) {
        if (pkg.getEClassifier(name) instanceof EClass c) {
            return c;
        }
        throw new IllegalStateException("CWM EClass not found: " + name + " in " + pkg.getName());
    }

    private static EDataType eDataType(EPackage pkg, String name) {
        if (pkg.getEClassifier(name) instanceof EDataType d) {
            return d;
        }
        throw new IllegalStateException("CWM EDataType not found: " + name + " in " + pkg.getName());
    }

    /** Writes the package as UTF-8 with the EPL header after the XML declaration. */
    public static void save(EPackage pkg, ResourceSet rs, URI outUri) throws IOException {
        Resource out = rs.createResource(outUri);
        out.getContents().add(pkg);
        Map<Object, Object> opts = new HashMap<>();
        opts.put(XMLResource.OPTION_ENCODING, "UTF-8");
        opts.put(XMLResource.OPTION_LINE_WIDTH, Integer.valueOf(96));
        out.save(opts);
        Path file = Path.of(outUri.toFileString());
        String content = Files.readString(file, StandardCharsets.UTF_8);
        String comment = "<!--\n"
                + "/*********************************************************************\n"
                + "* Copyright (c) 2026 Contributors to the Eclipse Foundation.\n"
                + "*\n"
                + "* This program and the accompanying materials are made\n"
                + "* available under the terms of the Eclipse Public License 2.0\n"
                + "* which is available at https://www.eclipse.org/legal/epl-2.0/\n"
                + "*\n"
                + "* SPDX-License-Identifier: EPL-2.0\n"
                + "**********************************************************************/\n"
                + "-->\n";
        int nl = content.indexOf('\n');
        Files.writeString(file, content.substring(0, nl + 1) + comment + content.substring(nl + 1),
                StandardCharsets.UTF_8);
    }
}
