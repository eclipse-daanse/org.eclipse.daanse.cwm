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

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.ColumnSets;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.Schemas;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.MigrationEmitterImpl;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.SchemaDifferImpl;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.support.ViewFixtures;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.db.mssqlserver.MicrosoftSqlServerDialect;
import org.eclipse.daanse.sql.dialect.db.postgresql.PostgreSqlDialect;
import org.junit.jupiter.api.Test;

/** When a changed view is replaced in place and when it is dropped and re-created, offline. */
class ViewReplaceTest {

    private static final Dialect PG = new PostgreSqlDialect();

    private final SchemaDiffer differ = new SchemaDifferImpl();

    @Test
    void appendedColumnIsReplacedInPlace() {
        List<ChangeOp> ops = plan(view("ID", "EMAIL"), view("ID", "EMAIL", "NAME"));
        assertThat(ops).singleElement().isInstanceOf(ChangeOp.ReplaceView.class);
    }

    @Test
    void removedOrReorderedColumnsAreDropAndCreate() {
        assertThat(plan(view("ID", "EMAIL", "NAME"), view("ID", "EMAIL")))
                .extracting(op -> op.getClass().getSimpleName()).containsExactly("DropView", "CreateView");
        assertThat(plan(view("ID", "EMAIL"), view("EMAIL", "ID")))
                .extracting(op -> op.getClass().getSimpleName()).containsExactly("DropView", "CreateView");
    }

    @Test
    void droppedTableColumnKeepsDropAndCreate() {
        Schema newSchema = view("ID", "EMAIL", "NAME");
        Table customers = Schemas.tables(newSchema).get(0);
        customers.getFeature().remove(ColumnSets.columns(customers).stream()
                .filter(c -> "NAME".equals(c.getName())).findFirst().orElseThrow());
        List<ChangeOp> ops = plan(view("ID", "EMAIL"), newSchema);
        assertThat(ops.get(0)).isInstanceOf(ChangeOp.DropView.class);
        assertThat(ops).noneMatch(ChangeOp.ReplaceView.class::isInstance);
    }

    @Test
    void emitterUsesOrReplaceWhereTheDialectHasIt() {
        MigrationEmitter emitter = new MigrationEmitterImpl(new DdlGeneratorFactoryImpl());
        List<ChangeOp> ops = plan(view("ID", "EMAIL"), view("ID", "EMAIL", "NAME"));

        assertThat(emitter.emit(ops, PG)).singleElement().asString().startsWith("CREATE OR REPLACE VIEW");
        assertThat(emitter.emit(ops, new MicrosoftSqlServerDialect()))
                .satisfiesExactly(s -> assertThat(s).startsWith("DROP VIEW"),
                        s -> assertThat(s).startsWith("CREATE VIEW"));
    }

    private List<ChangeOp> plan(Schema oldSchema, Schema newSchema) {
        return ChangePlanner.create().plan(differ.diff(oldSchema, newSchema));
    }

    private static Schema view(String... columns) {
        return ViewFixtures.build("sales", PG, List.of(columns));
    }
}
