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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Attribute;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.daanse.cwm.model.daanse.spatial.AxisOrder;
import org.eclipse.daanse.cwm.model.daanse.spatial.DistanceBand;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryFeature;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryKind;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryType;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryStorage;
import org.eclipse.daanse.cwm.model.daanse.spatial.GridLevel;
import org.eclipse.daanse.cwm.model.daanse.spatial.GridSystem;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystem;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystemAuthority;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystemKind;
import org.eclipse.daanse.cwm.model.daanse.spatial.SpatialAssignment;
import org.eclipse.daanse.cwm.model.daanse.spatial.SpatialFactory;
import org.eclipse.daanse.cwm.model.daanse.spatial.SpatialPackage;
import org.eclipse.daanse.cwm.model.daanse.spatial.SpatialPredicate;
import org.eclipse.daanse.cwm.model.daanse.spatial.SpatialRelation;
import org.eclipse.daanse.cwm.model.daanse.spatial.SpatialWeight;
import org.eclipse.daanse.cwm.model.daanse.spatial.UnmatchedPolicy;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Creates the elements of the spatial model and finds them from a column. Each element is owned by the namespace it
 * is created in (a package of geodata beside the relational model, for instance) and names its columns; a column owns
 * nothing of it.
 *
 * <p>The features of a column and the relations of a feature are found backwards through {@link InverseReferences}:
 * with an {@code ECrossReferenceAdapter} installed on the resource set in constant time, without one by a scan of the
 * model.</p>
 */
public final class SpatialElements {

    private static final SpatialFactory F = SpatialFactory.eINSTANCE;

    /** The name the logical data type of a property that holds a geometry is given. */
    public static final String GEOMETRY = "Geometry";

    /** The identifier of Geohash: rectangles of 1 to 12 characters, strictly nested. */
    public static final String GEOHASH = "geohash";

    /** The identifier of H3: hexagons of resolution 0 to 15; the parent is a function of the cell. */
    public static final String H3 = "h3";

    /** The identifier of the quadkey tiles of the web map: zoom 1 to 23, strictly nested. */
    public static final String QUADKEY = "quadkey";

    private SpatialElements() {
    }

    /**
     * A reference system owned by {@code owner}: {@code code} of {@code authority}, of {@code kind}, with two axes,
     * static, the first two axes in {@code axisOrder} as its register defines them (EPSG 4326 and 3035 north first,
     * EPSG 25832 east first); the rest is set on the element.
     */
    public static ReferenceSystem referenceSystem(Namespace owner, String name, ReferenceSystemAuthority authority,
            String code, ReferenceSystemKind kind, AxisOrder axisOrder) {
        ReferenceSystem system = F.createReferenceSystem();
        system.setAuthority(Objects.requireNonNull(authority, "authority"));
        system.setCode(Objects.requireNonNull(code, "code"));
        system.setKind(Objects.requireNonNull(kind, "kind"));
        system.setAxisOrder(Objects.requireNonNull(axisOrder, "axisOrder"));
        return owned(owner, name, system);
    }

    /** An EPSG reference system owned by {@code owner}, named {@code EPSG_<code>}, as {@link #referenceSystem}. */
    public static ReferenceSystem epsg(Namespace owner, long code, ReferenceSystemKind kind, AxisOrder axisOrder) {
        return referenceSystem(owner, "EPSG_" + code, ReferenceSystemAuthority.EPSG, Long.toString(code), kind,
                axisOrder);
    }

    /**
     * OGC CRS84 owned by {@code owner}, named {@code CRS84}: WGS 84, longitude and latitude in degrees, longitude
     * first, version {@code 1.3} as its URI {@code http://www.opengis.net/def/crs/OGC/1.3/CRS84} writes it; the default
     * of OGC API Features.
     */
    public static ReferenceSystem crs84(Namespace owner) {
        ReferenceSystem system = referenceSystem(owner, "CRS84", ReferenceSystemAuthority.OGC, "CRS84",
                ReferenceSystemKind.GEOGRAPHIC, AxisOrder.EAST_NORTH);
        system.setVersion("1.3");
        return system;
    }

    /**
     * OGC CRS84h owned by {@code owner}, named {@code CRS84h}: WGS 84 with the ellipsoidal height, three axes,
     * longitude first, version {@code 0} as its URI {@code http://www.opengis.net/def/crs/OGC/0/CRS84h} writes it.
     */
    public static ReferenceSystem crs84h(Namespace owner) {
        ReferenceSystem system = referenceSystem(owner, "CRS84h", ReferenceSystemAuthority.OGC, "CRS84h",
                ReferenceSystemKind.GEOGRAPHIC, AxisOrder.EAST_NORTH);
        system.setVersion("0");
        system.setAxes(3);
        return system;
    }

