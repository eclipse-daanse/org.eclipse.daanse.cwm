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

import java.sql.Types;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.MigrationEmitterImpl;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.SchemaDifferImpl;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.db.postgresql.PostgreSqlDialect;
import org.junit.jupiter.api.Test;

/**
 * Table split/merge detected from predecessor Dependency links: two or more
 * new tables claiming the same old one (split), or one new table claiming
 * two or more old ones (merge) — offline, no database.
 */
class TableSplitMergeTest {

    private static final RelationalFactory R = RelationalFactory.eINSTANCE;
    private static final Dialect PG = new PostgreSqlDialect();

    private final SchemaDiffer differ = new SchemaDifferImpl();

    @Test
    void oneOldTableClaimedByTwoNewOnesIsASplit() {
        Schema oldS = schemaWithTable("customer", "id", "name", "email");
        Table oldCustomer = table(oldS, "customer");

        Schema newS = R.createSchema();
        newS.setName("sales");
        Table names = tableWithColumns(newS, "customer_name", "id", "name");
        Table mails = tableWithColumns(newS, "customer_mail", "id", "email");
        PredecessorLinks.link(names, oldCustomer);
        PredecessorLinks.link(mails, oldCustomer);

        SchemaDiff diff = differ.diff(oldS, newS);

        assertThat(diff.tablesSplit()).hasSize(1);
        TableSplit split = diff.tablesSplit().get(0);
        assertThat(split.oldTable()).isSameAs(oldCustomer);
        assertThat(split.newTables()).containsExactlyInAnyOrder(names, mails);
        assertThat(diff.tablesAdded()).isEmpty();
        assertThat(diff.tablesDropped()).isEmpty();
        assertThat(diff.tablesRenamed()).isEmpty();
        assertThat(diff.tablesMerged()).isEmpty();
        assertThat(diff.isEmpty()).isFalse();
    }

    @Test
    void twoOldTablesClaimedByOneNewOneIsAMerge() {
        Schema oldS = R.createSchema();
        oldS.setName("sales");
        Table names = tableWithColumns(oldS, "customer_name", "id", "name");
        Table mails = tableWithColumns(oldS, "customer_mail", "id", "email");

        Schema newS = schemaWithTable("customer", "id", "name", "email");
        Table newCustomer = table(newS, "customer");
        PredecessorLinks.link(newCustomer, names);
        PredecessorLinks.link(newCustomer, mails);

        SchemaDiff diff = differ.diff(oldS, newS);

        assertThat(diff.tablesMerged()).hasSize(1);
        TableMerge merge = diff.tablesMerged().get(0);
        assertThat(merge.newTable()).isSameAs(newCustomer);
        assertThat(merge.oldTables()).containsExactlyInAnyOrder(names, mails);
        assertThat(diff.tablesAdded()).isEmpty();
        assertThat(diff.tablesDropped()).isEmpty();
        assertThat(diff.tablesSplit()).isEmpty();
    }

    @Test
    void splitIsPlannedAsOneOpAndWarnedAbout() {
        Schema oldS = schemaWithTable("customer", "id", "name", "email");
        Table oldCustomer = table(oldS, "customer");
        Schema newS = R.createSchema();
        newS.setName("sales");
        Table names = tableWithColumns(newS, "customer_name", "id", "name");
        Table mails = tableWithColumns(newS, "customer_mail", "id", "email");
        PredecessorLinks.link(names, oldCustomer);
        PredecessorLinks.link(mails, oldCustomer);

        SchemaDiff diff = differ.diff(oldS, newS);
        List<String> warnings = ChangePlanner.create().warnings(diff);
        List<ChangeOp> ops = ChangePlanner.create().plan(diff);

        assertThat(warnings).singleElement().asString()
                .contains("customer").contains("split into").contains("row data is not migrated");
        assertThat(ops).filteredOn(ChangeOp.SplitTable.class::isInstance).hasSize(1);
        ChangeOp.SplitTable op = (ChangeOp.SplitTable) ops.stream()
                .filter(ChangeOp.SplitTable.class::isInstance).findFirst().orElseThrow();
        assertThat(op.oldTable()).isSameAs(oldCustomer);
        assertThat(op.newTables()).containsExactlyInAnyOrder(names, mails);
    }

    @Test
    void mergeEmitsAWarningCommentThenDropsTheOldsAndCreatesTheNewOne() {
        Schema oldS = R.createSchema();
        oldS.setName("sales");
        Table names = tableWithColumns(oldS, "customer_name", "id", "name");
        Table mails = tableWithColumns(oldS, "customer_mail", "id", "email");
        Schema newS = schemaWithTable("customer", "id", "name", "email");
        Table newCustomer = table(newS, "customer");
        PredecessorLinks.link(newCustomer, names);
        PredecessorLinks.link(newCustomer, mails);

        SchemaDiff diff = differ.diff(oldS, newS);
        List<ChangeOp> ops = ChangePlanner.create().plan(diff);
        MigrationEmitter emitter = new MigrationEmitterImpl(new DdlGeneratorFactoryImpl());
        List<String> sql = emitter.emit(ops, PG);

        assertThat(sql.get(0)).startsWith("--").contains("merged into").contains("not migrated");
        assertThat(sql).anySatisfy(s -> assertThat(s).containsIgnoringCase("drop table").contains("customer_name"));
        assertThat(sql).anySatisfy(s -> assertThat(s).containsIgnoringCase("drop table").contains("customer_mail"));
        assertThat(sql).anySatisfy(s -> assertThat(s).containsIgnoringCase("create table").contains("customer"));
    }

    @Test
    void withoutDependencyLinksAmbiguousClaimsFallBackToTheOldAddedPlusWarnBehaviour() {
        Schema oldS = schemaWithTable("customer", "id");
        Schema newS = R.createSchema();
        newS.setName("sales");
        // no predecessor links, no matching name: two unrelated new tables
        tableWithColumns(newS, "customer_name", "id");
        tableWithColumns(newS, "customer_mail", "id");

        SchemaDiff diff = differ.diff(oldS, newS, DiffSettings.defaults().withDependencyLinks(false));

        assertThat(diff.tablesSplit()).isEmpty();
        assertThat(diff.tablesMerged()).isEmpty();
        assertThat(diff.tablesAdded()).hasSize(2);
        assertThat(diff.tablesDropped()).hasSize(1);
    }

    // fixtures

    private static Schema schemaWithTable(String tableName, String... colNames) {
        Schema s = R.createSchema();
        s.setName("sales");
        tableWithColumns(s, tableName, colNames);
        return s;
    }

    private static Table tableWithColumns(Schema s, String tableName, String... colNames) {
        Table t = R.createTable();
        t.setName(tableName);
        s.getOwnedElement().add(t);
        for (String col : colNames) {
            column(t, col);
        }
        return t;
    }

    private static void column(Table t, String name) {
        SQLSimpleType type = R.createSQLSimpleType();
        type.setName("INTEGER");
        type.setTypeNumber(Types.INTEGER);
        Column c = R.createColumn();
        c.setName(name);
        c.setType((Classifier) type);
        c.setIsNullable(NullableType.COLUMN_NULLABLE);
        t.getFeature().add(c);
    }

    private static Table table(Schema s, String name) {
        return s.getOwnedElement().stream()
                .filter(Table.class::isInstance).map(Table.class::cast)
                .filter(t -> name.equals(t.getName())).findFirst().orElseThrow();
    }
}
