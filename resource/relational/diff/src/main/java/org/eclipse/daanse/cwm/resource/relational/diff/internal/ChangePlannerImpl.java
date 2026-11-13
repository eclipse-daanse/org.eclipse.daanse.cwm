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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.QualifiedNames;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ColumnSets;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ForeignKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalRules;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLIndexes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.CreateOrder;
import org.eclipse.daanse.cwm.resource.relational.diff.api.ChangeMarkers;
import org.eclipse.daanse.cwm.resource.relational.diff.api.ChangeOp;
import org.eclipse.daanse.cwm.resource.relational.diff.api.ChangePlanner;
import org.eclipse.daanse.cwm.resource.relational.diff.api.ColumnChange;
import org.eclipse.daanse.cwm.resource.relational.diff.api.CommentChange;
import org.eclipse.daanse.cwm.resource.relational.diff.api.SchemaDiff;
import org.eclipse.daanse.cwm.resource.relational.diff.api.TableDiff;
import org.eclipse.daanse.cwm.resource.relational.diff.api.TableMerge;
import org.eclipse.daanse.cwm.resource.relational.diff.api.TableSplit;
import org.eclipse.daanse.cwm.resource.relational.diff.api.ViewChange;

/**
 * Orders a {@link SchemaDiff} into an executable {@link ChangeOp} list. The
 * phase topology: drops before adds, renames (tables, columns, indexes,
 * constraints) before structural alters, FKs around PK rebuilds, tables
 * before FKs, then views and triggers, comments last. A PK change on a table
 * with inbound foreign keys, of any schema of the model, drops and re-adds
 * the referencing FKs around the rebuild. Tables that go are dropped those
 * referring to others first, foreign keys on a cycle between them before.
 * Views that read a table losing, retyping or resizing a column, or a table
 * that goes, are dropped before and created again after, with the views that
 * read them; views are created each after the views it reads. A table the
 * plan creates gets its unique constraints, checks, indexes, foreign keys and
 * triggers as operations of their own.
 */
public final class ChangePlannerImpl implements ChangePlanner {

    private static final System.Logger LOG = System.getLogger(ChangePlannerImpl.class.getName());

    @Override
    public List<ChangeOp> plan(SchemaDiff diff) {
        warnings(diff).forEach(w -> LOG.log(System.Logger.Level.WARNING, w));
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
            for (ForeignKey fk : inboundForeignKeys(td.pkChange().oldPk())) {
                if (droppedFks.add(fk)) {
                    p.dropFk.add(new ChangeOp.DropForeignKey(tableOf(fk), fk));
                }
            }
        }

        // the tables to drop: those that refer to others first, foreign keys on a cycle dropped before
        List<Table> tablesToDrop = droppingOrder(diff.tablesDropped(), droppedFks, p);

        // views: those that go, those whose body changes, and those that read a table losing,
        // retyping or resizing columns or going itself, with the views that read them
        List<View> touched = viewsReadingChangedTables(diff);
        List<ViewChange> replaced = new ArrayList<>();
        List<View> oldViewsToDrop = new ArrayList<>(diff.viewsDropped());
        List<View> newViewsToCreate = new ArrayList<>(diff.viewsAdded());
        for (ViewChange vc : diff.viewsChanged()) {
            if (!touched.contains(vc.oldView()) && replaceable(vc)) {
                replaced.add(vc);
                newViewsToCreate.add(vc.newView());
            } else {
                oldViewsToDrop.add(vc.oldView());
                newViewsToCreate.add(vc.newView());
            }
        }
        Set<View> changedOld = Collections.newSetFromMap(new IdentityHashMap<>());
        diff.viewsChanged().forEach(vc -> changedOld.add(vc.oldView()));
        for (View view : touched) {
            if (changedOld.contains(view) || diff.viewsDropped().contains(view)) {
                continue;
            }
            oldViewsToDrop.add(view);
            counterpart(view, diff.newSchema()).ifPresent(newViewsToCreate::add);
        }
        for (View view : inCreateOrder(oldViewsToDrop).reversed()) {
            p.dropView.add(new ChangeOp.DropView(view));
        }

