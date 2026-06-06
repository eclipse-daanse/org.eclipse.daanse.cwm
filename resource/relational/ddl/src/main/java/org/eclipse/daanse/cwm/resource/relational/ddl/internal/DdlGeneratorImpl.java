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
package org.eclipse.daanse.cwm.resource.relational.ddl.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.daanse.cwm.resource.relational.ddl.api.CwmSchemaMapper;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlGenerator;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.Feature;
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
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.util.objectmodel.core.Namespaces;
import org.eclipse.daanse.cwm.util.resource.relational.ColumnSets;
import org.eclipse.daanse.cwm.util.resource.relational.ForeignKeys;
import org.eclipse.daanse.cwm.util.resource.relational.Indexes;
import org.eclipse.daanse.cwm.util.resource.relational.NamedColumnSets;
import org.eclipse.daanse.cwm.util.resource.relational.PrimaryKeys;
import org.eclipse.daanse.cwm.util.resource.relational.Schemas;
import org.eclipse.daanse.cwm.util.resource.relational.Tables;
import org.eclipse.daanse.cwm.util.resource.relational.UniqueConstraints;
import org.eclipse.daanse.cwm.util.resource.relational.Views;
import org.eclipse.daanse.jdbc.db.api.schema.ColumnDefinition;
import org.eclipse.daanse.jdbc.db.api.schema.SchemaReference;
import org.eclipse.daanse.jdbc.db.api.schema.TableReference;
import org.eclipse.daanse.jdbc.db.api.schema.Trigger.TriggerEvent;
import org.eclipse.daanse.jdbc.db.api.schema.Trigger.TriggerScope;
import org.eclipse.daanse.jdbc.db.api.schema.Trigger.TriggerTiming;
import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;

/**
 * Serialises a CWM relational {@link Schema} to an ordered list of dialect-
 * specific DDL statements via {@link Dialect#ddlGenerator()}. No JDBC.
 *
 * <p>
 * An instance pairs a {@link Dialect} with immutable {@link DdlSettings}, so it
 * is reusable and thread-safe. Table references are schema-qualified by default
 * (toggle via {@link DdlSettings#includeSchema()}); the catalog is never
 * emitted.
 *
 * <p>
 * Create order: schema, table (+PK), unique, check, index, foreign key, view,
 * trigger. {@link #dropSchema} uses the reverse.
 */
public final class DdlGeneratorImpl implements DdlGenerator {

    private final Dialect dialect;
    private final DdlSettings settings;

    public DdlGeneratorImpl(Dialect dialect) {
        this(dialect, DdlSettings.defaults());
    }

    public DdlGeneratorImpl(Dialect dialect, DdlSettings settings) {
        if (dialect == null) {
            throw new IllegalArgumentException("dialect must not be null");
        }
        this.dialect = dialect;
        this.settings = settings == null ? DdlSettings.defaults() : settings;
    }

    public DdlSettings settings() {
        return settings;
    }

    /** Serialise all features of {@code schema}. */
    public List<String> createSchema(Schema schema) {
        return createSchema(schema, Feature.ALL);
    }

