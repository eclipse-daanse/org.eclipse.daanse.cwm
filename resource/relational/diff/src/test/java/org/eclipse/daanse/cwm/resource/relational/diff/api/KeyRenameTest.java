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

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndexColumn;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLIndexes;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.MigrationEmitterImpl;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.SchemaDifferImpl;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.support.KeyRenameFixtures;
import org.eclipse.daanse.sql.dialect.db.h2.H2Dialect;
import org.eclipse.daanse.sql.dialect.db.mariadb.MariaDBDialect;
import org.junit.jupiter.api.Test;

/** Index and constraint renames: detection, planning and dialect fallbacks, offline. */
class KeyRenameTest {

    private final SchemaDiffer differ = new SchemaDifferImpl();

    @Test
    void sameShapeUnderNewNamesIsDetectedAsRenames() {
        SchemaDiff diff = differ.diff(schema(""), schema("_V2"));

        TableDiff customers = changed(diff, "CUSTOMERS");
        assertThat(customers.indexesRenamed()).extracting(r -> r.oldIndex().getName() + "->" + r.newIndex().getName())
                .containsExactly("IX_CUSTOMERS_NAME->IX_CUSTOMERS_NAME_V2");
        assertThat(customers.constraintsRenamed())
                .extracting(r -> r.oldConstraint().getName() + "->" + r.newConstraint().getName())
                .containsExactlyInAnyOrder("PK_CUSTOMERS->PK_CUSTOMERS_V2",
                        "UQ_CUSTOMERS_EMAIL->UQ_CUSTOMERS_EMAIL_V2", "CK_CUSTOMERS_ID->CK_CUSTOMERS_ID_V2");
        assertThat(customers.pkChange()).isNull();
        assertThat(customers.indexesAdded()).isEmpty();
        assertThat(customers.indexesDropped()).isEmpty();
        assertThat(customers.uniqueConstraintsAdded()).isEmpty();
        assertThat(customers.checksDropped()).isEmpty();

        TableDiff orders = changed(diff, "ORDERS");
        assertThat(orders.constraintsRenamed()).extracting(r -> r.newConstraint().getName())
                .containsExactly("FK_ORDERS_CUSTOMERS_V2");
        assertThat(orders.foreignKeysAdded()).isEmpty();
        assertThat(orders.foreignKeysDropped()).isEmpty();
    }

    @Test
    void plannerEmitsRenameOps() {
        List<ChangeOp> ops = ChangePlanner.create().plan(differ.diff(schema(""), schema("_V2")));

        assertThat(ops).filteredOn(ChangeOp.RenameIndex.class::isInstance).hasSize(1);
        assertThat(ops).filteredOn(ChangeOp.RenameConstraint.class::isInstance).hasSize(4);
        assertThat(ops).noneMatch(op -> op instanceof ChangeOp.DropIndex || op instanceof ChangeOp.CreateIndex
                || op instanceof ChangeOp.DropForeignKey || op instanceof ChangeOp.AddForeignKey);
    }

    @Test
    void changedShapeUnderSameNameIsDropPlusAdd() {
        Schema old = schema("");
        Schema neu = schema("");
        // same index name, now on EMAIL instead of NAME
        SQLIndex ix = SQLIndexes.indexes(neu).get(0);
        Table customers = (Table) ix.getSpannedClass();
        Column email = customers.getFeature().stream().filter(Column.class::isInstance).map(Column.class::cast)
                .filter(c -> "EMAIL".equals(c.getName())).findFirst().orElseThrow();
        ((SQLIndexColumn) ix.getIndexedFeature().get(0)).setFeature(email);

        TableDiff td = changed(differ.diff(old, neu), "CUSTOMERS");

        assertThat(td.indexesRenamed()).isEmpty();
        assertThat(td.indexesDropped()).extracting(SQLIndex::getName).containsExactly("IX_CUSTOMERS_NAME");
        assertThat(td.indexesAdded()).extracting(SQLIndex::getName).containsExactly("IX_CUSTOMERS_NAME");
    }

    @Test
    void heuristicOffLeavesDropPlusAdd() {
        TableDiff td = changed(differ.diff(schema(""), schema("_V2"),
                DiffSettings.defaults().withRenameHeuristic(false)), "CUSTOMERS");

        assertThat(td.indexesRenamed()).isEmpty();
        assertThat(td.indexesAdded()).hasSize(1);
        assertThat(td.indexesDropped()).hasSize(1);
        // a primary key rename needs no heuristic: same table, same columns
        assertThat(td.constraintsRenamed()).extracting(r -> r.newConstraint().getName())
                .containsExactly("PK_CUSTOMERS_V2");
    }

    @Test
    void markerResolvesARenameTheHeuristicCannot() {
        Schema old = schema("");
        Schema neu = schema("_V2");
        SQLIndex renamed = SQLIndexes.indexes(neu).get(0);
        ChangeMarkers.markRenamedFrom(renamed, "IX_CUSTOMERS_NAME");

        TableDiff td = changed(differ.diff(old, neu, DiffSettings.defaults().withRenameHeuristic(false)),
                "CUSTOMERS");

        assertThat(td.indexesRenamed()).hasSize(1);
        assertThat(td.indexesAdded()).isEmpty();
    }

    @Test
    void dialectRenamesWhereItCan() {
        List<String> sql = new MigrationEmitterImpl(new DdlGeneratorFactoryImpl())
                .emit(ChangePlanner.create().plan(differ.diff(schema(""), schema("_V2"))), new H2Dialect());

        assertThat(String.join("\n", sql)).contains("RENAME CONSTRAINT").contains("RENAME TO")
                .doesNotContain("DROP ");
    }

    @Test
    void withoutRenameConstraintConstraintsAreRecreatedAndPrimaryKeysLeftAlone() {
        List<String> sql = new MigrationEmitterImpl(new DdlGeneratorFactoryImpl())
                .emit(ChangePlanner.create().plan(differ.diff(schema(""), schema("_V2"))), new MariaDBDialect());
        String all = String.join("\n", sql);

        // MariaDB/MySQL cannot rename constraints: drop + add under the new name
        assertThat(all).contains("UQ_CUSTOMERS_EMAIL_V2").contains("CK_CUSTOMERS_ID_V2")
                .contains("FK_ORDERS_CUSTOMERS_V2");
        // ... but primary keys are unnamed there — nothing to do
        assertThat(all).doesNotContain("PK_CUSTOMERS");
        // the index itself can be renamed
        assertThat(all).contains("RENAME INDEX");
    }

    // fixture

    private static Schema schema(String suffix) {
        return KeyRenameFixtures.build("sales", suffix, "ID > 0");
    }

    private static TableDiff changed(SchemaDiff diff, String table) {
        return diff.tablesChanged().stream().filter(t -> table.equals(t.newTable().getName())).findFirst()
                .orElseThrow();
    }
}
