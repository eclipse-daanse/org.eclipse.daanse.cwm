/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.resource.relational.ddl.internal.support;

import java.sql.Types;

import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.DatatypesFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.QueryExpression;
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
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ReferentialRuleType;
import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;

/**
 * Shared CWM fixture: CUSTOMERS + ORDERS with PK/UC/CHECK/INDEX/FK and a
 * CUSTOMER_ORDERS view. Triggers are attached separately via
 * {@link #attachTrigger} since their bodies are dialect-specific.
 */
public final class SqlGenFixture {

    public static final RelationalFactory RF = RelationalFactory.eINSTANCE;
    public static final CoreFactory CF = CoreFactory.eINSTANCE;
    public static final DatatypesFactory DF = DatatypesFactory.eINSTANCE;

    public final Schema schema;
    public final Table customers;
    public final Table orders;
    public final View view;
    public final UniqueConstraint uniqueEmail;
    public final ForeignKey ordersFk;
    public final CheckConstraint emailCheck;
    public final SQLIndex nameIndex;

    private SqlGenFixture(Schema schema, Table customers, Table orders, View view, UniqueConstraint uniqueEmail,
            ForeignKey ordersFk, CheckConstraint emailCheck, SQLIndex nameIndex) {
        this.schema = schema;
        this.customers = customers;
        this.orders = orders;
        this.view = view;
        this.uniqueEmail = uniqueEmail;
        this.ordersFk = ordersFk;
        this.emailCheck = emailCheck;
        this.nameIndex = nameIndex;
    }

    public static SqlGenFixture build(String schemaName, Dialect dialect) {
        Schema schema = RF.createSchema();
        schema.setName(schemaName);

        Table customers = RF.createTable();
        customers.setName("CUSTOMERS");
        Column cId = col("ID", type("INTEGER", Types.INTEGER, 0, 0, 0), true);
        Column cEmail = col("EMAIL", type("CHARACTER VARYING", Types.VARCHAR, 100, 0, 0), true);
        Column cName = col("NAME", type("CHARACTER VARYING", Types.VARCHAR, 50, 0, 0), false);
        customers.getFeature().add(cId);
        customers.getFeature().add(cEmail);
        customers.getFeature().add(cName);
        schema.getOwnedElement().add(customers);

        PrimaryKey customersPk = RF.createPrimaryKey();
        customersPk.setName("PK_CUSTOMERS");
        customersPk.getFeature().add(cId);
        cId.getUniqueKey().add(customersPk);

        UniqueConstraint uc = RF.createUniqueConstraint();
        uc.setName("UC_CUSTOMERS_EMAIL");
        uc.getFeature().add(cEmail);
        cEmail.getUniqueKey().add(uc);

        CheckConstraint cc = RF.createCheckConstraint();
        cc.setName("CK_CUSTOMERS_ID_POS");
        BooleanExpression be = CF.createBooleanExpression();
        // A numeric comparison works on every major dialect; LENGTH/LEN/CHAR_LENGTH
        // diverge between PG/MariaDB and MSSQL.
        be.setBody(dialect.quoteIdentifier("ID").toString() + " > 0");
        be.setLanguage("SQL");
        cc.setBody(be);
        customers.getOwnedElement().add(cc);

        SQLIndex idx = RF.createSQLIndex();
        idx.setName("IDX_CUSTOMERS_NAME");
        idx.setSpannedClass(customers);
        SQLIndexColumn ifc = RF.createSQLIndexColumn();
        ifc.setFeature(cName);
        idx.getIndexedFeature().add(ifc);
        schema.getOwnedElement().add(idx);

        Table orders = RF.createTable();
        orders.setName("ORDERS");
        Column oId = col("ID", type("INTEGER", Types.INTEGER, 0, 0, 0), true);
        Column oCustomerId = col("CUSTOMER_ID", type("INTEGER", Types.INTEGER, 0, 0, 0), true);
        Column oTotal = col("TOTAL", type("DECIMAL", Types.DECIMAL, 0, 10, 2), false);
        orders.getFeature().add(oId);
        orders.getFeature().add(oCustomerId);
        orders.getFeature().add(oTotal);
        schema.getOwnedElement().add(orders);

        PrimaryKey ordersPk = RF.createPrimaryKey();
        ordersPk.setName("PK_ORDERS");
        ordersPk.getFeature().add(oId);
        oId.getUniqueKey().add(ordersPk);

        ForeignKey fk = RF.createForeignKey();
        fk.setName("FK_ORDERS_CUSTOMERS");
        fk.getFeature().add(oCustomerId);
        fk.setUniqueKey(customersPk);
        oCustomerId.getKeyRelationship().add(fk);
        fk.setDeleteRule(ReferentialRuleType.IMPORTED_KEY_CASCADE);
        // Oracle FKs don't accept ON UPDATE — emitter respects this on Oracle.
        fk.setUpdateRule(ReferentialRuleType.IMPORTED_KEY_NO_ACTION);

        View view = RF.createView();
        view.setName("CUSTOMER_ORDERS");
        QueryExpression qe = DF.createQueryExpression();
        qe.setLanguage("SQL");
        String custQ = dialect.quoteIdentifier(schemaName, "CUSTOMERS").toString();
        String ordQ = dialect.quoteIdentifier(schemaName, "ORDERS").toString();
        String nameQ = dialect.quoteIdentifier("NAME").toString();
        String totalQ = dialect.quoteIdentifier("TOTAL").toString();
        String custIdQ = dialect.quoteIdentifier("CUSTOMER_ID").toString();
        String idQ = dialect.quoteIdentifier("ID").toString();
        qe.setBody("SELECT C." + nameQ + ", O." + totalQ + " FROM " + custQ + " C JOIN " + ordQ + " O " + "ON O."
                + custIdQ + " = C." + idQ);
        view.setQueryExpression(qe);
        schema.getOwnedElement().add(view);

        return new SqlGenFixture(schema, customers, orders, view, uc, fk, cc, idx);
    }

    /** Attach a {@code BEFORE INSERT FOR EACH ROW} trigger. */
    public org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger attachTrigger(Table table, String triggerName,
            String procedureBody) {
        return attachTrigger(table, triggerName, procedureBody,
                org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ConditionTimingType.BEFORE,
                org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.EventManipulationType.INSERT);
    }

    public org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger attachTrigger(Table table, String triggerName,
            String procedureBody,
            org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ConditionTimingType timing,
            org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.EventManipulationType event) {
        org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger trg = RF.createTrigger();
        trg.setName(triggerName);
        trg.setTable(table);
        trg.setConditionTiming(timing);
        trg.setEventManipulation(event);
        trg.setActionOrientation(
                org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ActionOrientationType.ROW);
        org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ProcedureExpression body = CF.createProcedureExpression();
        body.setLanguage("SQL");
        body.setBody(procedureBody);
        trg.setActionStatement(body);
        table.getTrigger().add(trg);
        return trg;
    }

    private static SQLSimpleType type(String name, int jdbc, long max, long prec, long scale) {
        SQLSimpleType t = RF.createSQLSimpleType();
        t.setName(name);
        t.setTypeNumber(jdbc);
        if (max > 0)
            t.setCharacterMaximumLength(max);
        if (prec > 0)
            t.setNumericPrecision(prec);
        if (scale > 0)
            t.setNumericScale(scale);
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
