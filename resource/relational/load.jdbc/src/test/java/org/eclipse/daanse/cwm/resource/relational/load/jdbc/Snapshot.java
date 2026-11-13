/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.resource.relational.load.jdbc;

import java.lang.reflect.Proxy;
import java.sql.JDBCType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import org.eclipse.daanse.sql.jdbc.api.meta.IndexInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.IndexInfoItem;
import org.eclipse.daanse.sql.jdbc.api.meta.MetaInfo;
import org.eclipse.daanse.sql.jdbc.api.meta.StructureInfo;
import org.eclipse.daanse.sql.jdbc.api.schema.CheckConstraint;
import org.eclipse.daanse.sql.jdbc.api.schema.Function;
import org.eclipse.daanse.sql.jdbc.api.schema.ImportedKey;
import org.eclipse.daanse.sql.jdbc.api.schema.MaterializedView;
import org.eclipse.daanse.sql.jdbc.api.schema.Procedure;
import org.eclipse.daanse.sql.jdbc.api.schema.ProcedureReference;
import org.eclipse.daanse.sql.jdbc.api.schema.TableDefinition;
import org.eclipse.daanse.sql.jdbc.api.schema.UniqueConstraint;
import org.eclipse.daanse.sql.jdbc.api.schema.ViewDefinition;
import org.eclipse.daanse.sql.jdbc.record.meta.DatabaseInfoRecord;
import org.eclipse.daanse.sql.jdbc.record.meta.MetaInfoRecord;
import org.eclipse.daanse.sql.jdbc.record.meta.StructureInfoRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.CheckConstraintRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnDefinitionRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ColumnMetaDataRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ImportedKeyRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.IndexInfoItemRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.IndexInfoRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.MaterializedViewRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.PrimaryKeyRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ProcedureRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.TableDefinitionRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.TableMetaDataRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.UniqueConstraintRecord;
import org.eclipse.daanse.sql.jdbc.record.schema.ViewDefinitionRecord;
import org.eclipse.daanse.sql.model.schema.ColumnDefinition;
import org.eclipse.daanse.sql.model.schema.ColumnMetaData;
import org.eclipse.daanse.sql.model.schema.ColumnReference;
import org.eclipse.daanse.sql.model.schema.PrimaryKey;
import org.eclipse.daanse.sql.model.schema.SchemaReference;
import org.eclipse.daanse.sql.model.schema.TableReference;

/**
 * A JDBC metadata snapshot built in code, as a metadata provider would report it: tables and views
 * with their columns (all {@code INTEGER}), keys, foreign keys, indexes, materialized views and
 * procedures. A schema name {@code null} stands for a database without schemas.
 */
final class Snapshot {

    private final List<SchemaReference> schemas = new ArrayList<>();
    private final List<TableDefinition> tables = new ArrayList<>();
    private final List<ColumnDefinition> columns = new ArrayList<>();
    private final List<PrimaryKey> primaryKeys = new ArrayList<>();
    private final List<UniqueConstraint> uniqueConstraints = new ArrayList<>();
    private final List<CheckConstraint> checkConstraints = new ArrayList<>();
    private final List<ImportedKey> importedKeys = new ArrayList<>();
    private final List<ViewDefinition> viewDefinitions = new ArrayList<>();
    private final List<MaterializedView> materializedViews = new ArrayList<>();
    private final List<Procedure> procedures = new ArrayList<>();
    private final List<IndexInfo> indexInfos = new ArrayList<>();
    private final List<org.eclipse.daanse.sql.model.schema.Trigger> triggers = new ArrayList<>();

    static TableReference ref(String schema, String name) {
        return ref(schema, name, TableReference.TYPE_TABLE);
    }

    static TableReference ref(String schema, String name, String type) {
        return new TableReference(Optional.ofNullable(schema).map(SchemaReference::new), name, type);
    }

    /** A schema the database lists, whether it holds tables or not. */
    Snapshot schema(String name) {
        schemas.add(new SchemaReference(name));
        return this;
    }

    /** A table with the columns {@code columnNames}, each {@code INTEGER}. */
    Snapshot table(String schema, String name, String... columnNames) {
        return relation(ref(schema, name), columnNames);
    }

