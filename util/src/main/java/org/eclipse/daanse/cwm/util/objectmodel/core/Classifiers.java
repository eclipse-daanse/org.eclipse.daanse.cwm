/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.util.objectmodel.core;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Feature;

/**
 * Generic helpers over feature collections. The list overload covers feature
 * lists exposed by non-{@link Classifier} types (e.g. {@code KeyRelationship},
 * {@code UniqueKey}) that don't share a common Classifier ancestor.
 */
public final class Classifiers {

    private Classifiers() {
    }

    /** Stream of {@code c.getFeature()} restricted to instances of {@code type}. */
    public static <T extends Feature> Stream<T> featureStream(Classifier c, Class<T> type) {
        return featureStream(c == null ? null : c.getFeature(), type);
    }

    /**
     * Stream of {@code features} restricted to instances of {@code type}.
     * Null-safe.
     */
    public static <T extends Feature> Stream<T> featureStream(List<? extends Feature> features, Class<T> type) {
        if (features == null) {
            return Stream.empty();
        }
        return features.stream().filter(type::isInstance).map(type::cast);
    }

    /** First feature of the given type with the given name; first match wins. */
    public static <T extends Feature> Optional<T> findFeatureByName(Classifier c, Class<T> type, String name) {
        if (c == null || type == null || name == null) {
            return Optional.empty();
        }
        for (Feature f : c.getFeature()) {
            if (type.isInstance(f) && name.equals(f.getName())) {
                return Optional.of(type.cast(f));
            }
        }
        return Optional.empty();
    }
}
