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
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Package;

/**
 * Helpers around {@code core::Package}, whose CWM semantics make a package's
 * contents the union of what it owns and what it imports: <em>available =
 * ownedElement + importedElement</em>. Own elements keep their
 * {@code ownedElement} position, imports are appended (imports are an
 * unordered set); an element that is both owned and imported appears once.
 */
public final class Packages {

    private Packages() {
    }

    /**
     * Stream of the elements of {@code type} available in {@code pkg}: owned
     * elements first, in {@code ownedElement} order, then imports.
     */
    public static <T extends ModelElement> Stream<T> availableStream(Package pkg, Class<T> type) {
        if (pkg == null || type == null) {
            return Stream.empty();
        }
        return Stream.concat(
                pkg.getOwnedElement().stream(),
                pkg.getImportedElement().stream())
            .filter(type::isInstance).map(type::cast)
            .distinct();
    }

    /** List-returning twin of {@link #availableStream}. */
    public static <T extends ModelElement> List<T> available(Package pkg, Class<T> type) {
        return availableStream(pkg, type).toList();
    }

    /** The first available element of {@code type} named {@code name} — owned elements win over imports. */
    public static <T extends ModelElement> Optional<T> findAvailableByName(Package pkg, Class<T> type, String name) {
        if (name == null) {
            return Optional.empty();
        }
        return availableStream(pkg, type)
                .filter(e -> name.equals(e.getName()))
                .findFirst();
    }
}
