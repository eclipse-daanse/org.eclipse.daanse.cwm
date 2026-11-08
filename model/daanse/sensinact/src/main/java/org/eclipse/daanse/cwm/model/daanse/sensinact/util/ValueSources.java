/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.sensinact.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Expressions;
import org.eclipse.daanse.cwm.model.daanse.sensinact.ArrivalTimeSource;
import org.eclipse.daanse.cwm.model.daanse.sensinact.LiteralSource;
import org.eclipse.daanse.cwm.model.daanse.sensinact.PathSource;
import org.eclipse.daanse.cwm.model.daanse.sensinact.SensinactFactory;
import org.eclipse.daanse.cwm.model.daanse.sensinact.ValueSource;

/**
 * Creates value sources, one kind each: a path of features ({@link PathSource}), a fixed value
 * ({@link LiteralSource}), the time a source instance arrives ({@link ArrivalTimeSource}); and the
 * modifiers of a path that pick an element from a collection, give a fallback or a timestamp
 * format. A value source is contained by the mapping that uses it; the creating helpers of the
 * mappings take it as argument.
 */
public final class ValueSources {

    private ValueSources() {
    }

    /** The value at the end of a feature path. */
    public static PathSource path(StructuralFeature... steps) {
        Objects.requireNonNull(steps, "steps");
        if (steps.length == 0) {
            throw new IllegalArgumentException("a path has at least one step");
        }
        PathSource source = SensinactFactory.eINSTANCE.createPathSource();
        for (StructuralFeature step : steps) {
            source.getPath().add(Objects.requireNonNull(step, "step"));
        }
        return source;
    }

    /** A fixed value. */
    public static LiteralSource literal(String literal) {
        LiteralSource source = SensinactFactory.eINSTANCE.createLiteralSource();
        source.setLiteral(Objects.requireNonNull(literal, "literal"));
        return source;
    }

    /** The time a source instance arrives, for a timestamp. */
    public static ArrivalTimeSource arrivalTime() {
        return SensinactFactory.eINSTANCE.createArrivalTimeSource();
    }

    /** Picks the element at {@code index} (from 0) at the step that holds many values. */
    public static PathSource at(PathSource source, long index) {
        source.setIndex(index);
        return source;
    }

    /** Picks the element whose content matches {@code filter} ({@code key=line_5}) at the step that holds many values. */
    public static PathSource where(PathSource source, String filter) {
        source.setFilter(Expressions.bool(SensinactBindings.FILTER_LANGUAGE, Objects.requireNonNull(filter, "filter")));
        return source;
    }

    /** Gives the literal {@code literal} where the path yields no value. */
    public static PathSource orElse(PathSource source, String literal) {
        return orElse(source, literal(literal));
    }

    /** Gives the value of {@code fallback} where the path yields no value. */
    public static PathSource orElse(PathSource source, ValueSource fallback) {
        source.setFallback(Objects.requireNonNull(fallback, "fallback"));
        return source;
    }

    /** Parses a timestamp read as text with the DateTimeFormatter {@code pattern}. */
    public static PathSource format(PathSource source, String pattern) {
        source.setFormat(Objects.requireNonNull(pattern, "pattern"));
        return source;
    }

    /** The steps of a path; empty for a literal or the arrival time. */
    public static List<StructuralFeature> steps(ValueSource source) {
        return source instanceof PathSource path ? List.copyOf(path.getPath()) : List.of();
    }

    /** {@code source} and its fallbacks, in the order they are tried. */
    public static List<ValueSource> chain(ValueSource source) {
        List<ValueSource> chain = new ArrayList<>();
        for (ValueSource s = source; s != null; s = s instanceof PathSource path ? path.getFallback() : null) {
            chain.add(s);
        }
        return chain;
    }
}
