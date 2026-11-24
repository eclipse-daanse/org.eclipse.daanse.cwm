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

import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Multiplicity;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.MultiplicityRange;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.FeatureCollection;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.FeatureFactory;
import org.eclipse.daanse.cwm.model.daanse.resource.spatial.feature.FeatureProperty;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryFeature;
import org.eclipse.daanse.cwm.model.daanse.spatial.GeometryStorage;
import org.eclipse.daanse.cwm.model.daanse.spatial.WholeHolding;
import org.eclipse.daanse.cwm.model.daanse.spatial.util.SpatialElements;

/** Creates the properties of a feature collection and reads their multiplicity. */
public final class FeatureProperties {

    private FeatureProperties() {
    }

    /** A queryable property named {@code name} of {@code type}, a feature of {@code collection}; may be null. */
    public static FeatureProperty create(FeatureCollection collection, String name, Classifier type) {
        Objects.requireNonNull(collection, "collection");
        FeatureProperty property = FeatureFactory.eINSTANCE.createFeatureProperty();
        property.setName(Objects.requireNonNull(name, "name"));
        property.setType(Objects.requireNonNull(type, "type"));
        collection.getFeature().add(property);
        return property;
    }

    /** A property that is never null: multiplicity 1..1. */
    public static FeatureProperty required(FeatureCollection collection, String name, Classifier type) {
        FeatureProperty property = create(collection, name, type);
        Multiplicity multiplicity = CoreFactory.eINSTANCE.createMultiplicity();
        MultiplicityRange range = CoreFactory.eINSTANCE.createMultiplicityRange();
        range.setLower(1);
        range.setUpper(1);
        multiplicity.getRange().add(range);
        property.setMultiplicity(multiplicity);
        return property;
    }

    /**
     * A geometry property named {@code name}: of the data type {@code Geometry} of {@code types} (put there where it
     * is missing), and a whole holding of {@code feature}, which says what the geometry is.
     */
    public static FeatureProperty geometry(FeatureCollection collection, String name, Namespace types,
            GeometryFeature feature) {
        Objects.requireNonNull(feature, "feature");
        FeatureProperty property = create(collection, name, SpatialElements.geometryType(types));
        SpatialElements.whole(feature, property, GeometryStorage.NATIVE);
        return property;
    }

    /** A property used only to filter: queryable, never in a feature. */
    public static FeatureProperty filterOnly(FeatureProperty property) {
        property.setReturnable(false);
        return property;
    }

    /** Whether the property is never null: every range of its multiplicity has a lower bound from 1. */
    public static boolean required(FeatureProperty property) {
        Multiplicity multiplicity = property.getMultiplicity();
        if (multiplicity == null || multiplicity.getRange().isEmpty()) {
            return false;
        }
        for (MultiplicityRange range : multiplicity.getRange()) {
            if (range.getLower() < 1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the property holds a whole geometry: of the data type {@code Geometry}, or the attribute of a whole
     * holding. A property that holds one number of a geometry, such as {@code east}, is a number.
     */
    public static boolean isGeometry(FeatureProperty property) {
        return SpatialElements.isGeometryType(property.getType())
                || SpatialElements.holdings(property).stream().anyMatch(WholeHolding.class::isInstance);
    }

    /** The title: the display name, else the name. */
    public static String title(FeatureProperty property) {
        return property.getDisplayName() != null ? property.getDisplayName() : property.getName();
    }

    /** The family of the property's type. */
    public static TypeFamily family(FeatureProperty property) {
        return TypeFamily.of(property.getType());
    }
}
