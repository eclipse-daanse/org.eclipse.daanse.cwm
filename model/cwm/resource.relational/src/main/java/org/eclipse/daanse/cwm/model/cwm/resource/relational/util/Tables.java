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
package org.eclipse.daanse.cwm.model.cwm.resource.relational.util;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.foundation.keysindexes.UniqueKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Namespaces;

public final class Tables {

    private Tables() {
    }

    /** Primary key owned by the table, or empty if none. */
    public static Optional<PrimaryKey> findPrimaryKey(Table table) {
        if (table == null) {
            return Optional.empty();
        }
        return Namespaces.ownedElementStream(table, PrimaryKey.class).findFirst();
    }

    /**
     * All unique constraints (including the primary key) owned by the table,
     * deduplicated, declaration order.
     */
    public static List<UniqueConstraint> uniqueConstraints(Table table) {
        return uniqueConstraintStream(table).toList();
    }

    /** Stream twin of {@link #uniqueConstraints}. */
    public static Stream<UniqueConstraint> uniqueConstraintStream(Table table) {
        return Namespaces.ownedElementStream(table, UniqueConstraint.class).distinct();
    }

    /** All foreign keys owned by the table, deduplicated, declaration order. */
    public static List<ForeignKey> foreignKeys(Table table) {
        return foreignKeyStream(table).toList();
    }

    /** Stream twin of {@link #foreignKeys}. */
    public static Stream<ForeignKey> foreignKeyStream(Table table) {
        return Namespaces.ownedElementStream(table, ForeignKey.class).distinct();
    }

    /**
     * Columns of {@code table} that are single-column unique: covered by a
     * single-column {@link UniqueKey} (primary key or unique constraint) or by a
     * single-column unique index.
     */
    public static List<Column> singleColumnUniqueColumns(Table table) {
        LinkedHashSet<Column> unique = new LinkedHashSet<>();
        Namespaces.ownedElementStream(table, UniqueKey.class).filter(uk -> uk.getFeature().size() == 1)
                .map(uk -> uk.getFeature().get(0)).filter(Column.class::isInstance).map(Column.class::cast)
                .forEach(unique::add);
        for (SQLIndex index : SQLIndexes.uniqueSpanning(table)) {
            List<Column> columns = SQLIndexes.columns(index);
            if (columns.size() == 1) {
                unique.add(columns.get(0));
            }
        }
        return List.copyOf(unique);
    }

    /**
     * Non-null, has a non-blank name, and declares at least one valid named column.
     */
    public static boolean isValid(Table table) {
        if (table == null || table.getName() == null || table.getName().isBlank()) {
            return false;
        }
        return ColumnSets.columnStream(table).anyMatch(c -> c.getName() != null && !c.getName().isBlank());
    }
}
