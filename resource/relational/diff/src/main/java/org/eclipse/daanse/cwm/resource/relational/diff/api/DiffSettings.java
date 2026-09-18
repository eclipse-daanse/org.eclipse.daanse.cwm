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

import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;

/**
 * Switches for how the differ matches elements between old and new schema.
 * The three identity sources are independent and individually switchable;
 * when several fire for the same element the precedence is
 * Dependency &gt; TaggedValue marker &gt; heuristic.
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
 *                           {@link DdlSettings#COMMENT_TYPE_JDBC_REMARKS});
 *                           {@code null} leaves comments out of the diff
 */
public record DiffSettings(boolean useTagMarkers, boolean useDependencyLinks,
        boolean useRenameHeuristic, Scope scope, String commentType) {

    public enum Scope {
        /** Both schemas are complete; anything missing counts as added/dropped. */
        FULL,
        /**
         * The old schema is a stub holding only the elements of interest;
         * added/dropped outside the stub set is suppressed.
         */
        PARTIAL
    }

    public static DiffSettings defaults() {
        return new DiffSettings(true, true, true, Scope.FULL, DdlSettings.COMMENT_TYPE_JDBC_REMARKS);
    }

    public DiffSettings withTagMarkers(boolean on) {
        return new DiffSettings(on, useDependencyLinks, useRenameHeuristic, scope, commentType);
    }

    public DiffSettings withDependencyLinks(boolean on) {
        return new DiffSettings(useTagMarkers, on, useRenameHeuristic, scope, commentType);
    }

    public DiffSettings withRenameHeuristic(boolean on) {
        return new DiffSettings(useTagMarkers, useDependencyLinks, on, scope, commentType);
    }

    public DiffSettings withScope(Scope s) {
        return new DiffSettings(useTagMarkers, useDependencyLinks, useRenameHeuristic, s, commentType);
    }

    public DiffSettings withCommentType(String type) {
        return new DiffSettings(useTagMarkers, useDependencyLinks, useRenameHeuristic, scope, type);
    }
}
