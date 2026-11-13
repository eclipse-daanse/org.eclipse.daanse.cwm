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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.BooleanExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.PrimaryKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLIndexes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.sql.dialect.db.postgresql.PostgreSqlDialect;
import org.junit.jupiter.api.Test;

/**
 * Keys, constraints and indexes without a name get one when they are written; the drop script must
 * name what the create script named, and two of a kind on one table must not share a name.
 */
class DefaultNamesTest {

    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;

    private final DdlGeneratorImpl generator = new DdlGeneratorImpl(new PostgreSqlDialect());

    @Test
    void theDropScriptNamesWhatTheCreateScriptNamed() {
        Schema sales = sales();

        List<String> created = names(generator.createSchema(sales));
        List<String> dropped = names(generator.dropSchema(sales));

        assertThat(created).isNotEmpty();
        assertThat(dropped).containsExactlyInAnyOrderElementsOf(created);
    }

    @Test
    void twoUnnamedUniqueConstraintsOrChecksOfOneTableGetNamesOfTheirOwn() {
        Schema sales = sales();

        List<String> created = names(generator.createSchema(sales));

        assertThat(created).doesNotHaveDuplicates();
    }

    /**
     * {@code ORDERS} with unnamed keys, constraints and indexes, each kind led by one the generator
     * cannot write: an index without columns, a check without a condition, a foreign key without
     * its key.
     */
    private static Schema sales() {
        Catalog shop = Catalogs.create("shop");
        Schema sales = Schemas.create(shop, "sales");
        Table customer = Tables.create(sales, "customer");
        Column id = Columns.create(customer, "id", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        PrimaryKey customerKey = PrimaryKeys.create(customer, "pk_customer", id);

        Table orders = Tables.create(sales, "orders");
        Column orderId = Columns.create(orders, "id", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        Column code = Columns.create(orders, "code", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        Column note = Columns.create(orders, "note", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        Column customerId = Columns.create(orders, "customer_id", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        PrimaryKeys.create(orders, "pk_orders", orderId);

        SQLIndex empty = RF.createSQLIndex();
        empty.setSpannedClass(orders);
        sales.getOwnedElement().add(empty);
        SQLIndex onNote = SQLIndexes.index(null, false, orders, note);
        sales.getOwnedElement().add(onNote);

        orders.getOwnedElement().add(check(null));
        orders.getOwnedElement().add(check("id > 0"));
        orders.getOwnedElement().add(check("code > 0"));

        UniqueConstraint byCode = RF.createUniqueConstraint();
        byCode.getFeature().add(code);
        orders.getOwnedElement().add(byCode);
        UniqueConstraint byNote = RF.createUniqueConstraint();
        byNote.getFeature().add(note);
        orders.getOwnedElement().add(byNote);

        ForeignKey withoutKey = RF.createForeignKey();
        withoutKey.getFeature().add(customerId);
        orders.getOwnedElement().add(withoutKey);
        ForeignKey toCustomer = RF.createForeignKey();
        toCustomer.getFeature().add(customerId);
        toCustomer.setUniqueKey(customerKey);
        orders.getOwnedElement().add(toCustomer);
        return sales;
    }

    private static CheckConstraint check(String condition) {
        CheckConstraint check = RF.createCheckConstraint();
        if (condition != null) {
            BooleanExpression body = CoreFactory.eINSTANCE.createBooleanExpression();
            body.setLanguage("SQL");
            body.setBody(condition);
            check.setBody(body);
        }
        return check;
    }

    private static final Pattern NAMED = Pattern.compile(
            "(?:CONSTRAINT (?:IF EXISTS )?|INDEX (?:IF (?:NOT )?EXISTS )?(?:\"sales\"\\.)?)\"([^\"]+)\"");

    /** The names of the constraints and indexes the statements create or drop, primary keys aside. */
    private static List<String> names(List<String> statements) {
        List<String> names = new ArrayList<>();
        for (String statement : statements) {
            if (statement.startsWith("CREATE TABLE")) {
                continue;
            }
            Matcher m = NAMED.matcher(statement);
            if (m.find()) {
                names.add(m.group(1));
            }
        }
        return names;
    }
}
