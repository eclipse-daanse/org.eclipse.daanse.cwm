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

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.util.objectmodel.core.Namespaces;

public final class Schemas {

    private Schemas() {
    }

    /** Tables directly owned by {@code schema}. */
    public static List<Table> tables(Schema schema) {
        return tableStream(schema).toList();
    }

    /** Stream of tables directly owned by {@code schema}. */
    public static Stream<Table> tableStream(Schema schema) {
        return Namespaces.ownedElementStream(schema, Table.class);
    }

    /** Views directly owned by {@code schema}. */
    public static List<View> views(Schema schema) {
        return viewStream(schema).toList();
    }

    /** Stream of views directly owned by {@code schema}. */
    public static Stream<View> viewStream(Schema schema) {
        return Namespaces.ownedElementStream(schema, View.class);
    }

    /** All named column sets (tables + views) directly owned by {@code schema}. */
    public static List<NamedColumnSet> columnSets(Schema schema) {
        return columnSetStream(schema).toList();
    }

    /** Stream of named column sets directly owned by {@code schema}. */
    public static Stream<NamedColumnSet> columnSetStream(Schema schema) {
        return Namespaces.ownedElementStream(schema, NamedColumnSet.class);
    }

    /** Find an owned table by name. */
    public static Optional<Table> findTable(Schema schema, String name) {
        return Namespaces.findOwnedByName(schema, Table.class, name);
    }

    /** Find an owned view by name. */
    public static Optional<View> findView(Schema schema, String name) {
        return Namespaces.findOwnedByName(schema, View.class, name);
    }

    /** First owned element with the given name (any type). First match wins. */
    public static Optional<ModelElement> findOwnedByName(Schema schema, String name) {
        return Namespaces.findOwnedByName(schema, ModelElement.class, name);
    }

    /**
     * Enclosing {@link Catalog} reached via the namespace chain, or empty if
     * detached.
     */
    public static Optional<Catalog> findCatalog(Schema schema) {
        return schema == null ? Optional.empty() : Namespaces.walkUpTo(schema.getNamespace(), Catalog.class);
    }

    /** First owned element of the given type with the given name. */
    public static <T extends ModelElement> Optional<T> findOwnedByName(Schema schema, Class<T> type, String name) {
        return Namespaces.findOwnedByName(schema, type, name);
    }

    /** Dotted identifier {@code "catalog.schema"}; missing catalog is omitted. */
    public static String qualifiedName(Schema schema) {
        if (schema == null) {
            return null;
        }
        Optional<Catalog> catalog = findCatalog(schema);
        return catalog.map(c -> c.getName() + "." + schema.getName()).orElse(schema.getName());
    }
}
