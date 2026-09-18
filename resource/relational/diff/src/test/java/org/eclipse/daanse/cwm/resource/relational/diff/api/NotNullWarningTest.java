/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.resource.relational.diff.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Types;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Expression;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.SchemaDifferImpl;
import org.junit.jupiter.api.Test;

/** Changes that fail on a table with rows are reported by the planner, offline. */
class NotNullWarningTest {

    private static final RelationalFactory R = RelationalFactory.eINSTANCE;

    private final SchemaDiffer differ = new SchemaDifferImpl();

    @Test
    void addedNotNullColumnWithoutDefaultIsReported() {
        Schema newSchema = schema(false);
        column(table(newSchema), "REGION", false, null);

        assertThat(warnings(schema(false), newSchema)).singleElement().asString()
                .startsWith("sales.CUSTOMERS.REGION: NOT NULL column added without a default");
    }

    @Test
    void addedNotNullColumnWithDefaultOrNullableIsFine() {
        Schema withDefault = schema(false);
        column(table(withDefault), "REGION", false, "'EU'");
        Schema nullable = schema(false);
        column(table(nullable), "REGION", true, null);

        assertThat(warnings(schema(false), withDefault)).isEmpty();
        assertThat(warnings(schema(false), nullable)).isEmpty();
    }

    @Test
    void columnMadeNotNullIsReported() {
        assertThat(warnings(schema(true), schema(false))).singleElement().asString()
                .startsWith("sales.CUSTOMERS.EMAIL: column made NOT NULL");
        assertThat(warnings(schema(false), schema(true))).isEmpty();
    }

    private List<String> warnings(Schema oldSchema, Schema newSchema) {
        return ChangePlanner.create().warnings(differ.diff(oldSchema, newSchema));
    }

    private static Schema schema(boolean emailNullable) {
        Schema s = R.createSchema();
        s.setName("sales");
        Table t = R.createTable();
        t.setName("CUSTOMERS");
        s.getOwnedElement().add(t);
        column(t, "ID", false, null);
        column(t, "EMAIL", emailNullable, null);
        return s;
    }

    private static Table table(Schema s) {
        return (Table) s.getOwnedElement().get(0);
    }

    private static void column(Table t, String name, boolean nullable, String defaultValue) {
        SQLSimpleType varchar = R.createSQLSimpleType();
        varchar.setName("VARCHAR");
        varchar.setTypeNumber(Types.VARCHAR);
        Column c = R.createColumn();
        c.setName(name);
        c.setType(varchar);
        c.setIsNullable(nullable ? NullableType.COLUMN_NULLABLE : NullableType.COLUMN_NO_NULLS);
        if (defaultValue != null) {
            Expression e = CoreFactory.eINSTANCE.createExpression();
            e.setLanguage("SQL");
            e.setBody(defaultValue);
            c.setInitialValue(e);
        }
        t.getFeature().add(c);
    }
}
