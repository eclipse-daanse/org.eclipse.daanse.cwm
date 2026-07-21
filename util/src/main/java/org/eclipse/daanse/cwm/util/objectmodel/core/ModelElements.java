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
package org.eclipse.daanse.cwm.util.objectmodel.core;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;

public final class ModelElements {

    private ModelElements() {
    }

    /** Stream of a many-valued reference's values; empty for a {@code null} holder. */
    public static <H, T> Stream<T> referenceStream(H holder, Function<H, ? extends List<T>> reference) {
        return holder == null ? Stream.empty() : reference.apply(holder).stream();
    }

    /** List-returning twin of {@link #referenceStream}. */
    public static <H, T> List<T> references(H holder, Function<H, ? extends List<T>> reference) {
        return referenceStream(holder, reference).toList();
    }

    /**
     * First result of {@code lookup} on {@code element} accepted by {@code accept}, else the
     * same on the immediately enclosing namespace. Only that one level; use
     * {@link Namespaces#walkUpTo} for the whole chain.
     */
    public static <T> Optional<T> findInherited(ModelElement element,
            Function<ModelElement, Stream<T>> lookup, Predicate<T> accept) {
        if (element == null) {
            return Optional.empty();
        }
        Optional<T> own = lookup.apply(element).filter(accept).findFirst();
        if (own.isPresent()) {
            return own;
        }
        Namespace namespace = element.getNamespace();
        return namespace == null ? Optional.empty() : lookup.apply(namespace).filter(accept).findFirst();
    }

    /** Whether {@code holder} appears in its own reference. */
    public static <H extends ModelElement> boolean isSelfReferencing(H holder,
            Function<H, ? extends List<? extends ModelElement>> reference) {
        return holder != null && reference.apply(holder).contains(holder);
    }

    /**
     * Elements of the given type owned by {@code namespace} whose {@code reference}
     * is empty.
     */
    public static <T extends ModelElement> Stream<T> unreferencedStream(Namespace namespace, Class<T> type,
            Function<T, ? extends List<?>> reference) {
        return Namespaces.ownedElementStream(namespace, type).filter(t -> reference.apply(t).isEmpty());
    }
}