        // drop triggers, indexes / unique constraints / checks
        for (TableDiff td : diff.tablesChanged()) {
            td.triggersDropped().forEach(t -> p.dropMinor.add(new ChangeOp.DropTrigger(td.oldTable(), t)));
        }
        for (Table t : tablesToDrop) {
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
        tablesToDrop.forEach(t -> p.dropTable.add(new ChangeOp.DropTable(t)));

        // splits and merges: structural only, no row data moved (see warnings)
        diff.tablesSplit().forEach(s -> p.splitMerge.add(new ChangeOp.SplitTable(s.oldTable(), s.newTables())));
        diff.tablesMerged().forEach(m -> p.splitMerge.add(new ChangeOp.MergeTables(m.oldTables(), m.newTable())));

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

        // add columns, create tables: those referred to first
        for (TableDiff td : diff.tablesChanged()) {
            td.columnsAdded().forEach(c -> p.addColumn.add(new ChangeOp.AddColumn(td.newTable(), c)));
        }
        creationOrder(diff.tablesAdded()).forEach(t -> p.createTable.add(new ChangeOp.CreateTable(t)));

        // every table the plan creates gets what its CREATE TABLE does not hold: unique
        // constraints, checks, indexes, foreign keys and triggers; where a dialect declares a
        // constraint in CREATE TABLE, the emitter writes it there instead
        List<Table> created = createdTables(diff);

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
        for (Table t : created) {
            Tables.uniqueConstraints(t).stream().filter(uc -> !(uc instanceof PrimaryKey))
                    .forEach(uc -> p.addMinor.add(new ChangeOp.AddUniqueConstraint(t, uc)));
            Tables.checkConstraints(t).forEach(ck -> p.addMinor.add(new ChangeOp.AddCheckConstraint(t, ck)));
            SQLIndexes.spanning(t).forEach(i -> p.addMinor.add(new ChangeOp.CreateIndex(i)));
        }

        // FKs after all tables exist — explicit adds, FKs of created tables,
        // and inbound FKs re-attached after a PK rebuild
        for (TableDiff td : diff.tablesChanged()) {
            for (ForeignKey fk : td.foreignKeysAdded()) {
                p.addFk.add(new ChangeOp.AddForeignKey(td.newTable(), fk));
            }
        }
        for (Table t : created) {
            for (ForeignKey fk : Tables.foreignKeys(t)) {
                if (addedFks.add(fk)) {
                    p.addFk.add(new ChangeOp.AddForeignKey(t, fk));
                }
            }
        }
        for (TableDiff td : diff.tablesChanged()) {
            if (td.pkChange() == null || td.pkChange().newPk() == null) {
                continue;
            }
            for (ForeignKey fk : inboundForeignKeys(td.pkChange().newPk())) {
                if (addedFks.add(fk)) {
                    p.addFk.add(new ChangeOp.AddForeignKey(tableOf(fk), fk));
                }
            }
        }

        // views, each after the views it reads, then triggers (their bodies may use anything created above)
        for (View view : inCreateOrder(newViewsToCreate)) {
            ViewChange replace = replaced.stream().filter(vc -> vc.newView() == view).findFirst().orElse(null);
            p.createView.add(replace != null ? new ChangeOp.ReplaceView(replace.oldView(), replace.newView())
                    : new ChangeOp.CreateView(view));
        }
        for (TableDiff td : diff.tablesChanged()) {
            td.triggersAdded().forEach(t -> p.createView.add(new ChangeOp.CreateTrigger(td.newTable(), t)));
        }
        for (Table t : created) {
            t.getTrigger().forEach(trg -> p.createView.add(new ChangeOp.CreateTrigger(t, trg)));
        }

        // comments once every table and column exists under its final name; a view created again
        // gets its comments with it
        Set<View> viewsCreated = Collections.newSetFromMap(new IdentityHashMap<>());
        p.createView.stream().filter(ChangeOp.CreateView.class::isInstance)
                .forEach(op -> viewsCreated.add(((ChangeOp.CreateView) op).view()));
        for (CommentChange c : diff.commentsChanged()) {
            if (c.table() instanceof View view) {
                if (!viewsCreated.contains(view)) {
                    p.comment.add(new ChangeOp.SetViewComment(view, c.element(), c.newComment()));
                }
            } else if (c.table() instanceof Table table) {
                p.comment.add(new ChangeOp.SetComment(table, c.element(), c.newComment()));
            }
        }

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

    /**
     * The views of the old schema a change of a table reaches: a view blocks dropping, retyping or
     * resizing the columns it reads and dropping the table, so those that read a table losing,
     * retyping or resizing a column, or a table that goes, are dropped before and created again
     * after, changed or not; and so are the views that read such a view. What a view reads is found
     * as the DDL generator orders views: by {@code core::Dependency} where the model has them, else by
     * the names in its query.
     */
    private static List<View> viewsReadingChangedTables(SchemaDiff diff) {
        Set<NamedColumnSet> reached = Collections.newSetFromMap(new IdentityHashMap<>());
        reached.addAll(diff.tablesDropped());
        diff.tablesSplit().forEach(s -> reached.add(s.oldTable()));
        diff.tablesMerged().forEach(m -> reached.addAll(m.oldTables()));
        for (TableDiff td : diff.tablesChanged()) {
            boolean narrowed = !td.columnsDropped().isEmpty() || td.columnsChanged().stream()
                    .anyMatch(cc -> cc.aspects().contains(ColumnChange.Aspect.TYPE)
                            || cc.aspects().contains(ColumnChange.Aspect.SIZE));
            if (narrowed) {
                reached.add(td.oldTable());
            }
        }
        Set<View> touched = Collections.newSetFromMap(new IdentityHashMap<>());
        List<View> views = Schemas.views(diff.oldSchema());
        if (reached.isEmpty()) {
            return List.of();
        }
        boolean grew = true;
        while (grew) {
            grew = false;
            for (View view : views) {
                if (!touched.contains(view) && !CreateOrder.reads(view, List.copyOf(reached)).isEmpty()) {
                    touched.add(view);
                    reached.add(view);
                    grew = true;
                }
            }
        }
        // in the order of the model, so the same diff gives the same plan
        return views.stream().filter(touched::contains).toList();
    }

    /** The view of {@code schema} with the name of {@code view}, exactly or else in another case. */
    private static Optional<View> counterpart(View view, Schema schema) {
        Optional<View> same = Schemas.findView(schema, view.getName());
        if (same.isPresent()) {
            return same;
        }
        return Schemas.views(schema).stream().filter(v -> v.getName() != null && v.getName().equalsIgnoreCase(
                view.getName())).findFirst();
    }

    /** {@code views}, each after those of them it reads; in the order given where they read each other in a cycle. */
    private static List<View> inCreateOrder(List<View> views) {
        List<View> distinct = new ArrayList<>(new LinkedHashSet<>(views));
        try {
            return CreateOrder.views(distinct);
        } catch (IllegalStateException cycle) {
            return distinct;
        }
    }

    /** {@code tables}, each after those of them it refers to; in the order given where they refer to each other in a cycle. */
    private static List<Table> creationOrder(List<Table> tables) {
        Map<Table, Set<Table>> needs = new LinkedHashMap<>();
        for (Table table : tables) {
            Set<Table> referenced = new LinkedHashSet<>();
            for (ForeignKey fk : Tables.foreignKeys(table)) {
                ForeignKeys.targetTable(fk).filter(t -> t != table && tables.contains(t)).ifPresent(referenced::add);
            }
            needs.put(table, referenced);
        }
        try {
            return CreateOrder.ordered(needs, Cycle::new);
        } catch (Cycle cycle) {
            return tables;
        }
    }

    /**
     * {@code tables} in the order they can be dropped in: a table that refers to another of them
     * before it. Where they refer to each other in a cycle, the foreign keys between the tables of the
     * cycle are dropped first, added to {@code p}.
     */
    private static List<Table> droppingOrder(List<Table> tables, Set<ForeignKey> droppedFks, Phases p) {
        Map<Table, Set<Table>> needs = new LinkedHashMap<>();
        tables.forEach(t -> needs.put(t, new LinkedHashSet<>()));
        Map<ForeignKey, Table[]> edges = new LinkedHashMap<>();
        for (Table table : tables) {
            for (ForeignKey fk : Tables.foreignKeys(table)) {
                ForeignKeys.targetTable(fk).filter(t -> t != table && needs.containsKey(t)).ifPresent(target -> {
                    needs.get(target).add(table);
                    edges.put(fk, new Table[] { table, target });
                });
            }
        }
        while (true) {
            try {
                return CreateOrder.ordered(needs, Cycle::new);
            } catch (Cycle cycle) {
                Set<Object> onCycle = Collections.newSetFromMap(new IdentityHashMap<>());
                onCycle.addAll(cycle.members);
                int before = edges.size();
                for (Map.Entry<ForeignKey, Table[]> edge : List.copyOf(edges.entrySet())) {
                    Table from = edge.getValue()[0];
                    Table to = edge.getValue()[1];
                    if (onCycle.contains(from) && onCycle.contains(to)) {
                        if (droppedFks.add(edge.getKey())) {
                            p.dropFk.add(new ChangeOp.DropForeignKey(from, edge.getKey()));
                        }
                        edges.remove(edge.getKey());
                        boolean stillReferenced = edges.values().stream()
                                .anyMatch(e -> e[0] == from && e[1] == to);
                        if (!stillReferenced) {
                            needs.get(to).remove(from);
                        }
                    }
                }
                if (edges.size() == before) {
                    return tables;
                }
            }
        }
    }

    /** The tables the plan creates: added ones and those of splits and merges. */
    private static List<Table> createdTables(SchemaDiff diff) {
        List<Table> created = new ArrayList<>(diff.tablesAdded());
        diff.tablesSplit().forEach(s -> created.addAll(s.newTables()));
        diff.tablesMerged().forEach(m -> created.add(m.newTable()));
        return created;
    }

    /** Elements that need each other in a cycle. */
    private static final class Cycle extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final transient List<?> members;

        Cycle(List<?> members) {
            super(null, null, false, false);
            this.members = members;
        }
    }

