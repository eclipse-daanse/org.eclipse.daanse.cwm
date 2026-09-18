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

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ProcedureExpression;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ActionOrientationType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ConditionTimingType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.EventManipulationType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;

/** CUSTOMERS(ID pk, NAME), optionally with one row trigger TRG_CUSTOMERS_AUDIT. */
public final class TriggerFixtures {

    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;

    private TriggerFixtures() {
    }

    /** @param triggerBody {@code null} builds the table without a trigger */
    public static Schema build(String schemaName, String triggerBody, ConditionTimingType timing,
            EventManipulationType event) {
        Schema schema = RF.createSchema();
        schema.setName(schemaName);
        Table customers = RF.createTable();
        customers.setName("CUSTOMERS");
        schema.getOwnedElement().add(customers);
        Column id = col(customers, "ID", type("INTEGER", Types.INTEGER, 0), true);
        col(customers, "NAME", type("CHARACTER VARYING", Types.VARCHAR, 50), false);
        PrimaryKey pk = RF.createPrimaryKey();
        pk.setName("PK_CUSTOMERS");
        pk.getFeature().add(id);
        customers.getOwnedElement().add(pk);
        if (triggerBody != null) {
            Trigger trg = RF.createTrigger();
            trg.setName("TRG_CUSTOMERS_AUDIT");
            trg.setConditionTiming(timing);
            trg.setEventManipulation(event);
            trg.setActionOrientation(ActionOrientationType.ROW);
            ProcedureExpression body = CoreFactory.eINSTANCE.createProcedureExpression();
            body.setLanguage("SQL");
            body.setBody(triggerBody);
            trg.setActionStatement(body);
            customers.getTrigger().add(trg);
            schema.getOwnedElement().add(trg);
        }
        return schema;
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
