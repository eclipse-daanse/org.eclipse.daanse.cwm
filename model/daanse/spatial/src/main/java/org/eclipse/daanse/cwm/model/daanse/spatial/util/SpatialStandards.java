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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.daanse.cwm.emf.standard.StandardSets;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.daanse.spatial.AxisOrder;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystem;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystemAuthority;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystemKind;
import org.eclipse.daanse.cwm.model.daanse.spatial.SpatialPackage;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.xmi.XMIResource;

/**
 * The reference systems, grid systems and the geometry type everybody uses, as a standard set other model files
 * import: a package {@value #NAME} that owns them, so that a geometry feature, a relation's working system or a grid
 * level refers to them instead of a model owning its own copy.
 *
 * <p>The reference systems are WGS 84 as OGC CRS84 and CRS84h (longitude first) and as EPSG 4326 and 4979 (latitude
 * first), Web Mercator (EPSG 3857), ETRS89 (EPSG 4258), ETRS89 LAEA Europe (EPSG 3035), ETRS89 / UTM zones 32N and
 * 33N (EPSG 25832, 25833) and the four zones of DHDN / 3-degree Gauss-Kruger over Germany (EPSG 31466 to 31469), each
 * with the kind, the axes and the axis order its register defines, all static. The grid systems are geohash, h3 and
 * quadkey with their resolutions; the geometry type is {@code Geometry}.</p>
 *
 * <p>The set is the file {@value #SET_FILE} beside this class under the URI {@value #SET_URI}:
 * {@link #standardSet()} builds it, {@link #load(ResourceSet)} reads it from the jar, and a model file refers to an
 * element as {@code href="<SET_URI>#<id>"}, the id the UUID stated beside the element here.</p>
 */
public final class SpatialStandards {

    /** The name of the package of the set. */
    public static final String NAME = "Spatial";

    /** The URI of the standard set, under which a model file refers to its elements. */
    public static final String SET_URI =
            "https://www.daanse.org/spec/org.eclipse.daanse.cwm.model.daanse.spatial/spatial-standards.xmi";

    /** The file of the standard set, beside this class in the jar. */
    public static final String SET_FILE = "spatial-standards.xmi";

    /** The EPSG systems of the set: the id, code, kind, axes, the order of the first two axes in the register. */
    private record Epsg(String id, long code, ReferenceSystemKind kind, int axes, AxisOrder order) {
    }

    private static final List<Epsg> EPSG = List.of(
            new Epsg("_Ldwv4eUIEfGl4IEUgfF4xA", 4326, ReferenceSystemKind.GEOGRAPHIC, 2, AxisOrder.NORTH_EAST),
            new Epsg("_Ldwv4uUIEfGl4IEUgfF4xA", 4979, ReferenceSystemKind.GEOGRAPHIC, 3, AxisOrder.NORTH_EAST),
            new Epsg("_Ldwv4-UIEfGl4IEUgfF4xA", 3857, ReferenceSystemKind.PROJECTED, 2, AxisOrder.EAST_NORTH),
            new Epsg("_Ldwv5OUIEfGl4IEUgfF4xA", 4258, ReferenceSystemKind.GEOGRAPHIC, 2, AxisOrder.NORTH_EAST),
            new Epsg("_Ldwv5eUIEfGl4IEUgfF4xA", 3035, ReferenceSystemKind.PROJECTED, 2, AxisOrder.NORTH_EAST),
            new Epsg("_Ldwv5uUIEfGl4IEUgfF4xA", 25832, ReferenceSystemKind.PROJECTED, 2, AxisOrder.EAST_NORTH),
            new Epsg("_Ldwv5-UIEfGl4IEUgfF4xA", 25833, ReferenceSystemKind.PROJECTED, 2, AxisOrder.EAST_NORTH),
            new Epsg("_Ldwv6OUIEfGl4IEUgfF4xA", 31466, ReferenceSystemKind.PROJECTED, 2, AxisOrder.NORTH_EAST),
            new Epsg("_Ldwv6eUIEfGl4IEUgfF4xA", 31467, ReferenceSystemKind.PROJECTED, 2, AxisOrder.NORTH_EAST),
            new Epsg("_Ldwv6uUIEfGl4IEUgfF4xA", 31468, ReferenceSystemKind.PROJECTED, 2, AxisOrder.NORTH_EAST),
            new Epsg("_Ldwv6-UIEfGl4IEUgfF4xA", 31469, ReferenceSystemKind.PROJECTED, 2, AxisOrder.NORTH_EAST));

    private SpatialStandards() {
    }

    /** The standard set, built in memory with its ids, the package {@value #NAME} its root. */
    public static XMIResource standardSet() {
        XMIResource set = StandardSets.create(SET_URI);
        Package spatial = CoreFactory.eINSTANCE.createPackage();
        spatial.setName(NAME);
        set.getContents().add(spatial);
        StandardSets.id(spatial, "_Ldwv7OUIEfGl4IEUgfF4xA");
        StandardSets.id(SpatialElements.crs84(spatial), "_Ldwv7eUIEfGl4IEUgfF4xA");
        StandardSets.id(SpatialElements.crs84h(spatial), "_Ldwv7uUIEfGl4IEUgfF4xA");
        for (Epsg e : EPSG) {
            ReferenceSystem system = SpatialElements.epsg(spatial, e.code(), e.kind(), e.order());
            system.setAxes(e.axes());
            StandardSets.id(system, e.id());
        }
        StandardSets.id(SpatialElements.geohash(spatial), "_Ldwv7-UIEfGl4IEUgfF4xA");
        StandardSets.id(SpatialElements.h3(spatial), "_Ldwv8OUIEfGl4IEUgfF4xA");
        StandardSets.id(SpatialElements.quadkey(spatial), "_Ldwv8eUIEfGl4IEUgfF4xA");
        StandardSets.id(SpatialElements.geometryType(spatial), "_Ldwv8uUIEfGl4IEUgfF4xA");
        return set;
    }

    /**
     * Maps the standard set in {@code resourceSet} to its file in the jar: a model file there that refers to
     * its elements reads them from the jar ({@link StandardSets#map}).
     */
    public static void map(ResourceSet resourceSet) {
        StandardSets.map(resourceSet, SET_URI, SpatialStandards.class.getResource(SET_FILE), CorePackage.eINSTANCE,
                SpatialPackage.eINSTANCE);
    }

    /**
     * The package of the standard set in {@code resourceSet}, mapped there and read from the jar where it is not read
     * yet.
     */
    public static Package load(ResourceSet resourceSet) {
        map(resourceSet);
        return StandardSets.load(resourceSet, SET_URI, Package.class);
    }

    /** The reference system of {@code standard} with the code {@code code} of {@code authority}. */
    public static Optional<ReferenceSystem> referenceSystem(Package standard, ReferenceSystemAuthority authority,
            String code) {
        Objects.requireNonNull(standard, "standard");
        return standard.getOwnedElement().stream().filter(ReferenceSystem.class::isInstance)
                .map(ReferenceSystem.class::cast)
                .filter(s -> s.getAuthority() == authority && Objects.equals(code, s.getCode())).findFirst();
    }

    /** The EPSG system of {@code standard} with the code {@code code}. */
    public static Optional<ReferenceSystem> epsg(Package standard, long code) {
        return referenceSystem(standard, ReferenceSystemAuthority.EPSG, Long.toString(code));
    }
}
