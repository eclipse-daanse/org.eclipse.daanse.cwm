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

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.Classifier;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.resource.relational.diff.internal.SchemaDifferImpl;
import org.junit.jupiter.api.Test;

/**
 * Artifact case:
 * The old schema is merely a stub containing the affected elements—anything the
 * stub does not recognize must not appear as added or dropped.
 *
 */
class PartialDiffTest {

    private static final RelationalFactory R = RelationalFactory.eINSTANCE;

    @Test
    void stubOldSchemaSuppressesAddedOutsideTheStubSet() {
        // Neu: volles Schema mit customer (email jetzt 320) und orders.
        Schema newS = R.createSchema();
        newS.setName("sales");
        SQLSimpleType tVar = varchar(newS, 320);
        Table customer = table(newS, "customer");
        column(customer, "email", tVar);
        table(newS, "orders");

        // Alt-Stub: NUR customer.email in der alten Form (255) — orders fehlt bewusst.
        Schema oldStub = R.createSchema();
        oldStub.setName("sales");
        SQLSimpleType tVarOld = varchar(oldStub, 255);
        Table customerOld = table(oldStub, "customer");
        column(customerOld, "email", tVarOld);

        SchemaDiff diff = new SchemaDifferImpl().diff(oldStub, newS,
                DiffSettings.defaults().withScope(DiffSettings.Scope.PARTIAL));

        // Nur die Aenderung am Stub-Element, kein "orders wurde hinzugefuegt":
        assertThat(diff.tablesAdded()).isEmpty();
        assertThat(diff.tablesDropped()).isEmpty();
        assertThat(diff.tablesChanged()).hasSize(1);
        assertThat(diff.tablesChanged().get(0).columnsChanged()).hasSize(1);
    }

    @Test
    void unresolvableRenamedFromMarkerIsSuppressedNotAddedInPartialScope() {
        //New: "customer" carries `renamed From="custommer"` (typo) — the old stub does not contain this name.
        Schema newS = R.createSchema();
        newS.setName("sales");
        SQLSimpleType tVar = varchar(newS, 100);
        Table customer = table(newS, "customer");
        column(customer, "name", tVar);
        ChangeMarkers.markRenamedFrom(customer, "custommer");

        Schema oldStub = R.createSchema();
        oldStub.setName("sales");

        SchemaDiff diff = new SchemaDifferImpl().diff(oldStub, newS,
                DiffSettings.defaults().withScope(DiffSettings.Scope.PARTIAL));

        // Neither Rename nor Add — the marker points to nothing; PARTIAL
        // suppresses the element, just like any other element outside the stub.
        // To avoid this, use ChangePlanner.planMarkersOnly.
        assertThat(diff.tablesRenamed()).isEmpty();
        assertThat(diff.tablesAdded()).isEmpty();
        assertThat(diff.tablesChanged()).isEmpty();
    }

    //  fixture

    private static SQLSimpleType varchar(Schema s, int len) {
        SQLSimpleType t = R.createSQLSimpleType();
        t.setName("VARCHAR");
        t.setTypeNumber(Types.VARCHAR);
        t.setCharacterMaximumLength((long) len);
        s.getOwnedElement().add(t);
        return t;
    }

    private static Table table(Schema s, String name) {
        Table t = R.createTable();
        t.setName(name);
        s.getOwnedElement().add(t);
        return t;
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
