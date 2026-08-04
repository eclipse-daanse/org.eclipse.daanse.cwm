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
        if (value == null || "NULL".equalsIgnoreCase(value)) {
            // With a type code: an untyped null binds as "unspecified" on pgjdbc,
            // which the server cannot resolve when a column is null in every tuple.
            ps.setNull(index, jdbcType.getVendorTypeNumber());
            return;
        }
            // parseX, not valueOf: valueOf boxes and unboxes again for the
            // primitive setter, allocating once per value.
        switch (jdbcType) {
        case BOOLEAN:
            ps.setBoolean(index, parseBoolean(value));
            break;
        case BIGINT:
            ps.setLong(index, value.isEmpty() ? 0L : Long.parseLong(value));
            break;
        case DATE:
            ps.setDate(index, Date.valueOf(value));
            break;
        case INTEGER:
            ps.setInt(index, value.isEmpty() ? 0 : Integer.parseInt(value));
            break;
        case DECIMAL:
        case NUMERIC:
        case REAL:
        case DOUBLE:
        case FLOAT:
            ps.setDouble(index, value.isEmpty() ? 0.0 : Double.parseDouble(value));
            break;
        case SMALLINT:
            ps.setShort(index, value.isEmpty() ? (short) 0 : Short.parseShort(value));
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

    /**
     * Binds a boolean into a numeric column, as 1 or 0.
     *
     * <p>
     * Most databases here store booleans as a number — see
     * {@code DdlGenerator.booleanTypeName()} — and several refuse
     * {@code setBoolean} against that column. The text is still read as a
     * boolean, so both {@code 1/0} and {@code true/false} arrive correctly.
     */
    public static void setBooleanAsNumber(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null || "NULL".equalsIgnoreCase(value)) {
            ps.setNull(index, JDBCType.SMALLINT.getVendorTypeNumber());
            return;
        }
        // An empty field is false, not null - the same reading the native path
        // gives it, and the same the other types give an empty numeric field.
        ps.setShort(index, parseBoolean(value) ? (short) 1 : (short) 0);
    }

    /**
     * A boolean as a CSV writes it. {@code Boolean.valueOf("1")} is {@code false},
     * so a source that encodes booleans as 1/0 - which the shipped datasets do -
     * would load every one of them as false without this.
     */
    private static boolean parseBoolean(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }
}