    /**
     * Serialise {@code schema} into ordered {@code CREATE} statements, emitting
     * only entities whose {@link Feature} is in {@code features}. Returned
     * statements have no trailing semicolons.
     */
    public List<String> createSchema(Schema schema, Set<Feature> features) {
        if (schema == null) {
            throw new IllegalArgumentException("schema must not be null");
        }
        if (features == null || features.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        List<Table> tables = Schemas.tables(schema);
        List<View> views = Schemas.views(schema);

        if (features.contains(Feature.SCHEMA) && schema.getName() != null && !schema.getName().isBlank()) {
            out.add(dialect.ddlGenerator().createSchema(schema.getName(), true));
        }

        if (features.contains(Feature.TABLE)) {
            for (Table table : tables) {
                TableReference tref = tableRef(schema, table, TableReference.TYPE_TABLE);
                List<ColumnDefinition> cols = CwmSchemaMapper.columnDefinitions(tref, table);
                org.eclipse.daanse.jdbc.db.api.schema.PrimaryKey pkRef = null;
                if (features.contains(Feature.PRIMARY_KEY)) {
                    pkRef = primaryKeyRef(tref, table);
                }
                out.add(dialect.ddlGenerator().createTable(tref, cols, pkRef, settings.ifNotExists()));
            }
        }

        if (features.contains(Feature.UNIQUE)) {
            for (Table table : tables) {
                TableReference tref = tableRef(schema, table, TableReference.TYPE_TABLE);
                for (UniqueConstraint uc : Tables.findUniqueConstraints(table)) {
                    if (uc instanceof PrimaryKey) {
                        continue;
                    }
                    List<String> colNames = UniqueConstraints.columns(uc).stream().map(Column::getName).toList();
                    if (colNames.isEmpty()) {
                        continue;
                    }
                    String name = nameOrDefault(uc, "uc_" + table.getName());
                    out.add(dialect.ddlGenerator().addUniqueConstraint(tref, name, colNames));
                }
            }
        }

        if (features.contains(Feature.CHECK)) {
            for (Table table : tables) {
                TableReference tref = tableRef(schema, table, TableReference.TYPE_TABLE);
                int idx = 0;
                for (CheckConstraint cc : checkConstraintsOf(table)) {
                    String body = cc.getBody() == null ? null : cc.getBody().getBody();
                    if (body == null || body.isBlank()) {
                        continue;
                    }
                    String name = nameOrDefault(cc, "ck_" + table.getName() + "_" + (++idx));
                    out.add(dialect.ddlGenerator().addCheckConstraint(tref, name, body));
                }
            }
        }

        if (features.contains(Feature.INDEX)) {
            for (Table table : tables) {
                TableReference tref = tableRef(schema, table, TableReference.TYPE_TABLE);
                int idx = 0;
                for (SQLIndex i : Indexes.spanning(table)) {
                    List<String> colNames = indexColumnNames(i);
                    if (colNames.isEmpty()) {
                        continue;
                    }
                    String name = nameOrDefault(i, "idx_" + table.getName() + "_" + (++idx));
                    out.add(dialect.ddlGenerator().createIndex(name, tref, colNames, i.isIsUnique(), true));
                }
            }
        }

        if (features.contains(Feature.FOREIGN_KEY)) {
            for (Table table : tables) {
                TableReference tref = tableRef(schema, table, TableReference.TYPE_TABLE);
                int idx = 0;
                for (ForeignKey fk : Tables.findForeignKeys(table)) {
                    Optional<Table> refTable = ForeignKeys.targetTable(fk);
                    if (refTable.isEmpty()) {
                        continue;
                    }
                    List<String> fkCols = ForeignKeys.columns(fk).stream().map(Column::getName).toList();
                    if (fkCols.isEmpty()) {
                        continue;
                    }
                    List<String> refCols = refColumnNames(fk);
                    if (refCols.isEmpty()) {
                        continue;
                    }
                    Schema refSchema = NamedColumnSets.findSchema(refTable.get()).orElse(null);
                    TableReference refTref = tableRef(refSchema, refTable.get(), TableReference.TYPE_TABLE);
                    String name = nameOrDefault(fk, "fk_" + table.getName() + "_" + (++idx));
                    String onDelete = fk.getDeleteRule() == null ? null
                            : referentialAction(fk.getDeleteRule().getName());
                    String onUpdate = fk.getUpdateRule() == null ? null
                            : referentialAction(fk.getUpdateRule().getName());
                    out.add(dialect.ddlGenerator().addForeignKeyConstraint(tref, name, fkCols, refTref, refCols,
                            onDelete, onUpdate));
                }
            }
        }

        if (features.contains(Feature.VIEW)) {
            for (View view : views) {
                Optional<String> body = Views.queryBody(view);
                if (body.isEmpty() || body.get().isBlank()) {
                    continue;
                }
                TableReference vref = tableRef(schema, view, TableReference.TYPE_VIEW);
                out.add(dialect.ddlGenerator().createView(vref, body.get(), false));
            }
        }

        if (features.contains(Feature.TRIGGER)) {
            record TT(Table table, Trigger trigger, String name, String body, TriggerTiming timing, TriggerEvent event,
                    TriggerScope scope, String when) {
            }
            List<TT> all = new ArrayList<>();
            for (Table table : tables) {
                for (Trigger t : table.getTrigger()) {
                    String body = triggerBody(t);
                    if (body == null) {
                        continue;
                    }
                    TriggerTiming timing = triggerTiming(t);
                    TriggerEvent event = triggerEvent(t);
                    if (timing == null || event == null) {
                        continue;
                    }
                    TriggerScope scope = triggerScope(t);
                    String name = nameOrDefault(t, "trg_" + table.getName());
                    String when = triggerWhen(t);
                    all.add(new TT(table, t, name, body, timing, event, scope, when));
                }
            }

            // Group identical bodies so they share one CREATE PROCEDURE/FUNCTION.
            Map<String, String> procNameByBody = new LinkedHashMap<>();
            for (TT tt : all) {
                procNameByBody.computeIfAbsent(tt.body(), b -> tt.name() + "_fn");
            }
            Map<String, String> emittedProcs = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : procNameByBody.entrySet()) {
                Optional<String> procSql = dialect.ddlGenerator().createTriggerProcedure(e.getValue(), schema.getName(),
                        e.getKey());
                if (procSql.isPresent()) {
                    out.add(procSql.get());
                    emittedProcs.put(e.getKey(), e.getValue());
                }
            }
            for (TT tt : all) {
                TableReference tref = tableRef(schema, tt.table(), TableReference.TYPE_TABLE);
                String procName = emittedProcs.get(tt.body());
                if (procName != null) {
                    out.add(dialect.ddlGenerator().createTriggerUsingProcedure(tt.name(), schema.getName(), tt.timing(),
                            tt.event(), tref, tt.scope(), tt.when(), procName));
                } else {
                    out.add(dialect.ddlGenerator().createTrigger(tt.name(), tt.timing(), tt.event(), tref, tt.scope(),
                            tt.when(), tt.body()));
                }
            }
        }
        return out;
    }

