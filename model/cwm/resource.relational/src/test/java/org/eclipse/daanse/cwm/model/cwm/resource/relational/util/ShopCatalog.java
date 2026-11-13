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
package org.eclipse.daanse.cwm.model.cwm.resource.relational.util;

import java.sql.Types;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Expressions;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.TaggedValues;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;

/**
 * A catalog with two schemas and the SQL types outside both:
 *
 * <pre>
 * shop                              Catalog
 *   INTEGER, VARCHAR                SQLSimpleType
 *   stock                           Schema
 *     product (id, name)            pk_product
 *   sales                           Schema
 *     customer (id, name, email)    pk_customer, uq_customer_email
 *     orders (id, customer_id,      pk_orders, fk_orders_customer, fk_orders_product (into stock),
 *             product_id, qty)      ck_orders_qty
 *     ix_orders_customer            index on orders (customer_id)
 *     ix_customer_email             index on customer (email)
 *     trg_orders                    trigger on orders
 *     order_log (order_id)          pk_order_log, documentary
 *     v_customer_orders             view
 *     p_archive                     procedure
 * </pre>
 */
final class ShopCatalog {

    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;

    final Catalog shop = Catalogs.create("shop");
    final SQLSimpleType integer = SQLSimpleTypes.create(shop, "INTEGER", Types.INTEGER);
    final SQLSimpleType varchar = SQLSimpleTypes.create(shop, "VARCHAR", Types.VARCHAR);

    final Schema stock = Schemas.create(shop, "stock");
    final Table product = Tables.create(stock, "product");
    final Column productId = Columns.create(product, "id", integer);
    final Column productName = Columns.create(product, "name", varchar);
    final PrimaryKey pkProduct = PrimaryKeys.create(product, "pk_product", productId);

    final Schema sales = Schemas.create(shop, "sales");
    final Table customer = Tables.create(sales, "customer");
    final Column customerId = Columns.create(customer, "id", integer);
    final Column customerName = Columns.create(customer, "name", varchar);
    final Column email = Columns.create(customer, "email", varchar);
    final PrimaryKey pkCustomer = PrimaryKeys.create(customer, "pk_customer", customerId);
    final UniqueConstraint uqEmail = UniqueConstraints.create(customer, "uq_customer_email", email);

    final Table orders = Tables.create(sales, "orders");
    final Column orderId = Columns.create(orders, "id", integer);
    final Column orderCustomer = Columns.create(orders, "customer_id", integer);
    final Column orderProduct = Columns.create(orders, "product_id", integer);
    final Column qty = Columns.create(orders, "qty", integer);
    final PrimaryKey pkOrders = PrimaryKeys.create(orders, "pk_orders", orderId);
    final ForeignKey fkCustomer = ForeignKeys.create(orders, "fk_orders_customer", pkCustomer, orderCustomer);
    final ForeignKey fkProduct = ForeignKeys.create(orders, "fk_orders_product", pkProduct, orderProduct);
    final CheckConstraint checkQty = check(orders, "ck_orders_qty", "qty > 0");
    final SQLIndex ixOrdersCustomer = index(sales, "ix_orders_customer", orders, orderCustomer);
    final SQLIndex ixCustomerEmail = index(sales, "ix_customer_email", customer, email);
    final Trigger trigger = trigger(sales, "trg_orders", orders);

    final Table orderLog = Tables.create(sales, "order_log");
    final Column logOrder = Columns.create(orderLog, "order_id", integer);
    final PrimaryKey pkOrderLog = documentary(PrimaryKeys.create(orderLog, "pk_order_log", logOrder));
    final View view = Views.create(sales, "v_customer_orders", "SELECT id, name FROM sales.customer");
    final Procedure procedure = procedure(sales, "p_archive");

    private static CheckConstraint check(Table table, String name, String condition) {
        CheckConstraint check = RF.createCheckConstraint();
        check.setName(name);
        check.setBody(Expressions.bool("SQL", condition));
        table.getOwnedElement().add(check);
        return check;
    }

    private static SQLIndex index(Schema schema, String name, Table table, Column column) {
        SQLIndex index = SQLIndexes.index(name, false, table, column);
        schema.getOwnedElement().add(index);
        return index;
    }

    private static Trigger trigger(Schema schema, String name, Table table) {
        Trigger trigger = RF.createTrigger();
        trigger.setName(name);
        trigger.setTable(table);
        trigger.setActionStatement(Expressions.procedure("SQL", "BEGIN END"));
        trigger.setActionCondition(CoreFactory.eINSTANCE.createBooleanExpression());
        schema.getOwnedElement().add(trigger);
        return trigger;
    }

    private static Procedure procedure(Schema schema, String name) {
        Procedure procedure = RF.createProcedure();
        procedure.setName(name);
        schema.getOwnedElement().add(procedure);
        return procedure;
    }

    private static PrimaryKey documentary(PrimaryKey key) {
        TaggedValues.set(key, RelationalRules.DOCUMENTARY_TAG, "true");
        return key;
    }
}
