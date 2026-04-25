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

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.util.objectmodel.core.Namespaces;

/**
 * Helpers specific to {@link NamedColumnSet}. For column accessors use
 * {@link ColumnSets} — they apply to every {@code ColumnSet} subtype.
 */
public final class NamedColumnSets {

    private NamedColumnSets() {
    }

    /**
     * First enclosing {@link Schema} reached via {@code getNamespace()}, or empty
     * if detached.
     */
    public static Optional<Schema> findSchema(NamedColumnSet columnSet) {
        return columnSet == null ? Optional.empty() : Namespaces.walkUpTo(columnSet.getNamespace(), Schema.class);
    }

    /**
     * Enclosing {@link Catalog} via the schema level; empty if either ancestor is
     * missing.
     */
    public static Optional<Catalog> findCatalog(NamedColumnSet columnSet) {
        return findSchema(columnSet).flatMap(Schemas::findCatalog);
    }

    /**
     * Dotted identifier {@code "catalog.schema.table"}. Missing ancestors are
     * omitted. Display/logging format — no dialect quoting.
     */
    public static String qualifiedName(NamedColumnSet columnSet) {
        if (columnSet == null) {
            return null;
        }
        Optional<Schema> schema = findSchema(columnSet);
        Optional<Catalog> catalog = schema.flatMap(Schemas::findCatalog);
        StringBuilder sb = new StringBuilder();
        catalog.ifPresent(c -> sb.append(c.getName()).append('.'));
        schema.ifPresent(s -> sb.append(s.getName()).append('.'));
        sb.append(columnSet.getName());
        return sb.toString();
    }
}
