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
package org.eclipse.daanse.cwm.resource.relational.sql.resolve.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ColumnSets;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.synonym.Synonym;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.synonym.util.Synonyms;
import ai.starlake.transpiler.schema.JdbcColumn;
import ai.starlake.transpiler.schema.JdbcMetaData;

/**
 * Bridges a list of CWM {@link Schema}s to a JSQLTranspiler
 * {@link JdbcMetaData}, and keeps a back-mapping from
 * {@code (catalog, schema, table, column)} tuples (case-folded) to the original
 * CWM {@link Column} / {@link NamedColumnSet} EObjects so callers can recover
 * the EMF identity of every referenced column.
 *
 * <p>
 * Tables, views and query column sets are all registered as plain tables — for
 * v1 we don't recurse into derived-query expansion.
 * </p>
 * <p>
 * A {@link Synonym} whose chain ends at a {@link NamedColumnSet} is registered
 * under its own name with the target's columns, and its lookup keys map to the
 * target's real {@link Column}s — so lineage leads to the table, not to the
 * synonym. PUBLIC synonyms of the schemas' catalogs are also visible unqualified
 * in the current schema, unless an object of that name exists there (Oracle name
 * resolution: own schema first, then PUBLIC). Synonyms whose target is not in the
 * model (DB link, other database, procedure) are not registered.
 * </p>
 */
final class CwmJdbcMetaData {

    private record TableKey(String catalog, String schema, String table) {
        TableKey {
            catalog = norm(catalog);
            schema = norm(schema);
            table = norm(table);
        }
    }

    private record ColumnKey(String catalog, String schema, String table, String column) {
        ColumnKey {
            catalog = norm(catalog);
            schema = norm(schema);
            table = norm(table);
            column = norm(column);
        }
    }

    private final JdbcMetaData jdbcMetaData;
    private final Map<TableKey, NamedColumnSet> tablesByKey;
    private final Map<ColumnKey, Column> columnsByKey;
    private final String currentCatalog;
    private final String currentSchema;

    CwmJdbcMetaData(List<Schema> schemas, String currentCatalog, String currentSchema) {
        this.currentCatalog = currentCatalog == null ? "" : currentCatalog;
        this.currentSchema = currentSchema == null ? "" : currentSchema;
        this.jdbcMetaData = new JdbcMetaData(this.currentCatalog, this.currentSchema);
        this.tablesByKey = new HashMap<>();
        this.columnsByKey = new HashMap<>();

        for (Schema schema : schemas) {
            String catalogName = catalogNameOf(schema).orElse(this.currentCatalog);
            String schemaName = schema.getName() == null ? "" : schema.getName();
            // Tables, views and QueryColumnSets — every NamedColumnSet directly
            // owned by the schema is registered as a queryable relation with
            // its declared columns.
            for (NamedColumnSet ncs : Schemas.columnSets(schema)) {
                register(catalogName, schemaName, ncs.getName(), ncs);
            }
        }
        // Synonyms after all relations: a relation of the same name wins.
        for (Schema schema : schemas) {
            String catalogName = catalogNameOf(schema).orElse(this.currentCatalog);
            String schemaName = schema.getName() == null ? "" : schema.getName();
            for (Synonym synonym : Synonyms.synonyms(schema)) {
                finalColumnSet(synonym).ifPresent(ncs -> register(catalogName, schemaName, synonym.getName(), ncs));
            }
        }
        Set<Catalog> catalogs = new LinkedHashSet<>();
        for (Schema schema : schemas) {
            Schemas.findCatalog(schema).ifPresent(catalogs::add);
        }
        for (Catalog catalog : catalogs) {
            for (Schema schema : Catalogs.schemas(catalog)) {
                for (Synonym synonym : Synonyms.synonyms(schema)) {
                    if (synonym.isIsPublic()) {
                        finalColumnSet(synonym).ifPresent(
                                ncs -> register(this.currentCatalog, this.currentSchema, synonym.getName(), ncs));
                    }
                }
            }
        }
    }

    /**
     * Registers {@code ncs}'s columns as the relation {@code catalog.schema.name}.
     * {@code name} differs from {@code ncs}'s own name for a synonym; the lookup
     * keys still map to {@code ncs} and its columns. An already registered name is
     * left alone.
     */
    private void register(String catalogName, String schemaName, String name, NamedColumnSet ncs) {
        if (name == null)
            return;
        TableKey tableKey = new TableKey(catalogName, schemaName, name);
        if (tablesByKey.containsKey(tableKey))
            return;
        List<Column> cols = ColumnSets.columns(ncs);
        List<JdbcColumn> jdbcCols = new ArrayList<>(cols.size());
        for (Column c : cols) {
            if (c.getName() == null)
                continue;
            jdbcCols.add(new JdbcColumn(c.getName()));
            columnsByKey.put(new ColumnKey(catalogName, schemaName, name, c.getName()), c);
        }
        jdbcMetaData.addTable(catalogName, schemaName, name, jdbcCols);
        tablesByKey.put(tableKey, ncs);
    }

    private static Optional<NamedColumnSet> finalColumnSet(Synonym synonym) {
        return Synonyms.resolveFinal(synonym).filter(NamedColumnSet.class::isInstance)
                .map(NamedColumnSet.class::cast);
    }

    /** Pick the catalog name for a schema; empty string if no catalog. */
    private static Optional<String> catalogNameOf(Schema schema) {
        Optional<Catalog> cat = Schemas.findCatalog(schema);
        return cat.map(c -> c.getName() == null ? "" : c.getName());
    }

    JdbcMetaData jdbcMetaData() {
        return jdbcMetaData;
    }

    String currentCatalog() {
        return currentCatalog;
    }

    String currentSchema() {
        return currentSchema;
    }

    /**
     * Look up the original CWM {@link Column} EObject for a JSQLTranspiler
     * {@link JdbcColumn} hit. Returns {@code null} when the column was resolved to
     * something we never registered (e.g. a synthetic alias output column with no
     * real backing).
     */
    Column resolve(JdbcColumn jc) {
        if (jc == null || jc.columnName == null)
            return null;
        // JSQLTranspiler may return tableCatalog/tableSchema as null for
        // unqualified references — fall back to current values.
        String cat = jc.tableCatalog != null ? jc.tableCatalog : currentCatalog;
        String sch = jc.tableSchema != null ? jc.tableSchema : currentSchema;
        String tab = jc.tableName != null ? jc.tableName : "";
        return columnsByKey.get(new ColumnKey(cat, sch, tab, jc.columnName));
    }

    /**
     * Look up the original CWM {@link NamedColumnSet} for a (catalog, schema,
     * table) triple.
     */
    NamedColumnSet resolveTable(String catalog, String schema, String table) {
        return tablesByKey.get(new TableKey(catalog, schema, table));
    }

    /** Convenience: resolve a {@link JdbcColumn}'s table. */
    NamedColumnSet resolveTableOf(JdbcColumn jc) {
        if (jc == null)
            return null;
        String cat = jc.tableCatalog != null ? jc.tableCatalog : currentCatalog;
        String sch = jc.tableSchema != null ? jc.tableSchema : currentSchema;
        String tab = jc.tableName != null ? jc.tableName : "";
        return tablesByKey.get(new TableKey(cat, sch, tab));
    }

    /**
     * Case-fold normalisation. JSQLTranspiler's metadata is case-insensitive
     * internally (uses a CaseInsensitiveLinkedHashMap), so we mirror that here.
     * Identifiers preserved in original case in the EMF model are unaffected — we
     * only normalise lookup keys.
     */
    private static String norm(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }
}
