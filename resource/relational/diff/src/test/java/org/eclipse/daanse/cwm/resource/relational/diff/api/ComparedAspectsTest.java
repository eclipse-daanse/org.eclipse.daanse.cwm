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

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.daanse.cwm.resource.relational.diff.api.TestModels.column;
import static org.eclipse.daanse.cwm.resource.relational.diff.api.TestModels.table;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.BehavioralFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.Parameter;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.ParameterDirectionKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ProcedureExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.TaggedValues;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndexColumn;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ProcedureType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ReferentialRuleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ForeignKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.PrimaryKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalNames;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLIndexes;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.SchemaDifferImpl;
import org.junit.jupiter.api.Test;

/**
 * What the differ compares beyond names and columns: the rules and the target schema of a foreign
 * key, the filter and the order of an index, and the routines of a schema.
 */
class ComparedAspectsTest {

    private final SchemaDiffer differ = new SchemaDifferImpl();

    @Test
    void aForeignKeyWhoseDeleteRuleChangesIsDroppedAndAdded() {
        Schema oldSchema = orders("sales", ReferentialRuleType.IMPORTED_KEY_NO_ACTION);
        Schema newSchema = orders("sales", ReferentialRuleType.IMPORTED_KEY_CASCADE);

        SchemaDiff diff = differ.diff(oldSchema, newSchema);

        assertThat(diff.tablesChanged()).singleElement().satisfies(td -> {
            assertThat(td.foreignKeysDropped()).hasSize(1);
            assertThat(td.foreignKeysAdded()).hasSize(1);
        });
    }

    @Test
    void aForeignKeyThatRefersToTheTableOfTheSameNameInAnotherSchemaChanges() {
        Schema oldSchema = orders("sales", ReferentialRuleType.IMPORTED_KEY_NO_ACTION);
        Schema newSchema = orders("archive", ReferentialRuleType.IMPORTED_KEY_NO_ACTION);

        SchemaDiff diff = differ.diff(oldSchema, newSchema);

        assertThat(diff.tablesChanged()).singleElement().satisfies(td -> {
            assertThat(td.foreignKeysDropped()).hasSize(1);
            assertThat(td.foreignKeysAdded()).hasSize(1);
        });
    }

    @Test
    void anIndexWhoseFilterChangesIsDroppedAndAdded() {
        Schema oldSchema = indexed(null, true);
        Schema newSchema = indexed("qty > 0", true);

        SchemaDiff diff = differ.diff(oldSchema, newSchema);

        assertThat(diff.tablesChanged()).singleElement().satisfies(td -> {
            assertThat(td.indexesDropped()).hasSize(1);
            assertThat(td.indexesAdded()).hasSize(1);
        });
    }

    @Test
    void anIndexWhoseColumnTurnsDescendingIsDroppedAndAdded() {
        SchemaDiff diff = differ.diff(indexed(null, true), indexed(null, false));

        assertThat(diff.tablesChanged()).singleElement().satisfies(td -> {
            assertThat(td.indexesDropped()).hasSize(1);
            assertThat(td.indexesAdded()).hasSize(1);
        });
    }

    @Test
    void aRoutineThatGoesIsADifference() {
        Schema oldSchema = TestModels.sales();
        procedure(oldSchema, "fn_total");
        Schema newSchema = TestModels.sales();

        assertThat(differ.diff(oldSchema, newSchema).isEmpty()).isFalse();
    }

    /**
     * The specific name a database gives a routine is its own: a model read from one database and
     * the same model written by hand, or read from another, are alike without it.
     */
    @Test
    void theSpecificNameOfARoutineIsNotCompared() {
        Schema oldSchema = TestModels.sales();
        procedure(oldSchema, "fn_total");
        Schema newSchema = TestModels.sales();
        TaggedValues.set(procedure(newSchema, "fn_total"), RelationalNames.SPECIFIC_NAME_TAG, "fn_total_16384");

        assertThat(differ.diff(oldSchema, newSchema).isEmpty()).isTrue();
    }

    /**
     * {@code sales.orders.customer_id} referring to {@code <target>.customer}, where both
     * {@code sales.customer} and {@code archive.customer} exist.
     */
    private static Schema orders(String target, ReferentialRuleType deleteRule) {
        Schema sales = TestModels.sales();
        Schema archive = TestModels.schema(sales, "archive");
        PrimaryKey salesKey = customer(sales);
        PrimaryKey archiveKey = customer(archive);
        Table orders = table(sales, "orders", "id", "customer_id");
        ForeignKey fk = ForeignKeys.create(orders, "fk_orders_customer",
                "archive".equals(target) ? archiveKey : salesKey, column(orders, "customer_id"));
        fk.setDeleteRule(deleteRule);
        return sales;
    }

    private static PrimaryKey customer(Schema schema) {
        Table customer = table(schema, "customer", "id");
        return PrimaryKeys.create(customer, "pk_customer", column(customer, "id"));
    }

    private static Schema indexed(String filter, boolean ascending) {
        Schema sales = TestModels.sales();
        Table orders = table(sales, "orders", "id", "qty");
        SQLIndex index = SQLIndexes.index("ix_orders_qty", false, orders, column(orders, "qty"));
        ((SQLIndexColumn) index.getIndexedFeature().get(0)).setIsAscending(ascending);
        if (filter != null) {
            index.setFilterCondition(filter);
        }
        sales.getOwnedElement().add(index);
        return sales;
    }

    private static Procedure procedure(Schema schema, String name) {
        Procedure procedure = RelationalFactory.eINSTANCE.createProcedure();
        procedure.setName(name);
        procedure.setType(ProcedureType.FUNCTION);
        Parameter amount = BehavioralFactory.eINSTANCE.createParameter();
        amount.setName("amount");
        amount.setKind(ParameterDirectionKind.PDK_IN);
        amount.setType(TestModels.type(schema, "INTEGER"));
        procedure.getParameter().add(amount);
        ProcedureExpression body = CoreFactory.eINSTANCE.createProcedureExpression();
        body.setLanguage("SQL");
        body.setBody("SELECT amount * 2");
        procedure.setBody(body);
        schema.getOwnedElement().add(procedure);
        return procedure;
    }
}
