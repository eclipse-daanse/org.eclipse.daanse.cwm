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
package org.eclipse.daanse.cwm.resource.relational.diff.api;

import java.util.EnumSet;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;

/**
 * A view of the old schema and its counterpart in the new one that differ in what {@code aspects}
 * name.
 *
 * @param oldView the view as it existed in the old schema
 * @param newView the same view's declaration in the new schema
 * @param aspects what differs, at least one
 */
public record ViewChange(View oldView, View newView, Set<Aspect> aspects) {

    public ViewChange {
        aspects = aspects == null || aspects.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(aspects));
    }

    /** What the differ compares of a view. */
    public enum Aspect {
        /** The text of the query, subject to {@link Tolerance#IGNORE_EXPRESSION_TEXT}. */
        BODY,
        /**
         * Whether the view is materialized: a materialized view keeps the rows of its query, a plain
         * one runs the query each time. A view changes from the one to the other only by being
         * dropped and created again.
         */
        MATERIALIZED
    }
}
