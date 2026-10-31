/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.orm.util;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.StructuralFeature;
import org.eclipse.daanse.cwm.model.daanse.orm.CWMORMFactory;
import org.eclipse.daanse.cwm.model.daanse.orm.CollectionSupport;
import org.eclipse.daanse.cwm.model.daanse.orm.Ordering;

/**
 * Creates the ordering criteria of a collection: a feature path into the target type (the
 * related entity or the element embeddable), ending in an attribute. The name is the path with
 * dots.
 */
public final class Orderings {

    private Orderings() {
    }

    /** Orders ascending by the feature {@code path} of the target type (after earlier criteria). */
    public static Ordering by(CollectionSupport collection, StructuralFeature... path) {
        return add(collection, false, path);
    }

    /** Orders descending by the feature {@code path} of the target type (after earlier criteria). */
    public static Ordering descending(CollectionSupport collection, StructuralFeature... path) {
        return add(collection, true, path);
    }

    private static Ordering add(CollectionSupport collection, boolean descending, StructuralFeature... path) {
        if (path.length == 0) {
            throw new IllegalArgumentException("an ordering needs a feature path");
        }
        Ordering o = CWMORMFactory.eINSTANCE.createOrdering();
        o.getCwmPath().addAll(List.of(path));
        o.setName(OrmBindings.path(o.getCwmPath()));
        o.setDescending(descending);
        collection.getOrderBy().add(o);
        return o;
    }
}
