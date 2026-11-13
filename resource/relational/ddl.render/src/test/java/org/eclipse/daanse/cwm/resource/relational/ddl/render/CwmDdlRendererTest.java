/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.resource.relational.ddl.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Types;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.DatatypesFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.datatypes.QueryExpression;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ProcedureExpression;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLIndexes;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.View;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ActionOrientationType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ConditionTimingType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.EventManipulationType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.junit.jupiter.api.Test;

/** Verifies multi-dialect DDL emission for relational objects. */
class CwmDdlRendererTest {

    private static final RelationalFactory R = RelationalFactory.eINSTANCE;

    private final CwmDdlRenderer renderer =
        new CwmDdlRenderer(Dialects.of(List.of("ansi", "postgresql", "mysql")));

    private Schema sampleSchema() {
        Schema s = R.createSchema();
        s.setName("sales");
        SQLSimpleType tInt = type(s, "INTEGER", Types.INTEGER, 0);
        SQLSimpleType tVar = type(s, "VARCHAR", Types.VARCHAR, 255);
        Table customer = R.createTable();
        customer.setName("customer");
        s.getOwnedElement().add(customer);
        Column id = column(customer, "id", tInt, false);
        column(customer, "email", tVar, true);
        PrimaryKey pk = R.createPrimaryKey();
        pk.setName("pk_customer");
        pk.getFeature().add(id);
        customer.getOwnedElement().add(pk);

        View view = R.createView();
        view.setName("v_customer");
        view.setIsReadOnly(true);
        QueryExpression qe = DatatypesFactory.eINSTANCE.createQueryExpression();
        qe.setLanguage("SQL");
        qe.setBody("SELECT id FROM customer");
        view.setQueryExpression(qe);
        s.getOwnedElement().add(view);
        return s;
    }

    @Test
    void emitsCreateTablePerDialect() {
        Schema s = sampleSchema();
        Table customer = (Table) s.getOwnedElement().stream()
            .filter(Table.class::isInstance).findFirst().orElseThrow();

        List<DialectDdl> ddl = renderer.render(customer);
        assertThat(ddl).hasSize(3);
        assertThat(ddl).extracting(DialectDdl::dialect).containsExactly("ANSI", "PostgreSQL", "MySQL");
        for (DialectDdl d : ddl) {
            assertThat(String.join("\n", d.statements()))
                .containsIgnoringCase("create table")
                .contains("customer");
        }
        // dialect-specific identifier quoting differs: MySQL uses backticks, ANSI/PostgreSQL double-quotes
        assertThat(String.join("\n", ddl.get(2).statements())).contains("`customer`");
        assertThat(String.join("\n", ddl.get(0).statements())).contains("\"customer\"");
    }

    @Test
    void emitsCreateViewAndFullSchema() {
        Schema s = sampleSchema();
        View view = (View) s.getOwnedElement().stream()
            .filter(View.class::isInstance).findFirst().orElseThrow();

        assertThat(String.join("\n", renderer.render(view).get(0).statements()))
            .containsIgnoringCase("create view").contains("v_customer");

        // a Schema page renders the whole CREATE script (table + view)
        String schemaDdl = String.join("\n", renderer.render(s).get(0).statements());
        assertThat(schemaDdl).containsIgnoringCase("create table").containsIgnoringCase("create view");
    }

    @Test
    void tableScriptCoversEverythingBelowIt() {
        Schema s = sampleSchema();
        Table customer = (Table) s.getOwnedElement().stream()
            .filter(Table.class::isInstance).findFirst().orElseThrow();
        Column email = customer.getFeature().stream().filter(Column.class::isInstance)
            .map(Column.class::cast).filter(c -> "email".equals(c.getName())).findFirst().orElseThrow();

        UniqueConstraint uq = R.createUniqueConstraint();
        uq.setName("uq_customer_email");
        uq.getFeature().add(email);
        customer.getOwnedElement().add(uq);

        s.getOwnedElement().add(SQLIndexes.index("ix_customer_email", false, customer, email));

        Trigger trg = R.createTrigger();
        trg.setName("trg_customer_audit");
        trg.setEventManipulation(EventManipulationType.UPDATE);
        trg.setActionOrientation(ActionOrientationType.ROW);
        trg.setConditionTiming(ConditionTimingType.AFTER);
        ProcedureExpression body = CoreFactory.eINSTANCE.createProcedureExpression();
        body.setLanguage("SQL");
        body.setBody("INSERT INTO audit_log VALUES (NEW.id)");
        trg.setActionStatement(body);
        customer.getTrigger().add(trg);
        s.getOwnedElement().add(trg);

        String ansi = String.join("\n", renderer.render(customer).get(0).statements());
        assertThat(ansi)
            .containsIgnoringCase("create table")
            .contains("uq_customer_email")
            .contains("ix_customer_email")
            .contains("trg_customer_audit");
        // the sibling view belongs to the schema, not below the table
        assertThat(ansi).doesNotContain("v_customer");
    }

    @Test
    void nonRelationalObjectsYieldNothing() {
        // a SQLSimpleType has no standalone DDL
        SQLSimpleType t = sampleSchema().getOwnedElement().stream()
            .filter(SQLSimpleType.class::isInstance).map(SQLSimpleType.class::cast).findFirst().orElseThrow();
        assertThat(renderer.render(t)).isEmpty();
    }

    private static SQLSimpleType type(Schema s, String name, int jdbc, int maxLen) {
        SQLSimpleType t = R.createSQLSimpleType();
        t.setName(name);
        t.setTypeNumber(jdbc);
        if (maxLen > 0) {
            t.setCharacterMaximumLength((long) maxLen);
        }
        s.getOwnedElement().add(t);
        return t;
    }

    private static Column column(Table table, String name, SQLSimpleType type, boolean nullable) {
        Column c = R.createColumn();
        c.setName(name);
        c.setType((Classifier) type);
        c.setIsNullable(nullable ? NullableType.COLUMN_NULLABLE : NullableType.COLUMN_NO_NULLS);
        table.getFeature().add(c);
        return c;
    }
}
