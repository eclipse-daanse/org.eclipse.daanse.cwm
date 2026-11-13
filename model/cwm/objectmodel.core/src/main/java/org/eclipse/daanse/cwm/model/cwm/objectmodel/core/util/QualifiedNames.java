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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Namespace;
import org.eclipse.emf.ecore.EObject;

/**
 * The qualified name of a model element: its name and the names of the model
 * elements that contain it, outermost first, joined by dots
 * ({@code shop.sales.orders.id}).
 *
 * <p>
 * The owners are the containers up the containment tree, not only the
 * {@code namespace} chain, so an attribute (held by {@code Classifier.feature})
 * is named under its class. Containers that are no model element, such as a
 * tagged value, and model elements without a name add no segment. A display
 * format: names are not quoted, so a name that itself holds a dot reads like
 * two segments.
 * </p>
 */
public final class QualifiedNames {

    private static final String SEPARATOR = ".";

    private QualifiedNames() {
    }

    /** The qualified name of {@code element}; empty for {@code null} or when nothing on the way is named. */
    public static String of(EObject element) {
        Deque<String> names = new ArrayDeque<>();
        for (EObject o = element; o != null; o = o.eContainer()) {
            if (o instanceof ModelElement me && isNamed(me)) {
                names.addFirst(me.getName());
            }
        }
        return String.join(SEPARATOR, names);
    }

    /**
     * The element of {@code root}'s containment tree, {@code root} included,
     * with the qualified name {@code qualifiedName}. The name is read first as
     * {@link #of} writes it, that is starting with the names of {@code root}
     * and its own owners, then relative to {@code root}: for a root
     * {@code shop}, both {@code shop.sales.orders} and {@code sales.orders}
     * find the table. Elements without a name are passed through, their
     * children are looked up as if they hung on the element above. When two
     * elements carry the same qualified name, the first in containment order
     * wins.
     */
    public static Optional<ModelElement> find(Namespace root, String qualifiedName) {
        return find(root, qualifiedName, ModelElement.class);
    }

    /** As {@link #find(Namespace, String)}, the first such element that is a {@code type}. */
    public static <T extends ModelElement> Optional<T> find(Namespace root, String qualifiedName, Class<T> type) {
        if (root == null || qualifiedName == null || type == null) {
            return Optional.empty();
        }
        String prefix = of(root);
        Stream<ModelElement> absolute = Stream.empty();
        if (!prefix.isEmpty()) {
            if (qualifiedName.equals(prefix)) {
                absolute = Stream.of(root);
            } else if (qualifiedName.startsWith(prefix + SEPARATOR)) {
                absolute = below(root, qualifiedName.substring(prefix.length() + SEPARATOR.length()));
            }
        }
        return Stream.concat(absolute, below(root, qualifiedName))
                .filter(type::isInstance).map(type::cast)
                .findFirst();
    }

    /** The model elements that contain {@code element}, innermost first, the element itself not included. */
    static Stream<ModelElement> owners(EObject element) {
        if (element == null) {
            return Stream.empty();
        }
        return Stream.iterate(element.eContainer(), o -> o != null, EObject::eContainer)
                .filter(ModelElement.class::isInstance).map(ModelElement.class::cast);
    }

    private static Stream<ModelElement> below(EObject node, String rest) {
        return node.eContents().stream().flatMap(child -> {
            if (!(child instanceof ModelElement me) || !isNamed(me)) {
                return below(child, rest);
            }
            String name = me.getName();
            if (rest.equals(name)) {
                return Stream.of(me);
            }
            if (rest.startsWith(name + SEPARATOR)) {
                return below(me, rest.substring(name.length() + SEPARATOR.length()));
            }
            return Stream.empty();
        });
    }

    private static boolean isNamed(ModelElement element) {
        return element.getName() != null && !element.getName().isEmpty();
    }
}
