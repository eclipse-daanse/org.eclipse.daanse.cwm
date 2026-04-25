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

import org.eclipse.daanse.cwm.model.cwm.objectmodel.instance.DataSlot;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.instance.InstanceFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Row;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RowSet;
import org.junit.jupiter.api.Test;

class RowsTest {

    private static final RelationalFactory RF = RelationalFactory.eINSTANCE;
    private static final InstanceFactory IF = InstanceFactory.eINSTANCE;

    @Test
    void rowSet_rows_filtersOnlyRows() {
        RowSet rs = RF.createRowSet();
        Row r = RF.createRow();
        rs.getOwnedElement().add(r);
        assertThat(RowSets.rows(rs)).containsExactly(r);
        assertThat(RowSets.rowStream(rs)).containsExactly(r);
    }

    @Test
    void slots_dataValue_andValueOf() {
        Column col = RF.createColumn();
        col.setName("A");
        DataSlot ds = IF.createDataSlot();
        ds.setFeature(col);
        ds.setDataValue("hello");

        Row row = RF.createRow();
        row.getSlot().add(ds);

        assertThat(Rows.slots(row)).containsExactly(ds);
        assertThat(Rows.slotStream(row)).containsExactly(ds);
        assertThat(Rows.valueOf(row, col)).hasValue("hello");

        Column other = RF.createColumn();
        other.setName("B");
        assertThat(Rows.valueOf(row, other)).isEmpty();
    }

    @Test
    void nullSafe() {
        assertThat(RowSets.rows(null)).isEmpty();
        assertThat(RowSets.rowStream(null)).isEmpty();
        assertThat(Rows.slots(null)).isEmpty();
        assertThat(Rows.valueOf(null, RF.createColumn())).isEmpty();
        assertThat(Rows.valueOf(RF.createRow(), null)).isEmpty();
    }
}
