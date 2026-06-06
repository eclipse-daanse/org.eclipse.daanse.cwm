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

import java.util.List;

/**
 * A SQL query run directly against the {@code DataSource} via plain JDBC, with
 * optional expectations on row count, column count and cell values.
 *
 * @param name                display name for reports
 * @param query               SQL to execute
 * @param expectedRowCount    expected rows; {@code -1} = don't check
 * @param expectedColumnCount expected columns; {@code -1} = don't check
 * @param cellChecks          per-cell value expectations
 */
public record DatabaseQueryCheck(String name, String query, int expectedRowCount, int expectedColumnCount,
        List<DatabaseCellCheck> cellChecks) {

    public DatabaseQueryCheck {
        cellChecks = List.copyOf(cellChecks);
    }

    public DatabaseQueryCheck(String name, String query, List<DatabaseCellCheck> cellChecks) {
        this(name, query, -1, -1, cellChecks);
    }
}