    /** A table of a kind the database names, such as {@code SYSTEM TABLE}. */
    Snapshot tableOfType(String schema, String name, String type, String... columnNames) {
        return relation(ref(schema, name, type), columnNames);
    }

    Snapshot view(String schema, String name, String... columnNames) {
        relation(ref(schema, name, TableReference.TYPE_VIEW), columnNames);
        viewDefinitions.add(new ViewDefinitionRecord(ref(schema, name, TableReference.TYPE_VIEW),
                Optional.of("SELECT 1"), Optional.empty()));
        return this;
    }

    /** A materialized view as PostgreSQL reports it: only in the list of materialized views. */
    Snapshot materializedView(String schema, String name) {
        materializedViews.add(new MaterializedViewRecord(ref(schema, name), Optional.of("SELECT 1"), Optional.empty(),
                Optional.empty(), Optional.empty()));
        return this;
    }

    Snapshot primaryKey(String schema, String table, String name, String... columnNames) {
        primaryKeys.add(new PrimaryKeyRecord(ref(schema, table), columns(schema, table, columnNames),
                Optional.ofNullable(name)));
        return this;
    }

    Snapshot unique(String schema, String table, String name, String... columnNames) {
        uniqueConstraints.add(new UniqueConstraintRecord(name, ref(schema, table), columns(schema, table, columnNames)));
        return this;
    }

    Snapshot check(String schema, String table, String name, String clause) {
        checkConstraints.add(new CheckConstraintRecord(name, ref(schema, table), clause));
        return this;
    }

    /**
     * A foreign key as {@code getImportedKeys} reports it: one row per column, numbered from 1.
     *
     * @param name        {@code null} for a database that does not name it
     * @param targetKey   the name of the referenced key, {@code null} when not reported
     */
    Snapshot foreignKey(String name, String schema, String table, List<String> columnNames, String targetSchema,
            String targetTable, List<String> targetColumns, String targetKey) {
        for (int i = 0; i < columnNames.size(); i++) {
            importedKeys.add(new ImportedKeyRecord(
                    new ColumnReference(Optional.of(ref(targetSchema, targetTable)), targetColumns.get(i)),
                    new ColumnReference(Optional.of(ref(schema, table)), columnNames.get(i)), name, i + 1,
                    ImportedKey.ReferentialAction.NO_ACTION, ImportedKey.ReferentialAction.NO_ACTION,
                    Optional.ofNullable(targetKey), ImportedKey.Deferrability.NOT_DEFERRABLE));
        }
        return this;
    }

    /** Rows of {@code getImportedKeys} as given, for the order a driver reports them in. */
    Snapshot importedKey(ImportedKey row) {
        importedKeys.add(row);
        return this;
    }

    Snapshot index(String schema, String table, String name, boolean unique, String... columnNames) {
        return index(schema, table, name, unique, false, columnNames);
    }

    /**
     * An index the database made itself for a constraint and says so, as H2, MySQL and MariaDB say
     * it of the one they make for a foreign key.
     */
    Snapshot indexForConstraint(String schema, String table, String name, String... columnNames) {
        return index(schema, table, name, false, true, columnNames);
    }

    private Snapshot index(String schema, String table, String name, boolean unique, boolean forConstraint,
            String... columnNames) {
        List<IndexInfoItem> items = new ArrayList<>();
        for (int i = 0; i < columnNames.length; i++) {
            items.add(new IndexInfoItemRecord(Optional.of(name), IndexInfoItem.IndexType.TABLE_INDEX_OTHER,
                    Optional.of(new ColumnReference(Optional.of(ref(schema, table)), columnNames[i])), i + 1,
                    Optional.of(true), 0, 0, Optional.empty(), unique, forConstraint));
        }
        indexInfos.add(new IndexInfoRecord(ref(schema, table), items));
        return this;
    }

    /** A table or column comment, as {@code REMARKS} reports it. */
    Snapshot remarks(String schema, String table, String remarks) {
        TableReference ref = ref(schema, table);
        tables.replaceAll(td -> td.table().equals(ref) ? new TableDefinitionRecord(ref,
                new TableMetaDataRecord(Optional.of(remarks), Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty()))
                : td);
        return this;
    }

