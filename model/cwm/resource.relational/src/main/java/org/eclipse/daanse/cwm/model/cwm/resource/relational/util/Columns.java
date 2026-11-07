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

import java.util.Objects;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;

public final class Columns {

    private Columns() {
    }

    /**
     * A Column of {@code type}, owned by {@code parent} via {@code feature}.
     * {@code isNullable} keeps its Ecore default {@code COLUMN_NO_NULLS}.
     * Creation rule: see {@code objectmodel.core.util.Models}.
     */
    public static Column create(ColumnSet parent, String name, Classifier type) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Column column = RelationalFactory.eINSTANCE.createColumn();
        column.setName(name);
        column.setType(type);
        parent.getFeature().add(column);
        return column;
    }

    /** Like {@link #create(ColumnSet, String, Classifier)} with explicit nullability. */
    public static Column create(ColumnSet parent, String name, Classifier type, NullableType nullable) {
        Column column = create(parent, name, type);
        column.setIsNullable(Objects.requireNonNull(nullable, "nullable"));
        return column;
    }

    /**
     * Full form: nullability and the column-level sizes that override the
     * type's ({@code 0} leaves a size unset, as in the Ecore).
     */
    public static Column create(ColumnSet parent, String name, Classifier type, NullableType nullable,
            long length, long precision, long scale) {
        Column column = create(parent, name, type, nullable);
        column.setLength(length);
        column.setPrecision(precision);
        column.setScale(scale);
        return column;
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
