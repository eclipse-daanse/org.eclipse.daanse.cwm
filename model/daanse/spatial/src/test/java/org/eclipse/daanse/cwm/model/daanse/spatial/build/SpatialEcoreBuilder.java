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
import java.util.Map;

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
 * Programmatic builder for the {@code spatial} metamodel: what a geometry column holds, and the rules by which a
 * resolver derives keys from geometry.
 *
 * <p>A spatial relation is resolved once into an ordinary relational one; whoever reads the result, a cube for
 * instance, reads only keys. The model does not describe a join an engine performs: it describes how the assignment of
 * a child row to parent rows arises from geometry, and a resolver evaluates it in the database and leaves a foreign key
 * (a key pair, every child at most one parent) or a bridge with a share (a child in several parents, a part each). It
 * holds no coordinates and knows no OLAP class; a model that reads a result does not depend on it.</p>
 *
 * <p>Every reference into CWM and between the classes of the package is one-way with an EMOF
 * {@code Property.oppositeRoleName} (prefix {@code spatial}); construction and serialization follow
 * {@code model/daanse/units/.../UnitsEcoreBuilder}.</p>
 */
public final class SpatialEcoreBuilder {

    private static final String GENMODEL = "http://www.eclipse.org/emf/2002/GenModel";
    private static final String ECORE = "http://www.eclipse.org/emf/2002/Ecore";
    private static final String ECORE_OCL = "http://www.eclipse.org/emf/2002/Ecore/OCL";
    private static final String EMOF = "http://schema.omg.org/spec/MOF/2.0/emof.xml";
    /** The namespace URI of the package. */
    public static final String NS_URI = "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.spatial";

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
        final EClass modelElement;
        final EClass attribute;
        final EClass cwmClass;
        final EClass dataType;
        final EDataType floatType;
        final EDataType integerType;
        final EDataType booleanType;
        final EDataType stringType;

