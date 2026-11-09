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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.data.sink.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * A NULL of a national character column is bound with the plain type: pgjdbc refuses NCHAR, NVARCHAR, LONGNVARCHAR
 * and NCLOB in setNull ("Unknown Types value."), and Adventure Works declares its text columns nvarchar. Every other
 * type keeps its own code, which pgjdbc needs to resolve a column that is NULL in every tuple.
 */
class ANullIsBoundWithAPlainTypeTest {

    /** The type codes of the setNull calls a statement received. */
    private static List<Integer> nullTypes(JDBCType type) throws Exception {
        List<Integer> types = new ArrayList<>();
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
                ANullIsBoundWithAPlainTypeTest.class.getClassLoader(), new Class<?>[] { PreparedStatement.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("setNull")) {
                        types.add((Integer) args[1]);
                    }
                    return null;
                });
        TypeConverter.setTypedValue(statement, 1, type, null);
        TypeConverter.setTypedValue(statement, 2, type, "NULL");
        return types;
    }

    @Test
    void aNationalTypeIsBoundAsItsPlainOne() throws Exception {
        assertThat(nullTypes(JDBCType.NVARCHAR)).containsExactly(Types.VARCHAR, Types.VARCHAR);
        assertThat(nullTypes(JDBCType.NCHAR)).containsExactly(Types.CHAR, Types.CHAR);
        assertThat(nullTypes(JDBCType.LONGNVARCHAR)).containsExactly(Types.LONGVARCHAR, Types.LONGVARCHAR);
        assertThat(nullTypes(JDBCType.NCLOB)).containsExactly(Types.CLOB, Types.CLOB);
    }

    @Test
    void anyOtherTypeKeepsItsCode() throws Exception {
        assertThat(nullTypes(JDBCType.INTEGER)).containsExactly(Types.INTEGER, Types.INTEGER);
        assertThat(nullTypes(JDBCType.VARCHAR)).containsExactly(Types.VARCHAR, Types.VARCHAR);
        assertThat(nullTypes(JDBCType.TIMESTAMP)).containsExactly(Types.TIMESTAMP, Types.TIMESTAMP);
    }
}
