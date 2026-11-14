/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.resource.relational.jdbc.standalone;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.ResponsibleParty;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.behavioral.ParameterDirectionKind;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.BooleanExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ProcedureExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Stereotype;
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
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ColumnSets;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ForeignKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.PrimaryKeys;
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
 * The models the export tests write: a shop in two schemas, {@code <prefix>stock} and
 * {@code <prefix>sales}, with keys, a unique constraint, a check, foreign keys within and across
 * the schemas, an index, a view and comments, the roles {@code <prefix>reader} and
 * {@code <prefix>writer} with table, column and role privileges, and the user {@code <prefix>clerk},
 * a member of the writer. The names take a prefix, as the
 * roles of a database server are shared by all its databases. Conditions and queries quote their
 * names, in double quotes or, for MySQL, in backticks, so they read alike in every database.
 */
final class ShopModels {

    private static final RelationalFactory R = RelationalFactory.eINSTANCE;
    private static final PrivilegeFactory P = PrivilegeFactory.eINSTANCE;

    private ShopModels() {
    }

    /**
     * The shop; with {@code trigger} a PL/pgSQL row trigger on the orders and a PL/pgSQL function
     * {@code orders_of} that the reader may run, for PostgreSQL.
     */
    static Catalog shop(String prefix, boolean trigger) {
        return shop(prefix, trigger, "\"");
    }

    /** The shop, its conditions and queries quoting names with {@code quote}. */
    static Catalog shop(String prefix, boolean trigger, String quote) {
        Catalog shop = Catalogs.create("shop");
        SQLSimpleType integer = SQLSimpleTypes.create(shop, "INTEGER", Types.INTEGER);
        SQLSimpleType varchar = SQLSimpleTypes.create(shop, "VARCHAR", Types.VARCHAR);
        SQLSimpleType decimal = SQLSimpleTypes.create(shop, "DECIMAL", Types.DECIMAL);
        SQLSimpleType date = SQLSimpleTypes.create(shop, "DATE", Types.DATE);

        Schema stock = Schemas.create(shop, prefix + "stock");
        Table product = Tables.create(stock, "product");
        Column productId = Columns.create(product, "id", integer, NullableType.COLUMN_NO_NULLS);
        Columns.create(product, "name", varchar, NullableType.COLUMN_NO_NULLS, 100, 0, 0);
        Columns.create(product, "price", decimal, NullableType.COLUMN_NO_NULLS, 0, 10, 2);
        PrimaryKey productKey = PrimaryKeys.create(product, "pk_product", productId);

        Schema sales = Schemas.create(shop, prefix + "sales");
        Table customer = Tables.create(sales, "customer");
        Column customerId = Columns.create(customer, "id", integer, NullableType.COLUMN_NO_NULLS);
        Columns.create(customer, "name", varchar, NullableType.COLUMN_NO_NULLS, 100, 0, 0);
        Column email = Columns.create(customer, "email", varchar, NullableType.COLUMN_NULLABLE, 200, 0, 0);
        PrimaryKey customerKey = PrimaryKeys.create(customer, "pk_customer", customerId);
        UniqueConstraints.create(customer, "uq_customer_email", email);
        Descriptions.describe(customer, Descriptions.JDBC_REMARKS, null, "Who orders");
        Descriptions.describe(email, Descriptions.JDBC_REMARKS, null, "Where the invoice goes");

        Table orders = Tables.create(sales, "orders");
        Column orderId = Columns.create(orders, "id", integer, NullableType.COLUMN_NO_NULLS);
        Column orderCustomer = Columns.create(orders, "customer_id", integer, NullableType.COLUMN_NO_NULLS);
        Column orderProduct = Columns.create(orders, "product_id", integer, NullableType.COLUMN_NO_NULLS);
        Column qty = Columns.create(orders, "qty", integer, NullableType.COLUMN_NO_NULLS);
        Column orderedOn = Columns.create(orders, "ordered_on", date, NullableType.COLUMN_NULLABLE);
        PrimaryKeys.create(orders, "pk_orders", orderId);
        ForeignKeys.create(orders, "fk_orders_customer", customerKey, orderCustomer);
        ForeignKeys.create(orders, "fk_orders_product", productKey, orderProduct); // across the schemas
        orders.getOwnedElement().add(check("ck_orders_qty", quote + "qty" + quote + " > 0"));
        sales.getOwnedElement().add(SQLIndexes.index("ix_orders_ordered_on", false, orders, orderedOn));
        if (trigger) {
            sales.getOwnedElement().add(trigger("trg_orders_ordered_on", orders,
                    "BEGIN NEW.ordered_on := COALESCE(NEW.ordered_on, CURRENT_DATE); RETURN NEW; END;"));
        }
        Views.create(sales, "v_customer", "SELECT " + quote + "id" + quote + ", " + quote + "name" + quote + " FROM "
                + quote + sales.getName() + quote + "." + quote + "customer" + quote);
        Procedure ordersOf = trigger ? ordersOf(sales, integer) : null;

        Stereotype databaseRole = PrivilegeStereotypes.create(PrivilegeStereotypes.DATABASE_ROLE);
        shop.getOwnedElement().add(databaseRole);
        ResponsibleParty reader = role(shop, databaseRole, prefix + "reader");
        ResponsibleParty writer = role(shop, databaseRole, prefix + "writer");
        grant(reader, TablePrivilegeAction.SELECT, customer, false);
        grant(reader, TablePrivilegeAction.SELECT, orders, false);
        grant(reader, TablePrivilegeAction.SELECT, product, false);
        grant(writer, TablePrivilegeAction.INSERT, orders, true);
        if (ordersOf != null) {
            ProcedurePrivilege execute = P.createProcedurePrivilege();
            execute.setProcedure(ordersOf);
            execute.setGrantable(false);
            reader.getOwnedElement().add(execute);
        }
        grant(writer, TablePrivilegeAction.UPDATE, orders, false).getColumn().add(qty);
        RolePrivilege membership = P.createRolePrivilege();
        membership.setRole(reader);
        membership.setGrantable(Boolean.TRUE);
        writer.getOwnedElement().add(membership);
        Stereotype databaseUser = PrivilegeStereotypes.create(PrivilegeStereotypes.DATABASE_USER);
        shop.getOwnedElement().add(databaseUser);
        ResponsibleParty clerk = role(shop, databaseUser, prefix + "clerk");
        RolePrivilege clerkMembership = P.createRolePrivilege();
        clerkMembership.setRole(writer);
        clerkMembership.setGrantable(Boolean.FALSE);
        clerk.getOwnedElement().add(clerkMembership);
        return shop;
    }

