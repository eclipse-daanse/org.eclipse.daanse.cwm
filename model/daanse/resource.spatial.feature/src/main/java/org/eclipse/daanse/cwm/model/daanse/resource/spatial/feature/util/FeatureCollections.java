/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Description;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.FeatureCollection;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.FeatureFactory;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.FeatureProperty;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryFeature;
import org.eclipse.daanse.cwm.model.daanse.spatial.ReferenceSystem;
import org.eclipse.daanse.cwm.model.daanse.spatial.util.ReferenceSystems;
import org.eclipse.daanse.cwm.model.daanse.spatial.util.SpatialElements;

/**
 * Creates feature collections and reads what a server answers from them: the properties in order, the geometry
 * feature of a property, the reference systems of the list {@code crs} and the storage system (OGC API Features Part
 * 2).
 */
public final class FeatureCollections {

    /** The default reference system of OGC API Features: WGS 84, longitude and latitude in degrees. */
    public static final String CRS84 = "http://www.opengis.net/def/crs/OGC/1.3/CRS84";
    /** The default reference system for coordinates with a height: WGS 84 with the ellipsoidal height. */
    public static final String CRS84H = "http://www.opengis.net/def/crs/OGC/0/CRS84h";

    private FeatureCollections() {
    }

    /** A collection named {@code name} (the collection id), owned by {@code owner}. */
    public static FeatureCollection create(Package owner, String name) {
        FeatureCollection collection = FeatureFactory.eINSTANCE.createFeatureCollection();
        collection.setName(Objects.requireNonNull(name, "name"));
        Objects.requireNonNull(owner, "owner").getOwnedElement().add(collection);
        return collection;
    }

    /** The property whose value is the feature id. */
    public static FeatureCollection key(FeatureCollection collection, FeatureProperty key) {
        collection.setFeatureKey(Objects.requireNonNull(key, "key"));
        return collection;
    }

    /** The primary geometry. */
    public static FeatureCollection geometry(FeatureCollection collection, FeatureProperty geometry) {
        collection.setGeometry(Objects.requireNonNull(geometry, "geometry"));
        return collection;
    }

    /** The time of a feature is an instant, the value of {@code instant}; no interval. */
    public static FeatureCollection instant(FeatureCollection collection, FeatureProperty instant) {
        collection.setInstant(Objects.requireNonNull(instant, "instant"));
        collection.setIntervalStart(null);
        collection.setIntervalEnd(null);
        return collection;
    }

    /**
     * The time of a feature is an interval from {@code start} to {@code end}; either may be {@code null}, not both: a
     * start alone is open to the future, an end alone open to the past. No instant.
     */
    public static FeatureCollection interval(FeatureCollection collection, FeatureProperty start,
            FeatureProperty end) {
        if (start == null && end == null) {
            throw new IllegalArgumentException("an interval has a start, an end or both");
        }
        collection.setInstant(null);
        collection.setIntervalStart(start);
        collection.setIntervalEnd(end);
        return collection;
    }

    /** The description of the collection or a property: a Description of type {@code documentation}. */
    public static Description describe(ModelElement element, String text) {
        return Descriptions.describe(element, "documentation", null, Objects.requireNonNull(text, "text"));
    }

    /** The description of an element: the body of its Description of type {@code documentation}. */
    public static Optional<String> description(ModelElement element) {
        return Descriptions.find(element, "documentation").map(Description::getBody).filter(b -> !b.isBlank());
    }

    /** The properties of {@code collection}, in order. */
    public static List<FeatureProperty> properties(FeatureCollection collection) {
        List<FeatureProperty> properties = new ArrayList<>();
        for (Object f : collection.getFeature()) {
            if (f instanceof FeatureProperty p) {
                properties.add(p);
            }
        }
        return properties;
    }

    /** The property named {@code name}. */
    public static Optional<FeatureProperty> property(FeatureCollection collection, String name) {
        return properties(collection).stream().filter(p -> name.equals(p.getName())).findFirst();
    }

    /** The title: the display name, else the name. */
    public static String title(FeatureCollection collection) {
        return collection.getDisplayName() != null ? collection.getDisplayName() : collection.getName();
    }

    /** The geometry feature that names {@code property}, where exactly one does. */
    public static Optional<GeometryFeature> geometryFeature(FeatureProperty property) {
        List<GeometryFeature> features = SpatialElements.features(property);
        return features.size() == 1 ? Optional.of(features.get(0)) : Optional.empty();
    }

    /** The reference system the primary geometry is stored in, where the collection has one. */
    public static Optional<ReferenceSystem> storageSystem(FeatureCollection collection) {
        return Optional.ofNullable(collection.getGeometry()).flatMap(FeatureCollections::geometryFeature)
                .map(GeometryFeature::getReferenceSystem);
    }

    /** The URI of the storage system ({@code storageCrs}), where the collection has a primary geometry. */
    public static Optional<String> storageCrs(FeatureCollection collection) {
        return storageSystem(collection).map(ReferenceSystems::uri);
    }

    /**
     * The epoch of the primary geometry's coordinates in a dynamic storage system ({@code storageCrsCoordinateEpoch}),
     * as its geometry feature names it; none in a static system.
     */
    public static Optional<String> storageCrsCoordinateEpoch(FeatureCollection collection) {
        return Optional.ofNullable(collection.getGeometry()).flatMap(FeatureCollections::geometryFeature)
                .filter(f -> f.getReferenceSystem() != null && f.getReferenceSystem().isDynamic())
                .map(GeometryFeature::getCoordinateEpoch);
    }

    /** Whether the coordinates of the primary geometry hold a height. */
    public static boolean hasHeight(FeatureCollection collection) {
        return Optional.ofNullable(collection.getGeometry()).flatMap(FeatureCollections::geometryFeature)
                .map(GeometryFeature::isHasHeight).orElse(false);
    }

    /**
     * The list {@code crs} of a collection with a geometry: the default (CRS84, or CRS84h for a geometry with a
     * height), the storage system, then the systems the collection offers, each once; empty without a geometry.
     */
    public static List<String> crs(FeatureCollection collection) {
        if (collection.getGeometry() == null) {
            return List.of();
        }
        Set<String> uris = new LinkedHashSet<>();
        uris.add(hasHeight(collection) ? CRS84H : CRS84);
        storageCrs(collection).ifPresent(uris::add);
        for (ReferenceSystem system : collection.getReferenceSystems()) {
            uris.add(ReferenceSystems.uri(system));
        }
        return List.copyOf(uris);
    }
}