    /** Drop all features of {@code schema}. */
    public List<String> dropSchema(Schema schema) {
        return dropSchema(schema, Feature.ALL);
    }

    /**
     * Drop in reverse of create order. With {@link DdlSettings#cascade()},
     * table and schema drops append SQL-99 {@code CASCADE}; MySQL/MariaDB and
     * SQL Server ignore it.
     */
    public List<String> dropSchema(Schema schema, Set<Feature> features) {
        if (schema == null) {
            throw new IllegalArgumentException("schema must not be null");
        }
        if (features == null || features.isEmpty()) {
            return List.of();
        }
        boolean cascade = settings.cascade();
        List<String> out = new ArrayList<>();
        List<Table> tables = Schemas.tables(schema);
        List<View> views = Schemas.views(schema);

        if (features.contains(Feature.TRIGGER)) {
            Map<String, String> bodyToProc = new LinkedHashMap<>();
            for (Table table : tables) {
                TableReference tref = tableRef(schema, table, TableReference.TYPE_TABLE);
                for (Trigger t : table.getTrigger()) {
                    String body = triggerBody(t);
                    if (body == null) {
                        continue;
                    }
                    String name = nameOrDefault(t, "trg_" + table.getName());
                    bodyToProc.computeIfAbsent(body, b -> name + "_fn");
                    out.addAll(dialect.ddlGenerator().dropTriggerOnTable(name, tref, true));
                }
            }
            for (String procName : bodyToProc.values()) {
                Optional<String> drop = dialect.ddlGenerator().dropProcedure(procName, schema.getName(), true);
                if (drop.isPresent() && !out.contains(drop.get())) {
                    out.add(drop.get());
                }
            }
        }

        if (features.contains(Feature.VIEW)) {
            for (View view : views) {
                TableReference vref = tableRef(schema, view, TableReference.TYPE_VIEW);
                out.add(dialect.ddlGenerator().dropView(vref, true));
            }
        }

        if (features.contains(Feature.FOREIGN_KEY)) {
            for (Table table : tables) {
                TableReference tref = tableRef(schema, table, TableReference.TYPE_TABLE);
                int idx = 0;
                for (ForeignKey fk : Tables.findForeignKeys(table)) {
                    String name = nameOrDefault(fk, "fk_" + table.getName() + "_" + (++idx));
                    out.add(dialect.ddlGenerator().dropConstraint(tref, name, true));
                }
            }
        }

        if (features.contains(Feature.INDEX)) {
            for (Table table : tables) {
                TableReference tref = tableRef(schema, table, TableReference.TYPE_TABLE);
                int idx = 0;
                for (SQLIndex i : Indexes.spanning(table)) {
                    String name = nameOrDefault(i, "idx_" + table.getName() + "_" + (++idx));
                    out.add(dialect.ddlGenerator().dropIndex(name, tref, true));
                }
            }
        }

        if (features.contains(Feature.CHECK)) {
            for (Table table : tables) {
                TableReference tref = tableRef(schema, table, TableReference.TYPE_TABLE);
                int idx = 0;
                for (CheckConstraint cc : checkConstraintsOf(table)) {
                    String name = nameOrDefault(cc, "ck_" + table.getName() + "_" + (++idx));
                    out.add(dialect.ddlGenerator().dropConstraint(tref, name, true));
                }
            }
        }

        if (features.contains(Feature.UNIQUE)) {
            for (Table table : tables) {
                TableReference tref = tableRef(schema, table, TableReference.TYPE_TABLE);
                for (UniqueConstraint uc : Tables.findUniqueConstraints(table)) {
                    if (uc instanceof PrimaryKey) {
                        continue;
                    }
                    String name = nameOrDefault(uc, "uc_" + table.getName());
                    out.add(dialect.ddlGenerator().dropConstraint(tref, name, true));
                }
            }
        }

        if (features.contains(Feature.TABLE)) {
            for (Table table : tables) {
                TableReference tref = tableRef(schema, table, TableReference.TYPE_TABLE);
                out.add(dialect.ddlGenerator().dropTable(tref, true, cascade));
            }
        }

        if (features.contains(Feature.SCHEMA) && schema.getName() != null && !schema.getName().isBlank()) {
            out.add(dialect.ddlGenerator().dropSchema(schema.getName(), true, cascade));
        }
        return out;
    }

