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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.foundation.keysindexes.KeyRelationship;
import org.eclipse.daanse.cwm.model.cwm.foundation.keysindexes.UniqueKey;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Feature;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;

public final class Tables {

    private Tables() {
    }

    /** Primary key found via column back-references, or empty if none. */
    public static Optional<PrimaryKey> findPrimaryKey(Table table) {
        if (table == null) {
            return Optional.empty();
        }
        for (Feature f : table.getFeature()) {
            if (!(f instanceof StructuralFeature sf)) {
                continue;
            }
            for (UniqueKey uk : sf.getUniqueKey()) {
                if (uk instanceof PrimaryKey pk) {
                    return Optional.of(pk);
                }
            }
        }
        return Optional.empty();
    }

    /** Stream variant of {@link #findUniqueConstraints(Table)}. */
    public static Stream<UniqueConstraint> uniqueConstraintStream(Table table) {
        return findUniqueConstraints(table).stream();
    }

    /** Stream variant of {@link #findForeignKeys(Table)}. */
    public static Stream<ForeignKey> foreignKeyStream(Table table) {
        return findForeignKeys(table).stream();
    }

    /**
     * All unique constraints (including the primary key) on the table,
     * deduplicated, declaration order.
     */
    public static List<UniqueConstraint> findUniqueConstraints(Table table) {
        List<UniqueConstraint> out = new ArrayList<>();
        if (table == null) {
            return out;
        }
        for (Feature f : table.getFeature()) {
            if (!(f instanceof StructuralFeature sf)) {
                continue;
            }
            for (UniqueKey uk : sf.getUniqueKey()) {
                if (uk instanceof UniqueConstraint uc && !out.contains(uc)) {
                    out.add(uc);
                }
            }
        }
        return out;
    }

    /**
     * All foreign keys declared on any column of this table, deduplicated,
     * declaration order.
     */
    public static List<ForeignKey> findForeignKeys(Table table) {
        List<ForeignKey> out = new ArrayList<>();
        if (table == null) {
            return out;
        }
        for (Feature f : table.getFeature()) {
            if (!(f instanceof Column col)) {
                continue;
            }
            for (KeyRelationship kr : col.getKeyRelationship()) {
                if (kr instanceof ForeignKey fk && !out.contains(fk)) {
                    out.add(fk);
                }
            }
        }
        return out;
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
