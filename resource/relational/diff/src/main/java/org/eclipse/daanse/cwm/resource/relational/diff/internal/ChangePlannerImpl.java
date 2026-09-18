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
package org.eclipse.daanse.cwm.resource.relational.diff.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ColumnSets;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.resource.relational.diff.api.ChangeMarkers;
import org.eclipse.daanse.cwm.resource.relational.diff.api.ChangeOp;
import org.eclipse.daanse.cwm.resource.relational.diff.api.ChangePlanner;
import org.eclipse.daanse.cwm.resource.relational.diff.api.ColumnChange;
import org.eclipse.daanse.cwm.resource.relational.diff.api.SchemaDiff;
import org.eclipse.daanse.cwm.resource.relational.diff.api.TableDiff;

/**
 * Orders a {@link SchemaDiff} into an executable {@link ChangeOp} list. The
 * phase topology: drops before adds, renames (tables, columns, indexes,
 * constraints) before structural alters, FKs around PK rebuilds, tables
 * before FKs, then views and triggers, comments last. A PK change on a table
 * with inbound foreign keys drops and re-adds the referencing FKs around the
 * rebuild.
 */
public final class ChangePlannerImpl implements ChangePlanner {

    @Override
    public List<ChangeOp> plan(SchemaDiff diff) {
        Phases p = new Phases();

        Set<ForeignKey> droppedFks = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<ForeignKey> addedFks = Collections.newSetFromMap(new IdentityHashMap<>());
        for (TableDiff td : diff.tablesChanged()) {
            td.foreignKeysDropped().forEach(droppedFks::add);
            td.foreignKeysAdded().forEach(addedFks::add);
        }

        // drop FKs — explicitly dropped ones, plus inbound FKs of PK rebuilds
        for (TableDiff td : diff.tablesChanged()) {
            for (ForeignKey fk : td.foreignKeysDropped()) {
                p.dropFk.add(new ChangeOp.DropForeignKey(td.oldTable(), fk));
            }
        }
        for (TableDiff td : diff.tablesChanged()) {
            if (td.pkChange() == null || td.pkChange().oldPk() == null) {
                continue;
            }
            for (ForeignKey fk : inboundForeignKeys(diff.oldSchema(), td.pkChange().oldPk())) {
                if (!droppedFks.contains(fk)) {
                    droppedFks.add(fk);
                    p.dropFk.add(new ChangeOp.DropForeignKey(tableOf(fk), fk));
                }
            }
        }

        // drop views (removed ones and old bodies of changed ones)
        diff.viewsDropped().forEach(v -> p.dropView.add(new ChangeOp.DropView(v)));
        diff.viewsChanged().forEach(vc -> p.dropView.add(new ChangeOp.DropView(vc.oldView())));

        // drop triggers, indexes / unique constraints / checks
        for (TableDiff td : diff.tablesChanged()) {
            td.triggersDropped().forEach(t -> p.dropMinor.add(new ChangeOp.DropTrigger(td.oldTable(), t)));
        }
        for (Table t : diff.tablesDropped()) {
            // DROP TABLE takes its triggers along, but not their procedures
            t.getTrigger().forEach(trg -> p.dropMinor.add(new ChangeOp.DropTrigger(t, trg)));
        }
        for (TableDiff td : diff.tablesChanged()) {
            td.indexesDropped().forEach(i -> p.dropMinor.add(new ChangeOp.DropIndex(i)));
            td.uniqueConstraintsDropped()
                    .forEach(uc -> p.dropMinor.add(new ChangeOp.DropUniqueConstraint(td.oldTable(), uc)));
            td.checksDropped()
                    .forEach(ck -> p.dropMinor.add(new ChangeOp.DropCheckConstraint(td.oldTable(), ck)));
        }

        // drop PKs being changed/removed
        for (TableDiff td : diff.tablesChanged()) {
            if (td.pkChange() != null && td.pkChange().oldPk() != null) {
                p.dropPk.add(new ChangeOp.DropPrimaryKey(td.oldTable(), td.pkChange().oldPk()));
            }
        }

        // drop columns, drop tables
        for (TableDiff td : diff.tablesChanged()) {
            td.columnsDropped().forEach(c -> p.dropColumn.add(new ChangeOp.DropColumn(td.oldTable(), c)));
        }
        diff.tablesDropped().forEach(t -> p.dropTable.add(new ChangeOp.DropTable(t)));

        // renames before structural alters
        diff.tablesRenamed().forEach(r -> p.rename.add(
                new ChangeOp.RenameTable(r.newTable(), r.oldTable().getName())));
        for (TableDiff td : diff.tablesChanged()) {
            td.columnsRenamed().forEach(r -> p.rename.add(
                    new ChangeOp.RenameColumn(td.newTable(), r.newColumn(), r.oldColumn().getName())));
            td.indexesRenamed().forEach(r -> p.rename.add(
                    new ChangeOp.RenameIndex(r.newIndex(), r.oldIndex().getName())));
            td.constraintsRenamed().forEach(r -> p.rename.add(
                    new ChangeOp.RenameConstraint(td.newTable(), r.newConstraint(), r.oldConstraint().getName())));
        }

        // column alterations, split per aspect
        for (TableDiff td : diff.tablesChanged()) {
            for (ColumnChange cc : td.columnsChanged()) {
                if (cc.aspects().contains(ColumnChange.Aspect.TYPE)
                        || cc.aspects().contains(ColumnChange.Aspect.SIZE)) {
                    p.alter.add(new ChangeOp.AlterColumnType(td.newTable(), cc.oldColumn(), cc.newColumn()));
                }
                if (cc.aspects().contains(ColumnChange.Aspect.NULLABILITY)) {
                    p.alter.add(new ChangeOp.AlterColumnNullability(td.newTable(), cc.newColumn(),
                            cc.newColumn().getIsNullable() != NullableType.COLUMN_NO_NULLS));
                }
                if (cc.aspects().contains(ColumnChange.Aspect.DEFAULT)) {
                    p.alter.add(new ChangeOp.AlterColumnDefault(td.newTable(), cc.newColumn(),
                            defaultBody(cc.newColumn())));
                }
            }
        }

        // add columns, create tables
        for (TableDiff td : diff.tablesChanged()) {
            td.columnsAdded().forEach(c -> p.addColumn.add(new ChangeOp.AddColumn(td.newTable(), c)));
        }
        diff.tablesAdded().forEach(t -> p.createTable.add(new ChangeOp.CreateTable(t)));

        // re-add PK, add unique/check/index
        for (TableDiff td : diff.tablesChanged()) {
            if (td.pkChange() != null && td.pkChange().newPk() != null) {
                p.addMinor.add(new ChangeOp.AddPrimaryKey(td.newTable(), td.pkChange().newPk()));
            }
            td.uniqueConstraintsAdded()
                    .forEach(uc -> p.addMinor.add(new ChangeOp.AddUniqueConstraint(td.newTable(), uc)));
            td.checksAdded()
                    .forEach(ck -> p.addMinor.add(new ChangeOp.AddCheckConstraint(td.newTable(), ck)));
            td.indexesAdded().forEach(i -> p.addMinor.add(new ChangeOp.CreateIndex(i)));
        }

        // FKs after all tables exist — explicit adds, FKs of new tables,
        // and inbound FKs re-attached after a PK rebuild
        for (TableDiff td : diff.tablesChanged()) {
            for (ForeignKey fk : td.foreignKeysAdded()) {
                p.addFk.add(new ChangeOp.AddForeignKey(td.newTable(), fk));
            }
        }
        for (Table t : diff.tablesAdded()) {
            for (ForeignKey fk : Tables.foreignKeys(t)) {
                if (!addedFks.contains(fk)) {
                    p.addFk.add(new ChangeOp.AddForeignKey(t, fk));
                }
            }
        }
        for (TableDiff td : diff.tablesChanged()) {
            if (td.pkChange() == null || td.pkChange().newPk() == null) {
                continue;
            }
            for (ForeignKey fk : inboundForeignKeys(diff.newSchema(), td.pkChange().newPk())) {
                if (!addedFks.contains(fk)) {
                    addedFks.add(fk);
                    p.addFk.add(new ChangeOp.AddForeignKey(tableOf(fk), fk));
                }
            }
        }

        // views, then triggers (their bodies may use anything created above)
        diff.viewsChanged().forEach(vc -> p.createView.add(new ChangeOp.CreateView(vc.newView())));
        diff.viewsAdded().forEach(v -> p.createView.add(new ChangeOp.CreateView(v)));
        for (TableDiff td : diff.tablesChanged()) {
            td.triggersAdded().forEach(t -> p.createView.add(new ChangeOp.CreateTrigger(td.newTable(), t)));
        }
        for (Table t : diff.tablesAdded()) {
            t.getTrigger().forEach(trg -> p.createView.add(new ChangeOp.CreateTrigger(t, trg)));
        }

        // comments once every table and column exists under its final name
        diff.commentsChanged().forEach(c -> p.comment.add(
                new ChangeOp.SetComment(c.table(), c.element(), c.newComment())));

        return p.flatten();
    }

