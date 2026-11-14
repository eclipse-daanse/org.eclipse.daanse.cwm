/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.examples.jdbc.roundtrip;

import java.sql.Types;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.ParameterDirectionKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ModelElement;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Stereotype;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Expressions;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.TaggedValues;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLParameter;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ActionOrientationType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ConditionTimingType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.EventManipulationType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ProcedureType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ForeignKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.PrimaryKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.RelationalRules;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLIndexes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.UniqueConstraints;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Views;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.PrivilegeFactory;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.ProcedurePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.RolePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilege;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.TablePrivilegeAction;
import org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util.PrivilegeStereotypes;

/**
 * The shop as a CWM relational model held in Java code: the model every round trip starts from. It
 * holds more than a single database gets, so a selection has something to leave out.
 *
 * <pre>
 * shop
 *   sales                                          stock
 *     customer           PK, email UNIQUE,           product      PK
 *                        comments,                   warehouse    PK
 *                        referred_by → customer (d)  stock_level  PK (product_id, warehouse_id),
 *     orders             PK, → customer,                          → product, → warehouse
 *                        → stock.product, CHECK qty &gt; 0,
 *                        index, trigger (PL/pgSQL)
 *     order_log          order_id → orders (d)
 *     v_top_customers    reads v_customer_orders
 *     v_customer_orders  reads customer and orders
 *   role shop_reader     SELECT on customer, orders, order_log and stock.product
 *   role shop_writer     INSERT on orders WITH GRANT OPTION, UPDATE (qty) on orders,
 *                        member of shop_reader WITH ADMIN OPTION
 *   user shop_clerk      member of shop_writer, INSERT on order_log
 * </pre>
 *
 * <p>(d) marks a foreign key that is documentation only, tagged
 * {@value RelationalRules#DOCUMENTARY_TAG} = {@code true}: it says how the data hangs together, a
 * database need not enforce it. Some things stand in an order a script cannot run in, on purpose:
 * the schema {@code sales} comes before {@code stock}, which its orders refer to, and the view
 * {@code v_top_customers} before the view it reads. The DDL generator orders the statements by what
 * they refer to, not by the model.</p>
 */
public final class ShopModel {

    private static final RelationalFactory R = RelationalFactory.eINSTANCE;
    private static final PrivilegeFactory P = PrivilegeFactory.eINSTANCE;

    private ShopModel() {
    }

