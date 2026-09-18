/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.cwm.resource.relational.liquibase.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Types;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.resource.relational.diff.api.ChangeOp;
import org.junit.jupiter.api.Test;

/**
 * Delta-Writer offline: XML assertions. The round-trip via actual Liquibase
 * (test-scope liquibase-core against H2/PG) is a task for the implementation phase.
 */
class LiquibaseChangelogWriterTest {

    private static final RelationalFactory R = RelationalFactory.eINSTANCE;

    private final LiquibaseChangelogWriter writer = new LiquibaseChangelogWriter();

    @Test
    void renameColumnBecomesOneChangeSetWithRollback() {
        Table customer = customerTable();
        Column email = (Column) customer.getFeature().get(1);

        String xml = writer.write(List.of(new ChangeOp.RenameColumn(customer, email, "mail")));

        assertThat(xml)
                .contains("<databaseChangeLog")
                .contains("author=\"cwm-diff\"")
                .contains("<renameColumn")
                .contains("tableName=\"customer\"")
                .contains("oldColumnName=\"mail\"")
                .contains("newColumnName=\"email\"")
                .contains("<rollback>");
    }

    @Test
    void idsAreDeterministic() {
        Table customer = customerTable();
        List<ChangeOp> ops = List.of(new ChangeOp.RenameTable(customer, "kunde"));

        assertThat(writer.write(ops)).isEqualTo(writer.write(ops));
    }

    @Test
    void dropColumnGetsEmptyRollbackWithComment() {
        Table customer = customerTable();
        Column email = (Column) customer.getFeature().get(1);

        String xml = writer.write(List.of(new ChangeOp.DropColumn(customer, email)));

        assertThat(xml).contains("<dropColumn");
        // nicht ableitbare Inverse: leeres rollback, damit `rollback` explizit scheitert
        assertThat(xml).contains("<rollback/>");
    }

    @Test
    void snapshotWritesCreateChangelog() {
        Table customer = customerTable();
        Schema schema = (Schema) customer.getNamespace();

        String xml = new LiquibaseSnapshotWriter().write(schema);

        assertThat(xml)
                .contains("author=\"cwm-snapshot\"")
                .contains("<createTable")
                .contains("tableName=\"customer\"")
                .contains("<column name=\"id\"")
                .contains("<column name=\"email\"");
    }

    // fixture

    private static Table customerTable() {
        Schema s = R.createSchema();
        s.setName("sales");
        SQLSimpleType tVar = R.createSQLSimpleType();
        tVar.setName("VARCHAR");
        tVar.setTypeNumber(Types.VARCHAR);
        tVar.setCharacterMaximumLength(255L);
        s.getOwnedElement().add(tVar);
        Table customer = R.createTable();
        customer.setName("customer");
        s.getOwnedElement().add(customer);
        column(customer, "id", tVar);
        column(customer, "email", tVar);
        return customer;
    }

    private static Column column(Table table, String name, SQLSimpleType type) {
        Column c = R.createColumn();
        c.setName(name);
        c.setType((Classifier) type);
        c.setIsNullable(NullableType.COLUMN_NULLABLE);
        table.getFeature().add(c);
        return c;
    }
}
