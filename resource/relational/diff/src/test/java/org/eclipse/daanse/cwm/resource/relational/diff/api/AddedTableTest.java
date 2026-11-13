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
import static org.eclipse.daanse.cwm.resource.relational.diff.api.TestModels.check;
import static org.eclipse.daanse.cwm.resource.relational.diff.api.TestModels.column;
import static org.eclipse.daanse.cwm.resource.relational.diff.api.TestModels.table;
import static org.eclipse.daanse.cwm.resource.relational.diff.api.TestModels.trigger;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ForeignKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.PrimaryKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLIndexes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.UniqueConstraints;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.MigrationEmitterImpl;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.SchemaDifferImpl;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.db.duckdb.DuckDbDialect;
import org.eclipse.daanse.sql.dialect.db.postgresql.PostgreSqlDialect;
import org.junit.jupiter.api.Test;

/**
 * A table a migration creates gets what the create script of its model would give it: its unique
 * constraints, checks, indexes, foreign keys, triggers and comments, not only its columns and primary
 * key; and where the dialect cannot add a constraint to an existing table, CREATE TABLE declares it.
 */
class AddedTableTest {

    private static final Dialect PG = new PostgreSqlDialect();

    private final MigrationEmitter emitter = new MigrationEmitterImpl(new DdlGeneratorFactoryImpl());

    @Test
    void anAddedTableKeepsItsUniqueConstraintsChecksIndexesForeignKeysAndComment() {
        Schema oldSchema = customer();
        Schema newSchema = customer();
        orders(newSchema);

        List<String> sql = migrate(oldSchema, newSchema, PG);

        assertThat(sql).anyMatch(s -> s.startsWith("CREATE TABLE") && s.contains("\"orders\""))
                .anyMatch(s -> s.contains("\"uq_orders_code\" UNIQUE"))
                .anyMatch(s -> s.contains("\"ck_orders_qty\" CHECK"))
                .anyMatch(s -> s.startsWith("CREATE INDEX") && s.contains("\"ix_orders_qty\""))
                .anyMatch(s -> s.contains("\"fk_orders_customer\" FOREIGN KEY"))
                .anyMatch(s -> s.startsWith("COMMENT ON TABLE") && s.contains("What was ordered"));
    }

    @Test
    void theTablesOfASplitKeepTheirKeysChecksIndexesForeignKeysAndTriggers() {
        Schema oldSchema = customer();
        Table oldOrders = table(oldSchema, "orders", "id", "customer_id", "code", "qty");
        PrimaryKeys.create(oldOrders, "pk_orders", column(oldOrders, "id"));
        Schema newSchema = customer();
        Table head = orders(newSchema);
        trigger(head, "trg_orders_audit", "BEGIN RETURN NEW; END;");
        Table lines = table(newSchema, "order_line", "order_id", "line");
        PrimaryKeys.create(lines, "pk_order_line", column(lines, "order_id"), column(lines, "line"));
        UniqueConstraints.create(lines, "uq_order_line_line", column(lines, "line"));
        PredecessorLinks.link(head, oldOrders);
        PredecessorLinks.link(lines, oldOrders);

        SchemaDiff diff = new SchemaDifferImpl().diff(oldSchema, newSchema);
        List<String> sql = emitter.emit(ChangePlanner.create().plan(diff), PG);

        assertThat(diff.tablesSplit()).hasSize(1);
        assertThat(sql).anyMatch(s -> s.contains("\"uq_orders_code\" UNIQUE"))
                .anyMatch(s -> s.contains("\"uq_order_line_line\" UNIQUE"))
                .anyMatch(s -> s.contains("\"ck_orders_qty\" CHECK"))
                .anyMatch(s -> s.startsWith("CREATE INDEX") && s.contains("\"ix_orders_qty\""))
                .anyMatch(s -> s.contains("\"fk_orders_customer\" FOREIGN KEY"))
                .anyMatch(s -> s.startsWith("CREATE TRIGGER") && s.contains("\"trg_orders_audit\""));
    }

    @Test
    void whereTheDialectCannotAddConstraintsCreateTableDeclaresThem() {
        Schema oldSchema = customer();
        Schema newSchema = customer();
        orders(newSchema);

        List<String> sql = migrate(oldSchema, newSchema, new DuckDbDialect());

        assertThat(sql).filteredOn(s -> s.startsWith("CREATE TABLE")).singleElement().satisfies(create -> assertThat(
                create).contains("\"uq_orders_code\" UNIQUE", "\"ck_orders_qty\" CHECK", "\"fk_orders_customer\" FOREIGN KEY"));
        assertThat(sql).noneMatch(s -> s.startsWith("ALTER TABLE") && s.contains(" ADD CONSTRAINT"));
    }

    private List<String> migrate(Schema oldSchema, Schema newSchema, Dialect dialect) {
        return emitter.emit(ChangePlanner.create().plan(new SchemaDifferImpl().diff(oldSchema, newSchema)), dialect);
    }

    /** {@code sales.customer(id PK)}. */
    private static Schema customer() {
        Schema sales = TestModels.sales();
        Table customer = table(sales, "customer", "id");
        PrimaryKeys.create(customer, "pk_customer", column(customer, "id"));
        return sales;
    }

    /** {@code sales.orders(id PK, customer_id → customer, code UNIQUE, qty CHECK, index on qty)}, commented. */
    private static Table orders(Schema sales) {
        Table orders = table(sales, "orders", "id", "customer_id", "code", "qty");
        PrimaryKeys.create(orders, "pk_orders", column(orders, "id"));
        PrimaryKey customerKey = (PrimaryKey) table(sales, "customer").getOwnedElement().stream()
                .filter(PrimaryKey.class::isInstance).findFirst().orElseThrow();
        ForeignKeys.create(orders, "fk_orders_customer", customerKey, column(orders, "customer_id"));
        UniqueConstraints.create(orders, "uq_orders_code", column(orders, "code"));
        check(orders, "ck_orders_qty", "qty > 0");
        sales.getOwnedElement().add(SQLIndexes.index("ix_orders_qty", false, orders, column(orders, "qty")));
        Descriptions.describe(orders, Descriptions.JDBC_REMARKS, null, "What was ordered");
        return orders;
    }
}
