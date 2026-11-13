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
package org.eclipse.daanse.cwm.resource.relational.diff.api;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.BooleanExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ProcedureExpression;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ActionOrientationType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ConditionTimingType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.EventManipulationType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;

/** Small models for the offline tests: a catalog, its types, tables with integer and text columns. */
final class TestModels {

    private static final RelationalFactory R = RelationalFactory.eINSTANCE;

    private TestModels() {
    }

    /** A catalog {@code shop} with a schema {@code sales}; the catalog owns the SQL types. */
    static Schema sales() {
        return Schemas.create(Catalogs.create("shop"), "sales");
    }

    /** A further schema in the catalog of {@code schema}. */
    static Schema schema(Schema sibling, String name) {
        return Schemas.create((Catalog) sibling.getNamespace(), name);
    }

    static SQLSimpleType type(Schema schema, String name) {
        Catalog catalog = (Catalog) schema.getNamespace();
        return SQLSimpleTypes.ownedIn(catalog, name);
    }

    /** A table with not-null integer columns of the given names. */
    static Table table(Schema schema, String name, String... integerColumns) {
        Table table = Tables.create(schema, name);
        for (String column : integerColumns) {
            Columns.create(table, column, type(schema, "INTEGER"), NullableType.COLUMN_NO_NULLS);
        }
        return table;
    }

    static Column varchar(Table table, String name, int length) {
        Schema schema = (Schema) table.getNamespace();
        return Columns.create(table, name, type(schema, "VARCHAR"), NullableType.COLUMN_NULLABLE, length, 0, 0);
    }

    static Column column(Table table, String name) {
        return table.getFeature().stream().filter(Column.class::isInstance).map(Column.class::cast)
                .filter(c -> name.equals(c.getName())).findFirst().orElseThrow();
    }

    static Table table(Schema schema, String name) {
        return Schemas.findTable(schema, name).orElseThrow();
    }

    static CheckConstraint check(Table table, String name, String condition) {
        CheckConstraint check = R.createCheckConstraint();
        check.setName(name);
        BooleanExpression body = CoreFactory.eINSTANCE.createBooleanExpression();
        body.setLanguage("SQL");
        body.setBody(condition);
        check.setBody(body);
        table.getOwnedElement().add(check);
        return check;
    }

    /** A row trigger before insert, owned by the schema of {@code table}. */
    static Trigger trigger(Table table, String name, String body) {
        Trigger trigger = R.createTrigger();
        trigger.setName(name);
        trigger.setTable(table);
        trigger.setConditionTiming(ConditionTimingType.BEFORE);
        trigger.setEventManipulation(EventManipulationType.INSERT);
        trigger.setActionOrientation(ActionOrientationType.ROW);
        ProcedureExpression action = CoreFactory.eINSTANCE.createProcedureExpression();
        action.setLanguage("plpgsql");
        action.setBody(body);
        trigger.setActionStatement(action);
        table.getNamespace().getOwnedElement().add(trigger);
        return trigger;
    }

    /** The simple names of the classes of {@code ops}, in order. */
    static List<String> kinds(List<ChangeOp> ops) {
        return ops.stream().map(op -> op.getClass().getSimpleName()).toList();
    }

    static int indexOf(List<ChangeOp> ops, java.util.function.Predicate<ChangeOp> which) {
        for (int i = 0; i < ops.size(); i++) {
            if (which.test(ops.get(i))) {
                return i;
            }
        }
        return -1;
    }
}
