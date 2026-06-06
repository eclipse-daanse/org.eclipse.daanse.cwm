/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.cwm.data.sink.jdbc;

import java.sql.Date;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;

/**
 * Utility for setting typed values on a PreparedStatement from raw String
 * input.
 */
public class TypeConverter {

    private TypeConverter() {
    }

    /**
     * Sets a typed value on a PreparedStatement at the given index.
     *
     * @param ps       the prepared statement
     * @param index    the parameter index (1-based)
     * @param jdbcType the target JDBC type
     * @param value    the raw string value (may be null)
     * @throws SQLException if setting the value fails
     */
    public static void setTypedValue(PreparedStatement ps, int index, JDBCType jdbcType, String value)
            throws SQLException {
        if (value == null) {
            ps.setObject(index, null);
            return;
        }

        switch (jdbcType) {
        case BOOLEAN:
            ps.setBoolean(index, value.isEmpty() ? Boolean.FALSE : Boolean.valueOf(value));
            break;
        case BIGINT:
            ps.setLong(index, value.isEmpty() ? 0L : Long.valueOf(value));
            break;
        case DATE:
            ps.setDate(index, Date.valueOf(value));
            break;
        case INTEGER:
            ps.setInt(index, value.isEmpty() ? 0 : Integer.valueOf(value));
            break;
        case DECIMAL:
        case NUMERIC:
        case REAL:
        case DOUBLE:
        case FLOAT:
            ps.setDouble(index, value.isEmpty() ? 0.0 : Double.valueOf(value));
            break;
        case SMALLINT:
            ps.setShort(index, value.isEmpty() ? (short) 0 : Short.valueOf(value));
            break;
        case TIMESTAMP:
            ps.setTimestamp(index, Timestamp.valueOf(value));
            break;
        case TIME:
            ps.setTime(index, Time.valueOf(value));
            break;
        case VARCHAR:
        case CHAR:
        case LONGVARCHAR:
        case NVARCHAR:
        case NCHAR:
            ps.setString(index, value);
            break;
        default:
            ps.setString(index, value);
            break;
        }
    }
}
