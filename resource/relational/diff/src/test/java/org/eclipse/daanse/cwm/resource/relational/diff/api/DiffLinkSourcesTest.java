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
 * Switched identity sources for the differ. The heuristic detects a
 * rename only given an identical declaration—markers and dependencies must
 * detect it even when there is a SIMULTANEOUS structural change.
 */
class DiffLinkSourcesTest {

    private static final RelationalFactory R = RelationalFactory.eINSTANCE;

    private final SchemaDiffer differ = new SchemaDifferImpl();

    @Test
    void tagMarkerBeatsHeuristicOnRenamePlusTypeChange() {
        Schema oldS = schemaWith("customer", "name", Types.VARCHAR, 100);
        Schema newS = schemaWith("customer", "full_name", Types.VARCHAR, 255);
        Column renamed = firstColumn(newS);
        ChangeMarkers.markRenamedFrom(renamed, "name");

        SchemaDiff diff = differ.diff(oldS, newS,
                DiffSettings.defaults().withDependencyLinks(false).withRenameHeuristic(false));

        assertThat(diff.tablesChanged()).hasSize(1);
        assertThat(diff.tablesChanged().get(0).columnsRenamed()).hasSize(1);
        // Rename UND Groessenaenderung, nicht drop+add:
        assertThat(diff.tablesChanged().get(0).columnsDropped()).isEmpty();
        assertThat(diff.tablesChanged().get(0).columnsAdded()).isEmpty();
        assertThat(diff.tablesChanged().get(0).columnsChanged()).hasSize(1);
    }

    @Test
    void dependencyLinkWorksWhenBothModelsAreLoaded() {
        Schema oldS = schemaWith("customer", "name", Types.VARCHAR, 100);
        Schema newS = schemaWith("kunde", "name", Types.VARCHAR, 100);
        PredecessorLinks.link(table(newS, "kunde"), table(oldS, "customer"));

        SchemaDiff diff = differ.diff(oldS, newS,
                DiffSettings.defaults().withTagMarkers(false).withRenameHeuristic(false));

        assertThat(diff.tablesRenamed()).hasSize(1);
        assertThat(diff.tablesDropped()).isEmpty();
        assertThat(diff.tablesAdded()).isEmpty();
    }

    @Test
    void allSourcesOffFallsBackToDropPlusAdd() {
        Schema oldS = schemaWith("customer", "name", Types.VARCHAR, 100);
        Schema newS = schemaWith("kunde", "name", Types.VARCHAR, 100);
        PredecessorLinks.link(table(newS, "kunde"), table(oldS, "customer"));
        ChangeMarkers.markRenamedFrom(table(newS, "kunde"), "customer");

        SchemaDiff diff = differ.diff(oldS, newS,
                new DiffSettings(false, false, false, DiffSettings.Scope.FULL, null));

        assertThat(diff.tablesRenamed()).isEmpty();
        assertThat(diff.tablesDropped()).hasSize(1);
        assertThat(diff.tablesAdded()).hasSize(1);
    }

    @Test
    void dependencyWinsOverConflictingTag() {
        Schema oldS = schemaWith("customer", "name", Types.VARCHAR, 100);
        oldS.getOwnedElement().add(namedTable("orders"));
        Schema newS = schemaWith("kunde", "name", Types.VARCHAR, 100);
        // Tag behauptet orders, Dependency sagt customer — Dependency gewinnt:
        ChangeMarkers.markRenamedFrom(table(newS, "kunde"), "orders");
        PredecessorLinks.link(table(newS, "kunde"), table(oldS, "customer"));

        SchemaDiff diff = differ.diff(oldS, newS, DiffSettings.defaults());

        assertThat(diff.tablesRenamed()).hasSize(1);
        assertThat(diff.tablesRenamed().get(0).oldTable().getName()).isEqualTo("customer");
    }

    //  fixture

    private static Schema schemaWith(String tableName, String colName, int jdbc, int len) {
        Schema s = R.createSchema();
        s.setName("sales");
        SQLSimpleType t = R.createSQLSimpleType();
        t.setName("VARCHAR");
        t.setTypeNumber(jdbc);
        t.setCharacterMaximumLength((long) len);
        s.getOwnedElement().add(t);
        Table table = namedTable(tableName);
        s.getOwnedElement().add(table);
        Column c = R.createColumn();
        c.setName(colName);
        c.setType((Classifier) t);
        c.setIsNullable(NullableType.COLUMN_NULLABLE);
        table.getFeature().add(c);
        return s;
    }

    private static Table namedTable(String name) {
        Table table = R.createTable();
        table.setName(name);
        return table;
    }

    private static Table table(Schema s, String name) {
        return s.getOwnedElement().stream()
                .filter(Table.class::isInstance).map(Table.class::cast)
                .filter(t -> name.equals(t.getName())).findFirst().orElseThrow();
    }

    private static Column firstColumn(Schema s) {
        return s.getOwnedElement().stream()
                .filter(Table.class::isInstance).map(Table.class::cast)
                .flatMap(t -> t.getFeature().stream())
                .filter(Column.class::isInstance).map(Column.class::cast)
                .findFirst().orElseThrow();
    }
}
