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

import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;

public final class Columns {

    private Columns() {
    }

    /** Owning {@link ColumnSet}, or empty if detached. */
    public static Optional<ColumnSet> owner(Column column) {
        if (column == null || column.getOwner() == null) {
            return Optional.empty();
        }
        return column.getOwner() instanceof ColumnSet cs ? Optional.of(cs) : Optional.empty();
    }

    /**
     * Owning {@link NamedColumnSet} (Table or View), or empty for a bare ColumnSet
     * (e.g. InlineTable).
     */
    public static Optional<NamedColumnSet> namedOwner(Column column) {
        return owner(column).filter(NamedColumnSet.class::isInstance).map(NamedColumnSet.class::cast);
    }

    /** Owning {@link Table}, or empty when the owner is a View or non-Table set. */
    public static Optional<Table> tableOwner(Column column) {
        return owner(column).filter(Table.class::isInstance).map(Table.class::cast);
    }

    /**
     * Dotted identifier {@code "catalog.schema.table.column"}. Missing ancestors
     * are omitted. Display/logging format — no dialect quoting.
     */
    public static String qualifiedName(Column column) {
        if (column == null) {
            return null;
        }
        Optional<NamedColumnSet> ncs = namedOwner(column);
        if (ncs.isEmpty()) {
            return column.getName();
        }
        return NamedColumnSets.qualifiedName(ncs.get()) + "." + column.getName();
    }

    /** Non-null and has a non-blank name. */
    public static boolean isValid(Column column) {
        return column != null && column.getName() != null && !column.getName().isBlank();
    }
}