    /**
     * {@code CREATE OR REPLACE VIEW} keeps grants and dependent views, but
     * PostgreSQL only accepts it when the old columns stay, same name, same
     * type, same position; new columns may only be appended. A view without
     * modelled columns cannot be checked and is not replaced. A materialized
     * view has no {@code OR REPLACE}, and a view becomes materialized or ceases
     * to be only by being dropped and created again.
     */
    private static boolean replaceable(ViewChange vc) {
        if (!Objects.equals(vc.oldView().getName(), vc.newView().getName())
                || RelationalRules.MATERIALIZED.test(vc.oldView()) || RelationalRules.MATERIALIZED.test(vc.newView())) {
            return false;
        }
        List<Column> oldCols = ColumnSets.columns(vc.oldView());
        List<Column> newCols = ColumnSets.columns(vc.newView());
        if (oldCols.isEmpty() || newCols.size() < oldCols.size()) {
            return false;
        }
        for (int i = 0; i < oldCols.size(); i++) {
            Column o = oldCols.get(i);
            Column n = newCols.get(i);
            if (!Objects.equals(o.getName(), n.getName()) || !Objects.equals(typeName(o), typeName(n))) {
                return false;
            }
        }
        return true;
    }

    private static String typeName(Column c) {
        return c.getType() == null ? null : c.getType().getName();
    }

