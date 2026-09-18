/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.resource.relational.diff.internal.support;

import java.sql.Types;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.DatatypesFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.QueryExpression;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.sql.dialect.api.Dialect;

/**
 * {@code CUSTOMERS(ID PK, EMAIL, NAME)} with a view {@code CUSTOMER_V} over the
 * given columns; the view's modelled columns match its select list.
 */
public final class ViewFixtures {

    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;

    private ViewFixtures() {
    }

    public static Schema build(String schemaName, Dialect dialect, List<String> viewColumns) {
        Schema schema = RF.createSchema();
        schema.setName(schemaName);
        SQLSimpleType integer = type("INTEGER", Types.INTEGER, 0);
        SQLSimpleType varchar = type("CHARACTER VARYING", Types.VARCHAR, 100);

        Table customers = RF.createTable();
        customers.setName("CUSTOMERS");
        Column id = col("ID", integer, true);
        customers.getFeature().add(id);
        customers.getFeature().add(col("EMAIL", varchar, false));
        customers.getFeature().add(col("NAME", varchar, false));
        schema.getOwnedElement().add(customers);
        PrimaryKey pk = RF.createPrimaryKey();
        pk.setName("PK_CUSTOMERS");
        pk.getFeature().add(id);
        customers.getOwnedElement().add(pk);

        View view = RF.createView();
        view.setName("CUSTOMER_V");
        StringBuilder select = new StringBuilder("SELECT ");
        for (String name : viewColumns) {
            if (select.length() > 7) {
                select.append(", ");
            }
            select.append(dialect.quoteIdentifier(name));
            view.getFeature().add(col(name, "ID".equals(name) ? integer : varchar, false));
        }
        select.append(" FROM ").append(dialect.quoteIdentifier(schemaName, "CUSTOMERS"));
        QueryExpression qe = DatatypesFactory.eINSTANCE.createQueryExpression();
        qe.setLanguage("SQL");
        qe.setBody(select.toString());
        view.setQueryExpression(qe);
        schema.getOwnedElement().add(view);
        return schema;
    }

    private static SQLSimpleType type(String name, int jdbc, long charMax) {
        SQLSimpleType t = RF.createSQLSimpleType();
        t.setName(name);
        t.setTypeNumber(jdbc);
        if (charMax > 0) {
            t.setCharacterMaximumLength(charMax);
        }
        return t;
    }

    private static Column col(String name, SQLSimpleType type, boolean notNull) {
        Column c = RF.createColumn();
        c.setName(name);
        c.setType(type);
        c.setIsNullable(notNull ? NullableType.COLUMN_NO_NULLS : NullableType.COLUMN_NULLABLE);
        return c;
    }
}