        CwmRefs(EPackage core) {
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

    /** Creates the EPackage {@code spatial}: 7 classes, 12 enumerations. */
    public EPackage build() {
        EPackage p = ef.createEPackage();
        p.setName("spatial");
        p.setNsURI(NS_URI);
        p.setNsPrefix("spatial");
        EAnnotation pa = annotate(p,
                "Geodata for CWM attributes: what a geometry column holds, and the rules by which keys are derived "
                        + "from geometry. A GeometryFeature says what the columns it names hold: the kind of "
                        + "geometry, single or multi, with a height or without, the spatial reference system and the "
                        + "epoch of the coordinates, the accuracy, the storage and the order in which the stored "
                        + "coordinates hold the axes. A ReferenceSystem names a coordinate reference system by its "
                        + "authority and code, as an OGC URI does, with what its register defines: its kind, axes and "
                        + "axis order. A GeometryType is the logical data type of a property that holds a geometry. "
                        + "Distances and the bounds of distance classes are metres, whatever the reference system. "
                        + "A SpatialRelation "
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
                "What the columns it names hold: the kind of geometry, the spatial reference system and the epoch "
                        + "of the coordinates, the accuracy, how the value is stored and in which order the stored "
                        + "coordinates hold the axes. Several columns with one meaning (the same kind of geometry in "
                        + "two tables) share one feature. A feature states something about an attribute, it is not "
                        + "one, as a units::Quantity states a unit.");
        EClass relation = cls(p, "SpatialRelation",
                "A rule that assigns the rows of a child geometry to the rows of a parent geometry: when a "
                        + "parent matches (predicate), what happens when several match (assignment, weight, "
                        + "minimum share, tie break) or none (unmatched), in which reference system it is evaluated. "
                        + "A resolver evaluates it once and writes its result: a key pair (child key, parent key) "
                        + "for EXACTLY_ONE and LARGEST_SHARE, a bridge (child key, parent key, share) for "
                        + "ALL_WEIGHTED and ALL_EQUAL. It never alters a source table. The result holds no time: "
                        + "a territory that changes gets one result per state.");
        EClass grid = cls(p, "GridLevel",
                "The key of the cell of a grid a point lies in, of one system and resolution; the cells of a "
                        + "coarser resolution are the level above. No geometric join: both the cell and its "
                        + "parent come from a function, and in a strictly nested grid the parent's key is the "
                        + "head of the cell's. A resolver writes its result, per point the source key and the "
                        + "cell key of each level from the top of its chain down to it; a point without a "
                        + "geometry gets no cell and goes as unmatched says. It never alters the table of the "
                        + "points.");
        EClass band = cls(p, "DistanceBand",
                "The distance of a geometry to the nearest row of a reference geometry, put into classes by "
                        + "upper bounds in metres (0 to 1 km, 1 to 5 km, above), like a discretization. A resolver "
                        + "writes its result, a pair (source key, class) per source row, the class 1 up to and "
                        + "including the first bound and one more above each bound; a row whose distance cannot be "
                        + "measured gets no class and goes as unmatched says. It never alters the source table.");
        EClass system = cls(p, "ReferenceSystem",
                "A coordinate reference system, named as an OGC URI names it: "
                        + "http://www.opengis.net/def/crs/{authority}/{version}/{code}, EPSG 25832 or OGC CRS84. It "
                        + "holds what its register defines: its kind (degrees, a map projection, Cartesian metres), "
                        + "its axes and their order, and whether it is dynamic. How a column stores its coordinates "
                        + "(their order, the epoch they hold for) is said by the geometry feature. Owned by the model; "
                        + "the features and the working systems of relations and bands refer to it.");
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
                "The logical data type of a property that holds a geometry, as the SQL types of a catalog are its "
                        + "elements; named Geometry by convention. What the property holds (kind, multiplicity, "
                        + "height, reference system) is said by the geometry feature that names it, not by the type, "
                        + "and the type says nothing of how a value is stored: the SQL type of a column (GEOMETRY, "
                        + "GEOGRAPHY, a text) is no GeometryType.");

        EEnum kind = enm(p, "GeometryKind",
                "The dimension of what a geometry column holds; a multi form belongs to its kind, the "
                        + "multiplicity says whether rows hold one.",
                "POINT", "LINE", "POLYGON", "MIXED");
        EEnum multiplicity = enm(p, "GeometryMultiplicity",
                "Whether a row holds one geometry of its kind (a point, a line, a polygon) or several as one "
                        + "(a multi-point, a multi-line, a multi-polygon), or either of the two.",
                "EITHER", "SINGLE", "MULTI");
        EEnum authority = enm(p, "ReferenceSystemAuthority",
                "The register that defines a reference system: the EPSG geodetic parameter dataset, or the OGC "
                        + "(CRS84, CRS84h: WGS 84 with longitude first).",
                "EPSG", "OGC");
        EEnum systemKind = enm(p, "ReferenceSystemKind",
                "What the coordinates of a reference system are: longitude and latitude in degrees "
                        + "(GEOGRAPHIC), easting and northing on a map projection in the unit of its axes, metres "
                        + "for most and feet for some (PROJECTED), or Cartesian metres from the earth's centre "
                        + "(GEOCENTRIC).",
                "GEOGRAPHIC", "PROJECTED", "GEOCENTRIC");
        EEnum axisOrder = enm(p, "AxisOrder",
                "The order of the first two axes: east (x, longitude) first, or north (y, latitude) first.",
                "EAST_NORTH", "NORTH_EAST");
        EEnum storage = enm(p, "GeometryStorage",
                "How a column holds a geometry: the database's own geometry type, or well-known binary, "
                        + "well-known text or GeoJSON, which a resolver converts first.",
                "NATIVE", "WKB", "WKT", "GEOJSON");
        EEnum predicate = enm(p, "SpatialPredicate",
                "When a parent matches a child: the child lies within the parent (Within of OGC Simple "
                        + "Features), no point of the child lies outside the parent (CoveredBy as JTS, GEOS and "
                        + "PostGIS define it; unlike Within, a point on a border meets it), the two share a point "
                        + "(Intersects of OGC Simple Features), one point of the child lies within the parent "
                        + "(CENTROID_WITHIN: the child's centroid where it lies on the child, else a point on the "
                        + "child's surface, so that a crescent is placed by a point of its own), the parent is the "
                        + "nearest, or it lies within a distance.",
                "WITHIN", "COVERED_BY", "INTERSECTS", "CENTROID_WITHIN", "NEAREST", "WITHIN_DISTANCE");
        EEnum assignment = enm(p, "SpatialAssignment",
                "What happens when several parents match: at most one may (EXACTLY_ONE), the one with the "
                        + "largest part wins (LARGEST_SHARE), all stay with their parts divided by their sum "
                        + "(ALL_WEIGHTED), or all stay with equal shares (ALL_EQUAL).",
                "EXACTLY_ONE", "LARGEST_SHARE", "ALL_WEIGHTED", "ALL_EQUAL");
        EEnum weight = enm(p, "SpatialWeight",
                "What is measured to compare or divide the matches of a child: the area of the intersection "
                        + "relative to the child's area, or the length relative to the child's length.",
                "AREA", "LENGTH");
        EEnum unmatched = enm(p, "UnmatchedPolicy",
                "What happens to a row a rule cannot place (a child no parent matches, a point without a cell, "
                        + "a source whose distance cannot be measured): it stays with a null parent key, cell or "
                        + "class (KEEP_NULL; how that shows is the policy of the model that reads the result), a "
                        + "resolver stops and names it (ERROR), or it leaves the result (DROP).",
                "KEEP_NULL", "ERROR", "DROP");
        EEnum tieBreak = enm(p, "TieBreak",
                "Which parent wins an exact tie of EXACTLY_ONE: none, the tie is an error (as without a tie "
                        + "break), or the parent with the lowest key.",
                "ERROR", "LOWEST_PARENT_KEY");
        EEnum resultForm = enm(p, "ResultForm",
                "How a resolver leaves its result: a table written once, which any database reads, or a view "
                        + "recomputed whenever read, which needs the spatial functions in the database.",
                "TABLE", "VIEW");

        EReference featureElements = ref(feature, "modelElement", cwm.modelElement, 1, -1,
                "The columns that hold the geometry: attributes, such as a relational column or a record field.");
        oppositeRole(featureElements, "spatialGeometryFeature");
        attr(feature, "kind", kind, 1, 1, "The kind of geometry the columns hold.");
        attr(feature, "multiplicity", multiplicity, 1, 1,
                "Whether a row holds a single geometry of the kind, a multi form, or either.");
        attr(feature, "hasHeight", cwm.booleanType, 1, 1,
                "Whether the coordinates hold a height as a third value; that needs a reference system of three "
                        + "axes.")
                .setDefaultValueLiteral("false");
        EReference featureSystem = ref(feature, "referenceSystem", system, 1, 1,
                "The coordinate reference system of the geometries.");
        oppositeRole(featureSystem, "spatialGeometryFeatureInSystem");
        attr(feature, "coordinateEpoch", cwm.floatType, 0, 1,
                "The epoch the coordinates hold for, a decimal year such as 2010.5, stated where the reference "
                        + "system is dynamic and only there: with the system it is the coordinate metadata of the "
                        + "columns (OGC Topic 2), so that two columns of one dynamic system may hold two epochs.");
        attr(feature, "accuracy", cwm.floatType, 0, 1,
                "The positional accuracy in metres, a decimal number; for diagnostics only.");
        attr(feature, "storage", storage, 1, 1, "How the columns hold the value.");
        attr(feature, "axisOrder", axisOrder, 1, 1,
                "The order in which the stored coordinates hold the first two axes: east (longitude) first, as "
                        + "PostGIS, DuckDB and GeoJSON hold them, or north (latitude) first. It may differ from the "
                        + "order the reference system defines (EPSG 4326 defines latitude first), and two columns "
                        + "of one system may store their coordinates in two orders; GEOJSON storage holds east "
                        + "first always (RFC 7946).");
        constrain(feature, "HeightNeedsThreeAxes", "not self.hasHeight "
                + "or (not self.referenceSystem.oclIsUndefined() and self.referenceSystem.axes = 3)");
        constrain(feature, "EpochOnlyWhenDynamic", "self.coordinateEpoch.oclIsUndefined() "
                + "or (not self.referenceSystem.oclIsUndefined() and self.referenceSystem.dynamic)");
        constrain(feature, "GeoJsonEastFirst",
                "self.storage <> GeometryStorage::GEOJSON or self.axisOrder = AxisOrder::EAST_NORTH");

        EReference child = ref(relation, "child", feature, 1, 1, "The geometry that is assigned.");
        oppositeRole(child, "spatialRelationAsChild");
        EReference parent = ref(relation, "parent", feature, 1, 1, "The geometry it is assigned to.");
        oppositeRole(parent, "spatialRelationAsParent");
        EReference childKey = ref(relation, "childKey", cwm.attribute, 1, 1,
                "The key column of the table that holds the child geometry; the first column of the result. Where "
                        + "the table declares no unique key of this column alone, the rows of one key are the parts "
                        + "of one child and are united first (a postcode area held in four rows).");
        oppositeRole(childKey, "spatialRelationOfChildKey");
        EReference parentKey = ref(relation, "parentKey", cwm.attribute, 1, 1,
                "The key column of the table that holds the parent geometry; the second column of the result. "
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
        constrain(relation, "MinimumShareInRange", "self.minimumShare.oclIsUndefined() "
                + "or (self.minimumShare.toReal() >= 0 and self.minimumShare.toReal() < 1)");
        constrain(relation, "DistanceOnlyForWithinDistance",
                "(self.predicate = SpatialPredicate::WITHIN_DISTANCE) = not self.distance.oclIsUndefined()");

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
        constrain(grid, "ParentNotSelf", "self.parent.oclIsUndefined() or self.parent <> self");
        constrain(grid, "ResolutionInRange", "self.system.oclIsUndefined() "
                + "or (self.resolution >= self.system.minResolution and self.resolution <= self.system.maxResolution)");
        constrain(grid, "ParentOfTheSameSystem",
                "self.parent.oclIsUndefined() or self.parent.system = self.system");

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
                        + "register wrongly for every system of the other order; unset is not stated. How a column "
                        + "stores its coordinates is said by its geometry feature.")
                .setUnsettable(true);
        attr(system, "dynamic", cwm.booleanType, 1, 1,
                "Whether the system moves with a tectonic plate, so that a coordinate holds for one epoch, which "
                        + "the geometry feature of a column names.")
                .setDefaultValueLiteral("false");
        constrain(system, "AxesTwoOrThree", "self.axes = 2 or self.axes = 3");

        attr(gridSystem, "minResolution", cwm.integerType, 1, 1,
                "The coarsest resolution of the system: 1 for geohash and quadkey, 0 for h3.");
        attr(gridSystem, "maxResolution", cwm.integerType, 1, 1,
                "The finest resolution of the system: 12 for geohash, 15 for h3, 23 for quadkey.");
        attr(gridSystem, "nested", cwm.booleanType, 1, 1,
                "Whether the coarser cell holds its finer cells strictly, so that the parent's key is the head of "
                        + "the cell's (geohash, quadkey); false where the parent is a function of the cell, which "
                        + "holds its finer cells only approximately (h3).")
                .setDefaultValueLiteral("false");
        constrain(gridSystem, "ResolutionsInOrder",
                "self.minResolution >= 0 and self.minResolution <= self.maxResolution");

        return p;
    }

    /** Adds an OCL invariant: Ecore constraints list plus the OCL expression of the same name. */
    private void constrain(EClass c, String name, String ocl) {
        EAnnotation constraints = c.getEAnnotation(ECORE);
        if (constraints == null) {
            constraints = ef.createEAnnotation();
            constraints.setSource(ECORE);
            c.getEAnnotations().add(0, constraints);
        }
        String names = constraints.getDetails().get("constraints");
        constraints.getDetails().put("constraints", names == null ? name : names + " " + name);
        EAnnotation expressions = c.getEAnnotation(ECORE_OCL);
        if (expressions == null) {
            expressions = ef.createEAnnotation();
            expressions.setSource(ECORE_OCL);
            c.getEAnnotations().add(1, expressions);
        }
        expressions.getDetails().put(name, ocl);
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

    private EEnum enm(EPackage pkg, String name, String doc, String... literals) {
        EEnum e = ef.createEEnum();
        e.setName(name);
        int value = 0;
        for (String literal : literals) {
            EEnumLiteral l = ef.createEEnumLiteral();
            l.setName(literal);
            l.setValue(value++);
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
