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
package org.eclipse.daanse.cwm.util.resource.relational;

import java.util.List;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.daanse.cwm.util.objectmodel.core.Classifiers;

public final class UniqueConstraints {

    private UniqueConstraints() {
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
