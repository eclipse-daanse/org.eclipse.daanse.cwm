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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.eclipse.daanse.cwm.resource.relational.diff.api.TestModels.column;
import static org.eclipse.daanse.cwm.resource.relational.diff.api.TestModels.table;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndexColumn;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ForeignKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.PrimaryKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLIndexes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.UniqueConstraints;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Views;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.Unwritable;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.UnwritableException;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.MigrationEmitterImpl;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.db.duckdb.DuckDbDialect;
import org.eclipse.daanse.sql.dialect.db.postgresql.PostgreSqlDialect;
import org.junit.jupiter.api.Test;

/**
 * A migration is written with the settings and the dialect capabilities of the create script, by its
 * code: {@code IF [NOT] EXISTS}, schema names and {@code CASCADE} only where the settings ask for them,
 * an index the create script leaves out is not written, and an element gets the statements the create
 * script gives it.
 */
class EmitterSettingsTest {

    private static final Dialect PG = new PostgreSqlDialect();
    private static final MigrationEmitter EMITTER = new MigrationEmitterImpl(new DdlGeneratorFactoryImpl());

    @Test
    void withoutIfNotExistsNoStatementSaysIfExists() {
        DdlSettings settings = DdlSettings.defaults().withIfNotExists(false);
        Schema sales = TestModels.sales();
        Table customer = table(sales, "customer", "id");
        PrimaryKey key = PrimaryKeys.create(customer, "pk_customer", column(customer, "id"));
        Table orders = table(sales, "orders", "id", "customer_id");
        ForeignKey fk = ForeignKeys.create(orders, "fk_orders_customer", key, column(orders, "customer_id"));
        SQLIndex index = SQLIndexes.index("ix_orders_customer", false, orders, column(orders, "customer_id"));
        sales.getOwnedElement().add(index);
        View view = Views.create(sales, "v_orders", "SELECT id FROM sales.orders");

        List<String> sql = EMITTER.emit(List.of(new ChangeOp.DropForeignKey(orders, fk),
                new ChangeOp.DropView(view), new ChangeOp.DropIndex(index), new ChangeOp.DropTable(orders),
                new ChangeOp.CreateIndex(index)), PG, settings);

        assertThat(sql).hasSize(5).noneMatch(s -> s.contains(" IF EXISTS ") || s.contains(" IF NOT EXISTS "));
    }

    @Test
    void withoutSchemaNamesAndWithCascadeTheStatementsAreBareAndCascade() {
        DdlSettings settings = DdlSettings.defaults().withIncludeSchema(false).withCascade(true);
        Schema sales = TestModels.sales();
        Table orders = table(sales, "orders", "id", "qty");
        SQLIndex index = SQLIndexes.index("ix_orders_qty", false, orders, column(orders, "qty"));
        sales.getOwnedElement().add(index);

        List<String> sql = EMITTER.emit(List.of(new ChangeOp.DropIndex(index), new ChangeOp.DropTable(orders),
                new ChangeOp.CreateTable(orders), new ChangeOp.CreateIndex(index)), PG, settings);

        assertThat(sql).hasSize(4).noneMatch(s -> s.contains("\"sales\""));
        assertThat(sql.get(1)).isEqualTo("DROP TABLE IF EXISTS \"orders\" CASCADE");
    }

    @Test
    void theStatementsOfAnElementAreThoseOfTheCreateScript() {
        DdlSettings settings = DdlSettings.defaults().withIfNotExists(false);
        Schema sales = TestModels.sales();
        Table customer = table(sales, "customer", "id");
        PrimaryKey key = PrimaryKeys.create(customer, "pk_customer", column(customer, "id"));
        Table orders = table(sales, "orders", "id", "customer_id");
        ForeignKey fk = ForeignKeys.create(orders, "fk_orders_customer", key, column(orders, "customer_id"));
        SQLIndex index = SQLIndexes.index(null, false, orders, column(orders, "customer_id"));
        sales.getOwnedElement().add(index);
        View view = Views.create(sales, "v_orders", "SELECT id FROM sales.orders");

        List<String> script = new DdlGeneratorFactoryImpl().create(PG, settings).createSchema(sales);
        List<String> sql = EMITTER.emit(List.of(new ChangeOp.CreateTable(customer), new ChangeOp.CreateTable(orders),
                new ChangeOp.CreateIndex(index), new ChangeOp.AddForeignKey(orders, fk), new ChangeOp.CreateView(view)),
                PG, settings);

        assertThat(script).containsAll(sql);
    }

    /**
     * An index that filters its rows is written with its filter where the dialect has partial
     * indexes, and left out where it has none, as the create script leaves it out.
     */
    @Test
    void anIndexThatFiltersItsRowsIsWrittenAsTheCreateScriptWritesIt() {
        Schema sales = TestModels.sales();
        Table orders = table(sales, "orders", "id", "qty");
        SQLIndex index = SQLIndexes.index("ix_orders_qty", false, orders, column(orders, "qty"));
        index.setFilterCondition("qty > 0");
        sales.getOwnedElement().add(index);
        Dialect duckdb = new DuckDbDialect();

        assertThat(EMITTER.emit(List.of(new ChangeOp.CreateIndex(index)), PG)).singleElement().asString()
                .startsWith("CREATE INDEX").endsWith(" WHERE qty > 0");
        assertThat(new DdlGeneratorFactoryImpl().create(duckdb).createSchema(sales))
                .noneMatch(s -> s.startsWith("CREATE INDEX"));
        assertThat(EMITTER.emit(List.of(new ChangeOp.CreateIndex(index)), duckdb)).isEmpty();
    }

    /**
     * What the dialect cannot write is one rule for the whole plan. DuckDB declares a unique
     * constraint in {@code CREATE TABLE} only and keeps every index column ascending: not strict, the
     * constraint on an existing table is left out and the index is written ascending; strict, the
     * plan fails once, naming both.
     */
    @Test
    void whatTheDialectCannotWriteIsLeftOutOrStrictThePlanFailsOnceWithEveryCase() {
        Dialect duckdb = new DuckDbDialect();
        Schema sales = TestModels.sales();
        Table orders = table(sales, "orders", "id", "qty");
        UniqueConstraint unique = UniqueConstraints.create(orders, "uq_orders_qty", column(orders, "qty"));
        SQLIndex index = SQLIndexes.index("ix_orders_qty", false, orders, column(orders, "qty"));
        ((SQLIndexColumn) index.getIndexedFeature().get(0)).setIsAscending(false);
        sales.getOwnedElement().add(index);
        List<ChangeOp> ops = List.of(new ChangeOp.AddUniqueConstraint(orders, unique), new ChangeOp.CreateIndex(index));

        assertThat(EMITTER.emit(ops, duckdb)).singleElement()
                .satisfies(s -> assertThat(s).startsWith("CREATE INDEX").doesNotContain("DESC"));
        assertThatThrownBy(() -> EMITTER.emit(ops, duckdb, DdlSettings.defaults().withStrict(true)))
                .isInstanceOfSatisfying(UnwritableException.class, e -> assertThat(e.cases())
                        .extracting(Unwritable::element, Unwritable::what).containsExactly(
                                tuple("unique constraint shop.sales.orders.uq_orders_qty",
                                        "ALTER TABLE ADD CONSTRAINT"),
                                tuple("index shop.sales.ix_orders_qty", "DESC on column qty")));
    }
}