    /**
     * The grid system the model of {@code owner} knows by {@code identifier}, made in {@code owner} where the model has
     * none: resolutions {@code minResolution} to {@code maxResolution}, nested or not. A grid system is made once per
     * model and found again by its identifier, wherever in the model it is owned.
     *
     * @throws IllegalArgumentException where the model holds a grid system of that identifier with other resolutions
     *         or another nesting
     */
    public static GridSystem gridSystem(Namespace owner, String identifier, long minResolution, long maxResolution,
            boolean nested) {
        Objects.requireNonNull(owner, "owner");
        Optional<GridSystem> found = gridSystem(owner, Objects.requireNonNull(identifier, "identifier"));
        if (found.isPresent()) {
            GridSystem system = found.get();
            if (system.getMinResolution() != minResolution || system.getMaxResolution() != maxResolution
                    || system.isNested() != nested) {
                throw new IllegalArgumentException("the model holds the grid system " + identifier
                        + " with the resolutions " + system.getMinResolution() + " to " + system.getMaxResolution()
                        + (system.isNested() ? ", nested" : ", not nested") + "; asked for " + minResolution + " to "
                        + maxResolution + (nested ? ", nested" : ", not nested"));
            }
            return system;
        }
        GridSystem system = F.createGridSystem();
        system.setMinResolution(minResolution);
        system.setMaxResolution(maxResolution);
        system.setNested(nested);
        return owned(owner, identifier, system);
    }

    /** Geohash ({@value #GEOHASH}, 1 to 12, nested) in the model of {@code owner}, as {@link #gridSystem}. */
    public static GridSystem geohash(Namespace owner) {
        return gridSystem(owner, GEOHASH, 1, 12, true);
    }

    /** H3 ({@value #H3}, 0 to 15, not nested) in the model of {@code owner}, as {@link #gridSystem}. */
    public static GridSystem h3(Namespace owner) {
        return gridSystem(owner, H3, 0, 15, false);
    }

    /** The quadkey tiles ({@value #QUADKEY}, 1 to 23, nested) in the model of {@code owner}, as {@link #gridSystem}. */
    public static GridSystem quadkey(Namespace owner) {
        return gridSystem(owner, QUADKEY, 1, 23, true);
    }

    /**
     * The grid system the model of {@code element} knows by {@code identifier}: the model is the resource of the
     * element, or the tree of its root where it lies in none.
     */
    public static Optional<GridSystem> gridSystem(EObject element, String identifier) {
        Objects.requireNonNull(identifier, "identifier");
        for (GridSystem system : gridSystems(element)) {
            if (identifier.equals(system.getName())) {
                return Optional.of(system);
            }
        }
        return Optional.empty();
    }

    /** The grid systems of the model of {@code element}, in the order of the model. */
    public static List<GridSystem> gridSystems(EObject element) {
        Objects.requireNonNull(element, "element");
        Iterator<?> all = element.eResource() != null ? element.eResource().getAllContents()
                : EcoreUtil.getRootContainer(element).eAllContents();
        List<GridSystem> systems = new ArrayList<>();
        if (element.eResource() == null && EcoreUtil.getRootContainer(element) instanceof GridSystem root) {
            systems.add(root);
        }
        while (all.hasNext()) {
            if (all.next() instanceof GridSystem system) {
                systems.add(system);
            }
        }
        return systems;
    }

    /**
     * A geometry feature owned by {@code owner}: the {@code columns} hold geometries of {@code kind} in the reference
     * system {@code system}, stored as {@code storage} with the coordinates east first; single or multi as either,
     * without a height, without an epoch.
     */
    public static GeometryFeature feature(Namespace owner, String name, GeometryKind kind, ReferenceSystem system,
            GeometryStorage storage, ModelElement... columns) {
        Objects.requireNonNull(columns, "columns");
        if (columns.length == 0) {
            throw new IllegalArgumentException("a geometry feature names at least one column");
        }
        GeometryFeature feature = F.createGeometryFeature();
        feature.setKind(Objects.requireNonNull(kind, "kind"));
        feature.setReferenceSystem(Objects.requireNonNull(system, "system"));
        feature.setStorage(Objects.requireNonNull(storage, "storage"));
        for (ModelElement column : columns) {
            feature.getModelElement().add(Objects.requireNonNull(column, "column"));
        }
        return owned(owner, name, feature);
    }

