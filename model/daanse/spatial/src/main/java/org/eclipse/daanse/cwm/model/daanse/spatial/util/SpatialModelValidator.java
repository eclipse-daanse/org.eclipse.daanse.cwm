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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Multiplicity;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.MultiplicityRange;
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
import org.eclipse.daanse.cwm.model.daanse.validation.Finding;
import org.eclipse.daanse.cwm.model.daanse.validation.Findings;
import org.eclipse.daanse.cwm.model.daanse.validation.Rule;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * Checks geometry features and their holdings, spatial relations, grid levels, grid systems and distance bands beyond
 * what the metamodel enforces.
 * Errors stop a resolver, warnings do not.
 *
 * <ul>
 * <li>There is no {@code SP1}: a holding names attributes by its type, so that no class, package or association end
 * can stand where an attribute does.</li>
 * <li>{@code SP2} an attribute is named once, by one holding (in one of its parts).</li>
 * <li>{@code SP3} (warning) the type of the attribute of a whole holding fits its storage: {@code NATIVE} a geometry
 * type (the data type {@code Geometry} of a logical property among them), {@code WKT} and {@code GEOJSON} text,
 * {@code WKB} binary; a type that is not known is no finding.</li>
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
 * <li>{@code SP28} a whole holding stored as {@code GEOJSON} holds its coordinates east first (RFC 7946).</li>
 * <li>{@code SP29} (warning) a whole holding stored as {@code GEOJSON} holds longitude and latitude of WGS 84: OGC
 * CRS84 or CRS84h, or EPSG 4326 or 4979 stored east first; another system only by prior arrangement (RFC 7946).
 * Numbers have no storage: neither rule reads them.</li>
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
 * <li>{@code SP42} a geometry feature has a holding, and each holding names its attributes: a whole and a list
 * holding one, a holding in parts at least one part, each part one.</li>
 * <li>{@code SP43} a geometry held as numbers (in parts or as a list) is a single point, or the envelope of another
 * geometry.</li>
 * <li>{@code SP44} a holding of numbers names each role once.</li>
 * <li>{@code SP45} the roles fit the shape: a point has {@code EAST} and {@code NORTH} and no edge of an envelope; an
 * envelope its four edges {@code MIN_EAST}, {@code MIN_NORTH}, {@code MAX_EAST}, {@code MAX_NORTH} and no axis of a
 * point.</li>
 * <li>{@code SP46} a geometry held as numbers has its height roles exactly where it has a height: {@code HEIGHT} for
 * a point, {@code MIN_HEIGHT} and {@code MAX_HEIGHT} for an envelope.</li>
 * <li>{@code SP47} (warning) the attributes of numbers are numeric: the attribute of a part a number; that of a list
 * text where it has a separator, else several numbers (a multi-valued attribute of a number type, or an array); a
 * type that is not known, or a number type without a stated multiplicity, is no finding.</li>
 * <li>{@code SP48} the attributes of a holding in parts belong to one classifier: one row holds one value. A geometry
 * held whole may be held in several tables, one holding each.</li>
 * <li>{@code SP49} a geometry names a derivation exactly where it names what it is derived from.</li>
 * <li>{@code SP50} a geometry is not derived from itself, directly or through others.</li>
 * <li>{@code SP51} a derived geometry is in the reference system of the geometry it is derived from (one URI).</li>
 * <li>{@code SP52} the kind follows the derivation: an envelope is a single polygon; a corner, a centroid and a point
 * on the surface are single points.</li>
 * <li>{@code SP53} two corners derived from one geometry and held in one classifier are opposite corners: south-west
 * and north-east, or north-west and south-east.</li>
 * </ul>
 *
 * <p>Distances and the bounds of distance classes are metres, whatever the reference system. A relation, a grid level
 * and a band read a geometry from a holding in the table of their key; every holding of a geometry holds the same
 * value, and numbers hold only a point or an envelope (SP43), which a resolver builds, so that every predicate and
 * weight reads a geometry held as numbers.</p>
 */
