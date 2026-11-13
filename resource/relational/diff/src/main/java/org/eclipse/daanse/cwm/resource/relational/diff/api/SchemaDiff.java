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

import static org.eclipse.daanse.cwm.resource.relational.diff.api.Lines.ONLY_LEFT;
import static org.eclipse.daanse.cwm.resource.relational.diff.api.Lines.ONLY_RIGHT;
import static org.eclipse.daanse.cwm.resource.relational.diff.api.Lines.differs;
import static org.eclipse.daanse.cwm.resource.relational.diff.api.Lines.line;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalRules;

/**
 * The complete structural diff between two CWM relational schemas — what
 * {@link SchemaDiffer} produces and {@link ChangePlanner} turns into an
 * ordered {@link ChangeOp} list. Left is the old schema, right the new one.
 *
 * @param oldSchema          the schema being migrated from
 * @param newSchema          the schema being migrated to
 * @param tablesAdded        tables present only in the new schema
 * @param tablesDropped      tables present only in the old schema
 * @param viewsAdded         views present only in the new schema
 * @param viewsDropped       views present only in the old schema
 * @param tablesChanged      matched tables with at least one structural change
 * @param viewsChanged       matched views whose query changed, or which became
 *                           materialized or ceased to be
 * @param tablesRenamed      matched tables whose name changed
 * @param tablesSplit        old tables decomposed into two or more new tables
 *                           (predecessor Dependency links only)
 * @param tablesMerged       two or more old tables consolidated into one new
 *                           table (predecessor Dependency links only)
 * @param commentsChanged    table and column comments to set or remove, on
 *                           matched tables and on added tables and columns
 * @param proceduresAdded    routines present only in the new schema; a routine
 *                           is told apart by its name and the kinds and types
 *                           of its parameters
 * @param proceduresDropped  routines present only in the old schema
 * @param proceduresChanged  routines of the same signature that differ in
 *                           kind, parameter names or body
 */
public record SchemaDiff(Schema oldSchema, Schema newSchema,
        List<Table> tablesAdded, List<Table> tablesDropped,
        List<View> viewsAdded, List<View> viewsDropped,
        List<TableDiff> tablesChanged, List<ViewChange> viewsChanged,
        List<TableRename> tablesRenamed, List<TableSplit> tablesSplit,
        List<TableMerge> tablesMerged, List<CommentChange> commentsChanged,
        List<Procedure> proceduresAdded, List<Procedure> proceduresDropped,
        List<ProcedureChange> proceduresChanged) {

    public SchemaDiff {
        tablesAdded = List.copyOf(tablesAdded);
        tablesDropped = List.copyOf(tablesDropped);
        viewsAdded = List.copyOf(viewsAdded);
        viewsDropped = List.copyOf(viewsDropped);
        tablesChanged = List.copyOf(tablesChanged);
        viewsChanged = List.copyOf(viewsChanged);
        tablesRenamed = List.copyOf(tablesRenamed);
        tablesSplit = List.copyOf(tablesSplit);
        tablesMerged = List.copyOf(tablesMerged);
        commentsChanged = List.copyOf(commentsChanged);
        proceduresAdded = List.copyOf(proceduresAdded);
        proceduresDropped = List.copyOf(proceduresDropped);
        proceduresChanged = List.copyOf(proceduresChanged);
    }

    /** {@code true} iff the two schemas are structurally identical. */
    public boolean isEmpty() {
        return tablesAdded.isEmpty() && tablesDropped.isEmpty()
                && viewsAdded.isEmpty() && viewsDropped.isEmpty()
                && tablesChanged.isEmpty() && viewsChanged.isEmpty()
                && tablesRenamed.isEmpty() && tablesSplit.isEmpty()
                && tablesMerged.isEmpty() && commentsChanged.isEmpty()
                && proceduresAdded.isEmpty() && proceduresDropped.isEmpty() && proceduresChanged.isEmpty();
    }

    /**
     * One readable line per difference, sorted: {@code <kind> <qualified name>: <difference>}, where
     * the difference is {@code only left} (only in the old schema), {@code only right} (only in the
     * new one) or {@code <aspect> <old value> left, <new value> right}. An element without a name of
     * its own is named as {@code RelationalNames} names it. Empty iff {@link #isEmpty()}.
     */
    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        tablesDropped.forEach(t -> lines.add(line(t, ONLY_LEFT)));
        tablesAdded.forEach(t -> lines.add(line(t, ONLY_RIGHT)));
        tablesRenamed.forEach(r -> lines.add(line(r.oldTable(),
                differs("name", r.oldTable().getName(), r.newTable().getName()))));
        tablesSplit.forEach(s -> lines.add(line(s.oldTable(), "split into "
                + s.newTables().stream().map(Table::getName).collect(Collectors.joining(", ")) + " right")));
        tablesMerged.forEach(m -> lines.add("tables " + m.oldTables().stream().map(Lines::name)
                .collect(Collectors.joining(", ")) + ": merged into " + m.newTable().getName() + " right"));
        tablesChanged.forEach(td -> lines.addAll(td.lines()));
        viewsDropped.forEach(v -> lines.add(line(v, ONLY_LEFT)));
        viewsAdded.forEach(v -> lines.add(line(v, ONLY_RIGHT)));
        for (ViewChange change : viewsChanged) {
            for (ViewChange.Aspect aspect : change.aspects()) {
                lines.add(line(change.oldView(), switch (aspect) {
                case BODY -> "query differs";
                case MATERIALIZED -> differs("materialized", RelationalRules.MATERIALIZED.test(change.oldView()),
                        RelationalRules.MATERIALIZED.test(change.newView()));
                }));
            }
        }
        commentsChanged.forEach(c -> lines.add("comment of " + Lines.kind(c.element()) + " "
                + Lines.name(c.element()) + ": " + differs("text", Lines.quoted(c.oldComment()),
                        Lines.quoted(c.newComment()))));
        proceduresDropped.forEach(p -> lines.add(line(p, ONLY_LEFT)));
        proceduresAdded.forEach(p -> lines.add(line(p, ONLY_RIGHT)));
        for (ProcedureChange change : proceduresChanged) {
            Procedure left = change.oldProcedure();
            Procedure right = change.newProcedure();
            for (ProcedureChange.Aspect aspect : change.aspects()) {
                lines.add(line(left, switch (aspect) {
                case KIND -> differs("kind", left.getType(), right.getType());
                case PARAMETER_NAMES -> differs("parameter names", parameterNames(left), parameterNames(right));
                case RETURN_TYPE -> "type of the result differs";
                case BODY -> "body differs";
                }));
            }
        }
        return lines.stream().sorted().toList();
    }

    private static String parameterNames(Procedure procedure) {
        return procedure.getParameter().stream().map(p -> p.getName()).collect(Collectors.joining(", ", "(", ")"));
    }
}