    // -------------------- element-level (container-derived) --------------------

    /**
     * Table reference for {@code table}, deriving its {@link Schema} from the CWM
     * namespace chain and honoring {@link DdlSettings#includeSchema()}.
     */
    public TableReference tableReference(NamedColumnSet table) {
        Schema schema = NamedColumnSets.findSchema(table).orElse(null);
        String type = table instanceof View ? TableReference.TYPE_VIEW : TableReference.TYPE_TABLE;
        return tableRef(schema, table, type);
    }

    public List<ColumnDefinition> columnDefinitions(NamedColumnSet table) {
        return CwmSchemaMapper.columnDefinitions(tableReference(table), table);
    }

    /** {@code CREATE TABLE} for {@code table}, deriving its schema and PK. */
    public String createTable(Table table) {
        return createTable(NamedColumnSets.findSchema(table).orElse(null), table);
    }

    /**
     * {@code CREATE TABLE} for {@code table} qualified by an explicit
     * {@code schema}. Use this when the table is not (yet) contained under the
     * schema in the namespace chain — e.g. a freshly diffed table.
     */
    public String createTable(Schema schema, Table table) {
        TableReference tref = tableRef(schema, table, TableReference.TYPE_TABLE);
        List<ColumnDefinition> cols = CwmSchemaMapper.columnDefinitions(tref, table);
        org.eclipse.daanse.jdbc.db.api.schema.PrimaryKey pk = primaryKeyRef(tref, table);
        return dialect.ddlGenerator().createTable(tref, cols, pk, settings.ifNotExists());
    }

    // -------------------- internal --------------------

    private TableReference tableRef(Schema schema, NamedColumnSet ncs, String type) {
        Optional<SchemaReference> sref = (settings.includeSchema() && schema != null && schema.getName() != null
                && !schema.getName().isBlank()) ? Optional.of(new SchemaReference(Optional.empty(), schema.getName()))
                        : Optional.empty();
        return new TableReference(sref, ncs.getName(), type);
    }

    private org.eclipse.daanse.jdbc.db.api.schema.PrimaryKey primaryKeyRef(TableReference tref, Table table) {
        Optional<PrimaryKey> pkOpt = Tables.findPrimaryKey(table);
        if (pkOpt.isEmpty() || PrimaryKeys.columns(pkOpt.get()).isEmpty()) {
            return null;
        }
        return CwmSchemaMapper.primaryKey(tref, pkOpt.get());
    }