    /**
     * Adds to the shop what a model gains after it was exported: on {@code <prefix>stock.product} a
     * check without a name, a unique constraint, and a column naming the product that replaces it,
     * with a foreign key to the product; on {@code <prefix>sales.customer} an index and, with
     * {@code trigger}, a PL/pgSQL row trigger.
     *
     * @return the names the statements give what was added, the check its default name
     */
    static List<String> gain(Catalog shop, String prefix, boolean trigger) {
        return gain(shop, prefix, trigger, "\"");
    }

    /** As {@link #gain(Catalog, String, boolean)}, the check quoting its column with {@code quote}. */
    static List<String> gain(Catalog shop, String prefix, boolean trigger, String quote) {
        Table product = Schemas.findTable(Catalogs.findSchema(shop, prefix + "stock").orElseThrow(), "product")
                .orElseThrow();
        Column replacedBy = Columns.create(product, "replaced_by", column(product, "id").getType(),
                NullableType.COLUMN_NULLABLE);
        product.getOwnedElement().add(check(null, quote + "price" + quote + " >= 0"));
        UniqueConstraints.create(product, "uq_product_name", column(product, "name"));
        ForeignKeys.create(product, "fk_product_replaced_by", Tables.findPrimaryKey(product).orElseThrow(),
                replacedBy);

        Schema sales = Catalogs.findSchema(shop, prefix + "sales").orElseThrow();
        Table customer = Schemas.findTable(sales, "customer").orElseThrow();
        sales.getOwnedElement().add(SQLIndexes.index("ix_customer_name", false, customer, column(customer, "name")));
        List<String> names = new ArrayList<>(List.of("ck_product", "uq_product_name", "fk_product_replaced_by",
                "ix_customer_name"));
        if (trigger) {
            sales.getOwnedElement().add(trigger("trg_customer_name", customer,
                    "BEGIN NEW.name := TRIM(NEW.name); RETURN NEW; END;"));
            names.add("trg_customer_name");
        }
        return names;
    }

    private static Column column(Table table, String name) {
        return ColumnSets.columns(table).stream().filter(c -> name.equals(c.getName())).findFirst().orElseThrow();
    }

    static CheckConstraint check(String name, String condition) {
        CheckConstraint check = R.createCheckConstraint();
        check.setName(name);
        BooleanExpression body = CoreFactory.eINSTANCE.createBooleanExpression();
        body.setLanguage("SQL");
        body.setBody(condition);
        check.setBody(body);
        return check;
    }

    /** {@code orders_of(who INTEGER) RETURNS INTEGER}: how many orders a customer placed, in PL/pgSQL. */
    private static Procedure ordersOf(Schema sales, SQLSimpleType integer) {
        Procedure function = R.createProcedure();
        function.setName("orders_of");
        function.setType(ProcedureType.FUNCTION);
        SQLParameter who = R.createSQLParameter();
        who.setName("who");
        who.setKind(ParameterDirectionKind.PDK_IN);
        who.setType(integer);
        function.getParameter().add(who);
        SQLParameter result = R.createSQLParameter();
        result.setName("returnValue");
        result.setKind(ParameterDirectionKind.PDK_RETURN);
        result.setType(integer);
        function.getParameter().add(result);
        ProcedureExpression body = CoreFactory.eINSTANCE.createProcedureExpression();
        body.setLanguage("plpgsql");
        body.setBody("BEGIN RETURN (SELECT COUNT(*) FROM \"" + sales.getName() + "\".\"orders\" o"
                + " WHERE o.\"customer_id\" = who); END;");
        function.setBody(body);
        sales.getOwnedElement().add(function);
        return function;
    }

    private static Trigger trigger(String name, Table table, String body) {
        Trigger trigger = R.createTrigger();
        trigger.setName(name);
        trigger.setTable(table);
        trigger.setConditionTiming(ConditionTimingType.BEFORE);
        trigger.setEventManipulation(EventManipulationType.INSERT);
        trigger.setActionOrientation(ActionOrientationType.ROW);
        ProcedureExpression action = CoreFactory.eINSTANCE.createProcedureExpression();
        action.setLanguage("plpgsql");
        action.setBody(body);
        trigger.setActionStatement(action);
        return trigger;
    }

    /** A role or a user, as {@code marker} says, owned by the catalog. */
    static ResponsibleParty role(Catalog owner, Stereotype marker, String name) {
        ResponsibleParty role = BusinessinformationFactory.eINSTANCE.createResponsibleParty();
        role.setName(name);
        owner.getOwnedElement().add(role);
        marker.getExtendedElement().add(role);
        return role;
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
}