    /**
     * The foreign keys that refer to {@code pk}, in whatever schema of its model they lie: those
     * {@code UniqueKey.keyRelationship} names.
     */
    private static List<ForeignKey> inboundForeignKeys(PrimaryKey pk) {
        return pk.getKeyRelationship().stream().filter(ForeignKey.class::isInstance).map(ForeignKey.class::cast)
                .filter(fk -> fk.getNamespace() instanceof Table).toList();
    }

    @Override
    public List<String> warnings(SchemaDiff diff) {
        List<String> out = new ArrayList<>();
        for (TableDiff td : diff.tablesChanged()) {
            for (Column c : td.columnsAdded()) {
                if (notNull(c) && isBlank(defaultBody(c))) {
                    out.add(qualified(td.newTable(), c)
                            + ": NOT NULL column added without a default — fails if the table has rows");
                }
            }
            for (ColumnChange cc : td.columnsChanged()) {
                if (cc.aspects().contains(ColumnChange.Aspect.NULLABILITY) && notNull(cc.newColumn())) {
                    out.add(qualified(td.newTable(), cc.newColumn())
                            + ": column made NOT NULL — fails while it holds NULLs");
                }
            }
        }
        for (TableSplit s : diff.tablesSplit()) {
            out.add(s.oldTable().getName() + " split into "
                    + s.newTables().stream().map(Table::getName).collect(Collectors.joining(", "))
                    + " — row data is not migrated automatically");
        }
        for (TableMerge m : diff.tablesMerged()) {
            out.add(m.oldTables().stream().map(Table::getName).collect(Collectors.joining(", "))
                    + " merged into " + m.newTable().getName()
                    + " — row data is not migrated automatically");
        }
        diff.proceduresAdded().forEach(r -> out.add(QualifiedNames.of(r) + " added — routines are not migrated"));
        diff.proceduresDropped().forEach(r -> out.add(QualifiedNames.of(r) + " dropped — routines are not migrated"));
        diff.proceduresChanged().forEach(r -> out.add(QualifiedNames.of(r.oldProcedure())
                + " changed — routines are not migrated"));
        return out;
    }

    private static boolean notNull(Column c) {
        return c.getIsNullable() == NullableType.COLUMN_NO_NULLS;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String qualified(Table t, Column c) {
        String schema = t.getNamespace() == null ? null : t.getNamespace().getName();
        return (schema == null ? "" : schema + ".") + t.getName() + "." + c.getName();
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
        final List<ChangeOp> splitMerge = new ArrayList<>();
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
            out.addAll(splitMerge);
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
