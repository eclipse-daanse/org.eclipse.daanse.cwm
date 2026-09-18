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
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.ConditionTimingType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.EventManipulationType;
import org.eclipse.daanse.cwm.resource.relational.ddl.internal.DdlGeneratorFactoryImpl;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.MigrationEmitterImpl;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.SchemaDifferImpl;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.support.TriggerFixtures;
import org.eclipse.daanse.sql.dialect.db.postgresql.PostgreSqlDialect;
import org.junit.jupiter.api.Test;

/** Trigger add / drop / change: detection, planning and the emitted SQL, offline. */
class TriggerDiffTest {

    private static final String BODY = "BEGIN RETURN NEW; END;";

    private final SchemaDiffer differ = new SchemaDifferImpl();

    @Test
    void addedTriggerIsCreatedLast() {
        SchemaDiff diff = differ.diff(schema(null), schema(BODY));
        assertThat(diff.tablesChanged().get(0).triggersAdded()).hasSize(1);

        List<ChangeOp> ops = ChangePlanner.create().plan(diff);
        assertThat(ops).hasSize(1).first().isInstanceOf(ChangeOp.CreateTrigger.class);
    }

    @Test
    void droppedTriggerIsDropped() {
        List<ChangeOp> ops = ChangePlanner.create().plan(differ.diff(schema(BODY), schema(null)));
        assertThat(ops).hasSize(1).first().isInstanceOf(ChangeOp.DropTrigger.class);
    }

    @Test
    void changedBodyIsDropPlusCreate() {
        TableDiff td = differ.diff(schema(BODY), schema("BEGIN RETURN OLD; END;")).tablesChanged().get(0);
        assertThat(td.triggersDropped()).hasSize(1);
        assertThat(td.triggersAdded()).hasSize(1);
    }

    @Test
    void unchangedTriggerIsNoDifference() {
        assertThat(differ.diff(schema(BODY), schema(BODY)).isEmpty()).isTrue();
    }

    @Test
    void postgresGetsFunctionPlusTriggerAndDropsBoth() {
        MigrationEmitter emitter = new MigrationEmitterImpl(new DdlGeneratorFactoryImpl());
        PostgreSqlDialect pg = new PostgreSqlDialect();

        String create = String.join("\n",
                emitter.emit(ChangePlanner.create().plan(differ.diff(schema(null), schema(BODY))), pg));
        assertThat(create).contains("FUNCTION").contains("TRG_CUSTOMERS_AUDIT_fn").contains("CREATE TRIGGER");

        String drop = String.join("\n",
                emitter.emit(ChangePlanner.create().plan(differ.diff(schema(BODY), schema(null))), pg));
        assertThat(drop).contains("DROP TRIGGER").contains("DROP FUNCTION");
    }

    private static Schema schema(String body) {
        return TriggerFixtures.build("sales", body, ConditionTimingType.BEFORE, EventManipulationType.INSERT);
    }
}
