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
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.cwm.resource.relational.sql.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.DatatypesFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.QueryExpression;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.QueryColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.resource.relational.sql.resolve.api.ColumnUsage;
import org.eclipse.daanse.cwm.resource.relational.sql.resolve.api.Failure;
import org.eclipse.daanse.cwm.resource.relational.sql.resolve.api.Resolution;
import org.eclipse.daanse.cwm.resource.relational.sql.resolve.api.SqlResolver;
import org.eclipse.daanse.cwm.resource.relational.sql.resolve.api.Validation;
import org.eclipse.daanse.cwm.resource.relational.sql.resolve.internal.SqlResolverFactoryImpl;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class SqlResolverTest {

    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;
    private static final DatatypesFactory DF = DatatypesFactory.eINSTANCE;

    /**
     * Builds the {@code sales} schema used by most tests: customer(id, name, email)
     * and order_version(id, customer_id, total).
     */
    private static Schema buildSalesSchema() {
        Schema sales = RF.createSchema();
        sales.setName("sales");

        Table customer = RF.createTable();
        customer.setName("customer");
        customer.getFeature().add(column("id"));
        customer.getFeature().add(column("name"));
        customer.getFeature().add(column("email"));
        sales.getOwnedElement().add(customer);

        Table order = RF.createTable();
        order.setName("order_version");
        order.getFeature().add(column("id"));
        order.getFeature().add(column("customer_id"));
        order.getFeature().add(column("total"));
        sales.getOwnedElement().add(order);

        return sales;
    }

    private static Column column(String name) {
        Column c = RF.createColumn();
        c.setName(name);
        return c;
    }

    @Test
    void resolvesAcrossJoin() {
        Schema sales = buildSalesSchema();
        SqlResolver r = new SqlResolverFactoryImpl().create(List.of(sales));
        Resolution res = r.resolve("select c.name, o.total from customer c "
                + "join order_version o on c.id = o.customer_id " + "where c.email like '%@example.org'");

        assertTrue(res.ok(), () -> "expected ok, got: " + res.message());
        assertTrue(res.failure().isEmpty());
        // Five distinct underlying columns: customer.name, order.total,
        // customer.id, order.customer_id, customer.email.
        assertEquals(5, res.columnsUsed().size(), () -> res.columnsUsed().toString());
        assertEquals(2, res.tablesUsed().size());
        assertEquals(2, res.producedColumns().size());

        // Per-clause classification.
        Column emailCol = findByName(res.columnsUsed(), "email");
        assertNotNull(emailCol);
        assertTrue(res.columnUsage().get(emailCol).contains(ColumnUsage.WHERE));

        Column custIdCol = findByNameInTable(res.columnsUsed(), "id", "customer");
        assertNotNull(custIdCol);
        assertTrue(res.columnUsage().get(custIdCol).contains(ColumnUsage.JOIN));

        Column nameCol = findByName(res.columnsUsed(), "name");
        assertNotNull(nameCol);
        assertTrue(res.columnUsage().get(nameCol).contains(ColumnUsage.SELECT));
    }

    @Disabled("ORDER BY columns require the upstream jsqltranspiler fix "
            + "(JSQLResolver now records getOrderByColumns()); re-enable once that release is in use")
    @Test
    void classifiesOrderByColumns() {
        Schema sales = buildSalesSchema();
        SqlResolver r = new SqlResolverFactoryImpl().create(List.of(sales));
        Resolution res = r.resolve("select c.id from customer c where c.email = 'a' order by c.name");

        assertTrue(res.ok(), () -> "expected ok, got: " + res.message());
        Column nameCol = findByName(res.columnsUsed(), "name");
        assertNotNull(nameCol);
        assertTrue(res.columnUsage().get(nameCol).contains(ColumnUsage.ORDER_BY));
        assertEquals(List.of(nameCol), res.clauseColumns().get(ColumnUsage.ORDER_BY));
    }

    @Test
    void unknownColumn() {
        Schema sales = buildSalesSchema();
        SqlResolver r = new SqlResolverFactoryImpl().create(List.of(sales));
        Resolution res = r.resolve("select c.no_such_col from customer c");
        assertFalse(res.ok());
        assertEquals(Failure.UNKNOWN_COLUMN, res.failure().orElse(null));
    }

    @Test
    void unknownTable() {
        Schema sales = buildSalesSchema();
        SqlResolver r = new SqlResolverFactoryImpl().create(List.of(sales));
        Resolution res = r.resolve("select x from no_such_table");
        assertFalse(res.ok());
        assertEquals(Failure.UNKNOWN_TABLE, res.failure().orElse(null));
    }

    @Test
    void emptyBody() {
        SqlResolver r = new SqlResolverFactoryImpl().create(List.of(buildSalesSchema()));
        Resolution res = r.resolve("   ");
        assertFalse(res.ok());
        assertEquals(Failure.EMPTY, res.failure().orElse(null));
    }

    @Test
    void nonSqlLanguageFails() {
        SqlResolver r = new SqlResolverFactoryImpl().create(List.of(buildSalesSchema()));
        QueryExpression qe = DF.createQueryExpression();
        qe.setLanguage("MDX");
        qe.setBody("select [Measures] on 0 from [Cube]");
        Resolution res = r.resolve(qe);
        assertFalse(res.ok());
        assertEquals(Failure.NON_SQL_LANGUAGE, res.failure().orElse(null));
    }

    @Test
    void resolvesQueryColumnSet() {
        Schema sales = buildSalesSchema();
        QueryColumnSet qcs = RF.createQueryColumnSet();
        qcs.setName("customer_summary");
        // Declare two output columns.
        qcs.getFeature().add(column("name"));
        qcs.getFeature().add(column("total"));
        sales.getOwnedElement().add(qcs);

        QueryExpression qe = DF.createQueryExpression();
        qe.setLanguage("SQL");
        qe.setBody("select c.name, o.total from customer c " + "join order_version o on c.id = o.customer_id");
        qcs.setQuery(qe);

        SqlResolver r = new SqlResolverFactoryImpl().create(List.of(sales));
        Resolution res = r.resolve(qcs);
        assertTrue(res.ok(), () -> "expected ok, got: " + res.message());
        assertEquals(2, res.producedColumns().size());

        Validation v = r.validate(qcs, res);
        assertTrue(v.ok(), () -> "expected validation ok; missingDeclared=" + v.missingDeclared() + " missingProduced="
                + v.missingProduced() + " outOfOrder=" + v.outOfOrder());
    }

    @Test
    void validationFlagsDriftedQueryColumnSet() {
        Schema sales = buildSalesSchema();
        QueryColumnSet qcs = RF.createQueryColumnSet();
        qcs.setName("drifted");
        // Declared columns disagree with the query: declared=[name, email],
        // produced=[name, total].
        qcs.getFeature().add(column("name"));
        qcs.getFeature().add(column("email"));
        sales.getOwnedElement().add(qcs);

        QueryExpression qe = DF.createQueryExpression();
        qe.setBody("select c.name, o.total from customer c " + "join order_version o on c.id = o.customer_id");
        qcs.setQuery(qe);

        SqlResolver r = new SqlResolverFactoryImpl().create(List.of(sales));
        Resolution res = r.resolve(qcs);
        assertTrue(res.ok());
        Validation v = r.validate(qcs, res);
        assertFalse(v.ok());
        assertTrue(v.missingDeclared().contains("total"));
        assertTrue(v.missingProduced().contains("email"));
    }

    @Test
    void resolvesViewQueryExpression() {
        Schema sales = buildSalesSchema();
        View view = RF.createView();
        view.setName("v_customer_orders");
        QueryExpression qe = DF.createQueryExpression();
        qe.setBody("select c.name, o.total from customer c " + "join order_version o on c.id = o.customer_id");
        view.setQueryExpression(qe);
        sales.getOwnedElement().add(view);

        SqlResolver r = new SqlResolverFactoryImpl().create(List.of(sales));
        Resolution res = r.resolve(view.getQueryExpression());
        assertTrue(res.ok(), () -> res.message());
        assertEquals(2, res.producedColumns().size());
    }

    @Test
    void parseFailureClassified() {
        SqlResolver r = new SqlResolverFactoryImpl().create(List.of(buildSalesSchema()));
        Resolution res = r.resolve("select select from from");
        assertFalse(res.ok());
        assertEquals(Failure.PARSE, res.failure().orElse(null));
        assertNull(res.rewrittenSql());
    }


    private static Column findByName(java.util.Collection<Column> cs, String name) {
        for (Column c : cs)
            if (name.equals(c.getName()))
                return c;
        return null;
    }

    private static Column findByNameInTable(java.util.Collection<Column> cs, String colName, String tableName) {
        for (Column c : cs) {
            if (!colName.equals(c.getName()))
                continue;
            var owner = org.eclipse.daanse.cwm.util.resource.relational.Columns.namedOwner(c).orElse(null);
            if (owner != null && tableName.equals(owner.getName()))
                return c;
        }
        return null;
    }
}
