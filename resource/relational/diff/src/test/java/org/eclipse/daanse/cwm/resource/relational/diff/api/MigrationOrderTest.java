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
import static org.eclipse.daanse.cwm.resource.relational.diff.api.TestModels.indexOf;
import static org.eclipse.daanse.cwm.resource.relational.diff.api.TestModels.table;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Dependencies;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ForeignKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.PrimaryKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Views;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.SchemaDifferImpl;
import org.junit.jupiter.api.Test;

/**
 * The order a migration needs beyond drops before adds: views that read a column which goes are
 * dropped before it and created again after, whether they changed or not; tables that refer to each
 * other go referencing first; foreign keys of another schema make way for the primary key they refer
 * to.
 */
class MigrationOrderTest {

    private final SchemaDiffer differ = new SchemaDifferImpl();

    @Test
    void anUnchangedViewThatReadsADroppedColumnIsDroppedBeforeAndCreatedAfter() {
        Schema oldSchema = customerWithViews(true);
        Schema newSchema = customerWithViews(false);

        List<ChangeOp> ops = ChangePlanner.create().plan(differ.diff(oldSchema, newSchema));

        int dropColumn = indexOf(ops, ChangeOp.DropColumn.class::isInstance);
        int dropNames = indexOf(ops, op -> op instanceof ChangeOp.DropView d && "v_names".equals(d.view().getName()));
        int dropCount = indexOf(ops, op -> op instanceof ChangeOp.DropView d && "v_count".equals(d.view().getName()));
        int createNames = indexOf(ops,
                op -> op instanceof ChangeOp.CreateView c && "v_names".equals(c.view().getName()));
        int createCount = indexOf(ops,
                op -> op instanceof ChangeOp.CreateView c && "v_count".equals(c.view().getName()));
        assertThat(dropColumn).isNotNegative();
        assertThat(dropNames).as("the view reads the column").isNotNegative().isLessThan(dropColumn);
        assertThat(dropCount).as("the view reads the view that reads the column").isNotNegative()
                .isLessThan(dropNames);
        assertThat(createNames).isGreaterThan(dropColumn);
        assertThat(createCount).as("created after the view it reads").isGreaterThan(createNames);
        assertThat(ops).noneMatch(op -> op instanceof ChangeOp.DropView d && "v_other".equals(d.view().getName()));
    }

    @Test
    void aViewThatDependsOnTheTableByADependencyIsDroppedAndCreatedAgain() {
        Schema oldSchema = customerWithViews(true);
        Schema newSchema = customerWithViews(false);
        for (Schema s : List.of(oldSchema, newSchema)) {
            View hidden = Views.create(s, "v_hidden", "SELECT * FROM somewhere_else");
            Dependencies.create(s, "v_hidden reads customer", null, hidden, table(s, "customer"));
        }

        List<ChangeOp> ops = ChangePlanner.create().plan(differ.diff(oldSchema, newSchema));

        assertThat(ops).anyMatch(op -> op instanceof ChangeOp.DropView d && "v_hidden".equals(d.view().getName()))
                .anyMatch(op -> op instanceof ChangeOp.CreateView c && "v_hidden".equals(c.view().getName()));
    }

    @Test
    void droppedTablesGoReferencingFirst() {
        Schema oldSchema = TestModels.sales();
        Table parent = table(oldSchema, "parent", "id");
        PrimaryKey parentKey = PrimaryKeys.create(parent, "pk_parent", column(parent, "id"));
        Table child = table(oldSchema, "child", "id", "parent_id");
        ForeignKeys.create(child, "fk_child_parent", parentKey, column(child, "parent_id"));
        Schema newSchema = TestModels.sales();

        List<ChangeOp> ops = ChangePlanner.create().plan(differ.diff(oldSchema, newSchema));

        int dropParent = indexOf(ops, op -> op instanceof ChangeOp.DropTable d && d.table() == parent);
        int dropChild = indexOf(ops, op -> op instanceof ChangeOp.DropTable d && d.table() == child);
        assertThat(dropChild).isNotNegative().isLessThan(dropParent);
    }

