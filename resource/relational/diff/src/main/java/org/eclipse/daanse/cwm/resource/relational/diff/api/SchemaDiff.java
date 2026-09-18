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

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;

/**
 * The complete structural diff between two CWM relational schemas — what
 * {@link SchemaDiffer} produces and {@link ChangePlanner} turns into an
 * ordered {@link ChangeOp} list.
 *
 * @param oldSchema     the schema being migrated from
 * @param newSchema     the schema being migrated to
 * @param tablesAdded   tables present only in the new schema
 * @param tablesDropped tables present only in the old schema
 * @param viewsAdded    views present only in the new schema
 * @param viewsDropped  views present only in the old schema
 * @param tablesChanged matched tables with at least one structural change
 * @param viewsChanged  matched views whose query body changed
 * @param tablesRenamed matched tables whose name changed
 * @param commentsChanged table and column comments to set or remove, on
 *                      matched tables and on added tables and columns
 */
public record SchemaDiff(Schema oldSchema, Schema newSchema,
        List<Table> tablesAdded, List<Table> tablesDropped,
        List<View> viewsAdded, List<View> viewsDropped,
        List<TableDiff> tablesChanged, List<ViewBodyChange> viewsChanged,
        List<TableRename> tablesRenamed, List<CommentChange> commentsChanged) {

    public SchemaDiff {
        tablesAdded = List.copyOf(tablesAdded);
        tablesDropped = List.copyOf(tablesDropped);
        viewsAdded = List.copyOf(viewsAdded);
        viewsDropped = List.copyOf(viewsDropped);
        tablesChanged = List.copyOf(tablesChanged);
        viewsChanged = List.copyOf(viewsChanged);
        tablesRenamed = List.copyOf(tablesRenamed);
        commentsChanged = List.copyOf(commentsChanged);
    }

    /** {@code true} iff the two schemas are structurally identical. */
    public boolean isEmpty() {
        return tablesAdded.isEmpty() && tablesDropped.isEmpty()
                && viewsAdded.isEmpty() && viewsDropped.isEmpty()
                && tablesChanged.isEmpty() && viewsChanged.isEmpty()
                && tablesRenamed.isEmpty() && commentsChanged.isEmpty();
    }
}
