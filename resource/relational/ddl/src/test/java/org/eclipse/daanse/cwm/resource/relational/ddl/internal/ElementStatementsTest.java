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
package org.eclipse.daanse.cwm.resource.relational.ddl.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.BooleanExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ProcedureExpression;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ActionOrientationType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ConditionTimingType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.EventManipulationType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ForeignKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.PrimaryKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLIndexes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Views;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlGenerator;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlStatement;
import org.eclipse.daanse.sql.dialect.db.duckdb.DuckDbDialect;
import org.eclipse.daanse.sql.dialect.db.postgresql.PostgreSqlDialect;
import org.junit.jupiter.api.Test;

/**
 * The statements of one element alone are those the script writes for it, by the same code: what a
 * migration that adds or drops the element writes is what the create or drop script would.
 */
class ElementStatementsTest {

    @Test
    void eachElementAloneGetsTheStatementsTheScriptGivesIt() {
        for (DdlGenerator generator : List.of(new DdlGeneratorImpl(new PostgreSqlDialect()),
                new DdlGeneratorImpl(new PostgreSqlDialect(), DdlSettings.defaults().withIfNotExists(false)
                        .withIncludeSchema(false).withCascade(true)),
                new DdlGeneratorImpl(new DuckDbDialect()))) {
            Schema sales = sales();

            Map<ModelElement, List<String>> created = byElement(generator.create(List.of(sales)));
            Map<ModelElement, List<String>> dropped = byElement(generator.drop(List.of(sales)));

            assertThat(created).isNotEmpty();
            created.forEach((element, sql) -> assertThat(DdlStatement.sql(generator.createElement(element)))
                    .as("create %s", element).isEqualTo(sql));
            dropped.forEach((element, sql) -> assertThat(DdlStatement.sql(generator.dropElement(element)))
                    .as("drop %s", element).isEqualTo(sql));
        }
    }

    @Test
    void whatTheDialectDeclaresInCreateTableHasNoStatementsOfItsOwn() {
        Schema sales = sales();
        Table orders = Schemas.findTable(sales, "orders").orElseThrow();
        DdlGenerator duckdb = new DdlGeneratorImpl(new DuckDbDialect());
        DdlGenerator postgres = new DdlGeneratorImpl(new PostgreSqlDialect());

        for (ModelElement constraint : constraints(orders)) {
            assertThat(duckdb.inCreateTable(constraint)).as("%s", constraint).isTrue();
            assertThat(duckdb.createElement(constraint)).isEmpty();
            assertThat(duckdb.dropElement(constraint)).isEmpty();
            assertThat(postgres.inCreateTable(constraint)).isEqualTo(constraint instanceof PrimaryKey);
        }
        assertThat(duckdb.createElement(orders)).singleElement().asString().contains("FOREIGN KEY", "UNIQUE", "CHECK");
        assertThatThrownBy(() -> postgres.createElement(Catalogs.create("x"))).isInstanceOf(
                IllegalArgumentException.class);
    }

    @Test
    void aCommentIsSetAndRemovedOnATableOrColumn() {
        Schema sales = sales();
        Table orders = Schemas.findTable(sales, "orders").orElseThrow();
        DdlGenerator postgres = new DdlGeneratorImpl(new PostgreSqlDialect());

        assertThat(postgres.comment(orders, "What was ordered")).hasValueSatisfying(
                sql -> assertThat(sql).startsWith("COMMENT ON TABLE \"sales\".\"orders\"").contains("What was ordered"));
        assertThat(postgres.comment(orders.getFeature().get(0), null))
                .hasValueSatisfying(sql -> assertThat(sql).startsWith("COMMENT ON COLUMN").contains("NULL"));
    }

    private static Map<ModelElement, List<String>> byElement(List<DdlStatement> statements) {
        Map<ModelElement, List<String>> out = new LinkedHashMap<>();
        statements.forEach(s -> out.computeIfAbsent(s.element(), k -> new ArrayList<>()).add(s.sql()));
        return out;
    }

    private static List<ModelElement> constraints(Table table) {
        List<ModelElement> out = new ArrayList<>(Tables.uniqueConstraints(table));
        out.addAll(Tables.checkConstraints(table));
        out.addAll(Tables.foreignKeys(table));
        return out;
    }

    /**
     * {@code sales.customer(id PK)}, {@code sales.orders(id PK, customer_id → customer, code UNIQUE,
     * qty CHECK)} commented, an unnamed index, a view and a trigger.
     */
    private static Schema sales() {
        Catalog shop = Catalogs.create("shop");
        Schema sales = Schemas.create(shop, "sales");
        Table customer = Tables.create(sales, "customer");
        Column customerId = Columns.create(customer, "id", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        PrimaryKey customerKey = PrimaryKeys.create(customer, "pk_customer", customerId);
        Table orders = Tables.create(sales, "orders");
        Column id = Columns.create(orders, "id", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        Column ordersCustomer = Columns.create(orders, "customer_id", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        Column code = Columns.create(orders, "code", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        Column qty = Columns.create(orders, "qty", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        PrimaryKeys.create(orders, "pk_orders", id);
        ForeignKeys.create(orders, "fk_orders_customer", customerKey, ordersCustomer);
        UniqueConstraint unique = RelationalFactory.eINSTANCE.createUniqueConstraint();
        unique.getFeature().add(code);
        orders.getOwnedElement().add(unique);
        CheckConstraint check = RelationalFactory.eINSTANCE.createCheckConstraint();
        BooleanExpression condition = CoreFactory.eINSTANCE.createBooleanExpression();
        condition.setLanguage("SQL");
        condition.setBody("qty > 0");
        check.setBody(condition);
        orders.getOwnedElement().add(check);
        sales.getOwnedElement().add(SQLIndexes.index(null, false, orders, qty));
        Descriptions.describe(orders, Descriptions.JDBC_REMARKS, null, "What was ordered");
        Descriptions.describe(qty, Descriptions.JDBC_REMARKS, null, "How many");
        Views.create(sales, "v_orders", "SELECT id FROM sales.orders");
        Trigger trigger = RelationalFactory.eINSTANCE.createTrigger();
        trigger.setName("trg_orders_audit");
        trigger.setTable(orders);
        trigger.setConditionTiming(ConditionTimingType.BEFORE);
        trigger.setEventManipulation(EventManipulationType.INSERT);
        trigger.setActionOrientation(ActionOrientationType.ROW);
        ProcedureExpression body = CoreFactory.eINSTANCE.createProcedureExpression();
        body.setLanguage("plpgsql");
        body.setBody("BEGIN RETURN NEW; END;");
        trigger.setActionStatement(body);
        sales.getOwnedElement().add(trigger);
        return sales;
    }
}
