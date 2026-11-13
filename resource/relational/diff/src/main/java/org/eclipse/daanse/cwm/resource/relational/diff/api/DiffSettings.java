/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.resource.relational.diff.api;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions;

/**
 * Switches for how the differ matches elements between old and new schema, and what it lets pass as
 * the same. The three identity sources are independent and individually switchable; when several fire
 * for the same element the precedence is Dependency &gt; TaggedValue marker &gt; heuristic.
 *
 * @param useTagMarkers      read {@code daanse.change.renamedFrom} TaggedValues
 *                           on the new schema ({@link ChangeMarkers})
 * @param useDependencyLinks read {@code predecessor} Dependencies between new
 *                           and old elements ({@link PredecessorLinks})
 * @param useRenameHeuristic keep the conservative 1-dropped/1-added shape
 *                           heuristic as a fallback
 * @param scope              {@link Scope#FULL} compares everything;
 *                           {@link Scope#PARTIAL} treats the old schema as a
 *                           stub and only compares elements present in it
 * @param commentType        the {@code Description} type that holds the
 *                           database comment of tables and columns (default
 *                           {@link Descriptions#JDBC_REMARKS});
 *                           {@code null} leaves comments out of the diff
 * @param tolerances         the ways in which the models may differ without
 *                           it counting ({@link Tolerance}); empty by default,
 *                           so everything the differ looks at counts
 */
public record DiffSettings(boolean useTagMarkers, boolean useDependencyLinks,
        boolean useRenameHeuristic, Scope scope, String commentType, Set<Tolerance> tolerances) {

    public enum Scope {
        /** Both schemas are complete; anything missing counts as added/dropped. */
        FULL,
        /**
         * The old schema is a stub holding only the elements of interest;
         * added/dropped outside the stub set is suppressed.
         */
        PARTIAL
    }

    public DiffSettings {
        tolerances = tolerances == null || tolerances.isEmpty() ? Set.of()
                : Set.copyOf(EnumSet.copyOf(tolerances));
    }

    /** All identity sources, the full scope, the comments as {@link Descriptions#JDBC_REMARKS}, no tolerance. */
    public static DiffSettings defaults() {
        return new DiffSettings(true, true, true, Scope.FULL, Descriptions.JDBC_REMARKS, Set.of());
    }

    /** Whether {@code tolerance} is one of {@link #tolerances()}. */
    public boolean tolerates(Tolerance tolerance) {
        return tolerances.contains(tolerance);
    }

    public DiffSettings withTagMarkers(boolean on) {
        return new DiffSettings(on, useDependencyLinks, useRenameHeuristic, scope, commentType, tolerances);
    }

    public DiffSettings withDependencyLinks(boolean on) {
        return new DiffSettings(useTagMarkers, on, useRenameHeuristic, scope, commentType, tolerances);
    }

    public DiffSettings withRenameHeuristic(boolean on) {
        return new DiffSettings(useTagMarkers, useDependencyLinks, on, scope, commentType, tolerances);
    }

    public DiffSettings withScope(Scope s) {
        return new DiffSettings(useTagMarkers, useDependencyLinks, useRenameHeuristic, s, commentType, tolerances);
    }

    public DiffSettings withCommentType(String type) {
        return new DiffSettings(useTagMarkers, useDependencyLinks, useRenameHeuristic, scope, type, tolerances);
    }

    /** These settings with exactly {@code set} as tolerances. */
    public DiffSettings withTolerances(Collection<Tolerance> set) {
        return new DiffSettings(useTagMarkers, useDependencyLinks, useRenameHeuristic, scope, commentType,
                set == null || set.isEmpty() ? Set.of() : EnumSet.copyOf(set));
    }

    /** These settings with {@code more} added to the tolerances. */
    public DiffSettings withTolerances(Tolerance... more) {
        Set<Tolerance> set = tolerances.isEmpty() ? EnumSet.noneOf(Tolerance.class) : EnumSet.copyOf(tolerances);
        set.addAll(java.util.List.of(more));
        return withTolerances(set);
    }
}
