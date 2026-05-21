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
package org.eclipse.daanse.cwm.util.resource.relational;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.foundation.keysindexes.IndexedFeature;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.util.objectmodel.core.Namespaces;

public final class Indexes {

    private Indexes() {
    }

    /** All SQLIndexes owned directly by {@code schema}. */
    public static List<SQLIndex> indexes(Schema schema) {
        return indexStream(schema).toList();
    }

    public static Stream<SQLIndex> indexStream(Schema schema) {
        return Namespaces.ownedElementStream(schema, SQLIndex.class);
    }

    /**
     * SQLIndexes whose {@code spannedClass} is {@code table}. Walks the table's
     * owning schema; returns an empty list when the table has no parent yet.
     */
    public static List<SQLIndex> spanning(Table table) {
        Optional<Schema> schema = NamedColumnSets.findSchema(table);
        if (schema.isEmpty()) return List.of();
        return indexStream(schema.get())
                .filter(i -> i.getSpannedClass() == table)
                .toList();
    }

    /** Columns referenced by {@code idx}, in index column order. */
    public static List<Column> columns(SQLIndex idx) {
        return columnStream(idx).toList();
    }

    public static Stream<Column> columnStream(SQLIndex idx) {
        return idx.getIndexedFeature().stream()
                .map(IndexedFeature::getFeature)
                .filter(Column.class::isInstance)
                .map(Column.class::cast);
    }
}
