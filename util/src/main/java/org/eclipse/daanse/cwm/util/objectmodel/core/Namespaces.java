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
package org.eclipse.daanse.cwm.util.objectmodel.core;

import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;

public final class Namespaces {

    private Namespaces() {
    }

    /**
     * First ancestor (inclusive) of type {@code T} reached via
     * {@code getNamespace()}.
     */
    public static <T extends Namespace> Optional<T> walkUpTo(Namespace start, Class<T> type) {
        Namespace ns = start;
        while (ns != null) {
            if (type.isInstance(ns)) {
                return Optional.of(type.cast(ns));
            }
            ns = ns.getNamespace();
        }
        return Optional.empty();
    }

    /**
     * Stream of {@code ns.getOwnedElement()} restricted to instances of
     * {@code type}.
     */
    public static <T extends ModelElement> Stream<T> ownedElementStream(Namespace ns, Class<T> type) {
        if (ns == null) {
            return Stream.empty();
        }
        return ns.getOwnedElement().stream().filter(type::isInstance).map(type::cast);
    }

    /**
     * First owned element of the given type with the given name; first match wins.
     */
    public static <T extends ModelElement> Optional<T> findOwnedByName(Namespace ns, Class<T> type, String name) {
        if (ns == null || type == null || name == null) {
            return Optional.empty();
        }
        for (ModelElement me : ns.getOwnedElement()) {
            if (type.isInstance(me) && name.equals(me.getName())) {
                return Optional.of(type.cast(me));
            }
        }
        return Optional.empty();
    }
}
