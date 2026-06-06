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

import java.sql.JDBCType;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLDataType;

/**
 * Asserts a column exists (or is absent, per {@link #expectAbsent()}) with the
 * expected SQL type and nullability.
 *
 * @param columnName       column name in the parent table
 * @param expectAbsent     {@code true} → passes when the column is missing
 * @param expectedType     JDBC type name (e.g. {@code "INTEGER"}); empty =
 *                         don't check
 * @param expectedNullable nullable flag; empty = don't check
 */
public record DatabaseColumnCheck(String columnName, boolean expectAbsent, Optional<String> expectedType,
        Optional<Boolean> expectedNullable) {

    public DatabaseColumnCheck(String columnName, String expectedType) {
        this(columnName, false, Optional.ofNullable(expectedType), Optional.empty());
    }

    /**
     * Derives a presence check from a CWM {@link Column}, asserting its declared
     * SQL type where it maps to a {@link JDBCType}.
     */
    public static DatabaseColumnCheck fromCwm(Column cwmColumn) {
        Optional<String> typeName = Optional.empty();
        if (cwmColumn.getType() instanceof SQLDataType sdt) {
            long n = sdt.getTypeNumber();
            if (n != 0) {
                try {
                    typeName = Optional.of(JDBCType.valueOf((int) n).getName());
                } catch (IllegalArgumentException ignore) {
                    // unknown JDBC type code — skip type assertion
                }
            }
            if (typeName.isEmpty() && sdt.getName() != null && !sdt.getName().isBlank()) {
                typeName = Optional.of(sdt.getName());
            }
        }
        return new DatabaseColumnCheck(cwmColumn.getName(), false, typeName, Optional.empty());
    }
}