    /**
     * A spatial relation owned by {@code owner}: the rows of {@code child}, keyed by {@code childKey}, are assigned to
     * the rows of {@code parent}, keyed by {@code parentKey}, where {@code predicate} holds; several matches go as
     * {@code assignment} says, none as {@code unmatched} says. The result is a table until said otherwise.
     */
    public static SpatialRelation relation(Namespace owner, String name, GeometryFeature child, Attribute childKey,
            GeometryFeature parent, Attribute parentKey, SpatialPredicate predicate, SpatialAssignment assignment,
            UnmatchedPolicy unmatched) {
        SpatialRelation relation = F.createSpatialRelation();
        relation.setChild(Objects.requireNonNull(child, "child"));
        relation.setChildKey(Objects.requireNonNull(childKey, "childKey"));
        relation.setParent(Objects.requireNonNull(parent, "parent"));
        relation.setParentKey(Objects.requireNonNull(parentKey, "parentKey"));
        relation.setPredicate(Objects.requireNonNull(predicate, "predicate"));
        relation.setAssignment(Objects.requireNonNull(assignment, "assignment"));
        relation.setUnmatched(Objects.requireNonNull(unmatched, "unmatched"));
        return owned(owner, name, relation);
    }

    /**
     * Measures the matches of {@code relation} by {@code weight}; a part of the child below {@code minimumShare} (a
     * decimal number, {@code null} for none) is no match.
     */
    public static SpatialRelation weight(SpatialRelation relation, SpatialWeight weight, String minimumShare) {
        relation.setWeight(Objects.requireNonNull(weight, "weight"));
        relation.setMinimumShare(minimumShare);
        return relation;
    }

    /** A parent matches the child of {@code relation} within {@code distance}, a decimal number of metres. */
    public static SpatialRelation withinDistance(SpatialRelation relation, String distance) {
        relation.setPredicate(SpatialPredicate.WITHIN_DISTANCE);
        relation.setDistance(Objects.requireNonNull(distance, "distance"));
        return relation;
    }

    /** The key in {@code column} of the child table wins over the geometry where it is not null. */
    public static SpatialRelation explicitKey(SpatialRelation relation, Attribute column) {
        relation.setExplicitKey(Objects.requireNonNull(column, "column"));
        return relation;
    }

    /** {@code relation} is evaluated in the reference system {@code system}. */
    public static SpatialRelation workingSystem(SpatialRelation relation, ReferenceSystem system) {
        relation.setWorkingSystem(Objects.requireNonNull(system, "system"));
        return relation;
    }

    /** The table or view {@code result} holds what a resolver wrote for {@code relation}. */
    public static SpatialRelation result(SpatialRelation relation,
            org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class result) {
        relation.setResult(Objects.requireNonNull(result, "result"));
        return relation;
    }

    /**
     * A grid level owned by {@code owner}: the points of {@code source}, keyed by {@code sourceKey}, in the cells of
     * {@code system} at {@code resolution}, below {@code parent} ({@code null} for none). The result is a table
     * until said otherwise. The grid systems of the model are made by {@link #geohash}, {@link #h3},
     * {@link #quadkey} or {@link #gridSystem(Namespace, String, long, long, boolean)}.
     */
    public static GridLevel grid(Namespace owner, String name, GeometryFeature source, Attribute sourceKey,
            GridSystem system, long resolution, GridLevel parent) {
        GridLevel grid = F.createGridLevel();
        grid.setSource(Objects.requireNonNull(source, "source"));
        grid.setSourceKey(Objects.requireNonNull(sourceKey, "sourceKey"));
        grid.setSystem(Objects.requireNonNull(system, "system"));
        grid.setResolution(resolution);
        grid.setParent(parent);
        return owned(owner, name, grid);
    }

    /**
     * A distance band owned by {@code owner}: the distance of {@code source}, keyed by {@code sourceKey}, to the
     * nearest row of {@code reference} in classes up to each of {@code bounds}, decimal numbers of metres in ascending
     * order, and one above the last. The result is a table until said otherwise.
     */
    public static DistanceBand band(Namespace owner, String name, GeometryFeature source, Attribute sourceKey,
            GeometryFeature reference, String... bounds) {
        Objects.requireNonNull(bounds, "bounds");
        if (bounds.length == 0) {
            throw new IllegalArgumentException("a distance band has at least one bound");
        }
        DistanceBand band = F.createDistanceBand();
        band.setSource(Objects.requireNonNull(source, "source"));
        band.setSourceKey(Objects.requireNonNull(sourceKey, "sourceKey"));
        band.setReference(Objects.requireNonNull(reference, "reference"));
        for (String bound : bounds) {
            band.getBound().add(Objects.requireNonNull(bound, "bound"));
        }
        return owned(owner, name, band);
    }

