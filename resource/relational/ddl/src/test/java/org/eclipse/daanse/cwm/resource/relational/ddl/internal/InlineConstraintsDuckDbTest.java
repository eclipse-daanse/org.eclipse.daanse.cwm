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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.BooleanExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ForeignKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.PrimaryKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.UniqueConstraints;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlGenerator;
import org.eclipse.daanse.sql.dialect.db.duckdb.DuckDbDialect;
import org.junit.jupiter.api.Test;

/**
 * DuckDB cannot add a foreign key or a check to an existing table, nor a unique
 * constraint once another table refers to it: the generator declares them in
 * CREATE TABLE, creates the referenced table first, and drops the referencing
 * table first.
 */
class InlineConstraintsDuckDbTest {

    @Test
    void constraints_inline_inDependencyOrder() throws SQLException {
        Catalog shop = Catalogs.create("SHOP");
        Schema sales = Schemas.create(shop, "SALES");
        Table orders = Tables.create(sales, "ORDERS"); // listed before the table it references
        Column orderId = Columns.create(orders, "ID", SQLSimpleTypes.ownedIn(shop, "BIGINT"));
        Column customerId = Columns.create(orders, "CUSTOMER_ID", SQLSimpleTypes.ownedIn(shop, "BIGINT"));
        PrimaryKeys.create(orders, "PK_ORDERS", orderId);
        Table customer = Tables.create(sales, "CUSTOMER");
        Column id = Columns.create(customer, "ID", SQLSimpleTypes.ownedIn(shop, "BIGINT"));
        Column email = Columns.create(customer, "EMAIL", SQLSimpleTypes.ownedIn(shop, "VARCHAR"));
        PrimaryKey pk = PrimaryKeys.create(customer, "PK_CUSTOMER", id);
        UniqueConstraints.create(customer, "UC_CUSTOMER_EMAIL", email);
        ForeignKeys.create(orders, "FK_ORDERS_CUSTOMER", pk, customerId);
        CheckConstraint positive = RelationalFactory.eINSTANCE.createCheckConstraint();
        positive.setName("CK_ORDERS_ID");
        BooleanExpression condition = CoreFactory.eINSTANCE.createBooleanExpression();
        condition.setBody("\"ID\" > 0");
        condition.setLanguage("SQL");
        positive.setBody(condition);
        orders.getOwnedElement().add(positive);

        DdlGenerator generator = new DdlGeneratorFactoryImpl().create(new DuckDbDialect());
        List<String> create = generator.createSchema(sales);

        assertThat(create).noneMatch(sql -> sql.startsWith("ALTER TABLE"));
        assertThat(String.join("\n", create).indexOf("\"CUSTOMER\" (")).isLessThan(
                String.join("\n", create).indexOf("\"ORDERS\" ("));
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:"); Statement st = c.createStatement()) {
            for (String sql : create) {
                st.execute(sql);
            }
            st.execute("INSERT INTO \"SALES\".\"CUSTOMER\" VALUES (1, 'a@x')");
            st.execute("INSERT INTO \"SALES\".\"ORDERS\" VALUES (10, 1)");
            assertThrows(SQLException.class, () -> st.execute("INSERT INTO \"SALES\".\"ORDERS\" VALUES (11, 9)"));
            assertThrows(SQLException.class, () -> st.execute("INSERT INTO \"SALES\".\"CUSTOMER\" VALUES (2, 'a@x')"));
            assertThrows(SQLException.class, () -> st.execute("INSERT INTO \"SALES\".\"ORDERS\" VALUES (-1, 1)"));
        }
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:"); Statement st = c.createStatement()) {
            for (String sql : create) {
                st.execute(sql);
            }
            for (String sql : generator.dropSchema(sales)) {
                st.execute(sql);
            }
        }
    }
}
