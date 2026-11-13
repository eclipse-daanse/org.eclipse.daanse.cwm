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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalNames;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Views;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.CwmSchemaMapper;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlGenerator;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlGeneratorFactory;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlStatement;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.Unwritable;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.Unwritables;
import org.eclipse.daanse.cwm.resource.relational.diff.api.ChangeOp;
import org.eclipse.daanse.cwm.resource.relational.diff.api.MigrationEmitter;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.model.schema.ColumnDefinition;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Translates an ordered {@link ChangeOp} plan into dialect SQL, one operation
 * at a time. No ordering logic lives here — the plan arrives ordered.
 *
 * <p>Whatever the DDL generator writes for an element — a table with what the
 * dialect declares in it, a unique constraint, check, foreign key, index,
 * view, trigger, comment, and their drops — is taken from the generator
 * created with the same settings ({@link DdlGenerator#createElement},
 * {@link DdlGenerator#dropElement}), so a migration and a create script
 * agree on names, settings and the decisions of the dialect. What only a
 * migration does — alter, rename, add a column or primary key to an existing
 * table, replace a view — is written here, with the settings' {@code IF
 * EXISTS}.</p>
 *
 * <p>What the dialect cannot write is one rule for the whole plan
 * ({@link Unwritables}): the cases of the generator and those of a migration —
 * a constraint the dialect declares in {@code CREATE TABLE} only, to add to,
 * drop from or rename on a table the plan does not create; a rename the
 * dialect has no statement for; a comment it writes on no such element — are
 * left out, and the log names each, or, strict, the plan fails once with all of
 * them.</p>
 *
 * <p>Dialect coverage of the individual alterations (PG {@code ALTER COLUMN c
 * TYPE …}, Oracle {@code MODIFY (c …)}, MySQL {@code MODIFY COLUMN}, SQL Server
 * {@code ALTER COLUMN} and named {@code DF_} default constraints) comes from
 * the jdbc.db {@code DdlGenerator} defaults and per-dialect overrides.</p>
 */
@Component(service = MigrationEmitter.class)
public final class MigrationEmitterImpl implements MigrationEmitter {

    private final DdlGeneratorFactory ddlFactory;

    /** Constructor injection — also usable directly from plain code/tests. */
    @Activate
    public MigrationEmitterImpl(@Reference DdlGeneratorFactory ddlFactory) {
        this.ddlFactory = ddlFactory;
    }

    @Override
    public List<String> emit(List<ChangeOp> ops, Dialect dialect, DdlSettings settings) {
        if (ops == null) throw new IllegalArgumentException("ops must not be null");
        if (dialect == null) throw new IllegalArgumentException("dialect must not be null");
        if (settings == null) throw new IllegalArgumentException("settings must not be null");

        Unwritables found = new Unwritables(settings.strict(), true);
        Plan plan = new Plan(dialect, settings, ddlFactory.create(dialect, settings), ops, found);
        List<String> out = new ArrayList<>();
        for (ChangeOp op : ops) {
            out.addAll(found.collect(() -> {
                List<String> statements = new ArrayList<>();
                plan.translate(statements, op);
                return statements;
            }));
        }
        found.finish();
        return out;
    }

    /** One plan for one dialect: the generator, and the tables the plan creates and drops. */
    private static final class Plan {

        private final Dialect dialect;
        private final DdlSettings settings;
        private final DdlGenerator ddl;
        private final Set<Table> created = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<Table> dropped = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Unwritables found;

        Plan(Dialect dialect, DdlSettings settings, DdlGenerator ddl, List<ChangeOp> ops, Unwritables found) {
            this.dialect = dialect;
            this.settings = settings;
            this.ddl = ddl;
            this.found = found;
            for (ChangeOp op : ops) {
                switch (op) {
                case ChangeOp.CreateTable o -> created.add(o.table());
                case ChangeOp.SplitTable o -> {
                    created.addAll(o.newTables());
                    dropped.add(o.oldTable());
                }
                case ChangeOp.MergeTables o -> {
                    created.add(o.newTable());
                    dropped.addAll(o.oldTables());
                }
                case ChangeOp.DropTable o -> dropped.add(o.table());
                default -> {
                    // neither creates nor drops a table
                }
                }
            }
        }

        void translate(List<String> out, ChangeOp op) {
            switch (op) {
                //  drops
                case ChangeOp.DropForeignKey o -> dropConstraint(out, o.table(), o.foreignKey());
                case ChangeOp.DropView o -> out.addAll(sql(ddl.dropElement(o.view())));
                case ChangeOp.DropIndex o -> out.addAll(sql(ddl.dropElement(o.index())));
                case ChangeOp.DropUniqueConstraint o -> dropConstraint(out, o.table(), o.constraint());
                case ChangeOp.DropCheckConstraint o -> dropConstraint(out, o.table(), o.constraint());
                case ChangeOp.DropPrimaryKey o -> out.add(dialect.ddlGenerator().dropConstraint(
                        ddl.tableReference(o.table()), RelationalNames.of(o.primaryKey()), settings.ifNotExists()));
                case ChangeOp.DropColumn o -> out.add(dialect.ddlGenerator().alterTableDropColumn(
                        ddl.tableReference(o.table()), o.column().getName()));
                case ChangeOp.DropTable o -> out.addAll(sql(ddl.dropElement(o.table())));

                //  renames
                case ChangeOp.RenameTable o -> {
                    TableReference oldRef = new TableReference(ddl.tableReference(o.table()).schema(),
                            o.oldName(), TableReference.TYPE_TABLE);
                    addOrLeaveOut(out, dialect.ddlGenerator().renameTable(oldRef, o.table().getName()), o.table(),
                            "RENAME TABLE");
                }
                case ChangeOp.RenameColumn o -> addOrLeaveOut(out, dialect.ddlGenerator().renameColumn(
                        ddl.tableReference(o.table()), o.oldName(), o.column().getName()), o.column(),
                        "RENAME COLUMN");
                case ChangeOp.RenameIndex o -> {
                    Table t = (Table) o.index().getSpannedClass();
                    String renamed = dialect.ddlGenerator().renameIndex(o.oldName(), o.index().getName(),
                            ddl.tableReference(t));
                    if (renamed != null) {
                        out.add(renamed);
                    } else {
                        // no rename in this dialect: re-create under the new name
                        out.add(dialect.ddlGenerator().dropIndex(o.oldName(), ddl.tableReference(t),
                                settings.ifNotExists()));
                        out.addAll(sql(ddl.createElement(o.index())));
                    }
                }
                case ChangeOp.RenameConstraint o -> {
                    String renamed = dialect.ddlGenerator().renameConstraint(ddl.tableReference(o.table()),
                            o.oldName(), o.constraint().getName());
                    if (renamed != null) {
                        out.add(renamed);
                    } else if (ddl.inCreateTable(o.constraint()) && !(o.constraint() instanceof PrimaryKey)) {
                        // no rename, and neither drop nor add on an existing table
                        found.add(Unwritable.of(dialect, o.constraint(), "RENAME CONSTRAINT", "left out"));
                    } else if (!(o.constraint() instanceof PrimaryKey)) {
                        // no rename in this dialect: re-create under the new name. A primary
                        // key is skipped — dialects without RENAME CONSTRAINT (MySQL/MariaDB)
                        // do not name primary keys at all.
                        out.add(dialect.ddlGenerator().dropConstraint(ddl.tableReference(o.table()), o.oldName(),
                                settings.ifNotExists()));
                        out.addAll(sql(ddl.createElement(o.constraint())));
                    }
                }

                //  alters
                case ChangeOp.AlterColumnType o -> out.add(dialect.ddlGenerator().alterColumnType(
                        ddl.tableReference(o.table()), o.newColumn().getName(),
                        CwmSchemaMapper.columnMetaData(o.newColumn())));
                case ChangeOp.AlterColumnNullability o ->
                    out.add(dialect.ddlGenerator().alterColumnSetNullability(ddl.tableReference(o.table()),
                            o.column().getName(), o.nullable(), CwmSchemaMapper.columnMetaData(o.column())));
                case ChangeOp.AlterColumnDefault o -> {
                    if (o.defaultValue() == null || o.defaultValue().isBlank()) {
                        out.add(dialect.ddlGenerator().alterColumnDropDefault(ddl.tableReference(o.table()),
                                o.column().getName()));
                    } else {
                        out.add(dialect.ddlGenerator().alterColumnSetDefault(ddl.tableReference(o.table()),
                                o.column().getName(), o.defaultValue()));
                    }
                }

                //  adds
                case ChangeOp.AddColumn o -> {
                    ColumnDefinition cd = ddl.columnDefinitions(o.table()).stream()
                            .filter(x -> x.column().name().equals(o.column().getName()))
                            .findFirst().orElseThrow();
                    out.add(dialect.ddlGenerator().alterTableAddColumn(ddl.tableReference(o.table()), cd));
                }
                case ChangeOp.CreateTable o -> out.addAll(sql(ddl.createElement(o.table())));

                //  split / merge — structure only, row data is not moved
                case ChangeOp.SplitTable o -> {
                    out.add("-- " + o.oldTable().getName() + " split into " + names(o.newTables())
                            + " — row data is not migrated automatically");
                    out.addAll(sql(ddl.dropElement(o.oldTable())));
                    o.newTables().forEach(nt -> out.addAll(sql(ddl.createElement(nt))));
                }
                case ChangeOp.MergeTables o -> {
                    out.add("-- " + names(o.oldTables()) + " merged into " + o.newTable().getName()
                            + " — row data is not migrated automatically");
                    o.oldTables().forEach(ot -> out.addAll(sql(ddl.dropElement(ot))));
                    out.addAll(sql(ddl.createElement(o.newTable())));
                }
                case ChangeOp.AddPrimaryKey o -> {
                    List<String> cols = featureNames(o.primaryKey().getFeature());
                    if (!cols.isEmpty()) {
                        out.add(dialect.ddlGenerator().addPrimaryKeyConstraint(ddl.tableReference(o.table()),
                                RelationalNames.of(o.primaryKey()), cols));
                    }
                }
                case ChangeOp.AddUniqueConstraint o -> addConstraint(out, o.table(), o.constraint());
                case ChangeOp.AddCheckConstraint o -> addConstraint(out, o.table(), o.constraint());
                case ChangeOp.CreateIndex o -> out.addAll(sql(ddl.createElement(o.index())));
                case ChangeOp.AddForeignKey o -> addConstraint(out, o.table(), o.foreignKey());

                //  views
                case ChangeOp.CreateView o -> out.addAll(sql(ddl.createElement(o.view())));
                case ChangeOp.ReplaceView o -> {
                    if (dialect.supportsCreateOrReplaceView() && !ddl.materialized(o.oldView())
                            && !ddl.materialized(o.newView())) {
                        Views.queryBody(o.newView()).filter(body -> !body.isBlank())
                                .ifPresent(body -> out.add(dialect.ddlGenerator().createView(
                                        ddl.tableReference(o.newView()), body, true)));
                    } else {
                        out.addAll(sql(ddl.dropElement(o.oldView())));
                        out.addAll(sql(ddl.createElement(o.newView())));
                    }
                }

                //  triggers
                case ChangeOp.CreateTrigger o -> out.addAll(sql(ddl.createElement(o.trigger())));
                case ChangeOp.DropTrigger o -> out.addAll(sql(ddl.dropElement(o.trigger())));

                //  comments
                //  routines
                case ChangeOp.CreateRoutine o -> out.addAll(sql(ddl.createElement(o.routine())));
                case ChangeOp.DropRoutine o -> out.addAll(sql(ddl.dropElement(o.routine())));
                case ChangeOp.ReplaceRoutine o -> {
                    Optional<String> replace = ddl.replaceRoutine(o.newRoutine());
                    if (replace.isPresent()) {
                        out.add(replace.get());
                    } else {
                        out.addAll(sql(ddl.dropElement(o.oldRoutine())));
                        out.addAll(sql(ddl.createElement(o.newRoutine())));
                    }
                }

                case ChangeOp.SetComment o -> ddl.comment(o.element(), o.comment()).ifPresent(out::add);
                case ChangeOp.SetViewComment o -> ddl.comment(o.element(), o.comment()).ifPresent(out::add);
            }
        }

        /**
         * Adds a unique constraint, check or foreign key: as the generator adds it, or not at all where
         * the dialect declares it in the {@code CREATE TABLE} of a table this plan creates. On a table
         * the plan does not create, such a constraint is a case the dialect cannot write.
         */
        private void addConstraint(List<String> out, Table table, ModelElement constraint) {
            if (ddl.inCreateTable(constraint)) {
                if (!created.contains(table)) {
                    found.add(Unwritable.of(dialect, constraint, "ALTER TABLE ADD CONSTRAINT", "left out"));
                }
                return;
            }
            out.addAll(sql(ddl.createElement(constraint)));
        }

        /**
         * Drops a constraint as the generator drops it, or with its table where the dialect declares it
         * there. On a table the plan does not drop, such a constraint is a case the dialect cannot
         * write.
         */
        private void dropConstraint(List<String> out, Table table, ModelElement constraint) {
            if (ddl.inCreateTable(constraint)) {
                if (!dropped.contains(table)) {
                    found.add(Unwritable.of(dialect, constraint, "ALTER TABLE DROP CONSTRAINT", "left out"));
                }
                return;
            }
            out.addAll(sql(ddl.dropElement(constraint)));
        }

        /** {@code statement}, or, where the dialect has none ({@code null}), a case it cannot write. */
        private void addOrLeaveOut(List<String> out, String statement, ModelElement element, String what) {
            if (statement != null) {
                out.add(statement);
            } else {
                found.add(Unwritable.of(dialect, element, what, "left out"));
            }
        }
    }

    // helpers

    private static List<String> sql(List<DdlStatement> statements) {
        return DdlStatement.sql(statements);
    }

    private static String names(List<Table> tables) {
        return tables.stream().map(Table::getName).collect(Collectors.joining(", "));
    }

    private static List<String> featureNames(List<? extends StructuralFeature> features) {
        List<String> out = new ArrayList<>();
        for (StructuralFeature sf : features) {
            out.add(sf.getName());
        }
        return out;
    }
}