    @Override
    public List<ChangeOp> planMarkersOnly(Schema newSchema) {
        List<ChangeOp> ops = new ArrayList<>();
        for (Table t : Schemas.tables(newSchema)) {
            ChangeMarkers.renamedFrom(t)
                    .ifPresent(old -> ops.add(new ChangeOp.RenameTable(t, old)));
            for (Column c : ColumnSets.columns(t)) {
                ChangeMarkers.renamedFrom(c)
                        .ifPresent(old -> ops.add(new ChangeOp.RenameColumn(t, c, old)));
            }
        }
        for (View v : Schemas.views(newSchema)) {
            ChangeMarkers.renamedFrom(v).ifPresent(old -> {
                // a view rename is re-creation under the new name
                ops.add(new ChangeOp.CreateView(v));
            });
        }
        return ops;
    }

    // helpers

    /** All FKs in {@code schema} whose referenced unique key is {@code pk} (identity). */
    private static List<ForeignKey> inboundForeignKeys(Schema schema, PrimaryKey pk) {
        List<ForeignKey> out = new ArrayList<>();
        for (Table t : Schemas.tables(schema)) {
            for (ForeignKey fk : Tables.foreignKeys(t)) {
                if (fk.getUniqueKey() == pk) {
                    out.add(fk);
                }
            }
        }
        return out;
    }

