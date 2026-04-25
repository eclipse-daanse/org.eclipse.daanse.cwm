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

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Row;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RowSet;
import org.eclipse.daanse.cwm.util.objectmodel.core.Namespaces;

public final class RowSets {

    private RowSets() {
    }

    /** Rows of the row set, in declaration order. */
    public static List<Row> rows(RowSet rowSet) {
        return rowStream(rowSet).toList();
    }

    /** Stream of rows of the row set. */
    public static Stream<Row> rowStream(RowSet rowSet) {
        return Namespaces.ownedElementStream(rowSet, Row.class);
    }
}
