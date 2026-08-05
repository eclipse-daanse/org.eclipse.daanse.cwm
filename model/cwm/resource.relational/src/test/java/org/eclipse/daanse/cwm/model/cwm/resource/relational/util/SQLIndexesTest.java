/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.model.cwm.resource.relational.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndex;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLIndexColumn;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.InverseReferences;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.junit.jupiter.api.Test;

class SQLIndexesTest {

    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;

    private static Table table(Schema schema, String name, String... cols) {
        Table t = RF.createTable();
        t.setName(name);
        for (String cn : cols) {
            Column c = RF.createColumn();
            c.setName(cn);
            t.getFeature().add(c);
        }
        schema.getOwnedElement().add(t);
        return t;
    }

    private static SQLIndex index(Schema schema, Table t, String name, boolean unique, Column... cols) {
        SQLIndex idx = RF.createSQLIndex();
        idx.setName(name);
        idx.setIsUnique(unique);
        idx.setSpannedClass(t);
        for (Column c : cols) {
            SQLIndexColumn ic = RF.createSQLIndexColumn();
            ic.setFeature(c);
            idx.getIndexedFeature().add(ic);
        }
        schema.getOwnedElement().add(idx);
        return idx;
    }

    @Test
    void spanning_filtersBySpannedClass() {
        Schema schema = RF.createSchema();
        Table emp = table(schema, "EMP", "ID");
        Table dept = table(schema, "DEPT", "ID");
        SQLIndex empIdx = index(schema, emp, "IX_EMP", false, (Column) emp.getFeature().get(0));
        index(schema, dept, "IX_DEPT", false, (Column) dept.getFeature().get(0));

        assertThat(SQLIndexes.indexes(schema)).hasSize(2);
        assertThat(SQLIndexes.spanning(emp)).containsExactly(empIdx);
    }

    @Test
    void spanning_tableNothingPointsAt_empty() {
        Table orphan = RF.createTable();
        orphan.setName("ORPHAN");
        assertThat(SQLIndexes.spanning(orphan)).isEmpty();
        assertThat(SQLIndexes.uniqueSpanning(orphan)).isEmpty();
    }

    /**
     * CWM states no ownership rule for Index — unlike {@code UniqueKeyOwnedByClass} for
     * UniqueKey — so an Index may sit outside the table's schema and still span it.
     * Reading {@code spannedClass} in reverse finds it; scanning the table's own schema,
     * as an earlier version did, does not.
     */
    @Test
    void spanning_findsIndexOutsideTheTablesSchema() {
        ResourceSet set = new ResourceSetImpl();
        InverseReferences.install(set);
        Resource resource = new ResourceImpl(URI.createURI("test:/indexes"));
        set.getResources().add(resource);

        Schema data = RF.createSchema();
        data.setName("DATA");
        Schema admin = RF.createSchema();
        admin.setName("ADMIN");
        resource.getContents().add(data);
        resource.getContents().add(admin);

        Table emp = table(data, "EMP", "ID");
        // the index lives in ADMIN, the table in DATA
        SQLIndex remote = index(admin, emp, "IX_REMOTE", true, (Column) emp.getFeature().get(0));

        assertThat(SQLIndexes.indexes(data)).isEmpty();
        assertThat(SQLIndexes.spanning(emp)).containsExactly(remote);
        assertThat(SQLIndexes.uniqueSpanning(emp)).containsExactly(remote);
    }

    /** Stream twins agree with their list counterparts. */
    @Test
    void streamTwinsMatch() {
        Schema schema = RF.createSchema();
        Table emp = table(schema, "EMP", "ID");
        SQLIndex idx = index(schema, emp, "UX", true, (Column) emp.getFeature().get(0));

        assertThat(SQLIndexes.spanningStream(emp)).containsExactly(idx);
        assertThat(SQLIndexes.uniqueSpanningStream(emp)).containsExactly(idx);
        assertThat(SQLIndexes.spanningStream(null)).isEmpty();
    }

    @Test
    void uniqueSpanning_returnsOnlyUniqueIndexes() {
        Schema schema = RF.createSchema();
        Table emp = table(schema, "EMP", "ID", "EMAIL");
        Column id = (Column) emp.getFeature().get(0);
        Column email = (Column) emp.getFeature().get(1);
        index(schema, emp, "IX_PLAIN", false, id);
        SQLIndex uniqueIdx = index(schema, emp, "UX_EMAIL", true, email);

        assertThat(SQLIndexes.uniqueSpanning(emp)).containsExactly(uniqueIdx);
    }

    @Test
    void columns_inIndexOrder() {
        Schema schema = RF.createSchema();
        Table emp = table(schema, "EMP", "A", "B");
        Column a = (Column) emp.getFeature().get(0);
        Column b = (Column) emp.getFeature().get(1);
        SQLIndex idx = index(schema, emp, "IX", false, b, a);

        assertThat(SQLIndexes.columns(idx)).containsExactly(b, a);
        assertThat(SQLIndexes.columns(null)).isEmpty();
    }
}