    @Test
    void droppedTablesThatReferToEachOtherLoseTheirForeignKeysFirst() {
        Schema oldSchema = TestModels.sales();
        Table a = table(oldSchema, "a", "id", "b_id");
        Table b = table(oldSchema, "b", "id", "a_id");
        PrimaryKey aKey = PrimaryKeys.create(a, "pk_a", column(a, "id"));
        PrimaryKey bKey = PrimaryKeys.create(b, "pk_b", column(b, "id"));
        ForeignKey aToB = ForeignKeys.create(a, "fk_a_b", bKey, column(a, "b_id"));
        ForeignKeys.create(b, "fk_b_a", aKey, column(b, "a_id"));
        Schema newSchema = TestModels.sales();

        List<ChangeOp> ops = ChangePlanner.create().plan(differ.diff(oldSchema, newSchema));

        int firstDrop = indexOf(ops, ChangeOp.DropTable.class::isInstance);
        int dropForeignKey = indexOf(ops, op -> op instanceof ChangeOp.DropForeignKey d
                && (d.foreignKey() == aToB || "fk_b_a".equals(d.foreignKey().getName())));
        assertThat(dropForeignKey).isNotNegative().isLessThan(firstDrop);
    }

    @Test
    void aForeignKeyOfAnotherSchemaMakesWayForThePrimaryKeyItRefersTo() {
        Schema oldSales = crossSchema(false);
        Schema newSales = crossSchema(true);

        List<ChangeOp> ops = ChangePlanner.create().plan(differ.diff(oldSales, newSales));

        int dropFk = indexOf(ops, op -> op instanceof ChangeOp.DropForeignKey d
                && "fk_stock_level_customer".equals(d.foreignKey().getName()));
        int dropPk = indexOf(ops, ChangeOp.DropPrimaryKey.class::isInstance);
        int addPk = indexOf(ops, ChangeOp.AddPrimaryKey.class::isInstance);
        int addFk = indexOf(ops, op -> op instanceof ChangeOp.AddForeignKey a
                && "fk_stock_level_customer".equals(a.foreignKey().getName()));
        assertThat(dropFk).isNotNegative().isLessThan(dropPk);
        assertThat(addFk).isGreaterThan(addPk);
    }

    /**
     * {@code sales.customer(id, name[, nickname])} with {@code v_names} reading all its columns,
     * {@code v_count} reading {@code v_names}, and {@code v_other} reading nothing of customer.
     */
    private static Schema customerWithViews(boolean withNickname) {
        Schema sales = TestModels.sales();
        Table customer = table(sales, "customer", "id", "name");
        if (withNickname) {
            TestModels.varchar(customer, "nickname", 40);
        }
        table(sales, "other", "id");
        Views.create(sales, "v_count", "SELECT COUNT(*) AS n FROM sales.v_names");
        Views.create(sales, "v_names", "SELECT * FROM sales.customer");
        Views.create(sales, "v_other", "SELECT id FROM sales.other");
        return sales;
    }

    /**
     * {@code sales.customer(id, region)} with its primary key over id, or id and region, and
     * {@code stock.stock_level} referring to it from another schema.
     */
    private static Schema crossSchema(boolean widerKey) {
        Schema sales = TestModels.sales();
        Table customer = table(sales, "customer", "id", "region");
        PrimaryKey key = widerKey
                ? PrimaryKeys.create(customer, "pk_customer", column(customer, "id"), column(customer, "region"))
                : PrimaryKeys.create(customer, "pk_customer", column(customer, "id"));
        Schema stock = TestModels.schema(sales, "stock");
        Table level = table(stock, "stock_level", "customer_id", "customer_region");
        if (widerKey) {
            ForeignKeys.create(level, "fk_stock_level_customer", key, column(level, "customer_id"),
                    column(level, "customer_region"));
        } else {
            ForeignKeys.create(level, "fk_stock_level_customer", key, column(level, "customer_id"));
        }
        return sales;
    }
}
