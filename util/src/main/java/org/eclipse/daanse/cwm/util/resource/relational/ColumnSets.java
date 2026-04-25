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
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ColumnSet;
import org.eclipse.daanse.cwm.util.objectmodel.core.Classifiers;

public final class ColumnSets {

    private ColumnSets() {
    }

    /** Columns of the set in declaration order. */
    public static List<Column> columns(ColumnSet columnSet) {
        return columnStream(columnSet).toList();
    }

    /** Stream of columns in declaration order. */
    public static Stream<Column> columnStream(ColumnSet columnSet) {
        return Classifiers.featureStream(columnSet, Column.class);
    }

    /** Lookup a column by name (case-sensitive). */
    public static Optional<Column> findColumn(ColumnSet columnSet, String name) {
        return Classifiers.findFeatureByName(columnSet, Column.class, name);
    }
}
