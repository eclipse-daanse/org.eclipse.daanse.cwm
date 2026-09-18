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
import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.NamedColumnSets;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.UniqueConstraints;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Views;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.CwmSchemaMapper;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlGenerator;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlGeneratorFactory;
import org.eclipse.daanse.cwm.resource.relational.diff.api.ChangeOp;
import org.eclipse.daanse.cwm.resource.relational.diff.api.MigrationEmitter;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.model.schema.ColumnDefinition;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Translates an ordered {@link ChangeOp} plan into dialect SQL, one operation
 * at a time. No ordering logic lives here — the plan arrives ordered.
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
    public List<String> emit(List<ChangeOp> ops, Dialect dialect) {
        return emit(ops, dialect, false);
    }

    @Override
    public List<String> emit(List<ChangeOp> ops, Dialect dialect, boolean cascadeOnDrop) {
        if (ops == null) throw new IllegalArgumentException("ops must not be null");
        if (dialect == null) throw new IllegalArgumentException("dialect must not be null");

        List<String> out = new ArrayList<>();
        DdlGenerator ddl = ddlFactory.create(dialect);
        for (ChangeOp op : ops) {
            translate(out, dialect, ddl, op, cascadeOnDrop);
        }
        return out;
    }

    private static void translate(List<String> out, Dialect dialect, DdlGenerator ddl,
            ChangeOp op, boolean cascadeOnDrop) {
        switch (op) {
            //  drops
            case ChangeOp.DropForeignKey o -> out.add(dialect.ddlGenerator().dropConstraint(
                    ref(o.table()), nameOrDefault(o.foreignKey().getName(), "fk_" + o.table().getName()), true));
            case ChangeOp.DropView o -> out.add(dialect.ddlGenerator().dropView(
                    ref(o.view(), TableReference.TYPE_VIEW), true));
            case ChangeOp.DropIndex o -> {
                Table t = (Table) o.index().getSpannedClass();
                out.add(dialect.ddlGenerator().dropIndex(
                        nameOrDefault(o.index().getName(), "idx_" + t.getName()), ref(t), true));
            }
            case ChangeOp.DropUniqueConstraint o -> out.add(dialect.ddlGenerator().dropConstraint(
                    ref(o.table()), nameOrDefault(o.constraint().getName(), "uc_" + o.table().getName()), true));
            case ChangeOp.DropCheckConstraint o -> out.add(dialect.ddlGenerator().dropConstraint(
                    ref(o.table()), nameOrDefault(o.constraint().getName(), "ck_" + o.table().getName()), true));
            case ChangeOp.DropPrimaryKey o -> out.add(dialect.ddlGenerator().dropConstraint(
                    ref(o.table()), nameOrDefault(o.primaryKey().getName(), "pk_" + o.table().getName()), true));
            case ChangeOp.DropColumn o -> out.add(dialect.ddlGenerator().alterTableDropColumn(
                    ref(o.table()), o.column().getName()));
            case ChangeOp.DropTable o -> out.add(dialect.ddlGenerator().dropTable(
                    ref(o.table()), true, cascadeOnDrop));

            //  renames
            case ChangeOp.RenameTable o -> {
                TableReference oldRef = new TableReference(ref(o.table()).schema(),
                        o.oldName(), TableReference.TYPE_TABLE);
                addIfPresent(out, dialect.ddlGenerator().renameTable(oldRef, o.table().getName()));
            }
            case ChangeOp.RenameColumn o -> addIfPresent(out, dialect.ddlGenerator().renameColumn(
                    ref(o.table()), o.oldName(), o.column().getName()));
            case ChangeOp.RenameIndex o -> {
                Table t = (Table) o.index().getSpannedClass();
                String renamed = dialect.ddlGenerator().renameIndex(o.oldName(), o.index().getName(), ref(t));
                if (renamed != null) {
                    out.add(renamed);
                } else {
                    // no rename in this dialect: re-create under the new name
                    out.add(dialect.ddlGenerator().dropIndex(o.oldName(), ref(t), true));
                    createIndex(out, dialect, o.index());
                }
            }
            case ChangeOp.RenameConstraint o -> {
                String renamed = dialect.ddlGenerator().renameConstraint(ref(o.table()), o.oldName(),
                        o.constraint().getName());
                if (renamed != null) {
                    out.add(renamed);
                } else if (!(o.constraint() instanceof PrimaryKey)) {
                    // no rename in this dialect: re-create under the new name. A primary
                    // key is skipped — dialects without RENAME CONSTRAINT (MySQL/MariaDB)
                    // do not name primary keys at all.
                    out.add(dialect.ddlGenerator().dropConstraint(ref(o.table()), o.oldName(), true));
                    addConstraint(out, dialect, o.table(), o.constraint());
                }
            }

            //  alters
            case ChangeOp.AlterColumnType o -> out.add(dialect.ddlGenerator().alterColumnType(
                    ref(o.table()), o.newColumn().getName(), CwmSchemaMapper.columnMetaData(o.newColumn())));
            case ChangeOp.AlterColumnNullability o ->
                out.add(dialect.ddlGenerator().alterColumnSetNullability(ref(o.table()),
                        o.column().getName(), o.nullable(), CwmSchemaMapper.columnMetaData(o.column())));
            case ChangeOp.AlterColumnDefault o -> {
                if (o.defaultValue() == null || o.defaultValue().isBlank()) {
                    out.add(dialect.ddlGenerator().alterColumnDropDefault(ref(o.table()),
                            o.column().getName()));
                } else {
                    out.add(dialect.ddlGenerator().alterColumnSetDefault(ref(o.table()),
                            o.column().getName(), o.defaultValue()));
                }
            }

            //  adds
            case ChangeOp.AddColumn o -> {
                ColumnDefinition cd = CwmSchemaMapper.columnDefinitions(ref(o.table()), o.table()).stream()
                        .filter(x -> x.column().name().equals(o.column().getName()))
                        .findFirst().orElseThrow();
                out.add(dialect.ddlGenerator().alterTableAddColumn(ref(o.table()), cd));
            }
            case ChangeOp.CreateTable o -> {
                Schema schema = NamedColumnSets.findSchema(o.table()).orElse(null);
                out.add(schema != null ? ddl.createTable(schema, o.table()) : ddl.createTable(o.table()));
            }
            case ChangeOp.AddPrimaryKey o -> {
                List<String> cols = featureNames(o.primaryKey().getFeature());
                if (!cols.isEmpty()) {
                    out.add(dialect.ddlGenerator().addPrimaryKeyConstraint(ref(o.table()),
                            nameOrDefault(o.primaryKey().getName(), "pk_" + o.table().getName()), cols));
                }
            }
            case ChangeOp.AddUniqueConstraint o -> addUnique(out, dialect, o.table(), o.constraint());
            case ChangeOp.AddCheckConstraint o -> addCheck(out, dialect, o.table(), o.constraint());
            case ChangeOp.CreateIndex o -> createIndex(out, dialect, o.index());
            case ChangeOp.AddForeignKey o -> addForeignKey(out, dialect, o.table(), o.foreignKey());

            //  views
            case ChangeOp.CreateView o -> Views.queryBody(o.view())
                    .filter(body -> !body.isBlank())
                    .ifPresent(body -> out.add(dialect.ddlGenerator().createView(
                            ref(o.view(), TableReference.TYPE_VIEW), body, false)));
            case ChangeOp.ReplaceView o -> {
                out.add(dialect.ddlGenerator().dropView(
                        ref(o.oldView(), TableReference.TYPE_VIEW), true));
                Views.queryBody(o.newView())
                        .filter(body -> !body.isBlank())
                        .ifPresent(body -> out.add(dialect.ddlGenerator().createView(
                                ref(o.newView(), TableReference.TYPE_VIEW), body, false)));
            }

            //  triggers
            case ChangeOp.CreateTrigger o -> out.addAll(CwmSchemaMapper.createTrigger(dialect, ref(o.table()),
                    o.trigger()));
            case ChangeOp.DropTrigger o -> out.addAll(CwmSchemaMapper.dropTrigger(dialect, ref(o.table()),
                    o.trigger()));

            //  comments
            case ChangeOp.SetComment o -> {
                if (o.element() instanceof Column c) {
                    dialect.ddlGenerator().commentOnColumn(ref(o.table()), c.getName(), o.comment(),
                            CwmSchemaMapper.columnMetaData(c)).ifPresent(out::add);
                } else {
                    dialect.ddlGenerator().commentOnTable(ref(o.table()), o.comment()).ifPresent(out::add);
                }
            }
        }
    }

    // helpers

    private static void addUnique(List<String> out, Dialect dialect, Table table, UniqueConstraint uc) {
        List<String> cols = UniqueConstraints.columns(uc).stream().map(Column::getName).toList();
        if (!cols.isEmpty()) {
            out.add(dialect.ddlGenerator().addUniqueConstraint(ref(table),
                    nameOrDefault(uc.getName(), "uc_" + table.getName()), cols));
        }
    }

    private static void addCheck(List<String> out, Dialect dialect, Table table, CheckConstraint ck) {
        String body = ck.getBody() == null ? null : ck.getBody().getBody();
        if (body != null && !body.isBlank()) {
            out.add(dialect.ddlGenerator().addCheckConstraint(ref(table),
                    nameOrDefault(ck.getName(), "ck_" + table.getName()), body));
        }
    }

    private static void createIndex(List<String> out, Dialect dialect, SQLIndex index) {
        Table t = (Table) index.getSpannedClass();
        List<String> cols = new ArrayList<>();
        for (var ifc : index.getIndexedFeature()) {
            if (ifc.getFeature() instanceof Column c && c.getName() != null) {
                cols.add(c.getName());
            }
        }
        if (!cols.isEmpty()) {
            out.add(dialect.ddlGenerator().createIndex(nameOrDefault(index.getName(), "idx_" + t.getName()),
                    ref(t), cols, index.isIsUnique(), true));
        }
    }

    private static void addConstraint(List<String> out, Dialect dialect, Table table, ModelElement constraint) {
        switch (constraint) {
            case UniqueConstraint uc -> addUnique(out, dialect, table, uc);
            case CheckConstraint ck -> addCheck(out, dialect, table, ck);
            case ForeignKey fk -> addForeignKey(out, dialect, table, fk);
            default -> throw new IllegalArgumentException("not a constraint: " + constraint.eClass().getName());
        }
    }

    private static void addForeignKey(List<String> out, Dialect dialect, Table table, ForeignKey fk) {
        Optional<Table> target = org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ForeignKeys.targetTable(fk);
        if (target.isEmpty() || fk.getUniqueKey() == null) return;
        List<String> fkCols = org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ForeignKeys.columns(fk).stream()
                .map(Column::getName).toList();
        List<String> refCols = featureNames(fk.getUniqueKey().getFeature());
        if (fkCols.isEmpty() || refCols.isEmpty()) return;
        String name = nameOrDefault(fk.getName(), "fk_" + table.getName());
        String onDelete = fk.getDeleteRule() == null ? null : referentialAction(fk.getDeleteRule().getName());
        String onUpdate = fk.getUpdateRule() == null ? null : referentialAction(fk.getUpdateRule().getName());
        out.add(dialect.ddlGenerator().addForeignKeyConstraint(ref(table), name, fkCols,
                ref(target.get()), refCols, onDelete, onUpdate));
    }

    private static List<String> featureNames(List<? extends StructuralFeature> features) {
        List<String> out = new ArrayList<>();
        for (StructuralFeature sf : features) {
            out.add(sf.getName());
        }
        return out;
    }

    private static String referentialAction(String literal) {
        if (literal == null) return null;
        String upper = literal.toUpperCase();
        if (upper.contains("CASCADE")) return "CASCADE";
        if (upper.contains("SET_NULL") || upper.contains("SETNULL")) return "SET NULL";
        if (upper.contains("SET_DEFAULT") || upper.contains("SETDEFAULT")) return "SET DEFAULT";
        if (upper.contains("RESTRICT")) return "RESTRICT";
        if (upper.contains("NO_ACTION") || upper.contains("NOACTION")) return "NO ACTION";
        return null;
    }

    private static TableReference ref(Table t) {
        return ref(t, TableReference.TYPE_TABLE);
    }

    private static TableReference ref(NamedColumnSet ncs, String type) {
        SchemaReference sref = NamedColumnSets.findSchema(ncs)
                .filter(s -> s.getName() != null && !s.getName().isBlank())
                .map(s -> new SchemaReference(Optional.empty(), s.getName()))
                .orElse(null);
        return new TableReference(Optional.ofNullable(sref), ncs.getName(), type);
    }

    private static void addIfPresent(List<String> out, String statement) {
        if (statement != null) {
            out.add(statement);
        }
    }

    private static String nameOrDefault(String name, String fallback) {
        return name == null || name.isBlank() ? fallback : name;
    }
}
