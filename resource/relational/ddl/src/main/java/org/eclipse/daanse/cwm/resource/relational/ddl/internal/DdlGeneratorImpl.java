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

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Description;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions;
import org.eclipse.daanse.cwm.model.cwm.foundation.keysindexes.IndexedFeature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.QualifiedNames;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalPackage;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.DeferrabilityType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ColumnSets;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ForeignKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.NamedColumnSets;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.PrimaryKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalNames;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalRules;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLIndexes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.UniqueConstraints;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Views;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.CreateOrder;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.CwmSchemaMapper;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlGenerator;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlStatement;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.Unwritable;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.Unwritables;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.IndexColumn;
import org.eclipse.daanse.sql.dialect.api.generator.DdlGenerator.IndexDefinition;
import org.eclipse.daanse.sql.model.schema.ColumnDefinition;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Writes what CWM relational {@link Schema}s hold as ordered dialect-specific
 * DDL statements via {@link Dialect#ddlGenerator()}. No JDBC.
 *
 * <p>
 * An instance pairs a {@link Dialect} with immutable {@link DdlSettings}, so it
 * is reusable and thread-safe. A script is built as a list of parts, each the
 * statements that create one element and those that drop it again: the create
 * script runs the parts in order, the drop script runs them backwards. So the
 * drop script is the exact reverse of the create script and names what it
 * named.
 * </p>
 *
 * <p>
 * Order: every {@code CREATE SCHEMA}; every {@code CREATE TABLE} with its
 * primary key; the table and column comments; the unique constraints; the
 * checks; the indexes; the foreign keys; the views; the triggers. A constraint
 * the dialect cannot add to an existing table is declared in
 * {@code CREATE TABLE} instead; with foreign keys among them, a table comes
 * after the tables it refers to, across all schemas of the script, and tables
 * that refer to each other in a cycle are an error that names them. A view
 * comes after the views it reads: where a view of the script is a client of a
 * {@code core::Dependency} on another view of the script, the dependencies
 * between the views decide, otherwise the names of the script's views found
 * in each query text (a name, or a schema and a name; outside quotes case does
 * not count). Views that read each other in a cycle are an error that names
 * them. Elements of one kind keep the order of the model.
 * </p>
 *
 * <p>
 * What the dialect cannot write as the model has it is never passed over in
 * silence ({@link Unwritables}). Where the settings are not strict, it is
 * written plainer or left out, and the log names each case: a materialized view
 * is written as a plain view where the dialect has no materialized views, and
 * the comments on a materialized view and its columns are left out where it
 * has; an index that filters its rows is left out where the dialect has no
 * partial indexes; a descending index column is written ascending where the
 * dialect keeps no order in an index; a deferrable foreign key is written
 * without its deferrability; every index is left out where the dialect has no
 * index DDL; a comment is left out where the dialect writes none on its
 * element, as on a view and its columns where it names a view no differently
 * from a table ({@link #commentsOnViews()}); a trigger is written as the
 * dialect writes it, an {@code AFTER} trigger for a {@code BEFORE} one, a
 * statement trigger for a row trigger, without its {@code WHEN} condition.
 * Strict ({@link DdlSettings#strict()}), the create and the drop script fail
 * alike, with every case of the run.
 * </p>
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

    @Override
    public DdlSettings settings() {
        return settings;
    }

    @Override
    public List<DdlStatement> create(List<? extends Schema> schemas) {
        // Deliberately not gated on supportsDdl(): that is derived from
        // Connection.isReadOnly() and describes the session, not the language.
        Unwritables found = Unwritables.forCreate(settings);
        List<DdlStatement> out = new ArrayList<>();
        for (Part part : new Script(schemas, found).parts()) {
            part.create().forEach(sql -> out.add(new DdlStatement(part.element(), sql)));
        }
        found.finish();
        return out;
    }

    @Override
    public List<DdlStatement> drop(List<? extends Schema> schemas) {
        Unwritables found = Unwritables.forDrop(settings);
        List<DdlStatement> out = new ArrayList<>();
        for (Part part : new Script(schemas, found).parts().reversed()) {
            part.drop().forEach(sql -> out.add(new DdlStatement(part.element(), sql)));
        }
        found.finish();
        return out;
    }

    @Override
    public List<DdlStatement> createElement(ModelElement element) {
        Unwritables found = Unwritables.forCreate(settings);
        List<DdlStatement> out = new ArrayList<>();
        for (Part part : scriptOf(element, found).partsOf(element)) {
            part.create().forEach(sql -> out.add(new DdlStatement(part.element(), sql)));
        }
        found.finish();
        return out;
    }

    @Override
    public List<DdlStatement> dropElement(ModelElement element) {
        Unwritables found = Unwritables.forDrop(settings);
        List<DdlStatement> out = new ArrayList<>();
        for (Part part : scriptOf(element, found).partsOf(element).reversed()) {
            part.drop().forEach(sql -> out.add(new DdlStatement(part.element(), sql)));
        }
        found.finish();
        return out;
    }

    /**
     * Whether the dialect writes a comment on a view and on its columns: where the statement it
     * writes for a view names the view a view, so it differs from the one for a table of the same
     * name, as SQL Server's does. A dialect that writes the statement of a table for a view as well
     * writes none: PostgreSQL rejects {@code COMMENT ON TABLE} for a view, and MySQL and MariaDB
     * keep no comment on a view.
     */
    boolean commentsOnViews() {
        Optional<SchemaReference> schema = Optional.of(new SchemaReference("s"));
        Optional<String> onView = dialect.ddlGenerator()
                .commentOnTable(new TableReference(schema, "v", TableReference.TYPE_VIEW), "c");
        return onView.isPresent() && !onView.equals(dialect.ddlGenerator()
                .commentOnTable(new TableReference(schema, "v", TableReference.TYPE_TABLE), "c"));
    }

    /**
     * Whether {@code view} is written as a materialized view: the model says it is one (tagged
     * {@link RelationalRules#MATERIALIZED_TAG}), and the dialect writes materialized views. Where it
     * writes none, such a view is written as a plain view, a case it cannot write.
     */
    @Override
    public boolean materialized(View view) {
        return RelationalRules.MATERIALIZED.test(view) && dialect.ddlGenerator().supportsMaterializedViews();
    }

    @Override
    public boolean inCreateTable(ModelElement constraint) {
        return switch (constraint) {
        case PrimaryKey pk -> true;
        case UniqueConstraint uc -> !dialect.ddlGenerator().supportsAddUniqueConstraint();
        case CheckConstraint ck -> !dialect.ddlGenerator().supportsAddCheckConstraint();
        case ForeignKey fk -> !dialect.ddlGenerator().supportsAddForeignKeyConstraint();
        case null, default -> false;
        };
    }

    @Override
    public Optional<String> comment(ModelElement tableOrColumn, String text) {
        Optional<String> sql;
        String on;
        if (tableOrColumn instanceof Column column) {
            NamedColumnSet owner = Columns.namedOwner(column).orElseThrow(
                    () -> new IllegalArgumentException("column without table: " + QualifiedNames.of(column)));
            sql = dialect.ddlGenerator().commentOnColumn(ref(owner, homeOf(owner)), column.getName(), text,
                    CwmSchemaMapper.columnMetaData(column));
            on = "a comment on a column of a table";
        } else if (tableOrColumn instanceof Table table) {
            sql = dialect.ddlGenerator().commentOnTable(ref(table, homeOf(table)), text);
            on = "a comment on a table";
        } else {
            throw new IllegalArgumentException("comments are written on tables and columns, not on "
                    + (tableOrColumn == null ? null : tableOrColumn.eClass().getName()));
        }
        if (sql.isEmpty() && text != null) {
            Unwritables found = Unwritables.forCreate(settings);
            found.add(Unwritable.of(dialect, tableOrColumn, on, "left out"));
            found.finish();
        }
        return sql;
    }

    @Override
    public TableReference tableReference(NamedColumnSet table) {
        return ref(table, homeOf(table));
    }

    @Override
    public List<ColumnDefinition> columnDefinitions(NamedColumnSet table) {
        return CwmSchemaMapper.columnDefinitions(tableReference(table), table);
    }

    /** The script of the schema {@code element} is written into, or of none where it lies in no schema. */
    private Script scriptOf(ModelElement element, Unwritables found) {
        return new Script(schemaOf(element).stream().toList(), found);
    }

    // -------------------- one script --------------------

    /**
     * The statements that create one element, and those that drop it again.
     *
     * @param element the element they are written for
     */
    private record Part(ModelElement element, List<String> create, List<String> drop) {
    }

    /**
     * The parts of one script over some schemas, in create order, and the parts of a single element
     * as such a script writes them.
     */
    private final class Script {

        private final List<Schema> schemas;
        private final Set<Schema> home = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Map<ModelElement, String> names = new IdentityHashMap<>();
        private final Map<Table, List<ForeignKeySpec>> foreignKeys = new IdentityHashMap<>();
        private final Unwritables found;
        private final boolean inlineForeignKeys = !dialect.ddlGenerator().supportsAddForeignKeyConstraint();
        private final boolean inlineUnique = !dialect.ddlGenerator().supportsAddUniqueConstraint();
        private final boolean inlineChecks = !dialect.ddlGenerator().supportsAddCheckConstraint();
        private final boolean viewComments = commentsOnViews();

        Script(List<? extends Schema> schemas, Unwritables found) {
            if (schemas == null || schemas.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("schemas must not be or hold null");
            }
            this.schemas = List.copyOf(new LinkedHashSet<>(schemas));
            this.found = found;
            if (!settings.includeSchema() && this.schemas.size() > 1) {
                throw new IllegalArgumentException("Without schema names every table goes into the schema of the"
                        + " connection; a script then covers one schema, not " + this.schemas.stream()
                                .map(QualifiedNames::of).collect(Collectors.joining(", ")));
            }
            home.addAll(this.schemas);
            Set<EObject> roots = Collections.newSetFromMap(new IdentityHashMap<>());
            this.schemas.forEach(s -> roots.add(EcoreUtil.getRootContainer(s)));
            roots.forEach(root -> names.putAll(RelationalNames.in(root)));
        }

        List<Part> parts() {
            List<Part> parts = new ArrayList<>();
            List<Table> tables = new ArrayList<>();
            List<View> views = new ArrayList<>();
            for (Schema schema : schemas) {
                tables.addAll(Schemas.tables(schema));
                views.addAll(Schemas.views(schema));
                schemaPart(schema).ifPresent(parts::add);
            }
            for (Table table : inlineForeignKeys ? referencedFirst(tables) : tables) {
                parts.add(tablePart(table));
            }
            for (Table table : tables) {
                comment(table).flatMap(d -> commentPart(table, d)).ifPresent(parts::add);
                for (Column column : ColumnSets.columns(table)) {
                    comment(column).flatMap(d -> commentPart(column, d)).ifPresent(parts::add);
                }
            }
            if (!inlineUnique) {
                for (Table table : tables) {
                    uniques(table).forEach(uc -> parts.add(uniquePart(table, uc)));
                }
            }
            if (!inlineChecks) {
                for (Table table : tables) {
                    checks(table).forEach(ck -> parts.add(checkPart(table, ck)));
                }
            }
            for (Table table : tables) {
                for (SQLIndex index : SQLIndexes.spanning(table)) {
                    indexPart(index).ifPresent(parts::add);
                }
            }
            if (!inlineForeignKeys) {
                for (Table table : tables) {
                    foreignKeys(table).forEach(fk -> parts.add(foreignKeyPart(table, fk)));
                }
            }
            List<View> written = new ArrayList<>();
            for (View view : CreateOrder.views(views)) {
                viewPart(view).ifPresent(part -> {
                    parts.add(part);
                    written.add(view);
                });
            }
            for (View view : written) {
                comment(view).flatMap(d -> commentPart(view, d)).ifPresent(parts::add);
                for (Column column : ColumnSets.columns(view)) {
                    comment(column).flatMap(d -> commentPart(column, d)).ifPresent(parts::add);
                }
            }
            for (Table table : tables) {
                for (Trigger trigger : table.getTrigger()) {
                    triggerPart(trigger).ifPresent(parts::add);
                }
            }
            return parts;
        }

        /** The parts of {@code element} alone, as {@link #parts()} writes them. */
        List<Part> partsOf(ModelElement element) {
            return switch (element) {
            case Schema schema -> schemaPart(schema).stream().toList();
            case Table table -> List.of(tablePart(table));
            case PrimaryKey key -> List.of();
            case UniqueConstraint uc -> inlineUnique ? List.of()
                    : tableOf(uc).flatMap(table -> uniques(table).stream().filter(spec -> spec.element() == uc)
                            .findFirst().map(spec -> uniquePart(table, spec))).stream().toList();
            case CheckConstraint ck -> inlineChecks ? List.of()
                    : tableOf(ck).flatMap(table -> checks(table).stream().filter(spec -> spec.element() == ck)
                            .findFirst().map(spec -> checkPart(table, spec))).stream().toList();
            case ForeignKey fk -> inlineForeignKeys ? List.of()
                    : tableOf(fk).flatMap(table -> foreignKeys(table).stream().filter(spec -> spec.element() == fk)
                            .findFirst().map(spec -> foreignKeyPart(table, spec))).stream().toList();
            case SQLIndex index -> indexPart(index).stream().toList();
            case View view -> viewPart(view).stream().toList();
            case Trigger trigger -> triggerPart(trigger).stream().toList();
            case Description description -> Objects.equals(description.getType(), settings.commentType())
                    ? Descriptions.elements(description).stream().flatMap(e -> commentPart(e, description).stream())
                            .toList()
                    : List.of();
            case null -> throw new IllegalArgumentException("element must not be null");
            default -> throw new IllegalArgumentException("the DDL generator writes no " + element.eClass().getName()
                    + " on its own: " + QualifiedNames.of(element));
            };
        }

        private Optional<Part> schemaPart(Schema schema) {
            if (!settings.createSchema() || !settings.includeSchema() || !named(schema)) {
                return Optional.empty();
            }
            return Optional.of(new Part(schema,
                    List.of(dialect.ddlGenerator().createSchema(schema.getName(), settings.ifNotExists())),
                    List.of(dialect.ddlGenerator().dropSchema(schema.getName(), settings.ifNotExists(),
                            settings.cascade()))));
        }

        /** {@code CREATE TABLE} with the primary key and what the dialect declares in it. */
        private Part tablePart(Table table) {
            TableReference tref = ref(table, home);
            List<String> inline = new ArrayList<>();
            if (inlineUnique) {
                uniques(table).forEach(uc -> inline
                        .add(dialect.ddlGenerator().uniqueConstraintClause(uc.name(), uc.columns())));
            }
            if (inlineChecks) {
                checks(table).forEach(
                        ck -> inline.add(dialect.ddlGenerator().checkConstraintClause(ck.name(), ck.body())));
            }
            if (inlineForeignKeys) {
                foreignKeys(table).forEach(fk -> {
                    deferrability(fk.element());
                    inline.add(dialect.ddlGenerator().foreignKeyClause(fk.name(), fk.columns(), fk.referenced(),
                            fk.referencedColumns(), fk.onDelete(), fk.onUpdate()));
                });
            }
            String create = createTableSql(tref, table, primaryKeyName(table, this::name), inline);
            return new Part(table, List.of(create),
                    List.of(dialect.ddlGenerator().dropTable(tref, settings.ifNotExists(), settings.cascade())));
        }

        /**
         * The comment {@code description} holds, on {@code element}: a table, a view or a column of
         * one; nothing to drop, as it goes with its element. Where the dialect writes no comment on
         * such an element, as on a view where it names a view no differently from a table
         * ({@link #commentsOnViews()}), the comment is left out. A comment on an element of
         * another kind is none the DDL writes.
         */
        private Optional<Part> commentPart(ModelElement element, Description description) {
            if (description.getBody() == null) {
                return Optional.empty();
            }
            NamedColumnSet owner = element instanceof Column column ? Columns.namedOwner(column).orElse(null)
                    : element instanceof NamedColumnSet ncs ? ncs : null;
            if (!(owner instanceof Table || owner instanceof View)) {
                return Optional.empty();
            }
            Optional<String> sql = Optional.empty();
            if (owner instanceof View view && materialized(view)) {
                unwritable(element, "a comment on " + (element instanceof Column ? "a column of " : "")
                        + "a materialized view", "left out");
                return Optional.empty();
            }
            if (owner instanceof Table || viewComments) {
                sql = element instanceof Column column
                        ? dialect.ddlGenerator().commentOnColumn(ref(owner, home), column.getName(),
                                description.getBody(), CwmSchemaMapper.columnMetaData(column))
                        : dialect.ddlGenerator().commentOnTable(ref(owner, home), description.getBody());
            }
            if (sql.isEmpty()) {
                unwritable(element, "a comment on " + (element instanceof Column ? "a column of " : "")
                        + (owner instanceof View ? "a view" : "a table"), "left out");
            }
            return sql.map(statement -> new Part(description, List.of(statement), List.of()));
        }

        private Part uniquePart(Table table, UniqueSpec uc) {
            TableReference tref = ref(table, home);
            return new Part(uc.element(), List.of(dialect.ddlGenerator().addUniqueConstraint(tref, uc.name(),
                    uc.columns())), List.of(dialect.ddlGenerator().dropConstraint(tref, uc.name(),
                            settings.ifNotExists())));
        }

        private Part checkPart(Table table, CheckSpec ck) {
            TableReference tref = ref(table, home);
            return new Part(ck.element(), List.of(dialect.ddlGenerator().addCheckConstraint(tref, ck.name(),
                    ck.body())), List.of(dialect.ddlGenerator().dropConstraint(tref, ck.name(),
                            settings.ifNotExists())));
        }

        private Part foreignKeyPart(Table table, ForeignKeySpec fk) {
            deferrability(fk.element());
            TableReference tref = ref(table, home);
            return new Part(fk.element(),
                    List.of(dialect.ddlGenerator().addForeignKeyConstraint(tref, fk.name(), fk.columns(),
                            fk.referenced(), fk.referencedColumns(), fk.onDelete(), fk.onUpdate())),
                    List.of(dialect.ddlGenerator().dropConstraint(tref, fk.name(), settings.ifNotExists())));
        }

        /**
         * {@code CREATE INDEX} on a table, each column {@code DESC} where the model sorts it
         * descending, {@code WHERE} its filter where it has one. Where the dialect has no index DDL,
         * the index is left out; where it has no partial indexes, an index with a filter is left out
         * too, as without the filter a unique index would hold for more rows than the model says.
         * Where it keeps no order in an index, a descending column is written ascending: the index
         * holds the same rows, and only a scan in its order differs.
         */
        private Optional<Part> indexPart(SQLIndex index) {
            if (!(index.getSpannedClass() instanceof Table table)) {
                return Optional.empty();
            }
            boolean descending = dialect.ddlGenerator().supportsDescendingIndexColumns();
            List<IndexColumn> columns = new ArrayList<>();
            for (IndexedFeature item : index.getIndexedFeature()) {
                if (item.getFeature() instanceof Column column && column.getName() != null) {
                    columns.add(SQLIndexes.isDescending(item) && descending ? IndexColumn.descending(column.getName())
                            : IndexColumn.ascending(column.getName()));
                }
            }
            if (columns.isEmpty()) {
                return Optional.empty();
            }
            if (!dialect.supportsIndexDdl()) {
                unwritable(index, "CREATE INDEX", "left out");
                return Optional.empty();
            }
            String filter = index.getFilterCondition() == null || index.getFilterCondition().isBlank() ? null
                    : index.getFilterCondition();
            if (filter != null && !dialect.ddlGenerator().supportsFilteredIndexes()) {
                unwritable(index, "the filter WHERE " + filter, "left out");
                return Optional.empty();
            }
            if (!descending) {
                index.getIndexedFeature().stream().filter(SQLIndexes::isDescending)
                        .forEach(item -> unwritable(index, "DESC on column " + item.getFeature().getName(),
                                "written ascending"));
            }
            TableReference tref = ref(table, home);
            String name = name(index);
            boolean ifNotExists = settings.ifNotExists() && dialect.supportsCreateIndexIfNotExists();
            IndexDefinition definition = new IndexDefinition(name, tref, columns, index.isIsUnique(), filter);
            return Optional.of(new Part(index,
                    List.of(dialect.ddlGenerator().createIndex(definition, ifNotExists).orElseThrow()),
                    List.of(dialect.ddlGenerator().dropIndex(name, tref, settings.ifNotExists()))));
        }

        /**
         * {@code CREATE VIEW}; for a materialized view {@code CREATE MATERIALIZED VIEW} where the
         * dialect writes one ({@link #materialized(View)}), otherwise the plain view.
         */
        private Optional<Part> viewPart(View view) {
            Optional<String> body = Views.queryBody(view).filter(b -> !b.isBlank());
            if (body.isEmpty()) {
                return Optional.empty();
            }
            TableReference vref = ref(view, home);
            if (materialized(view)) {
                return Optional.of(new Part(view,
                        List.of(dialect.ddlGenerator().createMaterializedView(vref, body.get(), settings.ifNotExists())
                                .orElseThrow()),
                        List.of(dialect.ddlGenerator().dropMaterializedView(vref, settings.ifNotExists())
                                .orElseThrow())));
            }
            if (RelationalRules.MATERIALIZED.test(view)) {
                unwritable(view, "MATERIALIZED", "written as a plain view");
            }
            return Optional.of(new Part(view, List.of(dialect.ddlGenerator().createView(vref, body.get(), false)),
                    List.of(dialect.ddlGenerator().dropView(vref, settings.ifNotExists()))));
        }

        private Optional<Part> triggerPart(Trigger trigger) {
            if (trigger.getTable() == null) {
                return Optional.empty();
            }
            TableReference tref = ref(trigger.getTable(), home);
            String name = name(trigger);
            List<String> create = CwmSchemaMapper.createTrigger(dialect, tref, trigger, name);
            if (create.isEmpty()) {
                return Optional.empty();
            }
            if (CwmSchemaMapper.beforeTriggerWrittenAfter(dialect, tref, trigger, name)) {
                unwritable(trigger, "BEFORE", "written AFTER");
            }
            if (CwmSchemaMapper.rowTriggerWrittenPerStatement(dialect, tref, trigger, name)) {
                unwritable(trigger, "FOR EACH ROW", "written as a statement trigger");
            }
            if (CwmSchemaMapper.triggerConditionLeftOut(dialect, tref, trigger, name)) {
                unwritable(trigger, "WHEN " + CwmSchemaMapper.triggerWhen(trigger), "left out");
            }
            return Optional.of(new Part(trigger, create,
                    CwmSchemaMapper.dropTrigger(dialect, tref, trigger, name, settings.ifNotExists())));
        }

        /** The unique constraints of {@code table} that are no primary key and have columns. */
        private List<UniqueSpec> uniques(Table table) {
            List<UniqueSpec> specs = new ArrayList<>();
            for (UniqueConstraint uc : Tables.uniqueConstraints(table)) {
                if (uc instanceof PrimaryKey) {
                    continue;
                }
                List<String> columns = UniqueConstraints.columns(uc).stream().map(Column::getName).toList();
                if (!columns.isEmpty()) {
                    specs.add(new UniqueSpec(uc, name(uc), columns));
                }
            }
            return specs;
        }

        /** The checks of {@code table} that have a condition. */
        private List<CheckSpec> checks(Table table) {
            List<CheckSpec> specs = new ArrayList<>();
            for (CheckConstraint cc : Tables.checkConstraints(table)) {
                String body = cc.getBody() == null ? null : cc.getBody().getBody();
                if (body != null && !body.isBlank()) {
                    specs.add(new CheckSpec(cc, name(cc), body));
                }
            }
            return specs;
        }

        /** The foreign keys of {@code table} with columns and a key of a table to refer to. */
        private List<ForeignKeySpec> foreignKeys(Table table) {
            return foreignKeys.computeIfAbsent(table, this::resolveForeignKeys);
        }

        private List<ForeignKeySpec> resolveForeignKeys(Table table) {
            List<ForeignKeySpec> specs = new ArrayList<>();
            for (ForeignKey fk : Tables.foreignKeys(table)) {
                Optional<Table> target = ForeignKeys.targetTable(fk);
                List<String> columns = ForeignKeys.columns(fk).stream().map(Column::getName).toList();
                List<String> referencedColumns = fk.getUniqueKey() == null ? List.of()
                        : fk.getUniqueKey().getFeature().stream().filter(Column.class::isInstance)
                                .map(StructuralFeature::getName).toList();
                if (target.isEmpty() || columns.isEmpty() || referencedColumns.isEmpty()) {
                    continue;
                }
                specs.add(new ForeignKeySpec(fk, target.get(), name(fk), columns, ref(target.get(), home),
                        referencedColumns, ForeignKeys.referentialAction(fk.getDeleteRule()).orElse(null),
                        ForeignKeys.referentialAction(fk.getUpdateRule()).orElse(null)));
            }
            return specs;
        }

        /**
         * {@code tables} so that each comes after the tables of the list it
         * refers to by a foreign key; otherwise in model order.
         *
         * @throws IllegalStateException naming the tables that refer to each
         *                               other in a cycle
         */
        private List<Table> referencedFirst(List<Table> tables) {
            Map<Table, Set<Table>> referenced = new LinkedHashMap<>();
            for (Table table : tables) {
                Set<Table> targets = new LinkedHashSet<>();
                foreignKeys(table).stream().map(ForeignKeySpec::target).filter(t -> t != table).forEach(targets::add);
                referenced.put(table, targets);
            }
            return CreateOrder.ordered(referenced, cycle -> new IllegalStateException(dialect.name()
                    + " declares foreign keys in CREATE TABLE, so a table is created after the tables it refers to;"
                    + " these tables refer to each other in a cycle: " + CreateOrder.names(cycle)));
        }

        private String name(ModelElement element) {
            String name = names.get(element);
            return name != null ? name : RelationalNames.of(element);
        }

        /** The deferrability of {@code fk}, which the DDL writes not: a key is written not deferrable. */
        private void deferrability(ForeignKey fk) {
            if (fk.eIsSet(RelationalPackage.Literals.FOREIGN_KEY__DEFERRABILITY)
                    && fk.getDeferrability() != DeferrabilityType.NOT_DEFERRABLE) {
                unwritable(fk, "DEFERRABLE " + (fk.getDeferrability() == DeferrabilityType.INITIALLY_DEFERRED
                        ? "INITIALLY DEFERRED" : "INITIALLY IMMEDIATE"), "written NOT DEFERRABLE");
            }
        }

        private void unwritable(ModelElement element, String what, String instead) {
            found.add(Unwritable.of(dialect, element, what, instead));
        }
    }

    /** A unique constraint as the DDL names it: its name and its columns; a primary key is none. */
    private record UniqueSpec(UniqueConstraint element, String name, List<String> columns) {
    }

    /** A check as the DDL names it: its name and its condition. */
    private record CheckSpec(CheckConstraint element, String name, String body) {
    }

    /** One foreign key of a table, resolved for the dialect. */
    private record ForeignKeySpec(ForeignKey element, Table target, String name, List<String> columns,
            TableReference referenced, List<String> referencedColumns, String onDelete, String onUpdate) {
    }

    /** The schema {@code element} is written into: its own, or that of the table it belongs to. */
    private static Optional<Schema> schemaOf(ModelElement element) {
        return switch (element) {
        case Schema schema -> Optional.of(schema);
        case NamedColumnSet ncs -> NamedColumnSets.findSchema(ncs);
        case Description description -> Descriptions.elements(description).stream().findFirst()
                .flatMap(DdlGeneratorImpl::schemaOf);
        case Column column -> Columns.namedOwner(column).flatMap(NamedColumnSets::findSchema);
        case null -> Optional.empty();
        default -> RelationalNames.tableOf(element).flatMap(NamedColumnSets::findSchema);
        };
    }

    private static Optional<Table> tableOf(ModelElement element) {
        return RelationalNames.tableOf(element).filter(Table.class::isInstance).map(Table.class::cast);
    }

    /** The schemas a reference to {@code ncs} counts as home for: its own. */
    private static Set<Schema> homeOf(NamedColumnSet ncs) {
        Set<Schema> home = Collections.newSetFromMap(new IdentityHashMap<>());
        NamedColumnSets.findSchema(ncs).ifPresent(home::add);
        return home;
    }

    // -------------------- statements --------------------

    private String createTableSql(TableReference tref, Table table, String primaryKeyName, List<String> inline) {
        List<ColumnDefinition> cols = CwmSchemaMapper.columnDefinitions(tref, table);
        org.eclipse.daanse.sql.model.schema.PrimaryKey pk = Tables.findPrimaryKey(table)
                .filter(key -> !PrimaryKeys.columns(key).isEmpty())
                .map(key -> CwmSchemaMapper.primaryKey(tref, key, primaryKeyName)).orElse(null);
        // ifNotExists is a wish, not an instruction: a dialect without
        // the clause would be handed SQL it cannot parse.
        boolean ifNotExists = settings.ifNotExists() && dialect.supportsCreateTableIfNotExists();
        // the plain form unless there is something to declare inline: dialects override that one
        return inline.isEmpty() ? dialect.ddlGenerator().createTable(tref, cols, pk, ifNotExists)
                : dialect.ddlGenerator().createTable(tref, cols, pk, inline, ifNotExists);
    }

    private static String primaryKeyName(Table table, java.util.function.Function<ModelElement, String> names) {
        return Tables.findPrimaryKey(table).map(names).orElse(null);
    }

    /**
     * The reference to {@code ncs}: qualified by its schema with
     * {@link DdlSettings#includeSchema()}, and without it where the schema is
     * not one of {@code home}, the schemas of the script.
     */
    private TableReference ref(NamedColumnSet ncs, Set<Schema> home) {
        Schema schema = NamedColumnSets.findSchema(ncs).orElse(null);
        boolean qualify = named(schema) && (settings.includeSchema() || !home.contains(schema));
        return new TableReference(qualify ? Optional.of(new SchemaReference(schema.getName())) : Optional.empty(),
                ncs.getName(), ncs instanceof View ? TableReference.TYPE_VIEW : TableReference.TYPE_TABLE);
    }

    private Optional<Description> comment(ModelElement element) {
        return Descriptions.find(element, settings.commentType()).filter(d -> d.getBody() != null);
    }

    private static boolean named(Schema schema) {
        return schema != null && schema.getName() != null && !schema.getName().isBlank();
    }

}
