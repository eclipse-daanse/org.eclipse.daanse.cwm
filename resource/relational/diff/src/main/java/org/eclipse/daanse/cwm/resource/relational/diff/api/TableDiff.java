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

import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;

/**
 * The structural diff of a single table matched between the old and new
 * schema — every added/dropped/changed feature it owns. Unique constraints
 * exclude the primary key, which is tracked separately via {@link #pkChange}.
 *
 * @param oldTable                the table as it existed in the old schema
 * @param newTable                the same table's declaration in the new
 *                                 schema
 * @param columnsAdded             columns present only in the new table
 * @param columnsDropped           columns present only in the old table
 * @param columnsChanged           matched columns whose declaration changed
 * @param columnsRenamed           matched columns that were renamed
 * @param pkChange                 the primary key change, or {@code null}
 *                                  when the primary key is unchanged
 * @param uniqueConstraintsAdded   unique constraints (excluding the primary
 *                                 key) present only in the new table
 * @param uniqueConstraintsDropped unique constraints present only in the old
 *                                 table
 * @param checksAdded              check constraints present only in the new
 *                                 table
 * @param checksDropped            check constraints present only in the old
 *                                 table
 * @param foreignKeysAdded         foreign keys present only in the new table
 * @param foreignKeysDropped       foreign keys present only in the old table
 * @param indexesAdded             indexes present only in the new table
 * @param indexesDropped           indexes present only in the old table
 * @param indexesRenamed           matched indexes of the same shape whose
 *                                 name changed
 * @param constraintsRenamed       matched primary key, unique, check or
 *                                 foreign key constraints of the same shape
 *                                 whose name changed
 * @param triggersAdded            triggers present only in the new table
 * @param triggersDropped          triggers present only in the old table;
 *                                 a trigger whose name, timing, event,
 *                                 orientation, condition or body changed is
 *                                 reported as dropped and added, since
 *                                 triggers cannot be renamed portably
 */
public record TableDiff(Table oldTable, Table newTable,
        List<Column> columnsAdded, List<Column> columnsDropped,
        List<ColumnChange> columnsChanged, List<ColumnRename> columnsRenamed,
        PrimaryKeyChange pkChange,
        List<UniqueConstraint> uniqueConstraintsAdded, List<UniqueConstraint> uniqueConstraintsDropped,
        List<CheckConstraint> checksAdded, List<CheckConstraint> checksDropped,
        List<ForeignKey> foreignKeysAdded, List<ForeignKey> foreignKeysDropped,
        List<SQLIndex> indexesAdded, List<SQLIndex> indexesDropped,
        List<IndexRename> indexesRenamed, List<ConstraintRename> constraintsRenamed,
        List<Trigger> triggersAdded, List<Trigger> triggersDropped) {

    public TableDiff {
        columnsAdded = List.copyOf(columnsAdded);
        columnsDropped = List.copyOf(columnsDropped);
        columnsChanged = List.copyOf(columnsChanged);
        columnsRenamed = List.copyOf(columnsRenamed);
        uniqueConstraintsAdded = List.copyOf(uniqueConstraintsAdded);
        uniqueConstraintsDropped = List.copyOf(uniqueConstraintsDropped);
        checksAdded = List.copyOf(checksAdded);
        checksDropped = List.copyOf(checksDropped);
        foreignKeysAdded = List.copyOf(foreignKeysAdded);
        foreignKeysDropped = List.copyOf(foreignKeysDropped);
        indexesAdded = List.copyOf(indexesAdded);
        indexesDropped = List.copyOf(indexesDropped);
        indexesRenamed = List.copyOf(indexesRenamed);
        constraintsRenamed = List.copyOf(constraintsRenamed);
        triggersAdded = List.copyOf(triggersAdded);
        triggersDropped = List.copyOf(triggersDropped);
    }

    /**
     * One readable line per difference of this table, as {@link SchemaDiff#lines()} writes them; a
     * changed column gives a line per aspect.
     */
    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        columnsDropped.forEach(c -> lines.add(line(c, ONLY_LEFT)));
        columnsAdded.forEach(c -> lines.add(line(c, ONLY_RIGHT)));
        columnsRenamed.forEach(r -> lines.add(line(r.oldColumn(),
                differs("name", r.oldColumn().getName(), r.newColumn().getName()))));
        for (ColumnChange change : columnsChanged) {
            Column left = change.oldColumn();
            Column right = change.newColumn();
            for (ColumnChange.Aspect aspect : change.aspects()) {
                lines.add(line(left, switch (aspect) {
                case TYPE -> differs("type", Lines.type(left.getType()), Lines.type(right.getType()));
                case SIZE -> differs("size", Lines.size(left), Lines.size(right));
                case NULLABILITY -> differs("nullability", Lines.nullability(left), Lines.nullability(right));
                case DEFAULT -> differs("default", defaultOf(left), defaultOf(right));
                }));
            }
        }
        if (pkChange != null) {
            if (pkChange.newPk() == null) {
                lines.add(line(pkChange.oldPk(), ONLY_LEFT));
            } else if (pkChange.oldPk() == null) {
                lines.add(line(pkChange.newPk(), ONLY_RIGHT));
            } else {
                lines.add(line(pkChange.oldPk(), differs("columns", Lines.columns(pkChange.oldPk().getFeature()),
                        Lines.columns(pkChange.newPk().getFeature()))));
            }
        }
        List.of(uniqueConstraintsDropped, checksDropped, foreignKeysDropped, indexesDropped, triggersDropped)
                .forEach(dropped -> dropped.forEach(e -> lines.add(line(e, ONLY_LEFT))));
        List.of(uniqueConstraintsAdded, checksAdded, foreignKeysAdded, indexesAdded, triggersAdded)
                .forEach(added -> added.forEach(e -> lines.add(line(e, ONLY_RIGHT))));
        indexesRenamed.forEach(r -> lines.add(line(r.oldIndex(),
                differs("name", Lines.simpleName(r.oldIndex()), Lines.simpleName(r.newIndex())))));
        constraintsRenamed.forEach(r -> lines.add(line(r.oldConstraint(),
                differs("name", Lines.simpleName(r.oldConstraint()), Lines.simpleName(r.newConstraint())))));
        return lines;
    }

    private static String defaultOf(Column column) {
        return column.getInitialValue() == null ? null : column.getInitialValue().getBody();
    }

    /** {@code true} iff nothing about this table changed. */
    public boolean isEmpty() {
        return columnsAdded.isEmpty() && columnsDropped.isEmpty() && columnsChanged.isEmpty()
                && columnsRenamed.isEmpty() && pkChange == null
                && uniqueConstraintsAdded.isEmpty() && uniqueConstraintsDropped.isEmpty()
                && checksAdded.isEmpty() && checksDropped.isEmpty()
                && foreignKeysAdded.isEmpty() && foreignKeysDropped.isEmpty()
                && indexesAdded.isEmpty() && indexesDropped.isEmpty()
                && indexesRenamed.isEmpty() && constraintsRenamed.isEmpty()
                && triggersAdded.isEmpty() && triggersDropped.isEmpty();
    }
}
