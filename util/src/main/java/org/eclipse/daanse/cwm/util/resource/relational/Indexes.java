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
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.foundation.keysindexes.IndexedFeature;
import org.eclipse.daanse.cwm.model.cwm.foundation.keysindexes.KeysindexesPackage;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.util.objectmodel.core.InverseReferences;
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
     * SQLIndexes whose {@code spannedClass} is {@code table}, wherever they are owned.
     * Reverse navigation — see {@link InverseReferences} for the index it needs.
     */
    public static List<SQLIndex> spanning(Table table) {
        return spanningStream(table).toList();
    }

    /** Stream twin of {@link #spanning}. */
    public static Stream<SQLIndex> spanningStream(Table table) {
        return InverseReferences.referencing(table,
                KeysindexesPackage.Literals.INDEX__SPANNED_CLASS, SQLIndex.class);
    }

    /** Unique SQLIndexes whose {@code spannedClass} is {@code table}. */
    public static List<SQLIndex> uniqueSpanning(Table table) {
        return uniqueSpanningStream(table).toList();
    }

    /** Stream twin of {@link #uniqueSpanning}. */
    public static Stream<SQLIndex> uniqueSpanningStream(Table table) {
        return spanningStream(table).filter(SQLIndex::isIsUnique);
    }

    /** Columns referenced by {@code idx}, in index column order. */
    public static List<Column> columns(SQLIndex idx) {
        return columnStream(idx).toList();
    }

    public static Stream<Column> columnStream(SQLIndex idx) {
        if (idx == null) {
            return Stream.empty();
        }
        return idx.getIndexedFeature().stream()
                .map(IndexedFeature::getFeature)
                .filter(Column.class::isInstance)
                .map(Column.class::cast);
    }
}
