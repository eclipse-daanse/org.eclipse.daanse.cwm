/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.resource.relational.diff.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.BooleanExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ProcedureExpression;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.CheckConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ActionOrientationType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ConditionTimingType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.EventManipulationType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.PrimaryKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLIndexes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.MigrationEmitterImpl;
import org.eclipse.daanse.sql.dialect.db.postgresql.PostgreSqlDialect;
import org.junit.jupiter.api.Test;

/**
 * A migration drops what the create script of the old model created: the same names for keys,
 * constraints and indexes without a name of their own, and the function of the very trigger it drops.
 */
class GeneratorAgreementTest {

    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;
    private static final PostgreSqlDialect PG = new PostgreSqlDialect();

    private final MigrationEmitter emitter = new MigrationEmitterImpl(new DdlGeneratorFactoryImpl());

    @Test
    void aMigrationDropsUnnamedKeysConstraintsAndIndexesByTheNamesTheyWereCreatedWith() {
        Catalog shop = Catalogs.create("shop");
        Schema sales = Schemas.create(shop, "sales");
        Table customer = Tables.create(sales, "customer");
        Column id = Columns.create(customer, "id", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        PrimaryKey customerKey = PrimaryKeys.create(customer, "pk_customer", id);
        Table orders = Tables.create(sales, "orders");
        Column code = Columns.create(orders, "code", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        Column note = Columns.create(orders, "note", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        Column customerId = Columns.create(orders, "customer_id", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        UniqueConstraint byCode = unique(orders, code);
        UniqueConstraint byNote = unique(orders, note);
        CheckConstraint positive = check(orders, "code > 0");
        ForeignKey toCustomer = RF.createForeignKey();
        toCustomer.getFeature().add(customerId);
        toCustomer.setUniqueKey(customerKey);
        orders.getOwnedElement().add(toCustomer);
        SQLIndex onNote = SQLIndexes.index(null, false, orders, note);
        sales.getOwnedElement().add(onNote);

        List<String> created = names(new DdlGeneratorFactoryImpl().create(PG).createSchema(sales));
        List<String> dropped = names(emitter.emit(List.of(new ChangeOp.DropForeignKey(orders, toCustomer),
                new ChangeOp.DropIndex(onNote), new ChangeOp.DropCheckConstraint(orders, positive),
                new ChangeOp.DropUniqueConstraint(orders, byCode), new ChangeOp.DropUniqueConstraint(orders, byNote)),
                PG));

        assertThat(dropped).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(created);
    }

    @Test
    void droppingOneOfTwoTriggersWithTheSameBodyDropsItsOwnFunction() {
        Catalog shop = Catalogs.create("shop");
        Schema sales = Schemas.create(shop, "sales");
        Table orders = Tables.create(sales, "orders");
        Columns.create(orders, "qty", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        Trigger first = trigger(sales, orders, "trg_orders_first");
        Trigger second = trigger(sales, orders, "trg_orders_second");

        List<String> created = new DdlGeneratorFactoryImpl().create(PG).createSchema(sales);
        List<String> dropSecond = emitter.emit(List.of(new ChangeOp.DropTrigger(orders, second)), PG);

        List<String> createdFunctions = functions(created);
        List<String> droppedFunctions = functions(dropSecond);
        assertThat(droppedFunctions).singleElement().satisfies(f -> {
            assertThat(createdFunctions).as("dropped a function the create script made").contains(f);
            assertThat(created).as("the first trigger does not call it")
                    .noneMatch(s -> s.startsWith("CREATE TRIGGER \"" + first.getName() + "\"") && s.contains(f));
        });
    }

    private static UniqueConstraint unique(Table table, Column column) {
        UniqueConstraint unique = RF.createUniqueConstraint();
        unique.getFeature().add(column);
        table.getOwnedElement().add(unique);
        return unique;
    }

    private static CheckConstraint check(Table table, String condition) {
        CheckConstraint check = RF.createCheckConstraint();
        BooleanExpression body = CoreFactory.eINSTANCE.createBooleanExpression();
        body.setLanguage("SQL");
        body.setBody(condition);
        check.setBody(body);
        table.getOwnedElement().add(check);
        return check;
    }

    private static Trigger trigger(Schema owner, Table table, String name) {
        Trigger trigger = RF.createTrigger();
        trigger.setName(name);
        trigger.setTable(table);
        trigger.setConditionTiming(ConditionTimingType.BEFORE);
        trigger.setEventManipulation(EventManipulationType.UPDATE);
        trigger.setActionOrientation(ActionOrientationType.ROW);
        ProcedureExpression body = CoreFactory.eINSTANCE.createProcedureExpression();
        body.setLanguage("plpgsql");
        body.setBody("BEGIN RETURN NEW; END;");
        trigger.setActionStatement(body);
        owner.getOwnedElement().add(trigger);
        return trigger;
    }

    private static final Pattern NAMED = Pattern.compile(
            "(?:CONSTRAINT (?:IF EXISTS )?|INDEX (?:IF (?:NOT )?EXISTS )?(?:\"sales\"\\.)?)\"([^\"]+)\"");

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

    private static final Pattern FUNCTION = Pattern.compile("FUNCTION (?:IF EXISTS )?(\"[^(]+)\\(");

    /** The functions the statements create or drop, as written. */
    private static List<String> functions(List<String> statements) {
        List<String> functions = new ArrayList<>();
        for (String statement : statements) {
            if (statement.startsWith("CREATE TRIGGER")) {
                continue;
            }
            Matcher m = FUNCTION.matcher(statement);
            if (m.find()) {
                functions.add(m.group(1));
            }
        }
        return functions;
    }
}
