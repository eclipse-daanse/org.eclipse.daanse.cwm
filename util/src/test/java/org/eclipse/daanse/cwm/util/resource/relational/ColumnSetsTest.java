/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.cwm.util.resource.relational;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.junit.jupiter.api.Test;

class ColumnSetsTest {

    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;

    private static Table tableWith(String... cols) {
        Table t = RF.createTable();
        for (String n : cols) {
            Column c = RF.createColumn();
            c.setName(n);
            t.getFeature().add(c);
        }
        return t;
    }

    @Test
    void columns_andFindColumn() {
        Table t = tableWith("ID", "NAME", "AGE");
        assertThat(ColumnSets.columns(t)).extracting(Column::getName).containsExactly("ID", "NAME", "AGE");
        assertThat(ColumnSets.findColumn(t, "NAME")).isPresent();
        assertThat(ColumnSets.findColumn(t, "MISSING")).isEmpty();
    }

    @Test
    void nullSafe() {
        assertThat(ColumnSets.columns(null)).isEmpty();
        assertThat(ColumnSets.columnStream(null)).isEmpty();
        assertThat(ColumnSets.findColumn(null, "X")).isEmpty();
        assertThat(ColumnSets.findColumn(RF.createTable(), null)).isEmpty();
    }
}
