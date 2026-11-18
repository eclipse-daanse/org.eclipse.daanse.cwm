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

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.daanse.spatial.AxisOrder;
import org.eclipse.daanse.cwm.model.daanse.spatial.DistanceBand;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryFeature;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryKind;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryMultiplicity;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryStorage;
import org.eclipse.daanse.cwm.model.daanse.spatial.GridLevel;
import org.eclipse.daanse.cwm.model.daanse.spatial.GridSystem;
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
import org.eclipse.daanse.cwm.model.daanse.validation.Finding;
import org.eclipse.daanse.cwm.model.daanse.validation.Findings;
import org.eclipse.daanse.cwm.model.daanse.validation.Rule;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * Checks geometry features, spatial relations, grid levels, grid systems and distance bands beyond what the metamodel
 * enforces.
 * Errors stop a resolver, warnings do not.
 *
 * <ul>
 * <li>{@code SP1} a geometry feature names attributes (a column, a record field), not a class, a package or an
 * association end.</li>
 * <li>{@code SP2} an attribute is named by at most one geometry feature.</li>
 * <li>{@code SP3} (warning) the type of a column fits the storage: {@code NATIVE} a geometry type, {@code WKT} and
 * {@code GEOJSON} text, {@code WKB} binary; a type that is not known is no finding.</li>
 * <li>There is no {@code SP4}: each of its checks has a code of its own, {@code SP30} to {@code SP36}.</li>
 * <li>{@code SP5} {@code WITHIN}, {@code COVERED_BY}, {@code INTERSECTS} and {@code CENTROID_WITHIN} need a parent of
 * kind {@code POLYGON} or {@code MIXED}.</li>
 * <li>{@code SP6} a weight fits the child: {@code AREA} a polygon, {@code LENGTH} a line ({@code MIXED} is checked
 * per row by a resolver); a point has no weight.</li>
 * <li>{@code SP7} {@code EXACTLY_ONE} carries a weight only together with a minimum share above zero: the matches
 * below it are dropped first, then at most one may remain.</li>
 * <li>{@code SP8} {@code ALL_WEIGHTED} and {@code LARGEST_SHARE} need a weight.</li>
 * <li>{@code SP9} the minimum share is a decimal number from 0 below 1, above 0 only with a weight to measure it.</li>
 * <li>{@code SP10} a distance is given exactly with {@code WITHIN_DISTANCE}, and is above zero.</li>
 * <li>{@code SP11} {@code NEAREST} assigns {@code EXACTLY_ONE}.</li>
 * <li>{@code SP12} child and parent (a band's source and reference) share a reference system, or a working one is
 * named. One URI is one system, whatever order each column stores its coordinates in.</li>
 * <li>There is no {@code SP13}: a code keeps its meaning, and a measure in a geocentric system has {@code SP27}.</li>
 * <li>{@code SP14} a relation, a grid level or a band names its geometries and keys; the child key and an explicit key
 * are columns of a table that holds the child geometry, the parent key of one that holds the parent geometry; the
 * source key of a grid level or a distance band is a column of a table that holds its source geometry.</li>
 * <li>{@code SP15} (warning) {@code DROP}: the children without a parent, the points without a cell or the sources
 * without a class leave the result, and facts that reach the parent, cell or class through it vanish from the
 * sums.</li>
 * <li>{@code SP16} a declared result has the shape of its rule: child key and parent key, for {@code ALL_WEIGHTED} and
 * {@code ALL_EQUAL} also a numeric share, in this order; for a grid level the source key and the cell key of each
 * level from the top of its chain down to it (text; text or a number for a system that does not nest, such as
 * {@code h3}); for a distance band the source key and the class (a number).</li>
 * <li>{@code SP17} following child to parent never leads back: no place is its own ancestor.</li>
 * <li>{@code SP18} a grid level puts points into cells of a system and at a resolution it names, below a level of a
 * lower resolution over the same points by the same key.</li>
 * <li>{@code SP19} the bounds of a distance band are decimal numbers above zero, strictly ascending.</li>
 * <li>{@code SP20} (warning) a reference that does not resolve.</li>
 * <li>{@code SP21} (warning) an explicit key with {@code NEAREST}: the key decides the rows that carry it, not the
 * geometry, which may not be meant.</li>
 * <li>{@code SP22} a grid level takes single points: a source of multiplicity {@code MULTI} has no one cell.</li>
 * <li>{@code SP23} a geometry with a height has a reference system of three axes.</li>
 * <li>{@code SP24} (warning) a geometry feature in a dynamic reference system names the epoch its coordinates hold
 * for.</li>
 * <li>{@code SP25} (warning) a child or parent key, or the source key of a grid level or a distance band, is not a
 * declared key of one column of its table: a schema read from a database without declared keys still resolves. The
 * rows of such a key of a relation are the parts of one geometry, united first (a postcode area held in four rows),
 * the rows of a band's key the parts of one source; a grid level's key held in several rows is refused by a
 * resolver, a point has one row. A warning has a code of its own: no code stands for an error and a warning.</li>
 * <li>{@code SP26} a result of form {@code VIEW} where the rule would have to stop: a view is computed anew whenever it
 * is read and cannot stop, so {@code EXACTLY_ONE} without the tie break {@code LOWEST_PARENT_KEY} and the policy
 * {@code ERROR} for a child without a parent, a point without a cell or a source without a class need a table, which
 * a resolver checks when it writes it.</li>
 * <li>{@code SP27} (warning) an area, a length or a distance of a relation or a band measured in a geocentric reference
 * system: the planar functions read its x and y only and leave out z. In a geographic system areas, lengths and
 * distances are measured on the spheroid: no finding.</li>
 * <li>{@code SP28} a geometry stored as {@code GEOJSON} holds its coordinates east first (RFC 7946).</li>
 * <li>{@code SP29} (warning) a geometry stored as {@code GEOJSON} holds longitude and latitude of WGS 84: OGC CRS84 or
 * CRS84h, or EPSG 4326 or 4979 stored east first; another system only by prior arrangement (RFC 7946).</li>
 * <li>{@code SP30} a geometry feature names its reference system.</li>
 * <li>{@code SP31} the accuracy of a geometry feature is a decimal number of metres from zero up.</li>
 * <li>{@code SP32} a geometry feature names a coordinate epoch only where its reference system is dynamic.</li>
 * <li>{@code SP33} a coordinate epoch is a decimal year.</li>
 * <li>{@code SP34} a reference system has a code.</li>
 * <li>{@code SP35} an EPSG code is a positive whole number.</li>
 * <li>{@code SP36} a reference system has 2 or 3 axes.</li>
 * <li>{@code SP37} a reference system states the order of its first two axes as its register defines them: the
 * attribute has no default, since a default would copy the register wrongly for every system of the other
 * order.</li>
 * <li>{@code SP38} the resolution of a grid level lies within the resolutions of its grid system.</li>
 * <li>{@code SP39} the resolutions of a grid system run from zero up, its minimum at most its maximum.</li>
 * <li>{@code SP40} a grid system has an identifier (its name, by which a resolver knows it), once in its model.</li>
 * <li>{@code SP41} a chain of grid levels stays within one grid system.</li>
 * </ul>
 *
 * <p>Distances and the bounds of distance classes are metres, whatever the reference system.</p>
 */
public final class SpatialModelValidator {

    /** The module the rules of this validator belong to. */
    public static final String MODULE = "spatial";

    public static final Rule SP1 = Rule.error(MODULE, "SP1",
            "A geometry feature names attributes, not a class, a package or an association end.");

    public static final Rule SP2 = Rule.error(MODULE, "SP2", "An attribute is named by at most one geometry feature.");

    public static final Rule SP3 = Rule.warning(MODULE, "SP3", "The type of a geometry column fits its storage.");

    public static final Rule SP5 = Rule.error(MODULE, "SP5",
            "WITHIN, COVERED_BY, INTERSECTS and CENTROID_WITHIN have a parent of kind POLYGON or MIXED.");

    public static final Rule SP6 = Rule.error(MODULE, "SP6",
            "A weight fits the child: AREA a polygon, LENGTH a line; a point has no weight.");

    public static final Rule SP7 = Rule.error(MODULE, "SP7",
            "EXACTLY_ONE carries a weight only together with a minimum share above zero.");

    public static final Rule SP8 = Rule.error(MODULE, "SP8", "ALL_WEIGHTED and LARGEST_SHARE have a weight.");

    public static final Rule SP9 = Rule.error(MODULE, "SP9",
            "The minimum share is a decimal number from 0 below 1, above 0 only with a weight to measure it.");

    public static final Rule SP10 = Rule.error(MODULE, "SP10",
            "A distance is given exactly with WITHIN_DISTANCE, and is above zero.");

    public static final Rule SP11 = Rule.error(MODULE, "SP11", "NEAREST assigns EXACTLY_ONE.");

    public static final Rule SP12 = Rule.error(MODULE, "SP12",
            "Child and parent share a reference system, or a working one is named.");

    public static final Rule SP14 = Rule.error(MODULE, "SP14",
            "A relation, a grid level or a band names its geometries and keys, and each key is a column of a table "
                + "that holds its geometry.");

    public static final Rule SP15 = Rule.warning(MODULE, "SP15",
            "A row without a parent, a cell or a class stays in the result; under DROP its facts vanish from the "
                + "sums.");

    public static final Rule SP16 = Rule.error(MODULE, "SP16",
            "A declared result has the columns of its rule, in their order and of their types.");

    public static final Rule SP17 = Rule.error(MODULE, "SP17",
            "Following child to parent never leads back: no place is its own ancestor.");

    public static final Rule SP18 = Rule.error(MODULE, "SP18",
            "A grid level puts points into cells of a system and at a resolution it names, below a coarser level over "
                + "the same points.");

    public static final Rule SP19 = Rule.error(MODULE, "SP19",
            "The bounds of a distance band are decimal numbers above zero, strictly ascending.");

    public static final Rule SP20 = Rule.warning(MODULE, "SP20", "A reference resolves.");

    public static final Rule SP21 = Rule.warning(MODULE, "SP21",
            "NEAREST takes no explicit key, which would decide the rows that carry it instead of the geometry.");

    public static final Rule SP22 = Rule.error(MODULE, "SP22", "A grid level takes single points.");

    public static final Rule SP23 = Rule.error(MODULE, "SP23",
            "A geometry with a height has a reference system of three axes.");

    public static final Rule SP24 = Rule.warning(MODULE, "SP24",
            "A geometry feature in a dynamic reference system names the epoch its coordinates hold for.");

    public static final Rule SP25 = Rule.warning(MODULE, "SP25",
            "A child or parent key, or the source key of a grid level or a distance band, is a declared key of one "
                + "column of its table; else the rows of a key are the parts of one geometry, and a grid's key keeps "
                + "one row.");

    public static final Rule SP26 = Rule.error(MODULE, "SP26",
            "A result that has to be able to stop is a table, not a view.");

    public static final Rule SP27 = Rule.warning(MODULE, "SP27",
            "An area, a length or a distance is not measured in a geocentric reference system, of which the planar "
                + "functions read x and y only.");

    public static final Rule SP28 = Rule.error(MODULE, "SP28",
            "A geometry stored as GeoJSON holds its coordinates east first.");

    public static final Rule SP29 = Rule.warning(MODULE, "SP29",
            "A geometry stored as GeoJSON holds longitude and latitude of WGS 84 (CRS84, CRS84h, EPSG 4326 or "
                + "4979).");

    public static final Rule SP30 = Rule.error(MODULE, "SP30", "A geometry feature names its reference system.");

    public static final Rule SP31 = Rule.error(MODULE, "SP31",
            "The accuracy of a geometry feature is a decimal number of metres from zero up.");

    public static final Rule SP32 = Rule.error(MODULE, "SP32",
            "A geometry feature names a coordinate epoch only where its reference system is dynamic.");

    public static final Rule SP33 = Rule.error(MODULE, "SP33", "A coordinate epoch is a decimal year.");

    public static final Rule SP34 = Rule.error(MODULE, "SP34", "A reference system has a code.");

    public static final Rule SP35 = Rule.error(MODULE, "SP35", "An EPSG code is a positive whole number.");

    public static final Rule SP36 = Rule.error(MODULE, "SP36", "A reference system has 2 or 3 axes.");

    public static final Rule SP37 = Rule.error(MODULE, "SP37",
            "A reference system states the order of its first two axes as its register defines them.");

    public static final Rule SP38 = Rule.error(MODULE, "SP38",
            "The resolution of a grid level lies within the resolutions of its grid system.");

    public static final Rule SP39 = Rule.error(MODULE, "SP39",
            "The resolutions of a grid system run from zero up, its minimum at most its maximum.");

    public static final Rule SP40 = Rule.error(MODULE, "SP40", "A grid system has an identifier, once in its model.");

    public static final Rule SP41 = Rule.error(MODULE, "SP41", "A chain of grid levels stays within one grid system.");

    /** Every rule of this validator, in the order of their codes; there is no SP4 and no SP13. */
    public static final List<Rule> RULES = List.of(SP1, SP2, SP3, SP5, SP6, SP7, SP8, SP9, SP10, SP11, SP12, SP14,
            SP15, SP16, SP17, SP18, SP19, SP20, SP21, SP22, SP23, SP24, SP25, SP26, SP27, SP28, SP29, SP30, SP31, SP32,
            SP33, SP34, SP35, SP36, SP37, SP38, SP39, SP40, SP41);

    /** What a column type holds, as far as a geometry is concerned. */
    enum TypeKind {
        GEOMETRY, TEXT, BINARY, NUMBER, UNKNOWN
    }

    private static final Set<String> GEOMETRY_NAMES = Set.of("geometry", "geography", "point", "linestring",
            "polygon", "multipoint", "multilinestring", "multipolygon", "geometrycollection", "sdo_geometry",
            "st_geometry");
    private static final Set<String> TEXT_NAMES = Set.of("char", "character", "varchar", "character varying",
            "varchar2", "nchar", "nvarchar", "nvarchar2", "text", "tinytext", "mediumtext", "longtext", "string",
            "clob", "nclob", "longvarchar", "longnvarchar", "json", "jsonb");
    private static final Set<String> BINARY_NAMES = Set.of("binary", "varbinary", "longvarbinary", "blob",
            "tinyblob", "mediumblob", "longblob", "bytea", "bytes", "raw", "image");
    private static final Set<String> NUMBER_NAMES = Set.of("integer", "int", "long", "short", "byte", "float",
            "double", "real", "decimal", "numeric", "number", "bigint", "smallint", "tinyint", "money",
            "biginteger", "bigdecimal", "double precision");
    /** JDBC type codes: CHAR, VARCHAR, LONGVARCHAR, NCHAR, NVARCHAR, LONGNVARCHAR, CLOB, NCLOB. */
    private static final Set<Long> TEXT_JDBC = Set.of(1L, 12L, -1L, -15L, -9L, -16L, 2005L, 2011L);
    /** JDBC type codes: BINARY, VARBINARY, LONGVARBINARY, BLOB. */
    private static final Set<Long> BINARY_JDBC = Set.of(-2L, -3L, -4L, 2004L);
    /** JDBC type codes: TINYINT, SMALLINT, INTEGER, BIGINT, FLOAT, REAL, DOUBLE, NUMERIC, DECIMAL. */
    private static final Set<Long> NUMBER_JDBC = Set.of(-6L, 5L, 4L, -5L, 6L, 7L, 8L, 2L, 3L);

    private SpatialModelValidator() {
    }

    /** All findings for the geodata in {@code root} and below. */
    public static List<Finding> check(EObject root) {
        List<Finding> findings = new ArrayList<>();
        List<EObject> all = new ArrayList<>(List.of(root));
        root.eAllContents().forEachRemaining(all::add);
        Map<EObject, List<GeometryFeature>> byElement = new LinkedHashMap<>();
        List<SpatialRelation> relations = new ArrayList<>();
        Map<String, GridSystem> byIdentifier = new HashMap<>();
        for (EObject o : all) {
            if (o instanceof GeometryFeature f) {
                feature(f, findings);
                for (ModelElement e : f.getModelElement()) {
                    byElement.computeIfAbsent(e, k -> new ArrayList<>()).add(f);
                }
            } else if (o instanceof SpatialRelation r) {
                relation(r, findings);
                relations.add(r);
            } else if (o instanceof GridLevel g) {
                grid(g, findings);
            } else if (o instanceof DistanceBand b) {
                band(b, findings);
            } else if (o instanceof ReferenceSystem s) {
                system(s, findings);
            } else if (o instanceof GridSystem s) {
                gridSystem(s, byIdentifier, findings);
            }
        }
        for (Map.Entry<EObject, List<GeometryFeature>> e : byElement.entrySet()) {
            if (e.getValue().size() > 1) {
                findings.add(SP2.at(e.getKey(), path(e.getKey()) + " is named by " + e.getValue().size()
                        + " geometry features: " + String.join(", ", e.getValue().stream()
                                .map(SpatialModelValidator::path).toList()) + "; a column has one"));
            }
        }
        cycles(relations, findings);
        return findings;
    }

    /** The errors of {@link #check(EObject)} as {@code "rule message"} lines. */
    public static List<String> validate(EObject root) {
        return Findings.errorLines(check(root));
    }

    private static void feature(GeometryFeature f, List<Finding> findings) {
        ReferenceSystem system = f.getReferenceSystem();
        if (system == null) {
            findings.add(SP30.at(f, path(f) + " names no reference system"));
        } else if (system.eIsProxy()) {
            findings.add(SP20.at(f, path(f) + ": the reference system does not resolve"));
        } else if (f.isHasHeight() && system.getAxes() != 3) {
            findings.add(SP23.at(f, path(f) + " holds a height in " + path(system) + " of " + system.getAxes()
                    + " axes; a height is the third axis"));
        }
        if (f.getAccuracy() != null) {
            BigDecimal accuracy = decimal(f.getAccuracy());
            if (accuracy == null || accuracy.signum() < 0) {
                findings.add(SP31.at(f, path(f) + ": the accuracy '" + f.getAccuracy()
                        + "' is no decimal number of metres from zero up"));
            }
        }
        epoch(f, resolved(system) ? system : null, findings);
        if (f.getStorage() == GeometryStorage.GEOJSON) {
            if (f.getAxisOrder() != AxisOrder.EAST_NORTH) {
                findings.add(SP28.at(f, path(f) + " is stored as GEOJSON with its coordinates " + f.getAxisOrder()
                        + "; GeoJSON holds them east first"));
            }
            if (resolved(system) && !lonLatOfWgs84(system)) {
                findings.add(SP29.at(f, path(f) + " is stored as GEOJSON in " + ReferenceSystems.name(system)
                        + "; GeoJSON holds longitude and latitude of WGS 84 (CRS84), another system only by prior "
                        + "arrangement"));
            }
        }
        for (ModelElement e : f.getModelElement()) {
            if (e.eIsProxy()) {
                findings.add(SP20.at(f, path(f) + ": a column does not resolve"));
                continue;
            }
            if (!(e instanceof Attribute a)) {
                findings.add(SP1.at(f, path(f) + " names " + e.eClass().getName() + " " + path(e)
                        + "; a geometry feature names attributes"));
                continue;
            }
            Classifier type = a.getType();
            if (type == null || type.eIsProxy() || SpatialElements.isGeometryType(type)) {
                continue;
            }
            TypeKind held = kindOf(type);
            if (!fits(f.getStorage(), held)) {
                findings.add(SP3.at(f, path(f) + ": " + path(a) + " is of type " + type.getName() + " ("
                        + held.name().toLowerCase(Locale.ROOT) + "), which does not hold " + f.getStorage()));
            }
        }
    }

    /**
     * The epoch of a feature's coordinates: a decimal year (SP33), named where its system is dynamic (SP24 where it is
     * not) and only there (SP32).
     */
    private static void epoch(GeometryFeature f, ReferenceSystem system, List<Finding> findings) {
        if (f.getCoordinateEpoch() != null) {
            if (system != null && !system.isDynamic()) {
                findings.add(SP32.at(f, path(f) + " names the coordinate epoch " + f.getCoordinateEpoch()
                        + " but " + ReferenceSystems.name(system) + " is not dynamic; the coordinates of a static "
                        + "system hold for every epoch"));
            } else if (decimal(f.getCoordinateEpoch()) == null) {
                findings.add(SP33.at(f, path(f) + ": the coordinate epoch '" + f.getCoordinateEpoch()
                        + "' is no decimal year"));
            }
        } else if (system != null && system.isDynamic()) {
            findings.add(SP24.at(f, path(f) + " is in the dynamic system " + ReferenceSystems.name(system)
                    + " but names no coordinate epoch; its coordinates cannot be compared with those of another "
                    + "epoch"));
        }
    }

    /** Whether {@code system} is WGS 84 by longitude and latitude: OGC CRS84 or CRS84h, EPSG 4326 or 4979. */
    private static boolean lonLatOfWgs84(ReferenceSystem system) {
        String code = system.getCode() == null ? "" : system.getCode().trim();
        return system.getAuthority() == ReferenceSystemAuthority.OGC ? code.equals("CRS84") || code.equals("CRS84h")
                : code.equals("4326") || code.equals("4979");
    }

    private static void system(ReferenceSystem s, List<Finding> findings) {
        String code = s.getCode() == null ? "" : s.getCode().trim();
        if (code.isEmpty()) {
            findings.add(SP34.at(s, path(s) + " has no code"));
        } else if (s.getAuthority() == ReferenceSystemAuthority.EPSG && ReferenceSystems.epsg(s).isEmpty()) {
            findings.add(SP35.at(s, path(s) + " has the EPSG code '" + s.getCode()
                    + "'; an EPSG code is a positive whole number"));
        }
        if (s.getAxes() != 2 && s.getAxes() != 3) {
            findings.add(SP36.at(s, path(s) + " has " + s.getAxes() + " axes; a system has 2 or 3"));
        }
        if (!s.isSetAxisOrder()) {
            findings.add(SP37.at(s, path(s) + " states no axis order; the order its register defines is stated "
                    + "explicitly (EAST_NORTH or NORTH_EAST), there is no default"));
        }
    }

    private static boolean fits(GeometryStorage storage, TypeKind held) {
        if (held == TypeKind.UNKNOWN) {
            return true;
        }
        return switch (storage) {
        case NATIVE -> held == TypeKind.GEOMETRY;
        case WKB -> held == TypeKind.BINARY;
        case WKT, GEOJSON -> held == TypeKind.TEXT;
        };
    }

    private static void relation(SpatialRelation r, List<Finding> findings) {
        GeometryFeature child = r.getChild();
        GeometryFeature parent = r.getParent();
        boolean featuresKnown = known(r, child, "the child geometry", findings)
                & known(r, parent, "the parent geometry", findings);
        boolean keysKnown = known(r, r.getChildKey(), "the child key", findings)
                & known(r, r.getParentKey(), "the parent key", findings);
        if (r.getExplicitKey() != null && r.getExplicitKey().eIsProxy()) {
            findings.add(SP20.at(r, path(r) + ": the explicit key does not resolve"));
        }
        if (r.getResult() != null && r.getResult().eIsProxy()) {
            findings.add(SP20.at(r, path(r) + ": the result does not resolve"));
        }
        if (r.getWorkingSystem() != null && r.getWorkingSystem().eIsProxy()) {
            findings.add(SP20.at(r, path(r) + ": the working reference system does not resolve"));
        }
        SpatialPredicate predicate = r.getPredicate();
        SpatialAssignment assignment = r.getAssignment();
        boolean weighted = r.isSetWeight();
        BigDecimal minimumShare = null;
        if (r.getMinimumShare() != null) {
            minimumShare = decimal(r.getMinimumShare());
            if (minimumShare == null) {
                findings.add(SP9.at(r, path(r) + ": the minimum share '" + r.getMinimumShare()
                        + "' is no decimal number"));
            } else if (minimumShare.signum() < 0 || minimumShare.compareTo(BigDecimal.ONE) >= 0) {
                findings.add(SP9.at(r, path(r) + ": the minimum share " + r.getMinimumShare()
                        + " is not from 0 below 1"));
            } else if (minimumShare.signum() > 0 && !weighted) {
                findings.add(SP9.at(r, path(r) + ": the minimum share " + r.getMinimumShare()
                        + " has no weight to measure the part of the child by"));
            }
        }
        boolean tolerant = minimumShare != null && minimumShare.signum() > 0;
        if (assignment == SpatialAssignment.EXACTLY_ONE && weighted && !tolerant) {
            findings.add(SP7.at(r, path(r) + " assigns EXACTLY_ONE with the weight " + r.getWeight()
                    + " but no minimum share above zero; the weight would decide nothing"));
        }
        if ((assignment == SpatialAssignment.ALL_WEIGHTED || assignment == SpatialAssignment.LARGEST_SHARE)
                && !weighted) {
            findings.add(SP8.at(r, path(r) + " assigns " + assignment + " without a weight to compare or "
                    + "divide the matches by"));
        }
        if (predicate == SpatialPredicate.WITHIN_DISTANCE) {
            BigDecimal distance = r.getDistance() == null ? null : decimal(r.getDistance());
            if (r.getDistance() == null) {
                findings.add(SP10.at(r, path(r) + " matches WITHIN_DISTANCE without a distance"));
            } else if (distance == null || distance.signum() <= 0) {
                findings.add(SP10.at(r, path(r) + ": the distance '" + r.getDistance()
                        + "' is no decimal number above zero"));
            }
        } else if (r.getDistance() != null) {
            findings.add(SP10.at(r, path(r) + " has the distance " + r.getDistance() + " but matches "
                    + predicate + "; a distance belongs to WITHIN_DISTANCE"));
        }
        if (predicate == SpatialPredicate.NEAREST && assignment != SpatialAssignment.EXACTLY_ONE) {
            findings.add(SP11.at(r, path(r) + " matches NEAREST but assigns " + assignment
                    + "; the nearest parent is one"));
        }
        if (r.getResultForm() == ResultForm.VIEW) {
            if (assignment == SpatialAssignment.EXACTLY_ONE && r.getTieBreak() != TieBreak.LOWEST_PARENT_KEY) {
                findings.add(SP26.at(r, path(r) + " writes a VIEW but assigns EXACTLY_ONE without the tie "
                        + "break LOWEST_PARENT_KEY; a view cannot stop at a tie, write a TABLE"));
            }
        }
        unmatched(r, r.getUnmatched(), r.getResultForm(), "a child without a parent", "the children without a parent",
                "parent", findings);
        if (r.getExplicitKey() != null && predicate == SpatialPredicate.NEAREST) {
            findings.add(SP21.at(r, path(r) + " has an explicit key and matches NEAREST; the key, not the "
                    + "geometry, decides the rows that carry it"));
        }
        if (featuresKnown) {
            kinds(r, child, parent, findings);
            systems(r, child, parent, findings);
        }
        if (featuresKnown && keysKnown) {
            keys(r, findings);
        }
        if (keysKnown) {
            result(r, findings);
        }
    }

    /** Whether {@code target} is named and resolves; a missing one is an error, an unresolved one a warning. */
    private static boolean known(SpatialRelation r, EObject target, String what, List<Finding> findings) {
        if (target == null) {
            findings.add(SP14.at(r, path(r) + " names no " + what.substring("the ".length())));
            return false;
        }
        if (target.eIsProxy()) {
            findings.add(SP20.at(r, path(r) + ": " + what + " does not resolve"));
            return false;
        }
        return true;
    }

    private static void kinds(SpatialRelation r, GeometryFeature child, GeometryFeature parent,
            List<Finding> findings) {
        SpatialPredicate predicate = r.getPredicate();
        boolean needsArea = predicate == SpatialPredicate.WITHIN || predicate == SpatialPredicate.COVERED_BY
                || predicate == SpatialPredicate.INTERSECTS || predicate == SpatialPredicate.CENTROID_WITHIN;
        if (needsArea && parent.getKind() != GeometryKind.POLYGON && parent.getKind() != GeometryKind.MIXED) {
            findings.add(SP5.at(r, path(r) + " matches " + predicate + " against the " + parent.getKind()
                    + " parent " + path(parent) + "; it needs an area"));
        }
        if (r.isSetWeight()) {
            GeometryKind needed = r.getWeight() == SpatialWeight.AREA ? GeometryKind.POLYGON : GeometryKind.LINE;
            if (child.getKind() != needed && child.getKind() != GeometryKind.MIXED) {
                findings.add(SP6.at(r, path(r) + " weighs by " + r.getWeight() + " the " + child.getKind()
                        + " child " + path(child) + "; it needs a " + needed));
            }
        }
    }

    private static void systems(SpatialRelation r, GeometryFeature child, GeometryFeature parent,
            List<Finding> findings) {
        ReferenceSystem working = resolved(r.getWorkingSystem()) ? r.getWorkingSystem() : null;
        if (!resolved(child.getReferenceSystem()) || !resolved(parent.getReferenceSystem())) {
            return;
        }
        boolean same = ReferenceSystems.same(child.getReferenceSystem(), parent.getReferenceSystem());
        if (!same && r.getWorkingSystem() == null) {
            findings.add(SP12.at(r, path(r) + " relates the reference systems "
                    + ReferenceSystems.name(child.getReferenceSystem()) + " and "
                    + ReferenceSystems.name(parent.getReferenceSystem()) + " without a working one"));
        }
        boolean measures = r.isSetWeight() || r.getPredicate() == SpatialPredicate.NEAREST
                || r.getPredicate() == SpatialPredicate.WITHIN_DISTANCE && r.getDistance() != null;
        ReferenceSystem system = working != null ? working : same ? child.getReferenceSystem() : null;
        if (measures) {
            geocentric(r, system, r.isSetWeight() ? r.getWeight().name().toLowerCase(Locale.ROOT) : "a distance",
                    findings);
        }
    }

    /**
     * SP27 where {@code system} is geocentric: the planar functions read x and y only. A geographic system is measured
     * on the spheroid.
     */
    private static void geocentric(ModelElement rule, ReferenceSystem system, String what, List<Finding> findings) {
        if (system != null && system.getKind() == ReferenceSystemKind.GEOCENTRIC) {
            findings.add(SP27.at(rule, path(rule) + " measures " + what + " in the geocentric reference system "
                    + ReferenceSystems.name(system) + ", of which the planar functions read x and y only; name a "
                    + "geographic working system (measured on the spheroid) or a projected one"));
        }
    }

    /**
     * SP15 for a rule that drops the rows it cannot place ({@code many}), SP26 for a view that would have to stop at
     * one ({@code one}); {@code target} is what such a row lacks.
     */
    private static void unmatched(ModelElement rule, UnmatchedPolicy policy, ResultForm form, String one, String many,
            String target, List<Finding> findings) {
        if (form == ResultForm.VIEW && policy == UnmatchedPolicy.ERROR) {
            findings.add(SP26.at(rule, path(rule) + " writes a VIEW but stops at " + one
                    + " (ERROR); a view cannot stop, write a TABLE"));
        }
        if (policy == UnmatchedPolicy.DROP) {
            findings.add(SP15.at(rule, path(rule) + " drops " + many + "; facts that reach the " + target
                    + " through its result vanish from the sums"));
        }
    }

    private static void keys(SpatialRelation r, List<Finding> findings) {
        Set<Classifier> childTables = tables(r.getChild());
        Set<Classifier> parentTables = tables(r.getParent());
        key(r, r.getChildKey(), "child", "child", childTables, true, findings);
        key(r, r.getParentKey(), "parent", "parent", parentTables, true, findings);
        if (r.getExplicitKey() != null && !r.getExplicitKey().eIsProxy()) {
            key(r, r.getExplicitKey(), "explicit", "child", childTables, false, findings);
        }
    }

    /**
     * {@code key} of {@code rule} is a column of one of {@code tables}, those that hold the {@code geometry}; where
     * {@code unique}, a key not declared unique is a warning.
     */
    private static void key(ModelElement rule, Attribute key, String which, String geometry, Set<Classifier> tables,
            boolean unique, List<Finding> findings) {
        Classifier owner = key.getOwner();
        if (owner == null || !tables.contains(owner)) {
            findings.add(SP14.at(rule, path(rule) + ": the " + which + " key " + path(key)
                    + " is no column of a table that holds the " + geometry + " geometry"));
            return;
        }
        if (unique && !SpatialElements.isDeclaredKey(key)) {
            findings.add(SP25.at(rule, path(rule) + ": the " + which + " key " + path(key)
                    + " is no declared key of " + path(owner) + switch (rule) {
                    case GridLevel g -> "; a resolver refuses a key held in several rows, a point has one row";
                    case DistanceBand b -> "; the rows of one key are the parts of one source, the nearest counts";
                    default -> "; the rows of one key are the parts of one geometry, united first";
                    }));
        }
    }

    /**
     * The tables (owners) of the columns a feature names; a logical property typed by the data type Geometry, which
     * the feature may name beside its column, is no column.
     */
    private static Set<Classifier> tables(GeometryFeature feature) {
        Set<Classifier> tables = new HashSet<>();
        for (ModelElement e : feature.getModelElement()) {
            if (!e.eIsProxy() && e instanceof Attribute a && a.getOwner() != null
                    && !SpatialElements.isGeometryType(a.getType())) {
                tables.add(a.getOwner());
            }
        }
        return tables;
    }

    /** The source key of a grid level or a distance band: named, resolved, a column of a table of the source. */
    private static boolean sourceKey(ModelElement rule, GeometryFeature source, Attribute key,
            List<Finding> findings) {
        if (key == null) {
            findings.add(SP14.at(rule, path(rule) + " names no source key"));
            return false;
        }
        if (key.eIsProxy()) {
            findings.add(SP20.at(rule, path(rule) + ": the source key does not resolve"));
            return false;
        }
        if (source == null || source.eIsProxy()) {
            return false;
        }
        key(rule, key, "source", "source", tables(source), true, findings);
        return true;
    }

    private static void result(SpatialRelation r, List<Finding> findings) {
        if (r.getResult() == null || r.getResult().eIsProxy()) {
            return;
        }
        boolean shares = r.getAssignment() == SpatialAssignment.ALL_WEIGHTED
                || r.getAssignment() == SpatialAssignment.ALL_EQUAL;
        List<Attribute> columns = columns(r.getResult());
        int expected = shares ? 3 : 2;
        String shape = shares ? "child key, parent key and share" : "child key and parent key";
        if (columns.size() != expected) {
            findings.add(SP16.at(r, path(r) + ": the result " + path(r.getResult()) + " has "
                    + columns.size() + " columns; " + r.getAssignment() + " gives " + expected + ", " + shape));
            return;
        }
        sameType(r, columns.get(0), r.getChildKey(), "child", findings);
        sameType(r, columns.get(1), r.getParentKey(), "parent", findings);
        if (shares) {
            holds(r, columns.get(2), "the share", Set.of(TypeKind.NUMBER), findings);
        }
    }

    /**
     * The declared result of a grid level or a distance band: the source key, then the cell key or the class, of the
     * kinds {@code second} allows.
     */
    private static void result(ModelElement rule, org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class result,
            Attribute sourceKey, String what, Set<TypeKind> second, List<Finding> findings) {
        if (result == null) {
            return;
        }
        if (result.eIsProxy()) {
            findings.add(SP20.at(rule, path(rule) + ": the result does not resolve"));
            return;
        }
        List<Attribute> columns = columns(result);
        if (columns.size() != 2) {
            findings.add(SP16.at(rule, path(rule) + ": the result " + path(result) + " has " + columns.size()
                    + " columns; it gives 2, source key and " + what));
            return;
        }
        if (sourceKey != null && !sourceKey.eIsProxy()) {
            sameType(rule, columns.get(0), sourceKey, "source", findings);
        }
        holds(rule, columns.get(1), "the " + what, second, findings);
    }

    /** The attributes of a result, in order. */
    private static List<Attribute> columns(org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class result) {
        List<Attribute> columns = new ArrayList<>();
        for (Object f : result.getFeature()) {
            if (f instanceof Attribute a) {
                columns.add(a);
            }
        }
        return columns;
    }

    /** {@code column} is of one of the kinds {@code allowed}; a type that is not known is no finding. */
    private static void holds(ModelElement rule, Attribute column, String what, Set<TypeKind> allowed,
            List<Finding> findings) {
        Classifier type = column.getType();
        if (type == null || type.eIsProxy() || kindOf(type) == TypeKind.UNKNOWN || allowed.contains(kindOf(type))) {
            return;
        }
        findings.add(SP16.at(rule, path(rule) + ": " + what + " " + path(column) + " is of type "
                + type.getName() + ", not " + String.join(" or ", allowed.stream().sorted()
                        .map(k -> k == TypeKind.NUMBER ? "a number" : "text").toList())));
    }

    private static void sameType(ModelElement r, Attribute column, Attribute key, String which,
            List<Finding> findings) {
        Classifier a = column.getType();
        Classifier b = key.getType();
        if (a == null || b == null || a.eIsProxy() || b.eIsProxy() || a == b) {
            return;
        }
        if (a.getName() != null && a.getName().equalsIgnoreCase(b.getName())) {
            return;
        }
        findings.add(SP16.at(r, path(r) + ": the result's " + which + " key " + path(column) + " is of type "
                + a.getName() + ", the " + which + " key " + path(key) + " of " + b.getName()));
    }

    /** Each relation on a cycle of child to parent: following parents from its parent reaches its child. */
    private static void cycles(List<SpatialRelation> relations, List<Finding> findings) {
        Map<GeometryFeature, List<GeometryFeature>> parents = new HashMap<>();
        for (SpatialRelation r : relations) {
            if (resolved(r.getChild()) && resolved(r.getParent())) {
                parents.computeIfAbsent(r.getChild(), k -> new ArrayList<>()).add(r.getParent());
            }
        }
        for (SpatialRelation r : relations) {
            if (!resolved(r.getChild()) || !resolved(r.getParent())) {
                continue;
            }
            Deque<GeometryFeature> open = new ArrayDeque<>(List.of(r.getParent()));
            Set<GeometryFeature> seen = new HashSet<>();
            while (!open.isEmpty()) {
                GeometryFeature f = open.pop();
                if (f == r.getChild()) {
                    findings.add(SP17.at(r, path(r) + ": the parents of " + path(r.getParent())
                            + " lead back to its child " + path(r.getChild()) + "; no place is its own ancestor"));
                    break;
                }
                if (seen.add(f)) {
                    open.addAll(parents.getOrDefault(f, List.of()));
                }
            }
        }
    }

    private static void grid(GridLevel g, List<Finding> findings) {
        GeometryFeature source = g.getSource();
        if (source == null) {
            findings.add(SP14.at(g, path(g) + " names no points"));
        } else if (source.eIsProxy()) {
            findings.add(SP20.at(g, path(g) + ": the points do not resolve"));
        } else if (source.getKind() != GeometryKind.POINT) {
            findings.add(SP18.at(g, path(g) + " puts the " + source.getKind() + " geometry " + path(source)
                    + " into cells; a grid takes points"));
        } else if (source.getMultiplicity() == GeometryMultiplicity.MULTI) {
            findings.add(SP22.at(g, path(g) + " puts the multi-points " + path(source)
                    + " into cells; a multi-point has no one cell"));
        }
        GridSystem system = g.getSystem();
        if (system == null) {
            findings.add(SP18.at(g, path(g) + " names no grid system"));
        } else if (system.eIsProxy()) {
            findings.add(SP20.at(g, path(g) + ": the grid system does not resolve"));
        }
        String range = resolved(system) ? "; " + identifier(system) + " has " + system.getMinResolution() + " to "
                + system.getMaxResolution() : "";
        if (!g.isSetResolution()) {
            findings.add(SP18.at(g, path(g) + " names no resolution" + range));
        } else if (resolved(system) && (g.getResolution() < system.getMinResolution()
                || g.getResolution() > system.getMaxResolution())) {
            findings.add(SP38.at(g, path(g) + " has the resolution " + g.getResolution() + range));
        }
        GridLevel parent = g.getParent();
        if (parent != null && parent.eIsProxy()) {
            findings.add(SP20.at(g, path(g) + ": the level above does not resolve"));
        } else if (parent != null && resolved(system) && resolved(parent.getSystem())
                && !sameGridSystem(parent.getSystem(), system)) {
            findings.add(SP41.at(g, path(g) + " of " + identifier(system) + " lies below " + path(parent) + " of "
                    + identifier(parent.getSystem()) + "; a level lies below one of its own system"));
        } else if (parent != null && parent.getResolution() >= g.getResolution()) {
            findings.add(SP18.at(g, path(g) + " of resolution " + g.getResolution() + " lies below "
                    + path(parent) + " of resolution " + parent.getResolution() + "; the level above is coarser"));
        } else if (parent != null
                && (parent.getSource() != g.getSource() || parent.getSourceKey() != g.getSourceKey())) {
            findings.add(SP18.at(g, path(g) + " lies below " + path(parent) + ", which puts other points into "
                    + "cells; the levels of one hierarchy put the same points, by the same key, into cells"));
        }
        sourceKey(g, source, g.getSourceKey(), findings);
        gridResult(g, findings);
        unmatched(g, g.getUnmatched(), g.getResultForm(), "a point without a cell", "the points without a cell", "cell",
                findings);
    }

    /**
     * A grid system has an identifier, its name, once in its model (SP40), and resolutions from zero up, the minimum at
     * most the maximum (SP39).
     */
    private static void gridSystem(GridSystem s, Map<String, GridSystem> byIdentifier, List<Finding> findings) {
        String identifier = s.getName() == null ? "" : s.getName().trim();
        if (identifier.isEmpty()) {
            findings.add(SP40.at(s, path(s) + " has no identifier; a resolver knows a grid system by its name"));
        } else {
            GridSystem first = byIdentifier.putIfAbsent(identifier, s);
            if (first != null) {
                findings.add(SP40.at(s, path(s) + " has the identifier " + identifier + " of " + path(first)
                        + "; a grid system is made once in a model and found again by its identifier"));
            }
        }
        if (s.getMinResolution() < 0 || s.getMinResolution() > s.getMaxResolution()) {
            findings.add(SP39.at(s, path(s) + " has the resolutions " + s.getMinResolution() + " to "
                    + s.getMaxResolution() + "; they run from zero up, the minimum at most the maximum"));
        }
    }

    /** The name a resolver knows a grid system by. */
    private static String identifier(GridSystem system) {
        return system.getName() == null ? "a grid system without a name" : system.getName().trim();
    }

    /** Whether two grid systems are one: the same element, or the same identifier. */
    private static boolean sameGridSystem(GridSystem a, GridSystem b) {
        return a == b || (a.getName() != null && b.getName() != null && a.getName().trim().equals(b.getName().trim()));
    }

    /**
     * The declared result of a grid level: the source key, then the cell key of each level from the top of its chain
     * down to it (text; text or a number for a system that does not nest, whose parent is a function of the cell and
     * not the head of its key).
     */
    private static void gridResult(GridLevel g, List<Finding> findings) {
        if (g.getResult() == null) {
            return;
        }
        if (g.getResult().eIsProxy()) {
            findings.add(SP20.at(g, path(g) + ": the result does not resolve"));
            return;
        }
        int levels = SpatialElements.chain(g).size();
        List<Attribute> columns = columns(g.getResult());
        if (columns.size() != levels + 1) {
            findings.add(SP16.at(g, path(g) + ": the result " + path(g.getResult()) + " has " + columns.size()
                    + " columns; it gives " + (levels + 1) + ", source key and the cell key of " + levels
                    + (levels == 1 ? " level" : " levels, the coarsest first")));
            return;
        }
        if (g.getSourceKey() != null && !g.getSourceKey().eIsProxy()) {
            sameType(g, columns.get(0), g.getSourceKey(), "source", findings);
        }
        Set<TypeKind> cell = resolved(g.getSystem()) && g.getSystem().isNested() ? Set.of(TypeKind.TEXT)
                : Set.of(TypeKind.TEXT, TypeKind.NUMBER);
        for (Attribute column : columns.subList(1, columns.size())) {
            holds(g, column, "the cell key", cell, findings);
        }
    }

    private static void band(DistanceBand b, List<Finding> findings) {
        GeometryFeature source = b.getSource();
        GeometryFeature reference = b.getReference();
        boolean known = known(b, source, "source", findings) & known(b, reference, "reference", findings);
        BigDecimal last = null;
        if (b.getBound().isEmpty()) {
            findings.add(SP19.at(b, path(b) + " has no bound"));
        }
        for (String literal : b.getBound()) {
            BigDecimal bound = decimal(literal);
            if (bound == null || bound.signum() <= 0) {
                findings.add(SP19.at(b, path(b) + ": the bound '" + literal + "' is no decimal number above "
                        + "zero"));
                continue;
            }
            if (last != null && bound.compareTo(last) <= 0) {
                findings.add(SP19.at(b, path(b) + ": the bound " + literal + " follows " + last.toPlainString()
                        + "; the bounds ascend strictly"));
            }
            last = bound;
        }
        if (b.getWorkingSystem() != null && b.getWorkingSystem().eIsProxy()) {
            findings.add(SP20.at(b, path(b) + ": the working reference system does not resolve"));
        }
        sourceKey(b, source, b.getSourceKey(), findings);
        result(b, b.getResult(), b.getSourceKey(), "class", Set.of(TypeKind.NUMBER), findings);
        unmatched(b, b.getUnmatched(), b.getResultForm(), "a source without a class", "the sources without a class",
                "class", findings);
        if (!known || !resolved(source.getReferenceSystem()) || !resolved(reference.getReferenceSystem())) {
            return;
        }
        boolean same = ReferenceSystems.same(source.getReferenceSystem(), reference.getReferenceSystem());
        if (!same && b.getWorkingSystem() == null) {
            findings.add(SP12.at(b, path(b) + " measures between the reference systems "
                    + ReferenceSystems.name(source.getReferenceSystem()) + " and "
                    + ReferenceSystems.name(reference.getReferenceSystem()) + " without a working one"));
        }
        ReferenceSystem working = resolved(b.getWorkingSystem()) ? b.getWorkingSystem() : null;
        geocentric(b, working != null ? working : same ? source.getReferenceSystem() : null, "a distance", findings);
    }

    /** Whether a band names {@code f} as its {@code which} and it resolves: SP14 where it names none, else SP20. */
    private static boolean known(DistanceBand b, GeometryFeature f, String which, List<Finding> findings) {
        if (f == null) {
            findings.add(SP14.at(b, path(b) + " names no " + which));
            return false;
        }
        if (f.eIsProxy()) {
            findings.add(SP20.at(b, path(b) + ": the " + which + " does not resolve"));
            return false;
        }
        return true;
    }

    /** What a type holds: by its name (SQL, CWM, Ecore), else by its JDBC type code. */
    static TypeKind kindOf(Classifier type) {
        String name = type.getName() == null ? "" : plain(type.getName());
        if (GEOMETRY_NAMES.contains(name)) {
            return TypeKind.GEOMETRY;
        }
        if (TEXT_NAMES.contains(name)) {
            return TypeKind.TEXT;
        }
        if (BINARY_NAMES.contains(name)) {
            return TypeKind.BINARY;
        }
        if (NUMBER_NAMES.contains(name)) {
            return TypeKind.NUMBER;
        }
        EStructuralFeature typeNumber = type.eClass().getEStructuralFeature("typeNumber");
        if (typeNumber != null && type.eIsSet(typeNumber) && type.eGet(typeNumber) instanceof Number n) {
            long code = n.longValue();
            if (TEXT_JDBC.contains(code)) {
                return TypeKind.TEXT;
            }
            if (BINARY_JDBC.contains(code)) {
                return TypeKind.BINARY;
            }
            if (NUMBER_JDBC.contains(code)) {
                return TypeKind.NUMBER;
            }
        }
        return TypeKind.UNKNOWN;
    }

    /**
     * A type name in lower case without its length or precision ({@code VARCHAR(40)} is {@code varchar}), an Ecore
     * type name in its plain form ({@code EString} is {@code string}).
     */
    private static String plain(String name) {
        String n = name.trim();
        int paren = n.indexOf('(');
        if (paren > 0) {
            n = n.substring(0, paren).trim();
        }
        if (n.length() > 1 && n.charAt(0) == 'E' && Character.isUpperCase(n.charAt(1))) {
            n = n.substring(1);
            if (n.endsWith("Object")) {
                n = n.substring(0, n.length() - "Object".length());
            }
        }
        return n.toLowerCase(Locale.ROOT);
    }

    private static boolean resolved(EObject o) {
        return o != null && !o.eIsProxy();
    }

    private static BigDecimal decimal(String literal) {
        try {
            return new BigDecimal(literal.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The names from the root down to {@code element}, joined by dots. */
    static String path(EObject element) {
        Deque<String> names = new ArrayDeque<>();
        for (EObject o = element; o != null; o = o.eContainer()) {
            if (o instanceof ModelElement me && me.getName() != null) {
                names.addFirst(me.getName());
            }
        }
        return String.join(".", names);
    }
}
