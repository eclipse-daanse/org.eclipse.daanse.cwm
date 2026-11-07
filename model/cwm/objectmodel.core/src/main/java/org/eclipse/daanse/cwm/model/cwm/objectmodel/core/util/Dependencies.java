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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Dependency;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;

/**
 * Creates and reads plain {@code core::Dependency}s. CWM leaves
 * {@code Dependency.kind} open; callers bring their vocabulary (e.g. the
 * {@code reads}/{@code writes} convention between a transformation task and
 * the connection it runs over). Creation rule: see {@link Models}.
 *
 * <p>
 * {@code client} is navigable both ways ({@code ModelElement.clientDependency}
 * is its opposite), {@code supplier} only from the dependency; the supplier
 * side is answered through {@link InverseReferences}.
 * </p>
 */
public final class Dependencies {

    private Dependencies() {
    }

    /** A Dependency {@code client → supplier} of the given kind, owned by {@code parent}. */
    public static Dependency create(Namespace parent, String name, String kind, ModelElement client,
            ModelElement supplier) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(supplier, "supplier");
        Dependency dependency = CoreFactory.eINSTANCE.createDependency();
        dependency.setName(name);
        dependency.setKind(kind);
        dependency.getClient().add(client);
        dependency.getSupplier().add(supplier);
        parent.getOwnedElement().add(dependency);
        return dependency;
    }

    /** Dependencies of the given kind (any kind for {@code null}) in which {@code client} is a client. */
    public static Stream<Dependency> ofClientStream(ModelElement client, String kind) {
        if (client == null) {
            return Stream.empty();
        }
        return client.getClientDependency().stream()
                .filter(d -> kind == null || kind.equals(d.getKind()));
    }

    /** List-returning twin of {@link #ofClientStream}. */
    public static List<Dependency> ofClient(ModelElement client, String kind) {
        return ofClientStream(client, kind).toList();
    }

    /**
     * Dependencies of the given kind (any kind for {@code null}) in which
     * {@code supplier} is a supplier; needs an installed inverse index.
     */
    public static Stream<Dependency> ofSupplierStream(ModelElement supplier, String kind) {
        if (supplier == null) {
            return Stream.empty();
        }
        return InverseReferences.referencing(supplier, CorePackage.Literals.DEPENDENCY__SUPPLIER, Dependency.class)
                .filter(d -> kind == null || kind.equals(d.getKind()));
    }

    /** List-returning twin of {@link #ofSupplierStream}. */
    public static List<Dependency> ofSupplier(ModelElement supplier, String kind) {
        return ofSupplierStream(supplier, kind).toList();
    }
}
