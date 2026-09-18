/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.resource.relational.dml.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Types;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.PrimaryKey;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.junit.jupiter.api.Test;

/**
 * DML templates across all kinds; fixture idiom as in CwmDdlRendererTest:
 * sales.customer(id PK, name, email).
 */
class DmlTemplateGeneratorTest {

    private static final RelationalFactory R = RelationalFactory.eINSTANCE;

    private final DmlTemplateGenerator ansi = new DmlTemplateGenerator(new AnsiDialect());

    @Test
    void selectByPkCarriesOnePkParameter() {
        DmlTemplates t = ansi.templates(customerTable());
        DmlTemplate byPk = t.find(TemplateKind.SELECT_BY_PK).orElseThrow();
        assertThat(byPk.sql()).isEqualTo(
                "SELECT \"id\", \"name\", \"email\" FROM \"sales\".\"customer\" WHERE \"id\" = ?");
        assertThat(byPk.parameters()).hasSize(1);
        assertThat(byPk.parameters().get(0).name()).isEqualTo("id");
    }

    @Test
    void insertListsAllColumnsInOrder() {
        DmlTemplate insert = ansi.templates(customerTable()).find(TemplateKind.INSERT).orElseThrow();
        assertThat(insert.sql()).isEqualTo(
                "INSERT INTO \"sales\".\"customer\" (\"id\", \"name\", \"email\") VALUES (?, ?, ?)");
        assertThat(insert.parameters()).extracting(p -> p.name()).containsExactly("id", "name", "email");
    }

    @Test
    void updateByPkSetsNonPkColumnsPkLast() {
        DmlTemplate update = ansi.templates(customerTable()).find(TemplateKind.UPDATE_BY_PK).orElseThrow();
        assertThat(update.sql()).isEqualTo(
                "UPDATE \"sales\".\"customer\" SET \"name\" = ?, \"email\" = ? WHERE \"id\" = ?");
        assertThat(update.parameters()).extracting(p -> p.name()).containsExactly("name", "email", "id");
    }

    @Test
    void deleteByPk() {
        DmlTemplate delete = ansi.templates(customerTable()).find(TemplateKind.DELETE_BY_PK).orElseThrow();
        assertThat(delete.sql()).isEqualTo("DELETE FROM \"sales\".\"customer\" WHERE \"id\" = ?");
    }

    @Test
    void namedStyleUsesColonParameters() {
        DmlTemplate byPk = ansi
                .templates(customerTable(), DmlSettings.defaults().withParameterStyle(ParameterStyle.NAMED))
                .find(TemplateKind.SELECT_BY_PK).orElseThrow();
        assertThat(byPk.sql()).endsWith("WHERE \"id\" = :id");
    }

    @Test
    void withoutPrimaryKeyOnlySelectAllPageAndInsertRemain() {
        Table noPk = customerTable();
        noPk.getOwnedElement().removeIf(PrimaryKey.class::isInstance);

        DmlTemplates t = ansi.templates(noPk);
        assertThat(t.find(TemplateKind.SELECT_BY_PK)).isEmpty();
        assertThat(t.find(TemplateKind.UPDATE_BY_PK)).isEmpty();
        assertThat(t.find(TemplateKind.DELETE_BY_PK)).isEmpty();
        assertThat(t.find(TemplateKind.SELECT_ALL)).isPresent();
        assertThat(t.find(TemplateKind.INSERT)).isPresent();
    }

    @Test
    void pkOnlyTableHasNoUpdate() {
        Schema s = R.createSchema();
        s.setName("sales");
        SQLSimpleType tInt = type(s, "INTEGER", Types.INTEGER, 0);
        Table link = R.createTable();
        link.setName("customer_tag");
        s.getOwnedElement().add(link);
        Column c1 = column(link, "customer_id", tInt, false);
        Column c2 = column(link, "tag_id", tInt, false);
        PrimaryKey pk = R.createPrimaryKey();
        pk.setName("pk_customer_tag");
        pk.getFeature().add(c1);
        pk.getFeature().add(c2);
        link.getOwnedElement().add(pk);

        DmlTemplates t = ansi.templates(link);
        assertThat(t.find(TemplateKind.UPDATE_BY_PK)).isEmpty();
        // composite pk: by-pk templates chain all key columns
        assertThat(t.find(TemplateKind.DELETE_BY_PK).orElseThrow().sql())
                .isEqualTo("DELETE FROM \"sales\".\"customer_tag\""
                        + " WHERE \"customer_id\" = ? AND \"tag_id\" = ?");
    }

    // ------------------------------------------------------------------ fixture

    private static Table customerTable() {
        Schema s = R.createSchema();
        s.setName("sales");
        SQLSimpleType tInt = type(s, "INTEGER", Types.INTEGER, 0);
        SQLSimpleType tVar = type(s, "VARCHAR", Types.VARCHAR, 255);
        Table customer = R.createTable();
        customer.setName("customer");
        s.getOwnedElement().add(customer);
        Column id = column(customer, "id", tInt, false);
        column(customer, "name", tVar, true);
        column(customer, "email", tVar, true);
        PrimaryKey pk = R.createPrimaryKey();
        pk.setName("pk_customer");
        pk.getFeature().add(id);
        customer.getOwnedElement().add(pk);
        return customer;
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
