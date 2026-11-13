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
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CorePackage;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Stereotype;

/**
 * Reads which {@code core::Stereotype}s brand a model element. Only the
 * stereotype names its elements ({@code Stereotype.extendedElement}); the
 * element side is an association end without a reference, so it is answered
 * through {@link InverseReferences}: in constant time with an installed index,
 * otherwise by a sweep of the model.
 */
public final class Stereotypes {

    private Stereotypes() {
    }

    /** The stereotypes that extend {@code element}, by name. */
    public static Stream<Stereotype> ofStream(ModelElement element) {
        return InverseReferences.referencing(element, CorePackage.Literals.STEREOTYPE__EXTENDED_ELEMENT,
                Stereotype.class);
    }

    /** List-returning twin of {@link #ofStream}. */
    public static List<Stereotype> of(ModelElement element) {
        return ofStream(element).toList();
    }

    /** Whether a stereotype named {@code name} extends {@code element}. */
    public static boolean has(ModelElement element, String name) {
        if (name == null) {
            return false;
        }
        return ofStream(element).anyMatch(s -> name.equals(s.getName()));
    }
}
