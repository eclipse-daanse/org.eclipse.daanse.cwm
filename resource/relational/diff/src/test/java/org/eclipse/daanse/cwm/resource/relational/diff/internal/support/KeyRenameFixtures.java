/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.resource.relational.diff.internal.support;

import java.sql.Types;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.BooleanExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndexColumn;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;

/**
 * CUSTOMERS(ID pk, EMAIL unique, NAME indexed, check ID &gt; 0) and
 * ORDERS(ID pk, CUSTOMER_ID fk → CUSTOMERS). Every index and constraint name
 * carries {@code suffix}, so building the schema twice with different
 * suffixes yields pure index and constraint renames.
 */
public final class KeyRenameFixtures {

    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;

    private KeyRenameFixtures() {
    }

    /** @param checkBody e.g. {@code "ID" > 0}, quoted for the target dialect */
    public static Schema build(String schemaName, String suffix, String checkBody) {
        Schema schema = RF.createSchema();
        schema.setName(schemaName);

        Table customers = table(schema, "CUSTOMERS");
        Column cId = col(customers, "ID", type("INTEGER", Types.INTEGER, 0), true);
        Column cEmail = col(customers, "EMAIL", type("CHARACTER VARYING", Types.VARCHAR, 100), false);
        Column cName = col(customers, "NAME", type("CHARACTER VARYING", Types.VARCHAR, 50), false);
        PrimaryKey pk = RF.createPrimaryKey();
        pk.setName("PK_CUSTOMERS" + suffix);
        pk.getFeature().add(cId);
        customers.getOwnedElement().add(pk);
        UniqueConstraint uq = RF.createUniqueConstraint();
        uq.setName("UQ_CUSTOMERS_EMAIL" + suffix);
        uq.getFeature().add(cEmail);
        customers.getOwnedElement().add(uq);
        CheckConstraint ck = RF.createCheckConstraint();
        ck.setName("CK_CUSTOMERS_ID" + suffix);
        BooleanExpression body = CoreFactory.eINSTANCE.createBooleanExpression();
        body.setLanguage("SQL");
        body.setBody(checkBody);
        ck.setBody(body);
        customers.getOwnedElement().add(ck);
        SQLIndex ix = RF.createSQLIndex();
        ix.setName("IX_CUSTOMERS_NAME" + suffix);
        ix.setSpannedClass(customers);
        SQLIndexColumn ixc = RF.createSQLIndexColumn();
        ixc.setFeature(cName);
        ix.getIndexedFeature().add(ixc);
        schema.getOwnedElement().add(ix);

        Table orders = table(schema, "ORDERS");
        Column oId = col(orders, "ID", type("INTEGER", Types.INTEGER, 0), true);
        Column oCust = col(orders, "CUSTOMER_ID", type("INTEGER", Types.INTEGER, 0), true);
        PrimaryKey opk = RF.createPrimaryKey();
        opk.setName("PK_ORDERS");
        opk.getFeature().add(oId);
        orders.getOwnedElement().add(opk);
        ForeignKey fk = RF.createForeignKey();
        fk.setName("FK_ORDERS_CUSTOMERS" + suffix);
        fk.getFeature().add(oCust);
        fk.setUniqueKey(pk);
        orders.getOwnedElement().add(fk);
        return schema;
    }

    private static Table table(Schema schema, String name) {
        Table t = RF.createTable();
        t.setName(name);
        schema.getOwnedElement().add(t);
        return t;
    }

    private static SQLSimpleType type(String name, int jdbc, long max) {
        SQLSimpleType t = RF.createSQLSimpleType();
        t.setName(name);
        t.setTypeNumber(jdbc);
        if (max > 0) {
            t.setCharacterMaximumLength(max);
        }
        return t;
    }

    private static Column col(Table table, String name, SQLSimpleType type, boolean notNull) {
        Column c = RF.createColumn();
        c.setName(name);
        c.setType(type);
        c.setIsNullable(notNull ? NullableType.COLUMN_NO_NULLS : NullableType.COLUMN_NULLABLE);
        table.getFeature().add(c);
        return c;
    }
}