    /** The table or view {@code result} holds what a resolver wrote for {@code grid}. */
    public static GridLevel result(GridLevel grid, org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class result) {
        grid.setResult(Objects.requireNonNull(result, "result"));
        return grid;
    }

    /**
     * The levels of the hierarchy {@code grid} ends, from the top down to it: the chain of its parents, then itself;
     * its result holds a cell key for each. A parent that does not resolve, or one met a second time, ends the chain.
     */
    public static List<GridLevel> chain(GridLevel grid) {
        java.util.Deque<GridLevel> levels = new java.util.ArrayDeque<>();
        java.util.Set<GridLevel> seen = new java.util.HashSet<>();
        for (GridLevel level = grid; level != null && !level.eIsProxy() && seen.add(level); level = level.getParent()) {
            levels.addFirst(level);
        }
        return List.copyOf(levels);
    }

    /** The table or view {@code result} holds what a resolver wrote for {@code band}. */
    public static DistanceBand result(DistanceBand band,
            org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Class result) {
        band.setResult(Objects.requireNonNull(result, "result"));
        return band;
    }

    /**
     * The {@link GeometryType} of {@code owner}, named {@value #GEOMETRY}, made where it has none: the type of a
     * logical property that holds a geometry, as the SQL types of a catalog are its elements. What the property holds
     * (kind, multiplicity, height, reference system) is said by the geometry feature that names it, not by the type.
     */
    public static GeometryType geometryType(Namespace owner) {
        Objects.requireNonNull(owner, "owner");
        for (ModelElement e : owner.getOwnedElement()) {
            if (e instanceof GeometryType type) {
                return type;
            }
        }
        return owned(owner, GEOMETRY, F.createGeometryType());
    }

    /**
     * Whether {@code type} is a {@link GeometryType}, the logical data type of a geometry, not an SQL type of a
     * column: it says nothing of how a value is stored.
     */
    public static boolean isGeometryType(EObject type) {
        return type instanceof GeometryType;
    }

    /**
     * Whether the table that owns {@code column} declares a unique key (a primary key among them) of this column alone,
     * read from the CWM {@code keysindexes} without a dependency on it.
     */
    public static boolean isDeclaredKey(Attribute column) {
        if (column == null || !(column.getOwner() instanceof Namespace table)) {
            return false;
        }
        for (ModelElement owned : table.getOwnedElement()) {
            if (isA(owned.eClass(), "UniqueKey", KEYSINDEXES)
                    && owned.eGet(owned.eClass().getEStructuralFeature("feature")) instanceof List<?> features
                    && features.size() == 1 && features.get(0) == column) {
                return true;
            }
        }
        return false;
    }

    private static final String KEYSINDEXES = "http://www.omg.org/spec/CWM/1.1/foundation/keysindexes";

    private static boolean isA(org.eclipse.emf.ecore.EClass c, String name, String nsUri) {
        List<org.eclipse.emf.ecore.EClass> types = new java.util.ArrayList<>(List.of(c));
        types.addAll(c.getEAllSuperTypes());
        for (org.eclipse.emf.ecore.EClass t : types) {
            if (name.equals(t.getName()) && t.getEPackage() != null && nsUri.equals(t.getEPackage().getNsURI())) {
                return true;
            }
        }
        return false;
    }

    /** The geometry features that name {@code column}; more than one is an error of the model. */
    public static List<GeometryFeature> features(ModelElement column) {
        return InverseReferences.referencingList(column, SpatialPackage.Literals.GEOMETRY_FEATURE__MODEL_ELEMENT,
                GeometryFeature.class);
    }

    /** The relations whose child is {@code feature}: the parents its rows are assigned to. */
    public static List<SpatialRelation> asChild(GeometryFeature feature) {
        return InverseReferences.referencingList(feature, SpatialPackage.Literals.SPATIAL_RELATION__CHILD,
                SpatialRelation.class);
    }

    /** The relations whose parent is {@code feature}: the children assigned to its rows. */
    public static List<SpatialRelation> asParent(GeometryFeature feature) {
        return InverseReferences.referencingList(feature, SpatialPackage.Literals.SPATIAL_RELATION__PARENT,
                SpatialRelation.class);
    }

    private static <T extends ModelElement> T owned(Namespace owner, String name, T element) {
        element.setName(Objects.requireNonNull(name, "name"));
        Objects.requireNonNull(owner, "owner").getOwnedElement().add(element);
        return element;
    }
}