    /** A row trigger on a table, with its body. */
    Snapshot trigger(String schema, String table, String name,
            org.eclipse.daanse.sql.model.schema.Trigger.TriggerTiming timing,
            org.eclipse.daanse.sql.model.schema.Trigger.TriggerEvent event) {
        return trigger(schema, table, name, timing, event, "BEGIN NULL; END;");
    }

    /** A row trigger on a table whose action the database reports as {@code action}. */
    Snapshot trigger(String schema, String table, String name,
            org.eclipse.daanse.sql.model.schema.Trigger.TriggerTiming timing,
            org.eclipse.daanse.sql.model.schema.Trigger.TriggerEvent event, String action) {
        triggers.add(new org.eclipse.daanse.sql.jdbc.record.schema.TriggerRecord(
                new org.eclipse.daanse.sql.model.schema.TriggerReference(ref(schema, table), name), timing, event,
                Optional.of(action), Optional.empty(), Optional.of("ROW")));
        return this;
    }

    Snapshot procedure(String schema, String name, String specificName) {
        procedures.add(new ProcedureRecord(
                new ProcedureReference(Optional.ofNullable(schema).map(SchemaReference::new), name, specificName),
                Procedure.ProcedureType.NO_RESULT, Optional.empty(), List.of(), Optional.empty(), Optional.empty(),
                Optional.empty()));
        return this;
    }

    /** A procedure with its body and its whole {@code CREATE} statement, as the database reports them. */
    Snapshot procedure(String schema, String name, String body, String definition) {
        procedures.add(new ProcedureRecord(
                new ProcedureReference(Optional.ofNullable(schema).map(SchemaReference::new), name, name),
                Procedure.ProcedureType.NO_RESULT, Optional.empty(), List.of(), Optional.of(body),
                Optional.of(definition), Optional.empty()));
        return this;
    }

    MetaInfo build() {
        return new MetaInfoRecord(new DatabaseInfoRecord("TestDB", "1.0", 1, 0), structure(), null, List.of(),
                indexInfos);
    }

    /** The snapshot, noting in {@code asked} the name of every list a reader asks it for. */
    MetaInfo recording(Set<String> asked) {
        StructureInfo structure = structure();
        StructureInfo recorded = (StructureInfo) Proxy.newProxyInstance(StructureInfo.class.getClassLoader(),
                new Class<?>[] { StructureInfo.class }, (proxy, method, args) -> {
                    asked.add(method.getName());
                    return method.invoke(structure, args);
                });
        MetaInfo info = build();
        return (MetaInfo) Proxy.newProxyInstance(MetaInfo.class.getClassLoader(), new Class<?>[] { MetaInfo.class },
                (proxy, method, args) -> {
                    asked.add(method.getName());
                    return "structureInfo".equals(method.getName()) ? recorded : method.invoke(info, args);
                });
    }

    private StructureInfo structure() {
        return new StructureInfoRecord(List.of(), schemas, tables, columns, importedKeys, primaryKeys, triggers,
                List.of(), checkConstraints, uniqueConstraints, List.of(), viewDefinitions, procedures,
                List.<Function>of(), materializedViews, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of());
    }

    private Snapshot relation(TableReference table, String... columnNames) {
        tables.add(new TableDefinitionRecord(table));
        for (String column : columnNames) {
            columns.add(new ColumnDefinitionRecord(new ColumnReference(Optional.of(table), column),
                    new ColumnMetaDataRecord(JDBCType.INTEGER, "INTEGER", OptionalInt.of(10), OptionalInt.empty(),
                            OptionalInt.of(10), ColumnMetaData.Nullability.NULLABLE, OptionalInt.empty(),
                            Optional.empty(), Optional.empty(), ColumnMetaData.AutoIncrement.NO,
                            ColumnMetaData.GeneratedColumn.NO)));
        }
        return this;
    }

    private static List<ColumnReference> columns(String schema, String table, String... names) {
        return java.util.Arrays.stream(names).map(n -> new ColumnReference(Optional.of(ref(schema, table)), n))
                .toList();
    }
}
