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

import java.util.List;
import java.util.Locale;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ProcedureExpression;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.ForeignKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ActionOrientationType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ConditionTimingType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.EventManipulationType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ReferentialRuleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Catalogs;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Columns;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ForeignKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.PrimaryKeys;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Tables;
import org.eclipse.daanse.sql.dialect.db.postgresql.PostgreSqlDialect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The statements do not depend on the default locale of the JVM: in Turkish, {@code "i"} upper-cases
 * to a dotted {@code "İ"}, so a keyword compared after {@code toUpperCase()} no longer matches.
 */
class DefaultLocaleTest {

    private Locale before;

    @BeforeEach
    void turkish() {
        before = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
    }

    @AfterEach
    void restore() {
        Locale.setDefault(before);
    }

    @Test
    void anInsertTriggerAndRestrictAndNoActionRulesAreWrittenInTurkishToo() {
        Catalog shop = Catalogs.create("shop");
        Schema sales = Schemas.create(shop, "sales");
        Table customer = Tables.create(sales, "customer");
        Column id = Columns.create(customer, "id", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        PrimaryKey key = PrimaryKeys.create(customer, "pk_customer", id);
        Table orders = Tables.create(sales, "orders");
        Column customerId = Columns.create(orders, "customer_id", SQLSimpleTypes.ownedIn(shop, "INTEGER"));
        ForeignKey fk = ForeignKeys.create(orders, "fk_orders_customer", key, customerId);
        fk.setDeleteRule(ReferentialRuleType.IMPORTED_KEY_RESTRICT);
        fk.setUpdateRule(ReferentialRuleType.IMPORTED_KEY_NO_ACTION);
        Trigger audit = RelationalFactory.eINSTANCE.createTrigger();
        audit.setName("trg_orders_audit");
        audit.setTable(orders);
        audit.setConditionTiming(ConditionTimingType.AFTER);
        audit.setEventManipulation(EventManipulationType.INSERT);
        audit.setActionOrientation(ActionOrientationType.ROW);
        ProcedureExpression body = CoreFactory.eINSTANCE.createProcedureExpression();
        body.setLanguage("plpgsql");
        body.setBody("BEGIN RETURN NEW; END;");
        audit.setActionStatement(body);
        sales.getOwnedElement().add(audit);

        List<String> ddl = new DdlGeneratorImpl(new PostgreSqlDialect()).createSchema(sales);

        assertThat(ddl).anyMatch(s -> s.startsWith("CREATE TRIGGER") && s.contains("AFTER INSERT"));
        assertThat(ddl).filteredOn(s -> s.contains("FOREIGN KEY")).singleElement()
                .satisfies(s -> assertThat(s).contains("ON DELETE RESTRICT").contains("ON UPDATE NO ACTION"));
    }
}
