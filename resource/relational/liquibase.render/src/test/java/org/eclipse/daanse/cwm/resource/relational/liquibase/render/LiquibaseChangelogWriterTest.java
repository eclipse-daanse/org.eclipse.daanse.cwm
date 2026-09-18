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

import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.BusinessinformationFactory;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.Description;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.CoreFactory;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.ProcedureExpression;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Trigger;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.UniqueConstraint;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ActionOrientationType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ConditionTimingType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.EventManipulationType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.resource.relational.ddl.api.DdlSettings;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.eclipse.daanse.cwm.resource.relational.diff.api.ChangeOp;
import org.eclipse.daanse.cwm.resource.relational.diff.api.MigrationEmitter;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.MigrationEmitterImpl;
import org.junit.jupiter.api.Test;

/**
 * Delta-Writer offline: XML assertions. The round-trip via actual Liquibase
 * (test-scope liquibase-core against H2/PG) is a task for the implementation phase.
 */
class LiquibaseChangelogWriterTest {

    private static final RelationalFactory R = RelationalFactory.eINSTANCE;

    private static final MigrationEmitter EMITTER = new MigrationEmitterImpl(new DdlGeneratorFactoryImpl());

    private final LiquibaseChangelogWriter writer = new LiquibaseChangelogWriter(EMITTER);

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

        String xml = new LiquibaseSnapshotWriter(EMITTER).write(schema);

        assertThat(xml)
                .contains("author=\"cwm-snapshot\"")
                .contains("<createTable")
                .contains("tableName=\"customer\"")
                .contains("<column name=\"id\"")
                .contains("<column name=\"email\"");
    }

    @Test
    void renameIndexUsesEachDialectsStatementAndRendersTheRollbackFromTheSameIndex() {
        Table customer = customerTable();
        SQLIndex ix = R.createSQLIndex();
        ix.setName("ix_email_v2");
        ix.setSpannedClass(customer);
        customer.getNamespace().getOwnedElement().add(ix);

        String xml = writer.write(List.of(new ChangeOp.RenameIndex(ix, "ix_email")));

        assertThat(xml)
                .contains("<sql dbms=\"postgresql,oracle\" splitStatements=\"false\">ALTER INDEX "
                        + "&quot;sales&quot;.&quot;ix_email&quot; RENAME TO &quot;ix_email_v2&quot;</sql>")
                .contains("<sql dbms=\"mysql,mariadb\" splitStatements=\"false\">ALTER TABLE `sales`.`customer`"
                        + " RENAME INDEX `ix_email_v2` TO `ix_email`</sql>")
                .contains("<sql dbms=\"mssql\"");
        assertThat(ix.getName()).isEqualTo("ix_email_v2");
    }

    @Test
    void renameConstraintFallsBackWhereTheDialectCannotRename() {
        Table customer = customerTable();
        UniqueConstraint uq = R.createUniqueConstraint();
        uq.setName("uq_email_v2");
        uq.getFeature().add((Column) customer.getFeature().get(1));
        customer.getOwnedElement().add(uq);

        String xml = writer.write(List.of(new ChangeOp.RenameConstraint(customer, uq, "uq_email")));

        String rollback = xml.substring(xml.indexOf("<rollback>"));
        assertThat(xml).contains("RENAME CONSTRAINT &quot;uq_email&quot; TO &quot;uq_email_v2&quot;")
                .contains("<sql dbms=\"mysql\" splitStatements=\"false\">ALTER TABLE `sales`.`customer`"
                        + " DROP CONSTRAINT `uq_email`</sql>");
        assertThat(rollback).contains("RENAME CONSTRAINT &quot;uq_email_v2&quot; TO &quot;uq_email&quot;")
                .contains("ADD CONSTRAINT `uq_email` UNIQUE (`email`)");
        assertThat(uq.getName()).isEqualTo("uq_email_v2");
    }

    @Test
    void triggerIsWrittenPerDialectWithoutSplittingItsBody() {
        Table customer = customerTable();
        Trigger trg = R.createTrigger();
        trg.setName("trg_audit");
        trg.setConditionTiming(ConditionTimingType.BEFORE);
        trg.setEventManipulation(EventManipulationType.INSERT);
        trg.setActionOrientation(ActionOrientationType.ROW);
        ProcedureExpression body = CoreFactory.eINSTANCE.createProcedureExpression();
        body.setBody("BEGIN RETURN NEW; END;");
        trg.setActionStatement(body);
        customer.getTrigger().add(trg);
        customer.getNamespace().getOwnedElement().add(trg);

        String xml = writer.write(List.of(new ChangeOp.CreateTrigger(customer, trg)));
        String forward = xml.substring(0, xml.indexOf("<rollback>"));
        String rollback = xml.substring(xml.indexOf("<rollback>"));

        assertThat(forward).contains("<sql dbms=\"postgresql\" splitStatements=\"false\">CREATE")
                .contains("trg_audit_fn").contains("BEGIN RETURN NEW; END;").contains("CREATE TRIGGER");
        assertThat(rollback).contains("DROP TRIGGER").contains("DROP FUNCTION");

        String snapshot = new LiquibaseSnapshotWriter(EMITTER).write((Schema) customer.getNamespace());
        assertThat(snapshot.indexOf("CREATE TRIGGER")).isGreaterThan(snapshot.indexOf("<createTable"));
    }

    @Test
    void commentsBecomeRemarks() {
        Table customer = customerTable();
        Column email = (Column) customer.getFeature().get(1);

        String xml = writer.write(List.of(new ChangeOp.SetComment(customer, customer, "customers"),
                new ChangeOp.SetComment(customer, email, null)));

        assertThat(xml)
                .contains("<setTableRemarks schemaName=\"sales\" tableName=\"customer\" remarks=\"customers\"/>")
                .contains("<setColumnRemarks schemaName=\"sales\" tableName=\"customer\" columnName=\"email\"")
                .contains("remarks=\"\"/>");
    }

    @Test
    void snapshotCarriesComments() {
        Table customer = customerTable();
        Schema schema = (Schema) customer.getNamespace();
        Description d = BusinessinformationFactory.eINSTANCE.createDescription();
        d.setType(DdlSettings.COMMENT_TYPE_JDBC_REMARKS);
        d.setBody("customers");
        d.getModelElement().add(customer);
        schema.getOwnedElement().add(d);

        assertThat(new LiquibaseSnapshotWriter(EMITTER).write(schema))
                .contains("<setTableRemarks schemaName=\"sales\" tableName=\"customer\" remarks=\"customers\"/>");
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