    /** A new copy of the shop model. */
    public static Catalog build() {
        Catalog shop = Catalogs.create("shop");

        // the SQL types, owned by the catalog, one per name; lengths and precisions are the columns'
        SQLSimpleType integer = SQLSimpleTypes.create(shop, "INTEGER", Types.INTEGER);
        SQLSimpleType varchar = SQLSimpleTypes.create(shop, "VARCHAR", Types.VARCHAR);
        SQLSimpleType decimal = SQLSimpleTypes.create(shop, "DECIMAL", Types.DECIMAL);
        SQLSimpleType date = SQLSimpleTypes.create(shop, "DATE", Types.DATE);

        // sales before stock, although sales refers into stock
        Schema sales = Schemas.create(shop, "sales");
        Schema stock = Schemas.create(shop, "stock");

        // --- stock ---------------------------------------------------------------------------
        Table product = Tables.create(stock, "product");
        Column productId = Columns.create(product, "id", integer, NullableType.COLUMN_NO_NULLS);
        Columns.create(product, "name", varchar, NullableType.COLUMN_NO_NULLS, 100, 0, 0);
        Columns.create(product, "price", decimal, NullableType.COLUMN_NO_NULLS, 0, 10, 2);
        PrimaryKey productKey = PrimaryKeys.create(product, "pk_product", productId);

        Table warehouse = Tables.create(stock, "warehouse");
        Column warehouseId = Columns.create(warehouse, "id", integer, NullableType.COLUMN_NO_NULLS);
        Columns.create(warehouse, "city", varchar, NullableType.COLUMN_NULLABLE, 60, 0, 0);
        PrimaryKey warehouseKey = PrimaryKeys.create(warehouse, "pk_warehouse", warehouseId);

        Table stockLevel = Tables.create(stock, "stock_level");
        Column levelProduct = Columns.create(stockLevel, "product_id", integer, NullableType.COLUMN_NO_NULLS);
        Column levelWarehouse = Columns.create(stockLevel, "warehouse_id", integer, NullableType.COLUMN_NO_NULLS);
        Columns.create(stockLevel, "quantity", integer, NullableType.COLUMN_NO_NULLS);
        PrimaryKeys.create(stockLevel, "pk_stock_level", levelProduct, levelWarehouse);
        ForeignKeys.create(stockLevel, "fk_stock_level_product", productKey, levelProduct);
        ForeignKeys.create(stockLevel, "fk_stock_level_warehouse", warehouseKey, levelWarehouse);

        // --- sales ---------------------------------------------------------------------------
        Table customer = Tables.create(sales, "customer");
        Column customerId = Columns.create(customer, "id", integer, NullableType.COLUMN_NO_NULLS);
        Columns.create(customer, "name", varchar, NullableType.COLUMN_NO_NULLS, 100, 0, 0);
        Column email = Columns.create(customer, "email", varchar, NullableType.COLUMN_NULLABLE, 200, 0, 0);
        Column referredBy = Columns.create(customer, "referred_by", integer, NullableType.COLUMN_NULLABLE);
        PrimaryKey customerKey = PrimaryKeys.create(customer, "pk_customer", customerId);
        UniqueConstraints.create(customer, "uq_customer_email", email);
        documentary(ForeignKeys.create(customer, "fk_customer_referred_by", customerKey, referredBy));
        Descriptions.describe(customer, Descriptions.JDBC_REMARKS, null, "Who orders");
        Descriptions.describe(email, Descriptions.JDBC_REMARKS, null, "Where the invoice goes");

        Table orders = Tables.create(sales, "orders");
        Column orderId = Columns.create(orders, "id", integer, NullableType.COLUMN_NO_NULLS);
        Column orderCustomer = Columns.create(orders, "customer_id", integer, NullableType.COLUMN_NO_NULLS);
        Column orderProduct = Columns.create(orders, "product_id", integer, NullableType.COLUMN_NO_NULLS);
        Column qty = Columns.create(orders, "qty", integer, NullableType.COLUMN_NO_NULLS);
        Column orderedOn = Columns.create(orders, "ordered_on", date, NullableType.COLUMN_NULLABLE);
        PrimaryKey orderKey = PrimaryKeys.create(orders, "pk_orders", orderId);
        ForeignKeys.create(orders, "fk_orders_customer", customerKey, orderCustomer);
        ForeignKeys.create(orders, "fk_orders_product", productKey, orderProduct); // into stock
        check(orders, "ck_orders_qty", "qty > 0");
        sales.getOwnedElement().add(SQLIndexes.index("ix_orders_ordered_on", false, orders, orderedOn));
        trigger(sales, "trg_orders_ordered_on", orders,
                "BEGIN NEW.ordered_on := COALESCE(NEW.ordered_on, CURRENT_DATE); RETURN NEW; END;");
        Descriptions.describe(orders, Descriptions.JDBC_REMARKS, null, "One line per order");

        Table orderLog = Tables.create(sales, "order_log");
        Column logOrder = Columns.create(orderLog, "order_id", integer, NullableType.COLUMN_NO_NULLS);
        Columns.create(orderLog, "message", varchar, NullableType.COLUMN_NULLABLE, 400, 0, 0);
        // the log keeps its lines when an order goes
        documentary(ForeignKeys.create(orderLog, "fk_order_log_orders", orderKey, logOrder));

        // the view on a view first, the view it reads after it
        Views.create(sales, "v_top_customers",
                "SELECT id, name, order_count FROM sales.v_customer_orders WHERE order_count >= 10");
        Views.create(sales, "v_customer_orders", "SELECT c.id, c.name, COUNT(o.id) AS order_count"
                + " FROM sales.customer c LEFT JOIN sales.orders o ON o.customer_id = c.id GROUP BY c.id, c.name");

        // a function in PL/pgSQL, which only PostgreSQL writes
        Procedure ordersOf = R.createProcedure();
        ordersOf.setName("orders_of");
        ordersOf.setType(ProcedureType.FUNCTION);
        ordersOf.getParameter().add(parameter("who", ParameterDirectionKind.PDK_IN, integer));
        ordersOf.getParameter().add(parameter("returnValue", ParameterDirectionKind.PDK_RETURN, integer));
        ordersOf.setBody(Expressions.procedure("plpgsql",
                "BEGIN RETURN (SELECT COUNT(*) FROM sales.orders o WHERE o.customer_id = who); END;"));
        sales.getOwnedElement().add(ordersOf);

        // --- roles, a user and their privileges, in both schemas -------------------------------
        Stereotype databaseRole = PrivilegeStereotypes.create(PrivilegeStereotypes.DATABASE_ROLE);
        Stereotype databaseUser = PrivilegeStereotypes.create(PrivilegeStereotypes.DATABASE_USER);
        shop.getOwnedElement().add(databaseRole);
        shop.getOwnedElement().add(databaseUser);

        ResponsibleParty reader = party(shop, databaseRole, "shop_reader");
        for (Table table : Schemas.tables(sales)) {
            grant(reader, TablePrivilegeAction.SELECT, table, false);
        }
        grant(reader, TablePrivilegeAction.SELECT, product, false);
        ProcedurePrivilege execute = P.createProcedurePrivilege();
        execute.setProcedure(ordersOf);
        execute.setGrantable(false);
        reader.getOwnedElement().add(execute);

        ResponsibleParty writer = party(shop, databaseRole, "shop_writer");
        grant(writer, TablePrivilegeAction.INSERT, orders, true); // WITH GRANT OPTION
        grant(writer, TablePrivilegeAction.UPDATE, orders, false).getColumn().add(qty); // UPDATE (qty)
        member(writer, reader, true); // WITH ADMIN OPTION

        // a user, which the export creates without credentials: it logs in once it is given a way to
        ResponsibleParty clerk = party(shop, databaseUser, "shop_clerk");
        member(clerk, writer, false);
        grant(clerk, TablePrivilegeAction.INSERT, orderLog, false);
        return shop;
    }