public final class SpatialModelValidator {

    /** The module the rules of this validator belong to. */
    public static final String MODULE = "spatial";

    public static final Rule SP2 = Rule.error(MODULE, "SP2", "An attribute is named once, by one holding.");

    public static final Rule SP3 = Rule.warning(MODULE, "SP3",
            "The type of the attribute of a whole holding fits its storage.");

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
            "A whole holding stored as GeoJSON holds its coordinates east first.");

    public static final Rule SP29 = Rule.warning(MODULE, "SP29",
            "A whole holding stored as GeoJSON holds longitude and latitude of WGS 84 (CRS84, CRS84h, EPSG 4326 or "
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

    public static final Rule SP42 = Rule.error(MODULE, "SP42",
            "A geometry feature has a holding, and each holding names its attributes.");

    public static final Rule SP43 = Rule.error(MODULE, "SP43",
            "A geometry held as numbers is a single point, or the envelope of another geometry.");

    public static final Rule SP44 = Rule.error(MODULE, "SP44", "A holding of numbers names each role once.");

    public static final Rule SP45 = Rule.error(MODULE, "SP45",
            "The roles fit the shape: a point has EAST and NORTH and no edge, an envelope its four edges and no "
                + "axis of a point.");

    public static final Rule SP46 = Rule.error(MODULE, "SP46",
            "A geometry held as numbers has its height roles exactly where it has a height.");

    public static final Rule SP47 = Rule.warning(MODULE, "SP47",
            "The attributes of numbers are numeric: a part's a number, a list's text with a separator or several "
                + "numbers.");

    public static final Rule SP48 = Rule.error(MODULE, "SP48",
            "The attributes of a holding in parts belong to one classifier.");

    public static final Rule SP49 = Rule.error(MODULE, "SP49",
            "A geometry names a derivation exactly where it names what it is derived from.");

    public static final Rule SP50 = Rule.error(MODULE, "SP50",
            "A geometry is not derived from itself, directly or through others.");

    public static final Rule SP51 = Rule.error(MODULE, "SP51",
            "A derived geometry is in the reference system of the geometry it is derived from.");

    public static final Rule SP52 = Rule.error(MODULE, "SP52",
            "The kind follows the derivation: an envelope is a single polygon, a corner, a centroid or a point on "
                + "the surface a single point.");

    public static final Rule SP53 = Rule.error(MODULE, "SP53",
            "Two corners derived from one geometry and held in one classifier are opposite corners.");

    /** Every rule of this validator, in the order of their codes; there is no SP1, no SP4 and no SP13. */
    public static final List<Rule> RULES = List.of(SP2, SP3, SP5, SP6, SP7, SP8, SP9, SP10, SP11, SP12, SP14,
            SP15, SP16, SP17, SP18, SP19, SP20, SP21, SP22, SP23, SP24, SP25, SP26, SP27, SP28, SP29, SP30, SP31, SP32,
            SP33, SP34, SP35, SP36, SP37, SP38, SP39, SP40, SP41, SP42, SP43, SP44, SP45, SP46, SP47, SP48, SP49, SP50,
            SP51, SP52, SP53);

    /** What a type holds, as far as a geometry is concerned. */
    enum TypeKind {
        GEOMETRY, TEXT, BINARY, NUMBER, ARRAY, UNKNOWN
    }

    /** The roles of a point. */
    private static final Set<Ordinate> POINT_ROLES = EnumSet.of(Ordinate.EAST, Ordinate.NORTH, Ordinate.HEIGHT);
    /** The roles of an envelope. */
    private static final Set<Ordinate> ENVELOPE_ROLES = EnumSet.of(Ordinate.MIN_EAST, Ordinate.MIN_NORTH,
            Ordinate.MAX_EAST, Ordinate.MAX_NORTH, Ordinate.MIN_HEIGHT, Ordinate.MAX_HEIGHT);
    /** The corners of an envelope. */
    private static final Set<GeometryDerivation> CORNERS = EnumSet.of(GeometryDerivation.CORNER_SOUTH_WEST,
            GeometryDerivation.CORNER_NORTH_EAST, GeometryDerivation.CORNER_NORTH_WEST,
            GeometryDerivation.CORNER_SOUTH_EAST);

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
    /** The JDBC type code ARRAY. */
    private static final long ARRAY_JDBC = 2003L;

    private SpatialModelValidator() {
    }

    /** All findings for the geodata in {@code root} and below. */
    public static List<Finding> check(EObject root) {
        List<Finding> findings = new ArrayList<>();
        List<EObject> all = new ArrayList<>(List.of(root));
        root.eAllContents().forEachRemaining(all::add);
        Map<Attribute, List<GeometryHolding>> byAttribute = new LinkedHashMap<>();
        List<GeometryFeature> features = new ArrayList<>();
        List<SpatialRelation> relations = new ArrayList<>();
        Map<String, GridSystem> byIdentifier = new HashMap<>();
        for (EObject o : all) {
            if (o instanceof GeometryFeature f) {
                feature(f, findings);
                features.add(f);
                for (GeometryHolding h : f.getHolding()) {
                    for (Attribute a : SpatialElements.attributes(h)) {
                        byAttribute.computeIfAbsent(a, k -> new ArrayList<>()).add(h);
                    }
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
        for (Map.Entry<Attribute, List<GeometryHolding>> e : byAttribute.entrySet()) {
            if (e.getValue().size() > 1) {
                findings.add(SP2.at(e.getKey(), path(e.getKey()) + " is named " + e.getValue().size() + " times, by "
                        + String.join(", ", e.getValue().stream().map(SpatialModelValidator::describe).toList())
                        + "; an attribute holds one geometry or one number of it"));
            }
        }
        derivationCycles(features, findings);
        corners(features, findings);
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
        if (f.getHolding().isEmpty()) {
            findings.add(SP42.at(f, path(f) + " has no holding; nothing says where the geometry is held"));
        }
        for (GeometryHolding h : f.getHolding()) {
            switch (h) {
            case WholeHolding w -> whole(f, w, resolved(system) ? system : null, findings);
            case PartsHolding p -> numbers(f, p, partRoles(f, p, findings), findings);
            case ListHolding l -> numbers(f, l, listRoles(f, l, findings), findings);
            default -> {
                // a holding of another kind is not read by this model
            }
            }
        }
        derivation(f, findings);
    }

    /** A whole holding: its attribute and its type (SP42, SP20, SP3), GeoJSON east first in WGS 84 (SP28, SP29). */
    private static void whole(GeometryFeature f, WholeHolding w, ReferenceSystem system, List<Finding> findings) {
        if (w.getStorage() == GeometryStorage.GEOJSON) {
            if (w.getAxisOrder() != AxisOrder.EAST_NORTH) {
                findings.add(SP28.at(f, path(f) + " is stored as GEOJSON with its coordinates " + w.getAxisOrder()
                        + "; GeoJSON holds them east first"));
            }
            if (system != null && !lonLatOfWgs84(system)) {
                findings.add(SP29.at(f, path(f) + " is stored as GEOJSON in " + ReferenceSystems.name(system)
                        + "; GeoJSON holds longitude and latitude of WGS 84 (CRS84), another system only by prior "
                        + "arrangement"));
            }
        }
        Attribute a = w.getAttribute();
        if (a == null) {
            findings.add(SP42.at(f, path(f) + ": a whole holding names no attribute"));
            return;
        }
        if (a.eIsProxy()) {
            findings.add(SP20.at(f, path(f) + ": an attribute does not resolve"));
            return;
        }
        Classifier type = a.getType();
        if (type == null || type.eIsProxy()) {
            return;
        }
        TypeKind held = kindOf(type);
        if (!fits(w.getStorage(), held)) {
            findings.add(SP3.at(f, path(f) + ": " + path(a) + " is of type " + type.getName() + " ("
                    + held.name().toLowerCase(Locale.ROOT) + "), which does not hold " + w.getStorage()));
        }
    }

    /**
     * The roles of a holding in parts, in order; its parts and their attributes are named (SP42) and resolve (SP20),
     * each attribute is a number (SP47), and all lie in one classifier (SP48).
     */
    private static List<Ordinate> partRoles(GeometryFeature f, PartsHolding p, List<Finding> findings) {
        if (p.getPart().isEmpty()) {
            findings.add(SP42.at(f, path(f) + ": a holding in parts has no part"));
        }
        List<Ordinate> roles = new ArrayList<>();
        Set<Classifier> owners = new LinkedHashSet<>();
        for (CoordinatePart part : p.getPart()) {
            roles.add(part.getRole());
            Attribute a = part.getAttribute();
            if (a == null) {
                findings.add(SP42.at(f, path(f) + ": the part " + part.getRole() + " names no attribute"));
                continue;
            }
            if (a.eIsProxy()) {
                findings.add(SP20.at(f, path(f) + ": the attribute of the part " + part.getRole()
                        + " does not resolve"));
                continue;
            }
            if (a.getOwner() != null) {
                owners.add(a.getOwner());
            }
            Classifier type = a.getType();
            TypeKind held = type == null || type.eIsProxy() ? TypeKind.UNKNOWN : kindOf(type);
            if (held != TypeKind.NUMBER && held != TypeKind.UNKNOWN) {
                findings.add(SP47.at(f, path(f) + ": the part " + part.getRole() + " " + path(a) + " is of type "
                        + type.getName() + " (" + held.name().toLowerCase(Locale.ROOT) + "), not a number"));
            }
        }
        if (owners.size() > 1) {
            findings.add(SP48.at(f, path(f) + ": the parts lie in " + owners.size() + " classifiers, "
                    + String.join(", ", owners.stream().map(SpatialModelValidator::path).toList())
                    + "; a holding in parts is one row of one holder"));
        }
        return roles;
    }

    /**
     * The roles of a holding as a list, in order; its attribute is named (SP42) and resolves (SP20), and is text with
     * a separator, else holds several numbers (SP47).
     */
    private static List<Ordinate> listRoles(GeometryFeature f, ListHolding l, List<Finding> findings) {
        Attribute a = l.getAttribute();
        if (a == null) {
            findings.add(SP42.at(f, path(f) + ": a list names no attribute"));
        } else if (a.eIsProxy()) {
            findings.add(SP20.at(f, path(f) + ": an attribute does not resolve"));
        } else if (a.getType() != null && !a.getType().eIsProxy()) {
            Classifier type = a.getType();
            TypeKind held = kindOf(type);
            String why = null;
            if (l.getSeparator() != null) {
                if (held != TypeKind.TEXT && held != TypeKind.UNKNOWN) {
                    why = "; a list with a separator is text";
                }
            } else if (held == TypeKind.TEXT) {
                why = "; a list in a text names its separator";
            } else if (held == TypeKind.NUMBER && singleValued(a)) {
                why = ", one number; several numbers without a separator are an array or a multi-valued attribute";
            } else if (held != TypeKind.NUMBER && held != TypeKind.ARRAY && held != TypeKind.UNKNOWN) {
                why = "; several numbers without a separator are an array or a multi-valued attribute";
            }
            if (why != null) {
                findings.add(SP47.at(f, path(f) + ": the list " + path(a) + " is of type " + type.getName() + " ("
                        + held.name().toLowerCase(Locale.ROOT) + ")" + why));
            }
        }
        return List.copyOf(l.getOrdinate());
    }

    /** Whether {@code a} states a multiplicity of one value at most. */
    private static boolean singleValued(Attribute a) {
        Multiplicity multiplicity = a.getMultiplicity();
        if (multiplicity == null || multiplicity.getRange().isEmpty()) {
            return false;
        }
        for (MultiplicityRange range : multiplicity.getRange()) {
            if (range.getUpper() < 0 || range.getUpper() > 1) {
                return false;
            }
        }
        return true;
    }

    /**
     * A holding of numbers: a single point or an envelope (SP43), each role once (SP44), the roles of the shape
     * (SP45) and of the height (SP46).
     */
    private static void numbers(GeometryFeature f, GeometryHolding h, List<Ordinate> roles, List<Finding> findings) {
        boolean envelope = f.isSetDerivation() && f.getDerivation() == GeometryDerivation.ENVELOPE;
        boolean point = !envelope && f.getKind() == GeometryKind.POINT;
        if (!envelope && !point) {
            findings.add(SP43.at(f, path(f) + " is a " + f.getKind() + " held as numbers in " + form(h)
                    + "; numbers hold a single point or the envelope of another geometry"));
        } else if (point && f.getMultiplicity() == GeometryMultiplicity.MULTI) {
            findings.add(SP43.at(f, path(f) + " is a MULTI point held as numbers in " + form(h)
                    + "; numbers hold a single point"));
        }
        Set<Ordinate> seen = EnumSet.noneOf(Ordinate.class);
        Set<Ordinate> twice = new LinkedHashSet<>();
        for (Ordinate role : roles) {
            if (!seen.add(role)) {
                twice.add(role);
            }
        }
        if (!twice.isEmpty()) {
            findings.add(SP44.at(f, path(f) + " holds " + names(twice) + " more than once in " + form(h)
                    + "; each role is held once"));
        }
        if ((!envelope && !point) || (h instanceof PartsHolding p && p.getPart().isEmpty())) {
            return;
        }
        Set<Ordinate> plane = point ? EnumSet.of(Ordinate.EAST, Ordinate.NORTH)
                : EnumSet.of(Ordinate.MIN_EAST, Ordinate.MIN_NORTH, Ordinate.MAX_EAST, Ordinate.MAX_NORTH);
        Set<Ordinate> missing = EnumSet.copyOf(plane);
        missing.removeAll(seen);
        Set<Ordinate> foreign = EnumSet.copyOf(seen);
        foreign.retainAll(point ? ENVELOPE_ROLES : POINT_ROLES);
        if (!missing.isEmpty()) {
            findings.add(SP45.at(f, path(f) + " holds no " + names(missing) + " in " + form(h)
                    + (point ? "; a point is held by EAST and NORTH"
                            : "; an envelope is held by MIN_EAST, MIN_NORTH, MAX_EAST and MAX_NORTH")));
        }
        if (!foreign.isEmpty()) {
            findings.add(SP45.at(f, path(f) + " holds " + names(foreign) + " in " + form(h)
                    + (point ? ", the edges of an envelope; a point has none" : ", the axes of a point; an "
                            + "envelope has none")));
        }
        Set<Ordinate> height = point ? EnumSet.of(Ordinate.HEIGHT)
                : EnumSet.of(Ordinate.MIN_HEIGHT, Ordinate.MAX_HEIGHT);
        if (f.isHasHeight() && !seen.containsAll(height)) {
            Set<Ordinate> lacking = EnumSet.copyOf(height);
            lacking.removeAll(seen);
            findings.add(SP46.at(f, path(f) + " has a height but holds no " + names(lacking) + " in " + form(h)));
        } else if (!f.isHasHeight() && seen.stream().anyMatch(height::contains)) {
            Set<Ordinate> named = EnumSet.copyOf(height);
            named.retainAll(seen);
            findings.add(SP46.at(f, path(f) + " has no height but holds " + names(named) + " in " + form(h)
                    + "; a height needs hasHeight and a system of three axes"));
        }
    }

    /**
     * What a geometry is derived from: a derivation exactly with of (SP49), resolving (SP20), in the same reference
     * system (SP51), of the kind of the derivation and single (SP52).
     */
    private static void derivation(GeometryFeature f, List<Finding> findings) {
        GeometryFeature of = f.getOf();
        if (f.isSetDerivation() && of == null) {
            findings.add(SP49.at(f, path(f) + " is the " + f.getDerivation() + " of nothing; it names no geometry "
                    + "it is derived from"));
            return;
        }
        if (!f.isSetDerivation() && of != null) {
            findings.add(SP49.at(f, path(f) + " is derived from " + path(of) + " but names no derivation"));
            return;
        }
        if (of == null) {
            return;
        }
        if (of.eIsProxy()) {
            findings.add(SP20.at(f, path(f) + ": the geometry it is derived from does not resolve"));
            return;
        }
        GeometryDerivation d = f.getDerivation();
        if (resolved(f.getReferenceSystem()) && resolved(of.getReferenceSystem())
                && !ReferenceSystems.same(f.getReferenceSystem(), of.getReferenceSystem())) {
            findings.add(SP51.at(f, path(f) + " is the " + d + " of " + path(of) + " in "
                    + ReferenceSystems.name(of.getReferenceSystem()) + " but is in "
                    + ReferenceSystems.name(f.getReferenceSystem()) + "; a derived geometry keeps the system"));
        }
        GeometryKind kind = d == GeometryDerivation.ENVELOPE ? GeometryKind.POLYGON : GeometryKind.POINT;
        String rule = switch (d) {
        case ENVELOPE -> "an envelope is a single POLYGON";
        case CENTROID -> "a centroid is a single POINT";
        case POINT_ON_SURFACE -> "a point on the surface is a single POINT";
        default -> "a corner is a single POINT";
        };
        if (f.getKind() != kind) {
            findings.add(SP52.at(f, path(f) + " is the " + d + " of " + path(of) + " but of kind " + f.getKind()
                    + "; " + rule));
        } else if (f.getMultiplicity() == GeometryMultiplicity.MULTI) {
            findings.add(SP52.at(f, path(f) + " is the " + d + " of " + path(of) + " but MULTI; " + rule));
        }
    }

    /** Each geometry derived from itself, directly or through the geometries it is derived from (SP50). */
    private static void derivationCycles(List<GeometryFeature> features, List<Finding> findings) {
        for (GeometryFeature f : features) {
            if (!resolved(f.getOf())) {
                continue;
            }
            List<String> chain = new ArrayList<>();
            Set<GeometryFeature> seen = new HashSet<>();
            for (GeometryFeature g = f.getOf(); resolved(g) && seen.add(g); g = g.getOf()) {
                if (g == f) {
                    findings.add(SP50.at(f, path(f) + (chain.isEmpty() ? " is derived from itself"
                            : " is derived from itself through " + String.join(", ", chain))
                            + "; a derived geometry comes from another one"));
                    break;
                }
                chain.add(path(g));
            }
        }
    }

    /**
     * Two corners of one geometry held in one classifier are opposite (SP53): the second of a pair that is not is
     * named.
     */
    private static void corners(List<GeometryFeature> features, List<Finding> findings) {
        Map<GeometryFeature, List<GeometryFeature>> byOf = new LinkedHashMap<>();
        for (GeometryFeature f : features) {
            if (f.isSetDerivation() && CORNERS.contains(f.getDerivation()) && resolved(f.getOf())) {
                byOf.computeIfAbsent(f.getOf(), k -> new ArrayList<>()).add(f);
            }
        }
        for (Map.Entry<GeometryFeature, List<GeometryFeature>> e : byOf.entrySet()) {
            List<GeometryFeature> corners = e.getValue();
            for (int j = 1; j < corners.size(); j++) {
                GeometryFeature second = corners.get(j);
                for (int i = 0; i < j; i++) {
                    GeometryFeature first = corners.get(i);
                    Set<Classifier> common = new LinkedHashSet<>(tables(first));
                    common.retainAll(tables(second));
                    if (!common.isEmpty() && !opposite(first.getDerivation(), second.getDerivation())) {
                        findings.add(SP53.at(second, path(second) + " is the " + second.getDerivation() + " of "
                                + path(e.getKey()) + " beside its " + first.getDerivation() + " " + path(first)
                                + " in " + path(common.iterator().next()) + "; two corners of one geometry in one "
                                + "holder are opposite: south-west and north-east, or north-west and south-east"));
                        break;
                    }
                }
            }
        }
    }

    private static boolean opposite(GeometryDerivation a, GeometryDerivation b) {
        return switch (a) {
        case CORNER_SOUTH_WEST -> b == GeometryDerivation.CORNER_NORTH_EAST;
        case CORNER_NORTH_EAST -> b == GeometryDerivation.CORNER_SOUTH_WEST;
        case CORNER_NORTH_WEST -> b == GeometryDerivation.CORNER_SOUTH_EAST;
        case CORNER_SOUTH_EAST -> b == GeometryDerivation.CORNER_NORTH_WEST;
        default -> false;
        };
    }

    /** A holding as messages name it: the whole holding, the parts or the list of its feature. */
    private static String describe(GeometryHolding h) {
        String of = h.eContainer() == null ? "" : " of " + path(h.eContainer());
        return switch (h) {
        case WholeHolding w -> "the whole holding" + of;
        case PartsHolding p -> "the parts" + of;
        case ListHolding l -> "the list" + of;
        default -> "a holding" + of;
        };
    }

    /** A holding as a message about its feature names it: its whole holding, its parts, its list. */
    private static String form(GeometryHolding h) {
        return switch (h) {
        case WholeHolding w -> "its whole holding";
        case PartsHolding p -> "its parts";
        case ListHolding l -> "its list";
        default -> "a holding";
        };
    }

    private static String names(Set<Ordinate> roles) {
        return String.join(", ", roles.stream().map(Ordinate::getName).toList());
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

    /** The tables or classes (owners) of the attributes of all holdings of a feature. */
    private static Set<Classifier> tables(GeometryFeature feature) {
        Set<Classifier> tables = new LinkedHashSet<>();
        for (GeometryHolding h : feature.getHolding()) {
            for (Attribute a : SpatialElements.attributes(h)) {
                if (!a.eIsProxy() && a.getOwner() != null) {
                    tables.add(a.getOwner());
                }
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

    /**
     * What a type holds: the data type Geometry a geometry; a JSON array type an array, a JSON primitive type by its
     * value kind (read without a dependency, as a unique key is); else by its name (SQL, CWM, Ecore; an array as
     * {@code DOUBLE[]} or {@code DOUBLE ARRAY}), else by its JDBC type code.
     */
    static TypeKind kindOf(Classifier type) {
        if (type instanceof GeometryType) {
            return TypeKind.GEOMETRY;
        }
        if ("ArrayType".equals(type.eClass().getName())) {
            return TypeKind.ARRAY;
        }
        EStructuralFeature valueKind = type.eClass().getEStructuralFeature("valueKind");
        if (valueKind != null && type.eIsSet(valueKind)) {
            switch (String.valueOf(type.eGet(valueKind))) {
            case "json_number", "json_integer" -> {
                return TypeKind.NUMBER;
            }
            case "json_string" -> {
                return TypeKind.TEXT;
            }
            case "json_array" -> {
                return TypeKind.ARRAY;
            }
            default -> {
                // an object, a truth value or null holds no number and no text a geometry is read from
            }
            }
        }
        String name = type.getName() == null ? "" : plain(type.getName());
        if (name.endsWith("]") || name.equals("array") || name.endsWith(" array")) {
            return TypeKind.ARRAY;
        }
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
            if (code == ARRAY_JDBC) {
                return TypeKind.ARRAY;
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