    private static List<CheckConstraint> checkConstraintsOf(Table table) {
        // CWM stores CheckConstraints either on the table (ownedElement) or on
        // the column (Constraint.constrainedElement) — collect from both.
        List<CheckConstraint> out = new ArrayList<>();
        Namespaces.ownedElementStream(table, CheckConstraint.class).forEach(out::add);
        for (Column col : ColumnSets.columns(table)) {
            col.getConstraint().stream().filter(CheckConstraint.class::isInstance).map(CheckConstraint.class::cast)
                    .filter(c -> !out.contains(c)).forEach(out::add);
        }
        return out;
    }

    private static List<String> indexColumnNames(SQLIndex i) {
        List<String> out = new ArrayList<>();
        for (Column c : Indexes.columns(i)) {
            if (c.getName() != null) {
                out.add(c.getName());
            }
        }
        return out;
    }

    private static List<String> refColumnNames(ForeignKey fk) {
        List<String> out = new ArrayList<>();
        if (fk.getUniqueKey() == null) {
            return out;
        }
        for (StructuralFeature f : fk.getUniqueKey().getFeature()) {
            if (f instanceof Column c && c.getName() != null) {
                out.add(c.getName());
            }
        }
        return out;
    }

    private static String nameOrDefault(ModelElement e, String fallback) {
        String n = e == null ? null : e.getName();
        return (n == null || n.isBlank()) ? fallback : n;
    }

    private static String referentialAction(String literal) {
        // CWM names like IMPORTED_KEY_CASCADE / imported_key_no_action — match
        // on upper-cased contains so both underscore and camel forms work.
        if (literal == null) {
            return null;
        }
        String upper = literal.toUpperCase();
        if (upper.contains("CASCADE")) {
            return "CASCADE";
        }
        if (upper.contains("SET_NULL") || upper.contains("SETNULL")) {
            return "SET NULL";
        }
        if (upper.contains("SET_DEFAULT") || upper.contains("SETDEFAULT")) {
            return "SET DEFAULT";
        }
        if (upper.contains("RESTRICT")) {
            return "RESTRICT";
        }
        if (upper.contains("NO_ACTION") || upper.contains("NOACTION")) {
            return "NO ACTION";
        }
        return null;
    }

    private static String triggerBody(Trigger t) {
        if (t.getActionStatement() == null) {
            return null;
        }
        String body = t.getActionStatement().getBody();
        return (body == null || body.isBlank()) ? null : body;
    }

    private static String triggerWhen(Trigger t) {
        if (t.getActionCondition() == null) {
            return null;
        }
        String cond = t.getActionCondition().getBody();
        return (cond == null || cond.isBlank()) ? null : cond;
    }

    private static TriggerTiming triggerTiming(Trigger t) {
        if (t.getConditionTiming() == null) {
            return null;
        }
        String name = t.getConditionTiming().getName();
        if (name == null) {
            return null;
        }
        return switch (stripEnumPrefix(name).toUpperCase()) {
        case "BEFORE" -> TriggerTiming.BEFORE;
        case "AFTER" -> TriggerTiming.AFTER;
        case "INSTEAD", "INSTEADOF" -> TriggerTiming.INSTEAD_OF;
        default -> null;
        };
    }

    private static TriggerEvent triggerEvent(Trigger t) {
        if (t.getEventManipulation() == null) {
            return null;
        }
        String name = t.getEventManipulation().getName();
        if (name == null) {
            return null;
        }
        return switch (stripEnumPrefix(name).toUpperCase()) {
        case "INSERT" -> TriggerEvent.INSERT;
        case "UPDATE" -> TriggerEvent.UPDATE;
        case "DELETE" -> TriggerEvent.DELETE;
        default -> null;
        };
    }

    private static TriggerScope triggerScope(Trigger t) {
        if (t.getActionOrientation() == null) {
            return TriggerScope.STATEMENT;
        }
        String name = t.getActionOrientation().getName();
        return switch (stripEnumPrefix(name == null ? "" : name).toUpperCase()) {
        case "ROW" -> TriggerScope.ROW;
        default -> TriggerScope.STATEMENT;
        };
    }

    private static String stripEnumPrefix(String literal) {
        if (literal == null) {
            return "";
        }
        int us = literal.lastIndexOf('_');
        return us >= 0 ? literal.substring(us + 1) : literal;
    }
}