    private static SQLParameter parameter(String name, ParameterDirectionKind kind, SQLSimpleType type) {
        SQLParameter parameter = R.createSQLParameter();
        parameter.setName(name);
        parameter.setKind(kind);
        parameter.setType(type);
        return parameter;
    }

    private static void documentary(ModelElement element) {
        TaggedValues.set(element, RelationalRules.DOCUMENTARY_TAG, "true");
    }

    private static void check(Table table, String name, String condition) {
        CheckConstraint check = R.createCheckConstraint();
        check.setName(name);
        check.setBody(Expressions.bool("SQL", condition));
        table.getOwnedElement().add(check);
    }

    /** A row trigger before insert, its body in PL/pgSQL; the schema owns it, the table refers to it. */
    private static void trigger(Schema schema, String name, Table table, String body) {
        Trigger trigger = R.createTrigger();
        trigger.setName(name);
        trigger.setTable(table);
        trigger.setConditionTiming(ConditionTimingType.BEFORE);
        trigger.setEventManipulation(EventManipulationType.INSERT);
        trigger.setActionOrientation(ActionOrientationType.ROW);
        trigger.setActionStatement(Expressions.procedure("plpgsql", body));
        schema.getOwnedElement().add(trigger);
    }

    /** A role or user, owned by the catalog and branded by {@code marker}. */
    private static ResponsibleParty party(Catalog owner, Stereotype marker, String name) {
        ResponsibleParty party = BusinessinformationFactory.eINSTANCE.createResponsibleParty();
        party.setName(name);
        owner.getOwnedElement().add(party);
        marker.getExtendedElement().add(party);
        return party;
    }

    private static TablePrivilege grant(ResponsibleParty grantee, TablePrivilegeAction action, Table table,
            boolean grantable) {
        TablePrivilege privilege = P.createTablePrivilege();
        privilege.setAction(action);
        privilege.setTable(table);
        privilege.setGrantable(grantable);
        grantee.getOwnedElement().add(privilege);
        return privilege;
    }

    /** Makes {@code member} a member of {@code role}; {@code admin}: WITH ADMIN OPTION. */
    private static void member(ResponsibleParty member, ResponsibleParty role, boolean admin) {
        RolePrivilege membership = P.createRolePrivilege();
        membership.setRole(role);
        membership.setGrantable(admin);
        member.getOwnedElement().add(membership);
    }
}
