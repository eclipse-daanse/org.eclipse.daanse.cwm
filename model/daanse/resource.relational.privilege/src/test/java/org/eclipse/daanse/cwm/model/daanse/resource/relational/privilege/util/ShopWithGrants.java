/*********************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.daanse.cwm.model.daanse.resource.relational.privilege.util;

import java.sql.Types;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Stereotype;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Expressions;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.TaggedValues;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Procedure;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
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

/**
 * A catalog with two schemas, the SQL types and the roles outside both:
 *
 * <pre>
 * shop                              Catalog
 *   INTEGER, VARCHAR                SQLSimpleType
 *   databaseRole, databaseUser      Stereotype
 *   stock                           Schema
 *     product (id, name)            pk_product
 *   sales                           Schema
 *     customer (id, name, email)    pk_customer, uq_customer_email; comments on customer and email
 *     orders (id, customer_id,      pk_orders, fk_orders_customer, fk_orders_product (into stock),
 *             product_id, qty)      ck_orders_qty
 *     ix_orders_customer            index on orders (customer_id)
 *     trg_orders                    trigger on orders
 *     order_log (order_id)          pk_order_log, documentary
 *     v_customer_orders             view
 *     p_archive                     procedure
 *   shop_reader                     role: SELECT on customer, orders, order_log and stock.product
 *   shop_writer                     role: INSERT on orders, UPDATE (qty) on orders, EXECUTE p_archive,
 *                                   member of shop_reader
 *   alice                           user: member of shop_writer
 * </pre>
 */
final class ShopWithGrants {

    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;
    private static final PrivilegeFactory PF = PrivilegeFactory.eINSTANCE;

    static final String COMMENT = "JDBC_REMARKS";

    final Catalog shop = Catalogs.create("shop");
    final SQLSimpleType integer = SQLSimpleTypes.create(shop, "INTEGER", Types.INTEGER);
    final SQLSimpleType varchar = SQLSimpleTypes.create(shop, "VARCHAR", Types.VARCHAR);
    final Stereotype databaseRole = marker(PrivilegeStereotypes.DATABASE_ROLE);
    final Stereotype databaseUser = marker(PrivilegeStereotypes.DATABASE_USER);

    final Schema stock = Schemas.create(shop, "stock");
    final Table product = Tables.create(stock, "product");
    final Column productId = Columns.create(product, "id", integer);
    final PrimaryKey pkProduct = PrimaryKeys.create(product, "pk_product", productId);

    final Schema sales = Schemas.create(shop, "sales");
    final Table customer = Tables.create(sales, "customer");
    final Column customerId = Columns.create(customer, "id", integer);
    final Column customerName = Columns.create(customer, "name", varchar);
    final Column email = Columns.create(customer, "email", varchar);
    final PrimaryKey pkCustomer = PrimaryKeys.create(customer, "pk_customer", customerId);

    final Table orders = Tables.create(sales, "orders");
    final Column orderId = Columns.create(orders, "id", integer);
    final Column orderCustomer = Columns.create(orders, "customer_id", integer);
    final Column orderProduct = Columns.create(orders, "product_id", integer);
    final Column qty = Columns.create(orders, "qty", integer);
    final SQLIndex index = SQLIndexes.index("ix_orders_customer", false, orders, orderCustomer);
    final Trigger trigger = RF.createTrigger();

    final Table orderLog = Tables.create(sales, "order_log");
    final Column logOrder = Columns.create(orderLog, "order_id", integer);
    final Procedure archive = RF.createProcedure();

    final ResponsibleParty reader = party("shop_reader", databaseRole);
    final ResponsibleParty writer = party("shop_writer", databaseRole);
    final ResponsibleParty alice = party("alice", databaseUser);
    final TablePrivilege updateQty;

    ShopWithGrants() {
        UniqueConstraints.create(customer, "uq_customer_email", email);
        Descriptions.describe(customer, COMMENT, null, "Who orders");
        Descriptions.describe(email, COMMENT, null, "Where the invoice goes");
        PrimaryKeys.create(orders, "pk_orders", orderId);
        ForeignKeys.create(orders, "fk_orders_customer", pkCustomer, orderCustomer);
        ForeignKeys.create(orders, "fk_orders_product", pkProduct, orderProduct);
        CheckConstraint check = RF.createCheckConstraint();
        check.setName("ck_orders_qty");
        check.setBody(Expressions.bool("SQL", "qty > 0"));
        orders.getOwnedElement().add(check);
        sales.getOwnedElement().add(index);
        trigger.setName("trg_orders");
        trigger.setTable(orders);
        trigger.setActionStatement(Expressions.procedure("SQL", "BEGIN END"));
        trigger.setActionCondition(Expressions.bool("SQL", "TRUE"));
        sales.getOwnedElement().add(trigger);
        TaggedValues.set(PrimaryKeys.create(orderLog, "pk_order_log", logOrder), RelationalRules.DOCUMENTARY_TAG,
                "true");
        Views.create(sales, "v_customer_orders", "SELECT id, name FROM sales.customer");
        archive.setName("p_archive");
        sales.getOwnedElement().add(archive);

        for (Table table : new Table[] { customer, orders, orderLog, product }) {
            grant(reader, TablePrivilegeAction.SELECT, table);
        }
        grant(writer, TablePrivilegeAction.INSERT, orders);
        updateQty = grant(writer, TablePrivilegeAction.UPDATE, orders);
        updateQty.getColumn().add(qty);
        ProcedurePrivilege execute = PF.createProcedurePrivilege();
        execute.setProcedure(archive);
        writer.getOwnedElement().add(execute);
        member(writer, reader);
        member(alice, writer);
    }

    private Stereotype marker(String name) {
        Stereotype stereotype = PrivilegeStereotypes.create(name);
        shop.getOwnedElement().add(stereotype);
        return stereotype;
    }

    private ResponsibleParty party(String name, Stereotype marker) {
        ResponsibleParty party = BusinessinformationFactory.eINSTANCE.createResponsibleParty();
        party.setName(name);
        shop.getOwnedElement().add(party);
        marker.getExtendedElement().add(party);
        return party;
    }

    private static TablePrivilege grant(ResponsibleParty grantee, TablePrivilegeAction action, Table table) {
        TablePrivilege privilege = PF.createTablePrivilege();
        privilege.setAction(action);
        privilege.setTable(table);
        grantee.getOwnedElement().add(privilege);
        return privilege;
    }

    private static RolePrivilege member(ResponsibleParty member, ResponsibleParty role) {
        RolePrivilege membership = PF.createRolePrivilege();
        membership.setRole(role);
        member.getOwnedElement().add(membership);
        return membership;
    }
}
