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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Namespaces;

public final class Catalogs {

    private Catalogs() {
    }

    /** A root Catalog, owned by no namespace. Creation rule: see {@code objectmodel.core.util.Models}. */
    public static Catalog create(String name) {
        Objects.requireNonNull(name, "name");
        Catalog catalog = RelationalFactory.eINSTANCE.createCatalog();
        catalog.setName(name);
        return catalog;
    }

    /** A Catalog owned by {@code parent} via {@code ownedElement}. */
    public static Catalog create(Namespace parent, String name) {
        Objects.requireNonNull(parent, "parent");
        Catalog catalog = create(name);
        parent.getOwnedElement().add(catalog);
        return catalog;
    }

    /** Schemas directly owned by {@code catalog}. */
    public static List<Schema> schemas(Catalog catalog) {
        return schemaStream(catalog).toList();
    }

    /** Stream of schemas directly owned by {@code catalog}. */
    public static Stream<Schema> schemaStream(Catalog catalog) {
        return Namespaces.ownedElementStream(catalog, Schema.class);
    }

    /** Find an owned schema by name. */
    public static Optional<Schema> findSchema(Catalog catalog, String name) {
        return findOwnedByName(catalog, Schema.class, name);
    }

    /** First owned element of the given type with the given name. */
    public static <T extends ModelElement> Optional<T> findOwnedByName(Catalog catalog, Class<T> type, String name) {
        return Namespaces.findOwnedByName(catalog, type, name);
    }
}
