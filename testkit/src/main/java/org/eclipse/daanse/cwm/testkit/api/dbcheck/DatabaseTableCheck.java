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
 *   SmartCity Jena, Stefan Bischof - initial
 */
package org.eclipse.daanse.cwm.testkit.api.dbcheck;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.util.resource.relational.ColumnSets;

/**
 * Asserts a table exists (or is absent, per {@link #expectAbsent()}) with the
 * listed columns.
 */
public record DatabaseTableCheck(String tableName, boolean expectAbsent, List<DatabaseColumnCheck> columnChecks) {

    public DatabaseTableCheck {
        columnChecks = List.copyOf(columnChecks);
    }

    public DatabaseTableCheck(String tableName, List<DatabaseColumnCheck> columnChecks) {
        this(tableName, false, columnChecks);
    }

    /**
     * Derives a presence check for every column in the CWM {@link Table}.
     */
    public static DatabaseTableCheck fromCwm(Table cwmTable) {
        List<DatabaseColumnCheck> cols = new ArrayList<>();
        for (Column c : ColumnSets.columns(cwmTable)) {
            cols.add(DatabaseColumnCheck.fromCwm(c));
        }
        return new DatabaseTableCheck(cwmTable.getName(), false, cols);
    }
}
