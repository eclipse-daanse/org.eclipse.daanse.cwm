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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Classifiers;

public final class UniqueConstraints {

    private UniqueConstraints() {
    }

    /**
     * A UniqueConstraint over {@code columns}, owned by {@code parent} via
     * {@code ownedElement}. Creation rule: see
     * {@code objectmodel.core.util.Models}.
     */
    public static UniqueConstraint create(Table parent, String name, Column... columns) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(name, "name");
        UniqueConstraint uc = RelationalFactory.eINSTANCE.createUniqueConstraint();
        uc.setName(name);
        uc.getFeature().addAll(Arrays.asList(columns));
        parent.getOwnedElement().add(uc);
        return uc;
    }

    /** Columns covered by the constraint, in declared order. */
    public static List<Column> columns(UniqueConstraint uc) {
        return columnStream(uc).toList();
    }

    /** Stream of columns covered by the constraint. */
    public static Stream<Column> columnStream(UniqueConstraint uc) {
        return Classifiers.featureStream(uc == null ? null : uc.getFeature(), Column.class);
    }
}