    private static Table tableOf(ForeignKey fk) {
        return (Table) fk.getNamespace();
    }

    private static String defaultBody(Column c) {
        return c.getInitialValue() == null ? null : c.getInitialValue().getBody();
    }

    private static final class Phases {
        final List<ChangeOp> dropFk = new ArrayList<>();
        final List<ChangeOp> dropView = new ArrayList<>();
        final List<ChangeOp> dropMinor = new ArrayList<>();
        final List<ChangeOp> dropPk = new ArrayList<>();
        final List<ChangeOp> dropColumn = new ArrayList<>();
        final List<ChangeOp> dropTable = new ArrayList<>();
        final List<ChangeOp> rename = new ArrayList<>();
        final List<ChangeOp> alter = new ArrayList<>();
        final List<ChangeOp> addColumn = new ArrayList<>();
        final List<ChangeOp> createTable = new ArrayList<>();
        final List<ChangeOp> addMinor = new ArrayList<>();
        final List<ChangeOp> addFk = new ArrayList<>();
        final List<ChangeOp> createView = new ArrayList<>();
        final List<ChangeOp> comment = new ArrayList<>();

        List<ChangeOp> flatten() {
            List<ChangeOp> out = new ArrayList<>();
            out.addAll(dropFk);
            out.addAll(dropView);
            out.addAll(dropMinor);
            out.addAll(dropPk);
            out.addAll(dropColumn);
            out.addAll(dropTable);
            out.addAll(rename);
            out.addAll(alter);
            out.addAll(addColumn);
            out.addAll(createTable);
            out.addAll(addMinor);
            out.addAll(addFk);
            out.addAll(createView);
            out.addAll(comment);
            return out;
        }
    }
}
