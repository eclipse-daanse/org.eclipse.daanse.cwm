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
package org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.emf.ecore.EObject;

public final class Namespaces {

    private Namespaces() {
    }

    /**
     * First ancestor (inclusive) of type {@code T} reached via
     * {@code getNamespace()}.
     */
    public static <T extends Namespace> Optional<T> walkUpTo(Namespace start, Class<T> type) {
        if (type == null) {
            return Optional.empty();
        }
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
        if (ns == null || type == null) {
            return Stream.empty();
        }
        return ns.getOwnedElement().stream().filter(type::isInstance).map(type::cast);
    }

    /** List-returning twin of {@link #ownedElementStream}. */
    public static <T extends ModelElement> List<T> ownedElements(Namespace ns, Class<T> type) {
        return ownedElementStream(ns, type).toList();
    }

    /**
     * Depth-first stream of owned elements of {@code type}: direct children of
     * {@code ns} first, then (recursively) the children of every owned element
     * that is an instance of {@code descendInto}. Encounter order follows the
     * {@code ownedElement} list order at each level.
     */
    public static <T extends ModelElement> Stream<T> ownedElementStreamDeep(Namespace ns, Class<T> type,
            Class<? extends Namespace> descendInto) {
        if (ns == null || type == null || descendInto == null) {
            return Stream.empty();
        }
        return ns.getOwnedElement().stream().flatMap(me -> {
            Stream<T> self = type.isInstance(me) ? Stream.of(type.cast(me)) : Stream.empty();
            Stream<T> below = descendInto.isInstance(me)
                    ? ownedElementStreamDeep(descendInto.cast(me), type, descendInto)
                    : Stream.empty();
            return Stream.concat(self, below);
        });
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

    /**
     * The nearest namespace of {@code element} along the <em>containment</em>
     * tree: the element itself if it is a {@link Namespace}, otherwise the
     * closest {@code eContainer()} that is one. Unlike
     * {@link #walkUpTo(Namespace, Class)}, which follows the
     * {@code getNamespace()} chain (the {@code ownedElement} opposite), this
     * walk also covers elements held in other containments — a feature,
     * tagged value or expression has no namespace but does have a container.
     * Empty for a detached element.
     */
    public static Optional<Namespace> nearest(ModelElement element) {
        if (element == null) {
            return Optional.empty();
        }
        if (element instanceof Namespace ns) {
            return Optional.of(ns);
        }
        for (EObject c = element.eContainer(); c != null; c = c.eContainer()) {
            if (c instanceof Namespace ns) {
                return Optional.of(ns);
            }
        }
        return Optional.empty();
    }
}
